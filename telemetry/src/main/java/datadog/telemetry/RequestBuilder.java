package datadog.telemetry;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import datadog.telemetry.api.ApiVersion;
import datadog.telemetry.api.Application;
import datadog.telemetry.api.Host;
import datadog.telemetry.api.Payload;
import datadog.telemetry.api.RequestType;
import datadog.telemetry.api.Telemetry;
import datadog.trace.api.Config;
import datadog.trace.api.Platform;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestBuilder {

  private static final ApiVersion API_VERSION = ApiVersion.V1;
  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  private static final Logger log = LoggerFactory.getLogger(RequestBuilder.class);
  private static final JsonAdapter<Telemetry> JSON_ADAPTER =
      new Moshi.Builder().add(new JsonAdapter.Factory() {
        @Nullable
        @Override
        public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, final Moshi moshi) {
          Class<?> rawType = Types.getRawType(type);
          if (rawType != Payload.class) {
            return null;
          }

          return new JsonAdapter<Payload>() {
            @Override
            public Payload fromJson(JsonReader reader) throws IOException {
              return null;
            }

            @Override
            public void toJson(JsonWriter writer, @Nullable Payload value) throws IOException {
              if (value == null) {
                writer.nullValue();
                return;
              }

              Class<? extends Payload> actualClass = value.getClass();
              if (actualClass == Payload.class) {
                throw new RuntimeException("Tried to serialize generic payload");
              }

              JsonAdapter<? extends Payload> adapter = moshi.adapter(actualClass);
              ((JsonAdapter<Payload>) adapter).toJson(writer, value);
            }
          };
        }
      }).build().adapter(Telemetry.class);
  private static final AtomicLong SEQ_ID = new AtomicLong();
  private final HttpUrl httpUrl;
  private final Application application;
  private final Host host;
  private final String runtimeId;

  public RequestBuilder(HttpUrl httpUrl) {
    this.httpUrl = httpUrl;
    Config config = Config.get();

    String tracerVersion = "0.100.0";
    // TODO: retrieve tracer version
    //    try {
    //      tracerVersion = AgentJar.getAgentVersion();
    //    } catch (IOException e) {
    //      log.error("Unable to get tracer version ", e);
    //    }

    this.runtimeId = config.getRuntimeId();
    this.application =
        new Application()
            .env(config.getEnv())
            .serviceName(config.getServiceName())
            .serviceVersion(config.getVersion())
            .tracerVersion(tracerVersion)
            .languageName("jvm")
            .languageVersion(Platform.getLangVersion())
            .runtimeName(Platform.getRuntimeVendor())
            .runtimeVersion(Platform.getRuntimeVersion())
            .runtimePatches(Platform.getRuntimePatches())
            // TODO: get list of products appsec, profiler, etc.
            // .products()
    ;
    this.host = new Host()
        // TODO: retrieve host information
        // .hostname("")
        // .containerId("")
        // .os("")
        // .osVersion("")
        // .kernelName("")
        // .kernelRelease("")
        // .kernelVersion("")
    ;
  }

  public Request build(RequestType requestType) {
    return build(requestType, null);
  }

  public Request build(RequestType requestType, Payload payload) {
    Telemetry telemetry =
        new Telemetry()
            .apiVersion(API_VERSION)
            .requestType(requestType)
            .tracerTime(System.currentTimeMillis() / 1000L)
            .runtimeId(runtimeId)
            .seqId(SEQ_ID.incrementAndGet())
            // .debug()
            .application(application)
            .host(host)
            .payload(payload);

    String json = JSON_ADAPTER.toJson(telemetry);
    RequestBody body = RequestBody.create(JSON, json);

    return new Request.Builder()
        .url(httpUrl)
        // calculated by OkHttp?
        // .addHeader("Content-Type", "application/json")
        // .addHeader("Content-Length", body.contentLength())
        .addHeader("DD-Telemetry-API-Version", API_VERSION.toString())
        .addHeader("DD-Telemetry-Request-Type", requestType.toString())
        .post(body)
        .build();
  }
}
