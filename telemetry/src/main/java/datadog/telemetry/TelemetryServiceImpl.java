package datadog.telemetry;

import datadog.telemetry.api.AppDependenciesLoaded;
import datadog.telemetry.api.AppIntegrationsChange;
import datadog.telemetry.api.AppStarted;
import datadog.telemetry.api.Dependency;
import datadog.telemetry.api.GenerateMetrics;
import datadog.telemetry.api.Integration;
import datadog.telemetry.api.KeyValue;
import datadog.telemetry.api.Metric;
import datadog.telemetry.api.Payload;
import datadog.telemetry.api.RequestType;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TelemetryServiceImpl implements TelemetryService {

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

  private long lastPreparationTimestamp;

  public TelemetryServiceImpl(HttpUrl agentUrl) {
//    HttpUrl httpUrl = agentUrl.newBuilder().addPathSegments(API_ENDPOINT).build();
    HttpUrl httpUrl = HttpUrl.parse("http://127.0.0.1:12345");
    this.requestBuilder = new RequestBuilder(httpUrl);
  }

  @Override
  public void addStartedRequest() {
    Payload payload =
        new AppStarted()
            ._configuration(drainOrNull(configurations))
            .integrations(drainOrNull(integrations))
            .dependencies(drainOrNull(dependencies));

    queue.offer(requestBuilder.build(RequestType.APP_STARTED, payload));
  }

  @Override
  public Request appClosingRequest() {
    return requestBuilder.build(RequestType.APP_CLOSING);
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

  Queue<Request> prepareRequests() {
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

    // Heartbeat request if needed
    long curTime = System.currentTimeMillis();
    if (queue.isEmpty() && curTime - lastPreparationTimestamp > HEARTBEAT_INTERVAL) {
      Request request = requestBuilder.build(RequestType.APP_HEARTBEAT);
      queue.offer(request);
    }
    lastPreparationTimestamp = curTime;

    return queue;
  }

  private static <T> List<T> drainOrNull(BlockingQueue<T> srcQueue) {
    List<T> list = new LinkedList<>();
    int drained = srcQueue.drainTo(list);
    if (drained > 0) {
      return list;
    }
    return null;
  }
}
