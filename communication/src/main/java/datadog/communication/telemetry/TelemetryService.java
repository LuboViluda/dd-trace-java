package datadog.communication.telemetry;

import datadog.communication.telemetry.api.Dependency;
import datadog.communication.telemetry.api.Integration;
import datadog.communication.telemetry.api.KeyValue;
import datadog.communication.telemetry.api.Metric;
import java.io.Closeable;
import java.io.IOException;

public interface TelemetryService extends Closeable {

  void init();

  @Override
  void close() throws IOException;

  void appStarted();

  void appClosing();

  boolean addConfiguration(KeyValue configuration);

  boolean addDependency(Dependency dependency);

  boolean addIntegration(Integration integration);

  boolean addMetric(Metric metric);
}
