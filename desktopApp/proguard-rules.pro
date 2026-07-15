# kotlin-logging ships optional adapters for logging backends, coroutine MDC, and
# GraalVM native-image. Mission Visualization uses none of those integrations.
-dontwarn ch.qos.logback.**
-dontwarn kotlinx.coroutines.slf4j.**
-dontwarn com.oracle.svm.core.annotate.**
