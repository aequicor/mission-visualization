package io.aequicor.visualization.editor.ui

import androidx.compose.ui.graphics.Color
import io.aequicor.visualization.editor.domain.AppLanguage
import io.aequicor.visualization.editor.domain.RecentProject
import io.aequicor.visualization.editor.ui.strings.LandingStrings
import io.aequicor.visualization.editor.ui.strings.appStringsFor
import io.aequicor.visualization.editor.ui.theme.EditorColors
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Serializes the config the startup project screen (`window.__mvLanding`) renders from: the
 * localized [LandingStrings], the active [EditorColors] theme tokens (as CSS hex) and the recent
 * projects. Keeping copy + colors on the Kotlin side means the DOM screen stays a thin renderer
 * with no hardcoded strings or palette of its own.
 */
private val LandingConfigJson = Json { encodeDefaults = true }

@Serializable
private data class LandingConfigDto(
    val appName: String,
    val currentLanguage: String,
    val languages: List<LandingLanguageDto>,
    val supportsFolders: Boolean,
    val hasRecovery: Boolean,
    val browserProjectId: String? = null,
    val stringsByLang: Map<String, LandingStringsDto>,
    val colors: LandingColorsDto,
    val recents: List<LandingRecentDto>,
)

@Serializable
private data class LandingLanguageDto(
    val code: String,
    val nativeName: String,
)

@Serializable
private data class LandingStringsDto(
    val heading: String,
    val subtitle: String,
    val welcomeTitle: String,
    val welcomeSubtitle: String,
    val recentHeading: String,
    val noRecent: String,
    val connectFolder: String,
    val projectChoiceTitle: String,
    val createBrowserProject: String,
    val openDiskProject: String,
    val open: String,
    val remove: String,
    val localFolder: String,
    val reconnectHint: String,
    val openFailedTitle: String,
    val openFailedAccess: String,
    val openFailedRetry: String,
    val openFailedDismiss: String,
    val foldersUnavailable: String,
    val foldersUnavailableBrave: String,
    val copyFlagAddress: String,
    val flagAddressCopied: String,
    val recoverTitle: String,
    val recoverSubtitle: String,
    val language: String,
)

@Serializable
private data class LandingColorsDto(
    val backdrop: String,
    val card: String,
    val cardHover: String,
    val stroke: String,
    val ink: String,
    val muted: String,
    val accent: String,
    val accentSoft: String,
    val onAccentSoft: String,
    val danger: String,
)

@Serializable
private data class LandingRecentDto(
    val id: String,
    val displayName: String,
    val kind: String,
    val lastOpenedAtEpochMs: Long,
)

/** Product name shown above the landing heading (not localized — it is the app's name). */
private const val LandingAppName: String = "Mission Visualization"

private fun landingStringsDto(strings: LandingStrings): LandingStringsDto = LandingStringsDto(
    heading = strings.heading,
    subtitle = strings.subtitle,
    welcomeTitle = strings.welcomeTitle,
    welcomeSubtitle = strings.welcomeSubtitle,
    recentHeading = strings.recentHeading,
    noRecent = strings.noRecent,
    connectFolder = strings.connectFolder,
    projectChoiceTitle = strings.projectChoiceTitle,
    createBrowserProject = strings.createBrowserProject,
    openDiskProject = strings.openDiskProject,
    open = strings.open,
    remove = strings.remove,
    localFolder = strings.localFolder,
    reconnectHint = strings.reconnectHint,
    openFailedTitle = strings.openFailedTitle,
    openFailedAccess = strings.openFailedAccess,
    openFailedRetry = strings.openFailedRetry,
    openFailedDismiss = strings.openFailedDismiss,
    foldersUnavailable = strings.foldersUnavailable,
    foldersUnavailableBrave = strings.foldersUnavailableBrave,
    copyFlagAddress = strings.copyFlagAddress,
    flagAddressCopied = strings.flagAddressCopied,
    recoverTitle = strings.recoverTitle,
    recoverSubtitle = strings.recoverSubtitle,
    language = strings.language,
)

/** Builds the JSON config for [platformInstallLanding][io.aequicor.visualization.editor.platform]. */
internal fun buildLandingConfigJson(
    colors: EditorColors,
    recents: List<RecentProject>,
    supportsFolders: Boolean,
    hasRecovery: Boolean,
    browserProjectId: String?,
    language: AppLanguage,
): String {
    val dto = LandingConfigDto(
        appName = LandingAppName,
        currentLanguage = language.code,
        // Ship every language's strings so the screen's own language switcher re-localizes instantly.
        languages = AppLanguage.entries.map { LandingLanguageDto(it.code, it.nativeName) },
        supportsFolders = supportsFolders,
        hasRecovery = hasRecovery,
        browserProjectId = browserProjectId,
        stringsByLang = AppLanguage.entries.associate { it.code to landingStringsDto(appStringsFor(it).landing) },
        colors = LandingColorsDto(
            backdrop = colors.paneSurface.toCssHex(),
            card = colors.raisedSurface.toCssHex(),
            cardHover = colors.surfaceVariant.toCssHex(),
            stroke = colors.panelStroke.toCssHex(),
            ink = colors.ink.toCssHex(),
            muted = colors.mutedInk.toCssHex(),
            accent = colors.accent.toCssHex(),
            accentSoft = colors.accentContainer.toCssHex(),
            onAccentSoft = colors.onAccentContainer.toCssHex(),
            danger = colors.statusDanger.toCssHex(),
        ),
        recents = recents.map { LandingRecentDto(it.id, it.displayName, it.kind.code, it.lastOpenedAtEpochMs) },
    )
    return LandingConfigJson.encodeToString(LandingConfigDto.serializer(), dto)
}

/** Opaque `#rrggbb` for a theme token (alpha dropped — landing tokens are opaque surfaces/inks). */
private fun Color.toCssHex(): String {
    fun component(value: Float): String {
        val i = (value * 255f + 0.5f).toInt().coerceIn(0, 255)
        val s = i.toString(16)
        return if (s.length == 1) "0$s" else s
    }
    return "#" + component(red) + component(green) + component(blue)
}
