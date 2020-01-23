package io.opentelemetry.auto.bootstrap.instrumentation.java.concurrent;

import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.trace.Span;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class State {

  public static ContextStore.Factory<State> FACTORY =
      new ContextStore.Factory<State>() {
        @Override
        public State create() {
          return new State();
        }
      };

  private final AtomicReference<Span> parentSpanRef = new AtomicReference<>(null);

  private State() {}

  public boolean setParentSpan(final Span parentSpan) {
    final boolean result = parentSpanRef.compareAndSet(null, parentSpan);
    if (!result) {
      log.debug(
          "Failed to set parent span because another parent span is already set {}: new: {}, old: {}",
          this,
          parentSpan,
          parentSpanRef.get());
    }
    return result;
  }

  public void clearParentSpan() {
    parentSpanRef.set(null);
  }

  public Span getAndResetParentSpan() {
    return parentSpanRef.getAndSet(null);
  }
}
