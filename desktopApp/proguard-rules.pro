# kotlin-logging ships optional adapters for logging backends, coroutine MDC, and
# GraalVM native-image. Mission Visualization uses none of those integrations.
-dontwarn ch.qos.logback.**
-dontwarn kotlinx.coroutines.slf4j.**
-dontwarn com.oracle.svm.core.annotate.**

# Ktor discovers this JSON serialization extension through ServiceLoader. ProGuard
# does not infer the provider reference from META-INF/services and would otherwise
# keep the descriptor while removing the implementation from release packages.
-keep class io.ktor.serialization.kotlinx.json.KotlinxSerializationJsonExtensionProvider {
    public <init>();
}
