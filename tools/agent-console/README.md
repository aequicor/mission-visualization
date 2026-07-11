# agent-console — автоматизация для ИИ-агента

Headless-подсистема управления: ИИ-агент **сам обновляет экраны** (правит CNL-исходники
`*.layout.md` своими файловыми инструментами) и **сам читает экран** — выгружает
отрендеренный Canvas в PNG без окна и браузера. Тонкий JVM CLI поверх чистого конвейера
`compileSlm → IR → resolve → layout` + Compose-рендер в off-screen `ImageComposeScene`
(skiko, реальный текст-измеритель и забандленные шрифты — пиксели идентичны редактору).

## Цикл агента

```
правка *.layout.md  →  validate (IR-* диагностика, само-коррекция)
        ↑                          ↓
   render / inspect  ←  компиляция проекта
```

## Команды

Все команды принимают источник: `--project DIR` (папка `*.layout.md`) или `--samples`
(3 встроенных Welcome-экрана). JSON — в stdout, ошибки — в stderr, код выхода ≠ 0 при ошибке.

```bash
# Список экранов
./gradlew :tools:agent-console:run --args="screens --samples"

# Экран → PNG (чтение экрана; --scale 2 по умолчанию)
./gradlew :tools:agent-console:run --args="render --project /path/proj --screen welcomeVectors --out /tmp/t.png"
./gradlew :tools:agent-console:run --args="render --project /path/proj --all --out-dir /tmp/shots"

# Дерево с вычисленной геометрией (дешевле картинки; text-hug размеры приближённые)
./gradlew :tools:agent-console:run --args="inspect --project /path/proj --screen welcomeVectors --node rocket"

# Диагностика: компиляция + validateDesignDocument; exit 2 при error-severity
./gradlew :tools:agent-console:run --args="validate --project /path/proj --screen welcomeEditor"

# Выгрузить встроенные Welcome-экраны как стартовый корпус файлов
./gradlew :tools:agent-console:run --args="export-samples --to /path/proj"

# Скаффолд нового экрана (через CreateScreen-intent редактора: frontmatter + CNL + стабильный id)
./gradlew :tools:agent-console:run --args="create-screen --project /path/proj --preset mobile --title 'New Screen'"
```

## Устройство

| Файл | Роль |
|---|---|
| `AgentProject` | папка `*.layout.md` ↔ `List<MissionDocumentSource>`; `samples()` |
| `AgentSession` | фасад чистого конвейера: state + screens/inspect/validate/createScreen |
| `HeadlessRenderer` | `DesignArtboard` в `ImageComposeScene` → PNG-байты; сцена = root-фрейм × scale (без полей) |
| `AgentJson` | JSON-проекции (runtime-билдеры, без serialization-плагина) |
| `AgentCli` / `main` | подкоманды, парсинг аргументов, коды выхода |

Ядро (`AgentProject`/`AgentSession`/`HeadlessRenderer`) UI-агностично — будущий MCP-сервер
(фаза 2) оборачивает эти же классы в тулы `read_screen`/`update_screen`/`list_screens`/`validate`
без изменений в них.

Авторинг CNL — по `SLM-SKILL.md` и `design-book/semantic-layout-markdown-i18n.md`.

## Тесты

```bash
./gradlew :tools:agent-console:test
```

Включая реальные headless-рендеры: PNG-сигнатура + точные размеры IHDR (1440×1024 при
scale 1, 2880×2048 при scale 2).
