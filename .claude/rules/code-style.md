# Стандарты кода: Kotlin · Coroutines · Compose

Дополняет `@.claude/rules/architecture.md`. Правила — обязательные для нового кода.

## Kotlin
- Официальные [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
- **Именование:** типы — `PascalCase`; функции/свойства — `camelCase`; константы — `UPPER_SNAKE`.
  Пакеты — строчными без подчёркиваний (см. отклонение ниже).
- **Иммутабельность:** предпочитать `val`; данные — immutable `data class`; состояние и intent'ы —
  `sealed`-иерархии (уже так: `UiCommand`, `UiValue`, `UiLoadResult`).
- **Явность:** явная видимость для публичного API; expression body для коротких функций; trailing commas
  в многострочных списках параметров.
- **Отклонение проекта:** пакеты `ui_engine`, `mv_yaml_source` и т.п. используют snake_case, что нарушает
  конвенцию. Новые пакеты — без подчёркиваний; переименование существующих — отдельным крупным рефактором
  (не по ходу задач).

## Моделирование состояния (MVI — уже принято в проекте)
- Один immutable `State`, `sealed` набор intent'ов, чистый reducer `(State, Intent) -> State`
  (`reduceUiVisualization`). Паттерн сохраняем; побочные эффекты — вне reducer.

## Coroutines
- [Coroutines guide](https://kotlinlang.org/docs/coroutines-guide.html) ·
  [Best practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices).
- **Main-safety:** тяжёлую работу (парсинг/валидация больших документов) — через `withContext(dispatcher)`.
- **Инъекция dispatcher'ов:** `CoroutineDispatcher` передаётся как зависимость (для тестов), не берётся
  из `Dispatchers.*` внутри логики.
- **Structured concurrency:** без `GlobalScope`; scope владеет запусками; корректная отмена
  (проверять `isActive`, не глотать `CancellationException`).
- **Наружу — потоки состояния:** presentation отдаёт `StateFlow`/`Flow` иммутабельного состояния.
- **Тесты:** `kotlinx-coroutines-test` — `runTest`, `TestDispatcher`
  ([docs](https://developer.android.com/kotlin/coroutines/test)).

## Compose Multiplatform
- **Stateless + hoisting:** composable получает state и колбэки; своё состояние не создаёт (кроме чисто
  визуального `remember`). Бизнес-логики в UI нет.
- **`Modifier`:** первый необязательный параметр после обязательных, значение по умолчанию `Modifier`,
  применяется к корню компонента.
- **Небольшие компоненты + slot-API:** контент передавать лямбдами (`content: @Composable () -> Unit`),
  а не булевыми флагами.
- **Токены, а не «магия»:** цвета — из `UiRenderTokens` (`LapisBlue`, `DeepLapis`, `toneSurface`/`toneStroke`),
  отступы — через `spacingDp`. Сырые hex / `.dp` в компонентах не хардкодить.
- **Изоляция рендереров:** провайдеры в `components.*` зависят от общих контрактов
  (`compose_render_engine`), не друг от друга (см. layering rules в `ui_engine/README.md`).
- Референс: [Designing Effective Compose](https://getstream.io/blog/designing-effective-compose/).

## Форматирование и линт
- Пока в проекте нет ktlint/detekt. Рекомендуется добавить (ktlint — формат, detekt — запахи) и повесить
  на CI. До этого — официальный стиль Kotlin и настройки IDE по умолчанию.

## Чек-лист
- [ ] Именование и иммутабельность по конвенциям; новые пакеты без подчёркиваний.
- [ ] Suspend-работа main-safe; dispatcher'ы внедрены; без `GlobalScope`.
- [ ] Composable stateless, есть `Modifier`-параметр, цвета/отступы через токены.
