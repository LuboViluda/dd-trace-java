package com.datadog.appsec.telemetry;

import com.datadog.appsec.dependency.Dependency;
import com.datadog.appsec.dependency.DependencyService;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TelemetryRunnable implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(TelemetryRunnable.class);
  private static final int LOOP_SLEEP_MILLIS = 30_000;
  private final DependencyService dependencyService;

  public TelemetryRunnable(DependencyService dependencyService) {
    this.dependencyService = dependencyService;
  }

  @Override
  public void run() {
    while (!Thread.interrupted()) {
      try {
        runOnce();
        Thread.sleep(LOOP_SLEEP_MILLIS);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void runOnce() {
    Collection<Dependency> dependencies = this.dependencyService.determineNewDependencies();
    for (Dependency dep : dependencies) {
      log.warn("Dependency detected: {}", dep.toString());
    }
  }
}
