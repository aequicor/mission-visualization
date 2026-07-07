package io.aequicor.visualization

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform