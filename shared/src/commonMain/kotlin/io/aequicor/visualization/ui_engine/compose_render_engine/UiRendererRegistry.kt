package io.aequicor.visualization.ui_engine.compose_render_engine

import io.aequicor.visualization.ui_engine.components.badge.BadgeRendererProvider
import io.aequicor.visualization.ui_engine.components.bottombar.BottomBarRendererProvider
import io.aequicor.visualization.ui_engine.components.box.BoxRendererProvider
import io.aequicor.visualization.ui_engine.components.button.ButtonRendererProvider
import io.aequicor.visualization.ui_engine.components.card.CardRendererProvider
import io.aequicor.visualization.ui_engine.components.datatable.DataTableRendererProvider
import io.aequicor.visualization.ui_engine.components.dialog.DialogRendererProvider
import io.aequicor.visualization.ui_engine.components.emptystate.EmptyStateRendererProvider
import io.aequicor.visualization.ui_engine.components.form.FormRendererProvider
import io.aequicor.visualization.ui_engine.components.imageplaceholder.ImagePlaceholderRendererProvider
import io.aequicor.visualization.ui_engine.components.input.InputRendererProvider
import io.aequicor.visualization.ui_engine.components.list.ListRendererProvider
import io.aequicor.visualization.ui_engine.components.menu.MenuRendererProvider
import io.aequicor.visualization.ui_engine.components.screen.ScreenRendererProvider
import io.aequicor.visualization.ui_engine.components.section.SectionRendererProvider
import io.aequicor.visualization.ui_engine.components.sidebar.SidebarRendererProvider
import io.aequicor.visualization.ui_engine.components.tabs.TabsRendererProvider
import io.aequicor.visualization.ui_engine.components.text.TextRendererProvider
import io.aequicor.visualization.ui_engine.components.timeline.TimelineRendererProvider
import io.aequicor.visualization.ui_engine.components.topbar.TopBarRendererProvider

val DefaultUiComponentRendererProviders: List<UiComponentRendererProvider> = listOf(
    ScreenRendererProvider,
    SectionRendererProvider,
    BoxRendererProvider,
    FormRendererProvider,
    SidebarRendererProvider,
    DialogRendererProvider,
    TopBarRendererProvider,
    BottomBarRendererProvider,
    CardRendererProvider,
    ButtonRendererProvider,
    InputRendererProvider,
    ListRendererProvider,
    TabsRendererProvider,
    ImagePlaceholderRendererProvider,
    DataTableRendererProvider,
    TimelineRendererProvider,
    BadgeRendererProvider,
    MenuRendererProvider,
    EmptyStateRendererProvider,
    TextRendererProvider,
)

val DefaultUiNodeRenderers: Map<String, UiNodeRenderer> =
    DefaultUiComponentRendererProviders.associate { it.type to it.renderer }

fun registeredUiRendererTypes(): Set<String> =
    DefaultUiNodeRenderers.keys
