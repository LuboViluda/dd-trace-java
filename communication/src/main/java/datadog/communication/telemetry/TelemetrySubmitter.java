package datadog.communication.telemetry;

import java.util.Queue;
import okhttp3.Request;

public interface TelemetrySubmitter extends Runnable {

  void flushNow();

  interface Callback {
    Queue<Request> prepareRequests();
  }
}
