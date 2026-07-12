package io.aequicor.visualization

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    installFullscreenHotkey()
    installModifierReleaseOnFocusChange()
    installZoomGestureGuard()
    // Compose owns only #compose-root; the intro overlay (#mv-loader) is a separate
    // sibling in index.html, so mounting Compose never touches or removes it — we
    // fade it out ourselves once the first frame is painted.
    ComposeViewport(viewportContainerId = "compose-root") {
        MissionEditorApp()
        // Dismiss the HTML loading overlay only after Compose has actually painted its
        // first frame, so the handoff never flashes an empty canvas. The overlay owns
        // its own minimum-display floor (see index.html), so fast loads still get the
        // full intro before it fades.
        LaunchedEffect(Unit) {
            withFrameNanos { }
            dismissLoadingOverlay()
        }
    }
}

/**
 * Browsers often do not deliver the real `keyup` for Alt/Shift/Space when focus leaves the
 * page via OS app switching (notably Alt+Tab). Compose's canvas keeps modifier state from
 * key events, so synthesize a release on focus/visibility changes to clear stale modifiers.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun installModifierReleaseOnFocusChange() {
    js(
        """
        (function () {
          if (window.__mvModifierReleaseInstalled) return;
          window.__mvModifierReleaseInstalled = true;
          window.__mvModifierReleaseCount = 0;

          function dispatchKeyup(target, key, code) {
            if (!target || typeof target.dispatchEvent !== "function") return;
            try {
              target.dispatchEvent(new KeyboardEvent("keyup", {
                key: key,
                code: code,
                bubbles: true,
                cancelable: true,
                altKey: false,
                ctrlKey: false,
                metaKey: false,
                shiftKey: false
              }));
            } catch (_) {}
          }

          function releaseModifiers() {
            window.__mvModifierReleaseCount += 1;
            var targets = [document.activeElement, document.body, document, window];
            for (var i = 0; i < targets.length; i += 1) {
              dispatchKeyup(targets[i], "Alt", "AltLeft");
              dispatchKeyup(targets[i], "Shift", "ShiftLeft");
              dispatchKeyup(targets[i], " ", "Space");
              dispatchKeyup(targets[i], "Spacebar", "Space");
            }
          }

          window.addEventListener("blur", releaseModifiers, true);
          window.addEventListener("focus", releaseModifiers, true);
          window.addEventListener("pagehide", releaseModifiers, true);
          document.addEventListener("visibilitychange", releaseModifiers, true);
          document.addEventListener("focusout", releaseModifiers, true);
        })();
        """,
    )
}

/**
 * The editor canvas owns its own zoom (ctrl/⌘-wheel and pinch zoom the artboard, not the page).
 * Browsers still apply their native page zoom to a trackpad pinch — delivered as a `wheel` event
 * with `ctrlKey = true` — and to Safari's `gesture*` events, so the whole web page zooms out from
 * under the app. Suppress only those default actions: `preventDefault` cancels the browser zoom
 * without stopping propagation, so Compose/Skiko still receives the same wheel event and zooms the
 * canvas. Attached with `{ passive: false }` (required for `preventDefault`) and in the capture
 * phase so the guard runs regardless of where focus sits. Normal (no-modifier) wheel scrolling is
 * left untouched — the page can't scroll anyway (`overflow: hidden`) and Compose pans on it.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun installZoomGestureGuard() {
    js(
        """
        (function () {
          if (window.__mvZoomGuardInstalled) return;
          window.__mvZoomGuardInstalled = true;

          // Trackpad pinch / ctrl(⌘)+wheel → page zoom. Cancel the default; leave the event to
          // propagate so Compose's own wheel handler still zooms the artboard.
          window.addEventListener('wheel', function (e) {
            if (e.ctrlKey || e.metaKey) e.preventDefault();
          }, { passive: false, capture: true });

          // Safari trackpad pinch fires gesture events (not wheel) that also zoom the page.
          ['gesturestart', 'gesturechange', 'gestureend'].forEach(function (type) {
            window.addEventListener(type, function (e) { e.preventDefault(); }, { passive: false, capture: true });
          });

          // Ctrl/⌘ +/-/0 keyboard zoom would rescale the page too; keep the app at 100%.
          window.addEventListener('keydown', function (e) {
            if ((e.ctrlKey || e.metaKey) && (e.key === '+' || e.key === '-' || e.key === '=' || e.key === '0')) {
              e.preventDefault();
            }
          }, { capture: true });
        })();
        """,
    )
}

/** Fades out and removes the `#mv-loader` intro overlay defined in `index.html`. */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun dismissLoadingOverlay() {
    js("if (typeof window.__mvHideLoader === 'function') window.__mvHideLoader();")
}

/**
 * F11 / F10 toggle browser fullscreen for the whole page (either key — the user may reach
 * for either). A native `keydown` listener is used instead of Compose key handling so the
 * toggle fires regardless of focus and carries the user gesture the Fullscreen API requires;
 * `preventDefault` suppresses the browser's own F11 so page-content fullscreen is the single
 * consistent behavior.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun installFullscreenHotkey() {
    js(
        """
        document.addEventListener('keydown', function (e) {
            if (e.key === 'F11' || e.key === 'F10') {
                e.preventDefault();
                if (document.fullscreenElement) {
                    if (document.exitFullscreen) { document.exitFullscreen(); }
                } else {
                    var el = document.documentElement;
                    if (el.requestFullscreen) { el.requestFullscreen().catch(function () {}); }
                }
            }
        });
        """,
    )
}
