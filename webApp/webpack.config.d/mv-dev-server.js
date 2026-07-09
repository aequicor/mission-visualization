// Kotlin/Wasm appends every webpack.config.d/*.js to the generated webpack
// config. This one tunes the DEVELOPMENT dev-server only.
//
// The dev server otherwise brotli-compresses responses. On localhost that buys
// nothing — transfer is effectively instant and the real first-load cost is
// COMPILING the (unoptimized, ~27 MB) development wasm — while it strips the
// Content-Length header, which the boot download-meter (see index.html) needs
// to show a byte-accurate percentage. So we serve dev responses uncompressed:
// real Content-Length ⇒ an exact meter during development.
//
// Production is the opposite regime: there bytes travel the network, so the
// distribution is PRE-COMPRESSED (.br/.gz) and carries an uncompressed-size
// manifest for meter accuracy — see the :webApp:packWasmDist Gradle task.
if (config.devServer) {
    config.devServer.compress = false;
}
