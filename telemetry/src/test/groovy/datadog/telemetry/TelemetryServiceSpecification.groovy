package datadog.telemetry

import datadog.telemetry.api.AppStarted
import datadog.telemetry.api.KeyValue
import datadog.telemetry.api.Payload
import datadog.telemetry.api.RequestType
import datadog.trace.api.time.TimeSource
import okhttp3.Request
import spock.lang.Specification

class TelemetryServiceSpecification extends Specification {
  private final Request REQUEST = new Request.Builder()
    .url('https://example.com').build()

  TimeSource timeSource = Mock()
  RequestBuilder requestBuilder = Mock()
  TelemetryServiceImpl telemetryService =
    new TelemetryServiceImpl(requestBuilder, timeSource)

  void 'addStartedRequest adds app_started event'() {
    when:
    telemetryService.addStartedRequest()

    then:
    1 * requestBuilder.build(RequestType.APP_STARTED, _) >> REQUEST
    telemetryService.queue.peek().is(REQUEST)
  }

  void 'appClosingRequests returns an app_closing event'() {
    when:
    Request req = telemetryService.appClosingRequest()

    then:
    1 * requestBuilder.build(RequestType.APP_CLOSING) >> REQUEST
    req.is(REQUEST)
  }

  void 'added configuration pairs are reported in app_start'() {
    when:
    def value = new KeyValue(name: 'my name', value: 'my value')
    telemetryService.addConfiguration(value)
    telemetryService.addStartedRequest()

    then:
    1 * requestBuilder.build(RequestType.APP_STARTED, { AppStarted p ->
      p.configuration.first().with {
        it.name == 'my name' && it.value == 'my value'
      }
    }) >> REQUEST
  }

  void ''() {

  }
}
