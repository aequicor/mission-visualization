package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.aequicor.visualization.MissionEditorStateHolder
import io.aequicor.visualization.editor.domain.AppLanguage
import io.aequicor.visualization.editor.domain.RecentProject
import io.aequicor.visualization.editor.ui.strings.LocalStrings
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors

private sealed interface LandingCard {
    data object Welcome : LandingCard
    data class Recent(val project: RecentProject) : LandingCard
    data object OpenProject : LandingCard
}

private val LandingCardHeight = 88.dp

/** Native desktop counterpart of the WASM startup project screen. */
@Composable
internal fun ProjectLandingScreen(state: MissionEditorStateHolder) {
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current
    val cards = remember(state.recentProjectItems) {
        listOf(LandingCard.Welcome) + state.recentProjectItems.map(LandingCard::Recent) + LandingCard.OpenProject
    }
    var selected by remember(cards) { mutableStateOf(0) }
    var projectChoiceVisible by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun activate(card: LandingCard) {
        when (card) {
            LandingCard.Welcome -> state.openWelcomeProject()
            is LandingCard.Recent -> state.openRecentProject(card.project.id)
            LandingCard.OpenProject -> projectChoiceVisible = true
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.paneSurface)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || cards.isEmpty()) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight, Key.DirectionDown -> {
                        selected = (selected + 1) % cards.size
                        true
                    }
                    Key.DirectionLeft, Key.DirectionUp -> {
                        selected = (selected - 1 + cards.size) % cards.size
                        true
                    }
                    Key.Enter -> {
                        activate(cards[selected])
                        true
                    }
                    Key.Escape -> {
                        when {
                            projectChoiceVisible -> projectChoiceVisible = false
                            languageExpanded -> languageExpanded = false
                        }
                        true
                    }
                    else -> false
                }
            }
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            // Keep widthIn outside fillMaxSize: fillMaxSize first would lock the child to the
            // window width, making the 760 dp cap ineffective on desktop.
            modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Mission Visualization", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        strings.landing.heading,
                        color = colors.ink,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(strings.landing.subtitle, color = colors.mutedInk, fontSize = 14.sp)
                }
                Box {
                    Surface(
                        modifier = Modifier.clickable { languageExpanded = !languageExpanded },
                        shape = RoundedCornerShape(9.dp),
                        color = colors.raisedSurface,
                        border = BorderStroke(1.dp, colors.panelStroke),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            EditorSvgIcon(EditorIcon.Language, null, Modifier.size(16.dp), colors.ink)
                            Text(state.language.nativeName, color = colors.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            EditorSvgIcon(EditorIcon.ChevronDown, null, Modifier.size(14.dp), colors.mutedInk)
                        }
                    }
                    EditorDropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                        AppLanguage.entries.forEach { language ->
                            EditorDropdownMenuItem(
                                text = language.nativeName,
                                leadingContent = {
                                    EditorSvgIcon(
                                        if (language == state.language) EditorIcon.Check else EditorIcon.Language,
                                        null,
                                        Modifier.size(18.dp),
                                        if (language == state.language) colors.accent else colors.mutedInk,
                                    )
                                },
                            ) {
                                languageExpanded = false
                                state.selectLanguage(language)
                            }
                        }
                    }
                }
            }

            Text(
                strings.landing.recentHeading,
                modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
                color = colors.mutedInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(cards) { index, card ->
                    ProjectLandingCard(
                        card = card,
                        selected = index == selected,
                        onClick = {
                            selected = index
                            activate(card)
                        },
                        onRemove = (card as? LandingCard.Recent)?.let { recent ->
                            { state.removeRecentProject(recent.project.id) }
                        },
                    )
                }
            }

            state.projectLandingError?.let { error ->
                Text(
                    text = landingErrorMessage(error, strings.landing),
                    modifier = Modifier.padding(top = 14.dp),
                    color = colors.statusDanger,
                    fontSize = 12.sp,
                )
            }
        }
    }

    if (projectChoiceVisible) {
        Dialog(onDismissRequest = { projectChoiceVisible = false }) {
            Surface(
                modifier = Modifier.width(440.dp),
                shape = RoundedCornerShape(16.dp),
                color = colors.raisedSurface,
                border = BorderStroke(1.dp, colors.panelStroke),
                shadowElevation = 18.dp,
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        strings.landing.projectChoiceTitle,
                        color = colors.ink,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    LandingChoice(
                        icon = EditorIcon.Plus,
                        text = strings.menu.createProjectOnDisk,
                    ) {
                        projectChoiceVisible = false
                        state.createWelcomeFolderProject()
                    }
                    LandingChoice(
                        icon = EditorIcon.FolderOpen,
                        text = strings.landing.openDiskProject,
                    ) {
                        projectChoiceVisible = false
                        state.connectFolder()
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectLandingCard(
    card: LandingCard,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current
    val (icon, name, meta) = when (card) {
        LandingCard.Welcome -> Triple(EditorIcon.Home, strings.landing.welcomeTitle, strings.landing.welcomeSubtitle)
        is LandingCard.Recent -> Triple(EditorIcon.Folder, card.project.displayName, strings.landing.localFolder)
        LandingCard.OpenProject -> Triple(EditorIcon.Plus, strings.landing.connectFolder, "")
    }
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().height(LandingCardHeight).clip(shape).clickable(onClick = onClick),
        shape = shape,
        color = colors.raisedSurface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.panelStroke),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(shape = RoundedCornerShape(9.dp), color = colors.accentContainer) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    EditorSvgIcon(icon, null, Modifier.size(18.dp), colors.onAccentContainer)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(name, color = if (card == LandingCard.OpenProject) colors.accent else colors.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (meta.isNotEmpty()) Text(meta, color = colors.mutedInk, fontSize = 12.sp, lineHeight = 16.sp)
            }
            if (onRemove != null) {
                SmallIconButton(
                    icon = EditorIcon.Close,
                    contentDescription = strings.landing.remove,
                    onClick = onRemove,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun LandingChoice(icon: EditorIcon, text: String, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(11.dp),
        color = colors.raisedSurface,
        border = BorderStroke(1.dp, colors.panelStroke),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(9.dp), color = colors.accentContainer) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    EditorSvgIcon(icon, null, Modifier.size(18.dp), colors.onAccentContainer)
                }
            }
            Text(text, color = colors.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun landingErrorMessage(error: String, strings: io.aequicor.visualization.editor.ui.strings.LandingStrings): String =
    when (error) {
        "project-unavailable" -> strings.folderUnavailableError
        "folder-contains-project" -> strings.folderContainsProjectError
        else -> strings.projectCreateError
    }
