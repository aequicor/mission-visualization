package io.aequicor.visualization.editor.platform

import java.awt.Canvas
import java.awt.Component
import java.awt.Container
import java.awt.EventQueue
import java.awt.KeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Window
import java.lang.reflect.Proxy
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.RootPaneContainer

/**
 * Compose Desktop exposes AWT preciseWheelRotation as scrollDelta: one regular wheel notch is
 * normally 1, not one screen pixel. macOS trackpads emit a longer stream of fractional/momentum
 * values, so use a smaller distance there; Windows keeps the established wheel distance.
 */
internal actual fun platformCanvasWheelPanPixels(scrollUnits: Float, density: Float): Float =
    desktopCanvasWheelPanPixels(scrollUnits, density, isMacOs())

internal actual fun platformCanvasWheelZoomUnits(scrollUnits: Float): Float =
    scrollUnits * DesktopWheelZoomUnitsPerTick

internal fun desktopCanvasWheelPanPixels(scrollUnits: Float, density: Float, macOs: Boolean): Float =
    scrollUnits * (if (macOs) MacWheelPanDpPerUnit else DesktopWheelPanDpPerUnit) * density

/**
 * AWT exposes macOS pinch as an Apple magnification gesture rather than a Compose pointer scroll.
 * Use reflection so this file remains loadable on Windows/Linux JREs where com.apple.eawt is absent.
 */
internal actual fun installCanvasMagnification(
    onMagnification: (CanvasMagnificationEvent) -> Unit,
): CanvasMagnificationHandle {
    if (!isMacOs()) return NoCanvasMagnification

    val disposed = AtomicBoolean(false)
    var component: JComponent? = null
    var listener: Any? = null
    var removeListener: java.lang.reflect.Method? = null

    fun attach() {
        if (disposed.get() || listener != null) return
        val window = activeWindow() ?: return
        val rootPane = (window as? RootPaneContainer)?.rootPane ?: return
        val sceneCanvas = rootPane.firstDescendantCanvas()

        runCatching {
            val gestureListenerClass = Class.forName("com.apple.eawt.event.GestureListener")
            val magnificationListenerClass = Class.forName("com.apple.eawt.event.MagnificationListener")
            val utilitiesClass = Class.forName("com.apple.eawt.event.GestureUtilities")
            val proxy = Proxy.newProxyInstance(
                magnificationListenerClass.classLoader,
                arrayOf(magnificationListenerClass),
            ) { proxyInstance, method, args ->
                when (method.name) {
                    "magnify" -> {
                        val nativeEvent = args?.firstOrNull() ?: return@newProxyInstance null
                        val magnification = (nativeEvent.javaClass
                            .getMethod("getMagnification")
                            .invoke(nativeEvent) as Number).toFloat()
                        val pointer = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
                        val origin = runCatching {
                            (sceneCanvas ?: rootPane).locationOnScreen
                        }.getOrNull()
                        onMagnification(
                            CanvasMagnificationEvent(
                                magnification = magnification,
                                sceneXDp = if (pointer != null && origin != null) (pointer.x - origin.x).toFloat() else null,
                                sceneYDp = if (pointer != null && origin != null) (pointer.y - origin.y).toFloat() else null,
                            ),
                        )
                        runCatching { nativeEvent.javaClass.getMethod("consume").invoke(nativeEvent) }
                        null
                    }
                    "equals" -> proxyInstance === args?.firstOrNull()
                    "hashCode" -> System.identityHashCode(proxyInstance)
                    "toString" -> "MissionCanvasMagnificationListener"
                    else -> null
                }
            }
            val add = utilitiesClass.getMethod("addGestureListenerTo", JComponent::class.java, gestureListenerClass)
            val remove = utilitiesClass.getMethod("removeGestureListenerFrom", JComponent::class.java, gestureListenerClass)
            add.invoke(null, rootPane, proxy)
            component = rootPane
            listener = proxy
            removeListener = remove
        }.onFailure { error ->
            System.err.println("macOS canvas pinch listener could not be installed: ${error.message}")
        }
    }

    attach()
    if (listener == null) EventQueue.invokeLater(::attach)

    return CanvasMagnificationHandle {
        if (!disposed.compareAndSet(false, true)) return@CanvasMagnificationHandle
        val attachedComponent = component
        val attachedListener = listener
        val remove = removeListener
        if (attachedComponent != null && attachedListener != null && remove != null) {
            runCatching { remove.invoke(null, attachedComponent, attachedListener) }
        }
        component = null
        listener = null
        removeListener = null
    }
}

private fun activeWindow(): Window? {
    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    return focusManager.activeWindow
        ?: focusManager.focusedWindow
        ?: Window.getWindows().lastOrNull { it.isShowing && it.isDisplayable }
}

private fun Component.firstDescendantCanvas(): Canvas? {
    if (this is Canvas) return this
    if (this !is Container) return null
    components.forEach { child -> child.firstDescendantCanvas()?.let { return it } }
    return null
}

private fun isMacOs(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("mac")

private const val DesktopWheelPanDpPerUnit = 64f
private const val MacWheelPanDpPerUnit = 20f

/** With the shared 0.025 exponential sensitivity, one notch zooms by exp(0.15), about 16%. */
private const val DesktopWheelZoomUnitsPerTick = 6f
