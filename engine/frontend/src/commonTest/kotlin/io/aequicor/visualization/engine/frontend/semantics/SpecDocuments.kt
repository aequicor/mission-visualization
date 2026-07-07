package io.aequicor.visualization.engine.frontend.semantics

/**
 * The spec's RU source document (design-book/semantic-layout-markdown-i18n.md,
 * "Source Format" section) and a line-for-line faithful EN twin. Shared by the
 * parity, never-translated and golden compile tests.
 */
internal val SpecRuDocument = """
---
screen: missionDashboard
page: Operations
sourceLocale: ru-RU
targetLocales:
  - ru-RU
  - en-US
density: compact
platform: web
theme: light
frame:
  preset: desktop-1440
  width: 1440
  height: 1024
canvas:
  section: Mission Flow
  position:
    x: 1200
    y: 400
flow:
  id: missionOperations
  node: dashboard
  next:
    - createMissionDialog
breakpoints:
  - id: desktop
    minWidth: 1024
  - id: mobile
    maxWidth: 767
libraries:
  - id: ds
    source: "@company/design-system"
---

# Панель миссий

Верхняя панель: заголовок Mission Control, справа основная кнопка [Создать миссию](/missions/new).

Фильтры:
- Поиск по {{query.search}}
- Статус из {{query.status}}

Если {{missions.length == 0}}:
> Пустое состояние: миссий пока нет. Основное действие [Создать миссию](/missions/new).

Миссии:
- Карточка для каждой {{mission in missions}}:
  - Название: {{mission.name}}
  - Статус: {{mission.status}} как badge
  - Действие: [Открыть](/missions/{{mission.id}})
""".trimStart()

internal val SpecEnDocument = SpecRuDocument
    .replace("sourceLocale: ru-RU", "sourceLocale: en-US")
    .replace("# Панель миссий", "# Mission Dashboard")
    .replace(
        "Верхняя панель: заголовок Mission Control, справа основная кнопка [Создать миссию](/missions/new).",
        "Top bar: title Mission Control, on the right primary button [Create mission](/missions/new).",
    )
    .replace("Фильтры:", "Filters:")
    .replace("- Поиск по {{query.search}}", "- Search by {{query.search}}")
    .replace("- Статус из {{query.status}}", "- Status from {{query.status}}")
    .replace("Если {{missions.length == 0}}:", "If {{missions.length == 0}}:")
    .replace(
        "> Пустое состояние: миссий пока нет. Основное действие [Создать миссию](/missions/new).",
        "> Empty state: no missions yet. Primary action [Create mission](/missions/new).",
    )
    .replace("Миссии:", "Missions:")
    .replace("- Карточка для каждой {{mission in missions}}:", "- Card for each {{mission in missions}}:")
    .replace("  - Название: {{mission.name}}", "  - Name: {{mission.name}}")
    .replace("  - Статус: {{mission.status}} как badge", "  - Status: {{mission.status}} as badge")
    .replace(
        "  - Действие: [Открыть](/missions/{{mission.id}})",
        "  - Action: [Open](/missions/{{mission.id}})",
    )
