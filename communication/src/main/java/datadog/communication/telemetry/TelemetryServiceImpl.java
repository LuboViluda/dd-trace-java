package datadog.communication.telemetry;

import datadog.communication.ddagent.SharedCommunicationObjects;
import datadog.communication.telemetry.api.*;
import datadog.trace.util.AgentThreadFactory;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TelemetryServiceImpl implements TelemetryService, TelemetrySubmitter.Callback {

  private static final String API_ENDPOINT = "/telemetry/proxy/api/v2/apmtelemetry";
  private static final int HEARTBEAT_INTERVAL = 60 * 1000; // milliseconds

  private static final Logger log = LoggerFactory.getLogger(TelemetryServiceImpl.class);

  private final RequestBuilder requestBuilder;

  private final BlockingQueue<KeyValue> configurations = new LinkedBlockingQueue<>();
  private final BlockingQueue<Integration> integrations = new LinkedBlockingQueue<>();
  private final BlockingQueue<Dependency> dependencies = new LinkedBlockingQueue<>();
  private final BlockingQueue<Metric> metrics =
      new LinkedBlockingQueue<>(1024); // recommended capacity?

  private final Queue<Request> queue = new ArrayBlockingQueue<>(16);
  private final TelemetrySubmitterImpl submitter;
  private final Thread thread;
  private long lastHeartbeatTimestamp;

  public TelemetryServiceImpl(
      SharedCommunicationObjects sco, AgentThreadFactory agentThreadFactory) {
    HttpUrl httpUrl = sco.agentUrl.newBuilder().addPathSegments(API_ENDPOINT).build();
    this.requestBuilder = new RequestBuilder(httpUrl);
    this.submitter = new TelemetrySubmitterImpl(sco.okHttpClient, this);
    this.thread = agentThreadFactory.newThread(submitter);
  }

  @Override
  public void init() {
    thread.start();
  }

  @Override
  public void close() throws IOException {
    this.thread.interrupt();
    try {
      this.thread.join(5000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted waiting for thread " + this.thread.getName() + "to join");
    }
  }

  @Override
  public void appStarted() {
    Payload payload =
        new AppStarted()
            ._configuration(drainOrNull(configurations))
            .integrations(drainOrNull(integrations))
            .dependencies(drainOrNull(dependencies));

    Request request = requestBuilder.build(RequestType.APP_STARTED, payload);
    queue.offer(request);
  }

  @Override
  public void appClosing() {
    Request request = requestBuilder.build(RequestType.APP_CLOSING);
    if (queue.offer(request)) {
      // try to send app closing message instantly before process terminated
      submitter.flushNow();
    }
  }

  @Override
  public boolean addConfiguration(KeyValue configuration) {
    return this.configurations.offer(configuration);
  }

  @Override
  public boolean addDependency(Dependency dependency) {
    return this.dependencies.offer(dependency);
  }

  @Override
  public boolean addIntegration(Integration integration) {
    return this.integrations.offer(integration);
  }

  @Override
  public boolean addMetric(Metric metric) {
    return this.metrics.offer(metric);
  }

  @Override
  public Queue<Request> prepareRequests() {
    // New integrations
    if (!integrations.isEmpty()) {
      Payload payload = new AppIntegrationsChange().integrations(drainOrNull(integrations));
      Request request = requestBuilder.build(RequestType.APP_INTEGRATIONS_CHANGE, payload);
      queue.offer(request);
    }

    // New dependencies
    if (!dependencies.isEmpty()) {
      Payload payload = new AppDependenciesLoaded().dependencies(drainOrNull(dependencies));
      Request request = requestBuilder.build(RequestType.APP_DEPENDENCIES_LOADED, payload);
      queue.offer(request);
    }

    // New metrics
    if (!metrics.isEmpty()) {
      Payload payload =
          new GenerateMetrics()
              .namespace("appsec")
              .libLanguage("java")
              .libVersion("0.100.0")
              .series(drainOrNull(metrics));
      Request request = requestBuilder.build(RequestType.GENERATE_METRICS, payload);
      queue.offer(request);
    }

    // Heartbeat request if need
    if (System.currentTimeMillis() - lastHeartbeatTimestamp > HEARTBEAT_INTERVAL) {
      Request request = requestBuilder.build(RequestType.APP_HEARTBEAT);
      queue.offer(request);
      lastHeartbeatTimestamp = System.currentTimeMillis();
    }

    return queue;
  }

  private <T> List<T> drainOrNull(BlockingQueue<T> srcQueue) {
    List<T> list = new LinkedList<>();
    int drained = srcQueue.drainTo(list);
    if (drained > 0) {
      return list;
    }
    return null;
  }
}
