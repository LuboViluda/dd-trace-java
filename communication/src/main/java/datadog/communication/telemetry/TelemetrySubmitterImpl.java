package datadog.communication.telemetry;

import java.io.IOException;
import java.util.Queue;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TelemetrySubmitterImpl implements Runnable {
  private static final double BACKOFF_INITIAL = 3.0d;
  private static final double BACKOFF_BASE = 3.0d;
  private static final double BACKOFF_MAX_EXPONENT = 3.0d;

  private static final Logger log = LoggerFactory.getLogger(TelemetrySubmitterImpl.class);

  private final OkHttpClient okHttpClient;
  private int consecutiveFailures;
  private final ThreadSleeper threadSleeper;
  private final TelemetrySubmitter.Callback callback;

  public TelemetrySubmitterImpl(OkHttpClient okHttpClient, TelemetrySubmitter.Callback callback) {
    this.okHttpClient = okHttpClient;
    this.callback = callback;
    this.threadSleeper = new ThreadSleeper();
  }

  @Override
  public void run() {
    //      if (testingLatch != null) {
    //        testingLatch.countDown();
    //      }
    while (!Thread.interrupted()) {
      try {
        boolean success = mainLoopIteration();
        //          if (testingLatch != null) {
        //            testingLatch.countDown();
        //          }
        if (success) {
          successWait();
        } else {
          failureWait();
        }
      } catch (InterruptedException e) {
        log.info("Interrupted; exiting");
        Thread.currentThread().interrupt();
      }
    }
  }

  private boolean mainLoopIteration() throws InterruptedException {
    Queue<Request> queue = callback.prepareRequests();
    if (!queue.isEmpty()) {
      Request request;
      while ((request = queue.peek()) != null) {
        if (!sendRequest(request)) {
          return false;
        }
        // remove request from queue, in case of success submitting
        queue.poll();
      }
    }
    return true;
  }

  private void successWait() {
    consecutiveFailures = 0;
    int waitSeconds = 60;
    threadSleeper.sleep(waitSeconds * 1000L);
    //      if (testingLatch != null && testingLatch.getCount() > 0) {
    //        waitSeconds = 0;
    //      }
    //      try {
    //      } catch (InterruptedException e) {
    //        Thread.currentThread().interrupt();
    //      }
  }

  private void failureWait() {
    double waitSeconds;
    consecutiveFailures++;
    waitSeconds =
        BACKOFF_INITIAL
            * Math.pow(
                BACKOFF_BASE, Math.min((double) consecutiveFailures - 1, BACKOFF_MAX_EXPONENT));
    //      if (testingLatch != null && testingLatch.getCount() > 0) {
    //        waitSeconds = 0;
    //      }
    log.warn(
        "Last attempt to send telemetry failed; " + "will retry in {} seconds (num failures: {})",
        waitSeconds,
        consecutiveFailures);
    threadSleeper.sleep((long) (waitSeconds * 1000L));
    //      try {
    //        Thread.sleep((long) (waitSeconds * 1000L));
    //      } catch (InterruptedException e) {
    //        Thread.currentThread().interrupt();
    //      }
  }

  // Instantly send all queued request
  public void flushNow() {
    threadSleeper.awake();
  }

  private boolean sendRequest(Request request) {
    Response response;
    try {
      response = okHttpClient.newCall(request).execute();
    } catch (IOException e) {
      log.warn("IOException on HTTP request to Telemetry Intake Service", e);
      return false;
    }

    if (response.code() != 202) {
      log.warn(
          "Telemetry Intake Service responded with: " + response.code() + " " + response.message());
      return false;
    }
    return true;
  }

  private static class ThreadSleeper {
    private final Object monitor = new Object();
    private boolean wasWoken = false;

    public void sleep(long timeout) {
      synchronized (monitor) {
        if (!wasWoken) {
          try {
            monitor.wait(timeout);
          } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
          }
        }
        wasWoken = false;
      }
    }

    public synchronized void awake() {
      synchronized (monitor) {
        wasWoken = true;
        monitor.notify();
      }
    }
  }
}
