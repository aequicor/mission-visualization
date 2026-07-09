package io.aequicor.visualization.engine.scene.runtime

/**
 * Source of ids for transient runtime entities (snapshots, transition instances). Injected
 * so the runtime stays deterministic: the same event/delta sequence with the same generator
 * yields the same ids — the mandated deterministic-replay property. Never `Math.random`.
 */
fun interface IdGenerator {
    fun next(prefix: String): String
}

/** Monotonic `<prefix>_<n>` ids; the default deterministic generator. */
class SequentialIdGenerator : IdGenerator {
    private var counter: Int = 0

    override fun next(prefix: String): String = "${prefix}_${counter++}"
}
