package io.aequicor.visualization.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.aequicor.visualization.AppBuildInfo
import io.aequicor.visualization.FolderSyncPresence
import io.aequicor.visualization.MissionEditorStateHolder
import io.aequicor.visualization.editor.data.composeAgentFile
import io.aequicor.visualization.editor.data.encodeProjectSourcesJson
import io.aequicor.visualization.editor.domain.AppLanguage
import io.aequicor.visualization.editor.domain.AgentFileSelection
import io.aequicor.visualization.editor.domain.AgentSkillId
import io.aequicor.visualization.editor.platform.CanvasExportCrop
import io.aequicor.visualization.editor.platform.platformAppendCanvasPdfPage
import io.aequicor.visualization.editor.platform.platformBeginPdfExport
import io.aequicor.visualization.editor.platform.platformDownloadAgentFile
import io.aequicor.visualization.editor.platform.platformDownloadProjectZip
import io.aequicor.visualization.editor.platform.platformExportCanvasPng
import io.aequicor.visualization.editor.platform.platformFinishPdfExport
import io.aequicor.visualization.editor.platform.platformInstallLanding
import io.aequicor.visualization.editor.platform.platformOpenProjectFolder
import io.aequicor.visualization.editor.platform.platformOpenProjectZipArchive
import io.aequicor.visualization.editor.platform.platformSaveProjectFolder
import io.aequicor.visualization.editor.platform.platformSetActiveProjectId
import io.aequicor.visualization.editor.platform.platformSupportsAgentFileExport
import io.aequicor.visualization.editor.platform.platformSupportsLanding
import io.aequicor.visualization.editor.platform.platformSupportsProjectDiskIo
import io.aequicor.visualization.editor.platform.platformToggleFullscreen
import io.aequicor.visualization.editor.presentation.CompactLabel
import io.aequicor.visualization.editor.presentation.DesignEditorIntent
import io.aequicor.visualization.editor.presentation.LayerDropTarget
import io.aequicor.visualization.editor.presentation.LayerTreeRow
import io.aequicor.visualization.editor.presentation.PendingFit
import io.aequicor.visualization.editor.presentation.layerReparentKeepPutPosition
import io.aequicor.visualization.editor.presentation.ScreenPreset
import io.aequicor.visualization.editor.presentation.SourceTab
import io.aequicor.visualization.editor.presentation.ZOrderMove
import io.aequicor.visualization.editor.presentation.resolveLayerDropTarget
import io.aequicor.visualization.editor.ui.strings.LocalStrings
import io.aequicor.visualization.editor.ui.strings.MenuStrings
import io.aequicor.visualization.editor.ui.theme.LocalEditorColors
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignPage
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignSeverity
import io.aequicor.visualization.engine.ir.layout.LayoutBox
import io.aequicor.visualization.engine.ir.model.literalOrNull
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Left column: Source / Resources / Layers tabs plus the Screens list. */
@Composable
fun EditorSourcePane(state: MissionEditorStateHolder, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            shadowElevation = 0.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                SourcePaneHeader(state)
                FolderSyncBanner(state)
                when (state.workspace.sourceTab) {
                    SourceTab.Markdown -> SourceMarkdown(state)
                    SourceTab.Resources -> ResourcesTab(state)
                    SourceTab.Layers -> LayersTree(state)
                }
            }
        }
        ScreensPanel(state, Modifier.fillMaxWidth().height(300.dp))
    }
}

@Composable
private fun SourcePaneHeader(state: MissionEditorStateHolder) {
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var menuPane by remember { mutableStateOf(ProjectMenuPane.Root) }
    var selectedAgentSkills by remember { mutableStateOf(emptySet<AgentSkillId>()) }
    var mcpDialogVisible by remember { mutableStateOf(false) }

    fun openRootMenu() {
        menuPane = ProjectMenuPane.Root
        menuExpanded = true
    }

    fun closeMenu() {
        menuExpanded = false
        menuPane = ProjectMenuPane.Root
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(colors.raisedSurface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight()
                .border(BorderStroke(1.dp, colors.softStroke)),
            contentAlignment = Alignment.Center,
        ) {
            SmallIconButton(
                icon = EditorIcon.AppMenu,
                contentDescription = strings.menu.projectMenu,
                onClick = ::openRootMenu,
                modifier = Modifier.size(30.dp),
            )
            EditorDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                ProjectMenuTitleBar(projectName = state.displayProjectName, version = AppBuildInfo.VERSION)
                when (menuPane) {
                    ProjectMenuPane.Root -> {
                        FolderSyncMenuStatus(state)
                        if (platformSupportsLanding) {
                            EditorDropdownMenuItem(
                                strings.menu.projects,
                                leadingContent = { DropdownMenuIcon(EditorIcon.Home) },
                            ) {
                                closeMenu()
                                scope.launch {
                                    if (state.folderSync != FolderSyncPresence.Idle) state.disconnectFolder()
                                    platformSetActiveProjectId("")
                                    platformInstallLanding(
                                        buildLandingConfigJson(
                                            colors = colors,
                                            recents = state.recentProjectsList(),
                                            supportsFolders = state.supportsFolderSync,
                                            hasRecovery = state.hasRecoveryDraft(),
                                            browserProjectId = state.storedBrowserProjectId(),
                                            language = state.language,
                                        ),
                                    )
                                }
                            }
                        }
                        EditorDropdownMenuItem(strings.menu.open, leadingContent = { DropdownMenuIcon(EditorIcon.FolderOpen) }) { menuPane = ProjectMenuPane.Open }
                        EditorDropdownMenuItem(strings.menu.save, leadingContent = { DropdownMenuIcon(EditorIcon.Save) }) { menuPane = ProjectMenuPane.Save }
                        EditorDropdownMenuItem(strings.menu.export, leadingContent = { DropdownMenuIcon(EditorIcon.Export) }) { menuPane = ProjectMenuPane.Export }
                        if (platformSupportsAgentFileExport) {
                            EditorDropdownMenuItem(strings.menu.agentFile, leadingContent = { DropdownMenuIcon(EditorIcon.Markdown) }) {
                                selectedAgentSkills = emptySet()
                                menuPane = ProjectMenuPane.AgentSkills
                            }
                        }
                        if (state.mcpServer.available) {
                            EditorDropdownMenuItem(strings.menu.mcpServer, leadingContent = { DropdownMenuIcon(EditorIcon.Code) }) {
                                closeMenu()
                                mcpDialogVisible = true
                            }
                        }
                        EditorDropdownMenuItem(
                            "${strings.menu.language}: ${state.language.nativeName}",
                            leadingContent = { DropdownMenuIcon(EditorIcon.Language) },
                        ) { menuPane = ProjectMenuPane.Language }
                        EditorDropdownMenuItem(strings.menu.fullscreen, leadingContent = { DropdownMenuIcon(EditorIcon.Fullscreen) }) {
                            closeMenu()
                            platformToggleFullscreen()
                        }
                        when (state.folderSync) {
                            FolderSyncPresence.ReconnectNeeded -> EditorDropdownMenuItem(
                                strings.menu.folderReconnect(state.folderName ?: ""),
                                leadingContent = { DropdownMenuIcon(EditorIcon.FolderOpen) },
                            ) {
                                closeMenu()
                                state.reconnectFolder()
                            }
                            FolderSyncPresence.Watching -> EditorDropdownMenuItem(
                                strings.menu.folderDisconnect,
                                leadingContent = { DropdownMenuIcon(EditorIcon.Folder) },
                            ) {
                                closeMenu()
                                state.disconnectFolder()
                            }
                            else -> Unit
                        }
                    }
                    ProjectMenuPane.Open -> {
                        EditorDropdownMenuItem(strings.common.back, leadingContent = { DropdownMenuIcon(EditorIcon.ArrowBack) }) { menuPane = ProjectMenuPane.Root }
                        EditorDropdownMenuItem(strings.menu.welcomeProject, leadingContent = { DropdownMenuIcon(EditorIcon.Home) }) {
                            closeMenu()
                            state.openWelcomeProject()
                        }
                        if (platformSupportsProjectDiskIo) {
                            EditorDropdownMenuItem(strings.menu.openZipArchive, leadingContent = { DropdownMenuIcon(EditorIcon.Folder) }) {
                                closeMenu()
                                platformOpenProjectZipArchive()
                            }
                        }
                        if (state.supportsFolderSync) {
                            EditorDropdownMenuItem(strings.menu.openFolder, leadingContent = { DropdownMenuIcon(EditorIcon.FolderOpen) }) {
                                closeMenu()
                                state.connectFolder()
                            }
                        } else if (platformSupportsProjectDiskIo) {
                            // Browsers without the File System Access API can only import a folder
                            // once; keep the same user-facing action as a graceful fallback.
                            EditorDropdownMenuItem(strings.menu.openFolder, leadingContent = { DropdownMenuIcon(EditorIcon.FolderOpen) }) {
                                closeMenu()
                                platformOpenProjectFolder()
                            }
                        }
                    }
                    ProjectMenuPane.Save -> {
                        EditorDropdownMenuItem(strings.common.back, leadingContent = { DropdownMenuIcon(EditorIcon.ArrowBack) }) { menuPane = ProjectMenuPane.Root }
                        EditorDropdownMenuItem(strings.menu.saveInBrowser, leadingContent = { DropdownMenuIcon(EditorIcon.Save) }) {
                            closeMenu()
                            state.saveDraftNow()
                        }
                        if (platformSupportsProjectDiskIo) {
                            EditorDropdownMenuItem(strings.menu.saveToFolder, leadingContent = { DropdownMenuIcon(EditorIcon.Folder) }) {
                                closeMenu()
                                // A completed disk save makes the working set persistent: keep the
                                // local draft in sync from now on (cancelling the picker changes nothing).
                                platformSaveProjectFolder(encodeProjectSourcesJson(state.displayProjectName, state.designState.sources)) {
                                    state.saveDraftNow()
                                }
                            }
                            EditorDropdownMenuItem(strings.menu.saveAsZip, leadingContent = { DropdownMenuIcon(EditorIcon.Folder) }) {
                                closeMenu()
                                platformDownloadProjectZip(encodeProjectSourcesJson(state.displayProjectName, state.designState.sources)) {
                                    state.saveDraftNow()
                                }
                            }
                        }
                    }
                    ProjectMenuPane.Export -> {
                        EditorDropdownMenuItem(strings.common.back, leadingContent = { DropdownMenuIcon(EditorIcon.ArrowBack) }) { menuPane = ProjectMenuPane.Root }
                        EditorDropdownMenuItem(strings.menu.exportPngScreen, leadingContent = { DropdownMenuIcon(EditorIcon.Image) }) {
                            closeMenu()
                            exportCurrentScreenPng(state)
                        }
                        EditorDropdownMenuItem(strings.menu.exportPngComponent, leadingContent = { DropdownMenuIcon(EditorIcon.Image) }) {
                            closeMenu()
                            exportSelectedComponentPng(state)
                        }
                        EditorDropdownMenuItem(strings.menu.exportPdfAllScreens, leadingContent = { DropdownMenuIcon(EditorIcon.PictureAsPdf) }) {
                            closeMenu()
                            scope.launch { exportAllScreensPdf(state) }
                        }
                    }
                    ProjectMenuPane.AgentSkills -> {
                        ProjectMenuSectionTitle(strings.menu.agentSkillsTitle)
                        EditorDropdownMenuItem(strings.common.back, leadingContent = { DropdownMenuIcon(EditorIcon.ArrowBack) }) {
                            menuPane = ProjectMenuPane.Root
                        }
                        EditorDropdownMenuItem(
                            text = strings.menu.agentBaseSkill,
                            leadingContent = {
                                Checkbox(
                                    checked = true,
                                    onCheckedChange = null,
                                    modifier = Modifier.size(18.dp),
                                    enabled = false,
                                )
                            },
                            enabled = false,
                        ) {}
                        agentSkillRows(strings.menu).forEach { (skillId, label) ->
                            val selected = skillId in selectedAgentSkills
                            EditorDropdownMenuItem(
                                text = label,
                                leadingContent = {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            ) {
                                selectedAgentSkills = selectedAgentSkills.withSkill(skillId, !selected)
                            }
                        }
                        EditorDropdownMenuItem(strings.menu.agentNext, leadingContent = { DropdownMenuIcon(EditorIcon.Check) }) {
                            menuPane = ProjectMenuPane.AgentOutput
                        }
                    }
                    ProjectMenuPane.AgentOutput -> {
                        ProjectMenuSectionTitle(strings.menu.agentOutputTitle)
                        EditorDropdownMenuItem(strings.common.back, leadingContent = { DropdownMenuIcon(EditorIcon.ArrowBack) }) {
                            menuPane = ProjectMenuPane.AgentSkills
                        }
                        EditorDropdownMenuItem(strings.menu.downloadAgentsFile, leadingContent = { DropdownMenuIcon(EditorIcon.Save) }) {
                            val markdown = composeAgentFile(AgentFileSelection(includedSkillIds = selectedAgentSkills))
                            platformDownloadAgentFile("AGENTS.md", markdown)
                            closeMenu()
                        }
                        EditorDropdownMenuItem(strings.menu.downloadClaudeFile, leadingContent = { DropdownMenuIcon(EditorIcon.Save) }) {
                            val markdown = composeAgentFile(AgentFileSelection(includedSkillIds = selectedAgentSkills))
                            platformDownloadAgentFile("CLAUDE.md", markdown)
                            closeMenu()
                        }
                        EditorDropdownMenuItem(strings.menu.copyAgentFile, leadingContent = { DropdownMenuIcon(EditorIcon.Duplicate) }) {
                            val markdown = composeAgentFile(AgentFileSelection(includedSkillIds = selectedAgentSkills))
                            clipboard.setText(AnnotatedString(markdown))
                            closeMenu()
                        }
                    }
                    ProjectMenuPane.Language -> {
                        EditorDropdownMenuItem(strings.common.back, leadingContent = { DropdownMenuIcon(EditorIcon.ArrowBack) }) { menuPane = ProjectMenuPane.Root }
                        AppLanguage.entries.forEach { language ->
                            EditorDropdownMenuItem(
                                language.nativeName,
                                leadingContent = {
                                    DropdownMenuIcon(
                                        if (language == state.language) EditorIcon.Check else EditorIcon.Language,
                                    )
                                },
                            ) {
                                closeMenu()
                                state.selectLanguage(language)
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            TabStrip(
                tabs = SourceTab.entries,
                selected = state.workspace.sourceTab,
                title = { strings.labels.sourceTab(it) },
                icon = ::sourceTabIcon,
                onSelect = { tab -> state.updateWorkspace { it.copy(sourceTab = tab) } },
            )
        }
    }
    if (mcpDialogVisible) {
        McpServerDialog(state.mcpServer) { mcpDialogVisible = false }
    }
}

private enum class ProjectMenuPane { Root, Open, Save, Export, AgentSkills, AgentOutput, Language }

private fun Set<AgentSkillId>.withSkill(skillId: AgentSkillId, included: Boolean): Set<AgentSkillId> =
    if (included) this + skillId else this - skillId

private fun agentSkillRows(strings: MenuStrings): List<Pair<AgentSkillId, String>> =
    listOf(
        AgentSkillId.DIAGRAMS to strings.agentDiagramsSkill,
        AgentSkillId.VECTOR_GRAPHICS to strings.agentVectorGraphicsSkill,
        AgentSkillId.TYPOGRAPHY to strings.agentTypographySkill,
        AgentSkillId.ANNOTATIONS to strings.agentAnnotationsSkill,
        AgentSkillId.EDITOR to strings.agentEditorSkill,
    )

@Composable
private fun ProjectMenuSectionTitle(title: String) {
    val colors = LocalEditorColors.current
    Text(
        text = title,
        modifier = Modifier.width(260.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = colors.mutedInk,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun FolderSyncMenuStatus(state: MissionEditorStateHolder) {
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current
    val name = state.folderName.orEmpty()
    when (state.folderSync) {
        FolderSyncPresence.Idle -> Unit
        FolderSyncPresence.Connecting -> FolderSyncMenuStatusContent(
            dot = colors.mutedInk,
            title = strings.menu.folderConnecting,
            description = name,
        )
        FolderSyncPresence.Watching -> FolderSyncMenuStatusContent(
            dot = colors.statusPositive,
            title = strings.menu.folderWatching,
            description = strings.menu.folderWatchingDescription(name),
        )
        FolderSyncPresence.ReconnectNeeded -> FolderSyncMenuStatusContent(
            dot = colors.statusWarning,
            title = strings.menu.folderAccessRequired,
            description = strings.menu.folderAccessRequiredDescription(name),
        )
    }
}

@Composable
private fun FolderSyncMenuStatusContent(dot: Color, title: String, description: String) {
    val colors = LocalEditorColors.current
    Column(
        modifier = Modifier.width(280.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Text(
                text = title,
                color = colors.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = description,
            modifier = Modifier.padding(start = 16.dp),
            color = colors.mutedInk,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            maxLines = 2,
        )
    }
}

/**
 * Non-blocking notice strip under the header: surfaces a compile error in the connected folder
 * (canvas keeps its last good state) and a recoverable "your edit was replaced" conflict backup.
 * Renders nothing when there is nothing to report.
 */
@Composable
private fun FolderSyncBanner(state: MissionEditorStateHolder) {
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current
    val backup = state.folderConflictBackup
    when {
        backup != null -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.statusWarning.copy(alpha = 0.14f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(strings.menu.folderConflict, color = colors.ink, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(
                strings.menu.folderRestoreEdit,
                color = colors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { state.restoreFolderConflictBackup() },
            )
            Text(
                strings.menu.folderDismiss,
                color = colors.mutedInk,
                fontSize = 12.sp,
                modifier = Modifier.clickable { state.dismissFolderConflictBackup() },
            )
        }
        state.folderExternalError -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.statusDanger.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(colors.statusDanger))
            Text(strings.menu.folderExternalError, color = colors.ink, fontSize = 12.sp)
        }
    }
}

/** Prompts for a storage mode after the first edit to the in-memory Welcome tour. */
@Composable
fun EditorBrowserSaveBanner(state: MissionEditorStateHolder) {
    if (!state.browserSaveNoticeVisible) return
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current
    Dialog(onDismissRequest = state::dismissBrowserSaveNotice) {
        Surface(
            modifier = Modifier.width(420.dp),
            shape = RoundedCornerShape(14.dp),
            color = colors.raisedSurface,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = strings.menu.createProjectTitle,
                    color = colors.ink,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = strings.menu.createProjectMessage,
                    color = colors.mutedInk,
                    style = MaterialTheme.typography.bodyMedium,
                )
                ProjectCreationChoice(
                    text = strings.menu.createProjectInBrowser,
                    accent = true,
                    onClick = state::createBrowserProject,
                )
                if (state.supportsFolderSync) {
                    ProjectCreationChoice(
                        text = strings.menu.createProjectOnDisk,
                        accent = false,
                        onClick = state::createFolderProject,
                    )
                }
                Text(
                    text = strings.menu.cancel,
                    modifier = Modifier.align(Alignment.End).clickable { state.dismissBrowserSaveNotice() }.padding(8.dp),
                    color = colors.mutedInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ProjectCreationChoice(text: String, accent: Boolean, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (accent) colors.accent else colors.raisedSurface,
        border = if (accent) null else BorderStroke(1.dp, colors.panelStroke),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            color = if (accent) Color.White else colors.ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ProjectMenuTitleBar(projectName: String, version: String) {
    val colors = LocalEditorColors.current
    Column(Modifier.fillMaxWidth().background(colors.raisedSurface)) {
        Text(
            projectName,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "v$version",
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 10.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.mutedInk,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.softStroke))
    }
}

private fun exportCurrentScreenPng(state: MissionEditorStateHolder) {
    val pageName = state.designState.document
        ?.pageById(state.designState.selectedPageId)
        ?.name
        ?.ifBlank { state.designState.selectedPageId }
        ?: "screen"
    val crop = state.artboardLayout?.let { exportCropForBox(state, it) }
    platformExportCanvasPng("${safeExportName(pageName)}.png", crop)
}

private fun exportSelectedComponentPng(state: MissionEditorStateHolder) {
    val selectedId = state.designState.selectedNodeId
    val layout = state.artboardLayout ?: return
    val box = layout.findBySourceId(selectedId) ?: return
    val nodeName = state.designState.document?.nodeById(selectedId)?.name?.ifBlank { selectedId } ?: selectedId
    platformExportCanvasPng("${safeExportName(nodeName)}.png", exportCropForBox(state, box))
}

private suspend fun exportAllScreensPdf(state: MissionEditorStateHolder) {
    val document = state.designState.document ?: return
    val originalPageId = state.designState.selectedPageId
    val originalViewport = state.workspace.viewport
    platformBeginPdfExport()
    document.pages.forEach { page ->
        state.dispatch(DesignEditorIntent.SelectPage(page.id))
        state.updateWorkspace { it.copy(pendingFit = PendingFit.Screen) }
        waitFrames(4)
        val crop = state.artboardLayout?.let { exportCropForBox(state, it) }
        platformAppendCanvasPdfPage(page.name.ifBlank { page.id }, crop)
    }
    platformFinishPdfExport("mission-visualization-screens.pdf")
    if (originalPageId.isNotBlank()) {
        state.dispatch(DesignEditorIntent.SelectPage(originalPageId))
    }
    state.updateWorkspace { it.copy(viewport = originalViewport, pendingFit = PendingFit.None) }
}

private suspend fun waitFrames(count: Int) {
    repeat(count) {
        withFrameNanos { }
    }
}

private fun exportCropForBox(state: MissionEditorStateHolder, box: LayoutBox): CanvasExportCrop? {
    val bounds = state.canvasExportBounds ?: return null
    val viewport = state.workspace.viewport
    val density = bounds.density
    val left = bounds.left + viewport.toScreenX(box.x, density)
    val top = bounds.top + viewport.toScreenY(box.y, density)
    val width = box.width * viewport.zoomPx(density)
    val height = box.height * viewport.zoomPx(density)
    if (width <= 0.0 || height <= 0.0) return null
    return CanvasExportCrop(left, top, width, height)
}

private fun safeExportName(value: String): String =
    value.lowercase()
        .replace(Regex("""[^a-z0-9а-яё._-]+"""), "-")
        .trim('-', '.', '_')
        .ifBlank { "export" }


// --- Markdown source viewer --------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceMarkdown(state: MissionEditorStateHolder) {
    val colors = LocalEditorColors.current
    val design = state.designState
    val source = remember(design.selectedPageId, design.sources, design.compiledResults) {
        sourceForSelectedPage(state)
    }
    if (source == null) {
        Box(Modifier.fillMaxSize().background(colors.paneSurface), contentAlignment = Alignment.Center) {
            Text(
                LocalStrings.current.source.noSlmSource,
                color = colors.mutedInk,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }
    var fieldValue by remember(source.index) { mutableStateOf(TextFieldValue(source.content)) }
    LaunchedEffect(source.index, source.content) {
        if (fieldValue.text != source.content) fieldValue = TextFieldValue(source.content)
    }
    val lines = remember(source.content) { source.content.lines().ifEmpty { listOf("") } }
    val lineCount = lines.size.coerceAtLeast(1)
    val longestLine = lines.maxOfOrNull { it.length } ?: 0
    val codeStyle = MaterialTheme.typography.bodySmall.copy(
        color = colors.codeInk,
        fontFamily = FontFamily.Monospace,
        lineHeight = SourceCodeLineHeightSp.sp,
    )
    val editorWidth = ((longestLine.coerceAtLeast(48) * SourceCodeCharWidthDp) + 28).dp
    val editorHeight = ((lineCount * SourceCodeLineHeightDp) + 24).dp
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val diagnostics = remember(source.fileName, design.diagnostics) {
        design.diagnostics.filter { diagnostic ->
            diagnostic.location?.file.isNullOrBlank() || diagnostic.location?.file == source.fileName
        }
    }
    val stableCursorScrollSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
        }
    }

    Column(Modifier.fillMaxSize().background(colors.paneSurface)) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val contentHeight = maxOf(editorHeight, maxHeight)
            CompositionLocalProvider(LocalBringIntoViewSpec provides stableCursorScrollSpec) {
                Row(
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(verticalScroll)
                        .horizontalScroll(horizontalScroll),
                ) {
                    Column(
                        modifier = Modifier.width(46.dp).height(contentHeight).background(colors.gutterSurface).padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        repeat(lineCount) { index ->
                            Text(
                                (index + 1).toString(),
                                modifier = Modifier.height(SourceCodeLineHeightDp.dp),
                                color = colors.gutterInk,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                    Box(Modifier.width(editorWidth).height(contentHeight).padding(top = 12.dp, start = 12.dp, end = 12.dp)) {
                        BasicTextField(
                            value = fieldValue,
                            onValueChange = { next ->
                                fieldValue = next
                                if (next.text != source.content) {
                                    state.dispatch(DesignEditorIntent.EditSource(source.index, next.text))
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = codeStyle,
                            cursorBrush = SolidColor(colors.accent),
                            singleLine = false,
                        )
                    }
                }
            }
            SourceScrollbars(verticalScroll, horizontalScroll)
        }
        if (diagnostics.isNotEmpty()) {
            SourceDiagnosticsBlock(diagnostics)
        }
    }
}

@Composable
private fun SourceDiagnosticsBlock(diagnostics: List<DesignDiagnostic>) {
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current.source
    val clipboard = LocalClipboardManager.current
    val errorCount = diagnostics.count { it.severity == DesignSeverity.Error }
    val warningCount = diagnostics.size - errorCount
    val text = remember(diagnostics) { formatSourceDiagnostics(diagnostics) }
    val accent = if (errorCount > 0) colors.statusDanger else colors.statusWarning

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 180.dp)
            .background(accent.copy(alpha = 0.08f))
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.55f)))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            Text(
                "${strings.diagnostics} · ${strings.errors}: $errorCount · ${strings.warnings}: $warningCount",
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                color = colors.ink,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            SmallIconButton(
                icon = EditorIcon.Duplicate,
                contentDescription = strings.copyDiagnostics,
                onClick = { clipboard.setText(AnnotatedString(text)) },
                modifier = Modifier.size(26.dp),
            )
        }
        SelectionContainer(Modifier.fillMaxWidth().weight(1f)) {
            Text(
                text = text,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                color = colors.codeInk,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

internal fun formatSourceDiagnostics(diagnostics: List<DesignDiagnostic>): String =
    diagnostics.joinToString("\n") { diagnostic ->
        val severity = diagnostic.severity.name.uppercase()
        val code = diagnostic.code.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty()
        val location = diagnostic.location?.let { source ->
            val file = source.file.ifBlank { "SLM" }
            val line = source.line.takeIf { it > 0 }?.let { ":$it" }.orEmpty()
            " $file$line"
        }.orEmpty()
        "$severity$code$location — ${diagnostic.message}"
    }

private const val SourceCodeLineHeightDp = 20
private const val SourceCodeLineHeightSp = 20
private const val SourceCodeCharWidthDp = 8

/** SLM source of the page currently selected, or null for an in-memory screen. */
private data class SourceReference(val index: Int, val fileName: String, val content: String)

private fun sourceForSelectedPage(state: MissionEditorStateHolder): SourceReference? {
    val design = state.designState
    val pageId = design.selectedPageId
    design.compiledResults.forEachIndexed { index, result ->
        val doc = result.document ?: return@forEachIndexed
        val screenId = doc.screen?.id.orEmpty()
        val matches = doc.pages.any { page -> screenId.ifBlank { page.id } == pageId }
        if (matches) {
            return design.sources.getOrNull(index)?.let { SourceReference(index, it.fileName, it.content) }
        }
    }
    return null
}

// --- Layers tree -------------------------------------------------------------

private data class LayerRow(val node: DesignNode, val depth: Int)

/**
 * Layers tree: paint order shown front-first (top of the list = front), expand/collapse
 * of frames, hover + selection sync, visibility/lock toggles and per-row reorder.
 */
@Composable
private fun LayersTree(state: MissionEditorStateHolder) {
    val colors = LocalEditorColors.current
    val design = state.designState
    val ws = state.workspace
    val page = design.document?.pageById(design.selectedPageId)
    val focusRequester = remember { FocusRequester() }
    val rows = remember(page, ws.collapsedLayers) {
        page?.let { flattenLayers(it, ws.collapsedLayers) } ?: emptyList()
    }
    val rowHeightPx = with(LocalDensity.current) { LayerRowHeight.toPx() }
    // One indent level in px (matches LayerRowView's `depth * 16.dp` start padding). Horizontal drag
    // distance / this = how many levels the pointer wants to nest in or pop out of at a trailing gap.
    val indentStepPx = with(LocalDensity.current) { 16.dp.toPx() }
    // Drag-to-reorder / reparent state, local to the tree. The pointer's absolute Y
    // (from the top of the tree) resolves through the pure banding/index mapping in
    // [resolveLayerDropTarget]: an insertion LINE between rows (before/after — including
    // "after the last row" = the visual end of the list) or a container highlight.
    val rowModels = remember(rows) {
        rows.map { row ->
            LayerTreeRow(
                nodeId = row.node.id,
                depth = row.depth,
                isContainer = row.node.kind is io.aequicor.visualization.engine.ir.model.DesignNodeKind.Frame ||
                    row.node.children.isNotEmpty(),
            )
        }
    }
    var dragId by remember { mutableStateOf("") }
    // The dragged row's own depth is the baseline; horizontal drag distance shifts the target depth
    // (drag right to nest deeper, left to pop out), independent of where in the row it was grabbed.
    var dragBaseDepth by remember { mutableStateOf(0) }
    var dragDx by remember { mutableStateOf(0f) }
    var dragPointerY by remember { mutableStateOf(0f) }
    var dropTarget by remember { mutableStateOf<LayerDropTarget?>(null) }
    fun clearDrag() {
        dragId = ""
        dropTarget = null
    }
    Column(
        modifier = Modifier.fillMaxSize().background(colors.paneSurface)
            .focusRequester(focusRequester)
            .focusTarget()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if ((event.key == Key.Delete || event.key == Key.Backspace) && state.designState.selectedNodeIds.isNotEmpty()) {
                    state.dispatch(DesignEditorIntent.DeleteNodes(state.designState.selectedNodeIds))
                    true
                } else {
                    false
                }
            }
            .verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
    ) {
        val gap = dropTarget as? LayerDropTarget.InsertGap
        rows.forEachIndexed { index, row ->
            LayerRowView(
                state = state,
                row = row,
                isDropTarget = dragId.isNotEmpty() &&
                    (dropTarget as? LayerDropTarget.IntoContainer)?.rowIndex == index,
                dropLineAbove = dragId.isNotEmpty() && gap?.gapIndex == index,
                dropLineBelow = dragId.isNotEmpty() && index == rows.lastIndex && gap?.gapIndex == rows.size,
                dropLineIndent = (8 + (gap?.depth ?: 0) * 16).dp,
                onRequestFocus = { runCatching { focusRequester.requestFocus() } },
                onDragStart = { localY ->
                    dragId = row.node.id
                    dragBaseDepth = row.depth
                    dragDx = 0f
                    dragPointerY = index * rowHeightPx + localY
                    dropTarget = null
                },
                onDrag = { dx, dy ->
                    if (dragId.isNotEmpty()) {
                        dragDx += dx
                        dragPointerY += dy
                        val pointerDepth = (dragBaseDepth + (dragDx / indentStepPx).roundToInt()).coerceAtLeast(0)
                        dropTarget = state.designState.document?.let { doc ->
                            resolveLayerDropTarget(doc, rowModels, dragId, (dragPointerY / rowHeightPx).toDouble(), pointerDepth)
                        }
                    }
                },
                onDrop = {
                    val target = dropTarget
                    if (dragId.isNotEmpty() && target != null) applyLayerDrop(state, target, dragId)
                    clearDrag()
                },
                onDragCancel = { clearDrag() },
            )
        }
        if (rows.isEmpty()) {
            Text(LocalStrings.current.source.emptyScreen, color = colors.mutedInk, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
        }
    }
}

private val LayerRowHeight = 30.dp

/**
 * Dispatches a resolved layers drop: a gap inserts at its (already reverse-mapped)
 * document index, a container drop nests into it at the paint-order front. Invalid
 * drops never resolve (see [resolveLayerDropTarget]), and the reducer re-validates.
 */
private fun applyLayerDrop(state: MissionEditorStateHolder, target: LayerDropTarget, dragId: String) {
    val document = state.designState.document
    val layout = state.artboardLayout
    // A layers-tree drop carries no pointer coordinates, so re-base the node into the new
    // free container's frame to keep it visually put (a canvas drag does this via pointer
    // geometry). Null leaves flow / same-parent / anchored moves untouched.
    val newParentId = when (target) {
        is LayerDropTarget.IntoContainer -> target.nodeId
        is LayerDropTarget.InsertGap -> target.parentId
    }
    val position = document?.let { layerReparentKeepPutPosition(it, layout, dragId, newParentId) }
    when (target) {
        is LayerDropTarget.IntoContainer ->
            state.dispatch(DesignEditorIntent.ReparentNode(dragId, target.nodeId, position = position))
        is LayerDropTarget.InsertGap ->
            state.dispatch(DesignEditorIntent.ReparentNode(dragId, target.parentId, target.index, position = position))
    }
}

@Composable
private fun LayerRowView(
    state: MissionEditorStateHolder,
    row: LayerRow,
    isDropTarget: Boolean,
    dropLineAbove: Boolean,
    dropLineBelow: Boolean,
    dropLineIndent: Dp,
    onRequestFocus: () -> Unit,
    onDragStart: (Float) -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDrop: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val colors = LocalEditorColors.current
    val design = state.designState
    val ws = state.workspace
    val node = row.node
    val selected = node.id in design.selectedNodeIds
    val hovered = node.id == state.hoveredNodeId
    val hasChildren = node.children.isNotEmpty()
    val collapsed = node.id in ws.collapsedLayers
    val visible = node.visible.literalOrNull() ?: true
    Row(
        modifier = Modifier.fillMaxWidth().height(LayerRowHeight)
            .background(
                when {
                    selected -> colors.selectionFill
                    hovered -> colors.raisedSurface
                    else -> Color.Transparent
                },
            )
            .then(if (isDropTarget) Modifier.border(BorderStroke(1.5.dp, colors.accent)) else Modifier)
            .drawWithContent {
                drawContent()
                // Insertion line: the exact slot a dragged layer will land in, indented
                // to the destination depth. Drawn on the row boundary (above, or below
                // the last row for "end of the list").
                if (dropLineAbove || dropLineBelow) {
                    val y = if (dropLineAbove) 1.dp.toPx() else size.height - 1.dp.toPx()
                    val indent = dropLineIndent.toPx()
                    drawLine(
                        color = colors.accent,
                        start = Offset(indent, y),
                        end = Offset(size.width - 8.dp.toPx(), y),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawCircle(
                        color = colors.accent,
                        radius = 3.dp.toPx(),
                        center = Offset(indent, y),
                    )
                }
            }
            .pointerInput(node.id) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Enter) state.updateHoveredNode(node.id)
                        if (e.type == PointerEventType.Exit && state.hoveredNodeId == node.id) state.updateHoveredNode("")
                    }
                }
            }
            .pointerInput(node.id) {
                detectDragGestures(
                    onDragStart = { offset -> onRequestFocus(); onDragStart(offset.y) },
                    onDragEnd = { onDrop() },
                    onDragCancel = { onDragCancel() },
                ) { change, dragAmount -> change.consume(); onDrag(dragAmount.x, dragAmount.y) }
            }
            .clickable {
                onRequestFocus()
                state.dispatch(DesignEditorIntent.SelectNode(node.id))
            }
            .padding(start = (8 + row.depth * 16).dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Expand / collapse chevron.
        Box(
            Modifier
                .size(16.dp)
                .then(
                    if (hasChildren) {
                        Modifier.clip(CircleShape).clickable {
                            state.updateWorkspace {
                                it.copy(collapsedLayers = if (collapsed) it.collapsedLayers - node.id else it.collapsedLayers + node.id)
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (hasChildren) {
                Text(
                    if (collapsed) ">" else "v",
                    color = colors.mutedInk,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        EditorSvgIcon(
            icon = layerIcon(node.type),
            contentDescription = node.type,
            modifier = Modifier.size(16.dp),
            tint = if (selected) colors.accent else colors.mutedInk,
        )
        Text(
            node.name.ifBlank { node.id },
            modifier = Modifier.weight(1f),
            color = if (selected) colors.accent else if (visible) colors.ink else colors.mutedInk,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        // Row actions appear on hover/selection to avoid clutter.
        val strings = LocalStrings.current
        if (selected || hovered) {
            LayerAction(EditorIcon.ArrowUp, contentDescription = strings.source.bringForward, enabled = true) { state.dispatch(DesignEditorIntent.ReorderNode(node.id, ZOrderMove.Forward)) }
            LayerAction(EditorIcon.ArrowDown, contentDescription = strings.source.sendBackward, enabled = true) { state.dispatch(DesignEditorIntent.ReorderNode(node.id, ZOrderMove.Backward)) }
            LayerIconAction(
                icon = EditorIcon.Lock,
                contentDescription = if (node.locked) strings.source.unlockLayer else strings.source.lockLayer,
                active = node.locked,
            ) { state.dispatch(DesignEditorIntent.SetLocked(node.id, !node.locked)) }
        }
        LayerIconAction(
            icon = if (visible) EditorIcon.Visibility else EditorIcon.VisibilityOff,
            contentDescription = if (visible) strings.source.hideLayer else strings.source.showLayer,
            muted = !visible,
        ) { state.dispatch(DesignEditorIntent.SetVisible(node.id, !visible)) }
    }
}

@Composable
private fun LayerAction(icon: EditorIcon, contentDescription: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        EditorSvgIcon(icon, contentDescription = contentDescription, modifier = Modifier.size(14.dp), tint = colors.controlInk)
    }
}

private fun flattenLayers(page: DesignPage, collapsed: Set<String>): List<LayerRow> {
    val rows = mutableListOf<LayerRow>()
    fun visit(node: DesignNode, depth: Int) {
        rows += LayerRow(node, depth)
        if (node.id !in collapsed) {
            // Front-first: last paint-order child shown at the top of its group.
            node.children.asReversed().forEach { visit(it, depth + 1) }
        }
    }
    page.children.asReversed().forEach { visit(it, 0) }
    return rows
}

@Composable
private fun LayerIconAction(
    icon: EditorIcon,
    contentDescription: String,
    enabled: Boolean = true,
    active: Boolean = false,
    muted: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalEditorColors.current
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        EditorSvgIcon(
            icon = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(17.dp),
            tint = when {
                !enabled || muted -> colors.mutedInk
                active -> colors.accent
                else -> colors.controlInk
            },
        )
    }
}

private fun sourceTabIcon(tab: SourceTab): EditorIcon = when (tab) {
    SourceTab.Markdown -> EditorIcon.Markdown
    SourceTab.Resources -> EditorIcon.Assets
    SourceTab.Layers -> EditorIcon.Layers
}

private fun layerIcon(type: String): EditorIcon = when (type) {
    "frame", "group", "section", "screen" -> EditorIcon.Frame
    "text" -> EditorIcon.Text
    "instance" -> EditorIcon.Component
    "shape" -> EditorIcon.Rectangle
    else -> EditorIcon.Layers
}

// --- Screens panel -----------------------------------------------------------

@Composable
private fun ScreensPanel(state: MissionEditorStateHolder, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    val strings = LocalStrings.current
    val design = state.designState
    val document = design.document
    val statusColors = listOf(colors.statusPositive, colors.statusWarning, colors.statusDanger)
    var presetMenu by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactText(
                    label = strings.source.screens,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Box {
                    SmallIconButton(EditorIcon.Plus, contentDescription = strings.source.createScreen, onClick = { presetMenu = true })
                    EditorDropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                        ScreenPreset.entries.forEach { preset ->
                            EditorDropdownMenuItem(
                                text = "${strings.source.screenPreset(preset)}  ${preset.width.toInt()}x${preset.height.toInt()}",
                                onClick = {
                                    presetMenu = false
                                    val count = (document?.pages?.size ?: 0) + 1
                                    state.dispatch(DesignEditorIntent.CreateScreen(preset, "Screen $count"))
                                },
                                leadingContent = { ScreenPresetPreview(preset) },
                            )
                        }
                    }
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                document?.pages?.forEachIndexed { index, page ->
                    ScreenRow(
                        title = page.name.ifBlank { page.id },
                        subtitle = "${page.children.firstOrNull()?.size?.width?.toInt() ?: 0} x ${page.children.firstOrNull()?.size?.height?.toInt() ?: 0}",
                        status = statusColors[index % statusColors.size],
                        selected = page.id == design.selectedPageId,
                        onClick = { state.dispatch(DesignEditorIntent.SelectPage(page.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenPresetPreview(preset: ScreenPreset, modifier: Modifier = Modifier.size(18.dp)) {
    val colors = LocalEditorColors.current
    Canvas(modifier) {
        val ratio = (preset.width / preset.height).toFloat().coerceIn(0.35f, 1.8f)
        val maxW = size.width - 2f
        val maxH = size.height - 2f
        val w: Float
        val h: Float
        if (ratio >= 1f) {
            w = maxW
            h = maxW / ratio
        } else {
            h = maxH
            w = maxH * ratio
        }
        val topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2f)
        val corner = CornerRadius(2f, 2f)
        drawRoundRect(colors.selectionFill, topLeft = topLeft, size = Size(w, h), cornerRadius = corner)
        drawRoundRect(colors.controlInk, topLeft = topLeft, size = Size(w, h), cornerRadius = corner, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        when (preset) {
            ScreenPreset.Desktop -> drawLine(colors.accent, Offset(topLeft.x + w * 0.35f, topLeft.y + h + 2f), Offset(topLeft.x + w * 0.65f, topLeft.y + h + 2f), strokeWidth = 1.dp.toPx())
            ScreenPreset.Tablet,
            ScreenPreset.Mobile -> drawCircle(colors.accent, radius = 1.2f, center = Offset(topLeft.x + w / 2f, topLeft.y + h - 2.2f))
            ScreenPreset.Square -> drawCircle(colors.accent, radius = 1.4f, center = Offset(topLeft.x + w - 3f, topLeft.y + 3f))
        }
    }
}

@Composable
private fun ScreenRow(title: String, subtitle: String, status: Color, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp)
            .background(if (selected) colors.selectionFill else Color.White)
            .border(BorderStroke(if (selected) 1.dp else 0.dp, if (selected) colors.selectionStroke else Color.Transparent))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp, 34.dp).background(Color.White, RoundedCornerShape(4.dp)).border(1.dp, if (selected) colors.thumbnailSelectedStroke else colors.panelStroke, RoundedCornerShape(4.dp)))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.ink, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.subtleInk, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(12.dp).background(status, CircleShape))
    }
}
