package datadog.trace.instrumentation.jaxrs1;

import static datadog.trace.agent.tooling.bytebuddy.matcher.ClassLoaderMatchers.hasClassesNamed;
import static datadog.trace.agent.tooling.bytebuddy.matcher.DDElementMatchers.hasSuperMethod;
import static datadog.trace.agent.tooling.bytebuddy.matcher.DDElementMatchers.safeHasSuperType;
import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.namedOneOf;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.jaxrs1.JaxRsAnnotationsDecorator.DECORATE;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class JaxRsAnnotationsInstrumentation extends Instrumenter.Tracing
    implements Instrumenter.ForTypeHierarchy {

  private static final String JAX_ENDPOINT_OPERATION_NAME = "jax-rs.request";

  public JaxRsAnnotationsInstrumentation() {
    super("jax-rs", "jaxrs", "jax-rs-annotations");
  }

  // this is required to make sure instrumentation won't apply to jax-rs 2
  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return not(hasClassesNamed("javax.ws.rs.container.AsyncResponse"))
        // Optimization for expensive typeMatcher.
        .and(hasClassesNamed("javax.ws.rs.Path"));
  }

  @Override
  public ElementMatcher<TypeDescription> hierarchyMatcher() {
    return safeHasSuperType(
        isAnnotatedWith(named("javax.ws.rs.Path"))
            .<TypeDescription>or(declaresMethod(isAnnotatedWith(named("javax.ws.rs.Path")))));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.tooling.ClassHierarchyIterable",
      "datadog.trace.agent.tooling.ClassHierarchyIterable$ClassIterator",
      packageName + ".JaxRsAnnotationsDecorator",
    };
  }

  @Override
  public void adviceTransformations(AdviceTransformation transformation) {
    transformation.applyAdvice(
        isMethod()
            .and(
                hasSuperMethod(
                    isAnnotatedWith(
                        namedOneOf(
                            "javax.ws.rs.Path",
                            "javax.ws.rs.DELETE",
                            "javax.ws.rs.GET",
                            "javax.ws.rs.HEAD",
                            "javax.ws.rs.OPTIONS",
                            "javax.ws.rs.POST",
                            "javax.ws.rs.PUT")))),
        JaxRsAnnotationsInstrumentation.class.getName() + "$JaxRsAnnotationsAdvice");
  }

  public static class JaxRsAnnotationsAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope nameSpan(
        @Advice.This final Object target, @Advice.Origin final Method method) {
      // Rename the parent span according to the path represented by these annotations.
      final AgentSpan parent = activeSpan();

      final AgentSpan span = startSpan(JAX_ENDPOINT_OPERATION_NAME);
      span.setMeasured(true);
      DECORATE.onJaxRsSpan(span, parent, target.getClass(), method);
      DECORATE.afterStart(span);

      final AgentScope scope = activateSpan(span);
      scope.setAsyncPropagation(true);
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final AgentScope scope, @Advice.Thrown final Throwable throwable) {
      final AgentSpan span = scope.span();
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      scope.close();
      scope.span().finish();
    }
  }
}
