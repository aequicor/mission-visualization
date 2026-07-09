# webApp — веб-обёртка (Kotlin/Wasm + Compose)

Тонкая обёртка над `:shared` UI, собираемая в WebAssembly. Здесь же живут
`index.html` / `styles.css` — оболочка страницы и boot-оверлей загрузки.

## Первая загрузка и её оптимизация

Compose-приложение на Wasm грузит два бинарника:

| Файл | dev | prod (wasm-opt) | gzip | brotli |
|---|---:|---:|---:|---:|
| app wasm (`<hash>.wasm`) | ~27 МБ | ~4 МБ | ~1.2 МБ | ~1.0 МБ |
| skiko wasm | 8.25 МБ | 8.25 МБ | ~3.2 МБ | ~2.9 МБ |
| `webApp.js` | ~3.8 МБ | ~0.5 МБ | ~0.1 МБ | — |

Ключевые выводы:

- **dev-сборка (`wasmJsBrowserDevelopmentRun`) весит ~35 МБ** — не оптимизирована,
  с debug-инфо. Годится только для разработки. **Пользователям всегда отдаём
  production-сборку.**
- Production `wasmJsBrowserDistribution` уже прогоняет Binaryen **wasm-opt + DCE**
  (app wasm ~27 МБ → ~4 МБ). Отдельно настраивать нечего.
- Дальше решает **сжатие транспорта**: ~13 МБ prod → **~4.5 МБ gzip / ~3.8 МБ
  brotli**. Это главный рычаг времени первой загрузки по сети.

### Production-сборка одной командой

```bash
./gradlew :webApp:packWasmDist
# → webApp/build/dist/wasmJs/productionExecutable
```

`packWasmDist` берёт готовую дистрибуцию и доводит её «до сети»:

1. Кладёт рядом **предсжатые `.gz` (всегда) и `.br`** (если в системе есть CLI
   `brotli`) — статические хосты отдают их прозрачно (nginx `gzip_static` /
   `brotli_static`, Netlify, Cloudflare Pages, …).
2. Инжектит в `index.html` манифест `window.__MV_WASM_SIZES` — **несжатые**
   размеры wasm — чтобы индикатор загрузки оставался точным даже при
   gzip/brotli (там браузер отдаёт JS уже распакованные байты, а `Content-Length`
   — это сжатый размер).

### Хостинг

- **Включить brotli/gzip** для `application/wasm` (и js/css/html). При наличии
  `.br`/`.gz` (см. выше) — раздавать их напрямую (`gzip_static on;` /
  `brotli_static on;`).
- **Иммутабельное кеширование** для контент-хешированных wasm:
  `Cache-Control: public, max-age=31536000, immutable`; `index.html`/`webApp.js`
  — `must-revalidate`. Для Netlify/Cloudflare Pages это уже задано в
  [`_headers`](src/webMain/resources/_headers); для nginx/Apache — настроить
  эквивалент. (Кеш ускоряет только повторные загрузки, не первую.)
- Отдавать правильный `Content-Type: application/wasm` — иначе теряется
  streaming-компиляция (компиляция во время скачивания).

## Индикатор загрузки (проценты)

Boot-оверлей `#mv-loader` (в `index.html`) показывает реальный прогресс
скачивания. Механика — в inline-скрипте в `<head>` (перед `webApp.js`):

- Патчит **только `window.fetch`** для `*.wasm` (оба бинарника и fallback skiko
  идут через `fetch`). `WebAssembly.*` не трогаем — у app wasm есть 3-й аргумент
  compile-options (`js-string` builtins), который наивная обёртка
  `instantiateStreaming` потеряла бы → LinkError.
- Тело ответа считается через `pipeThrough(TransformStream)` (backpressure от
  консьюмера, без буферизации 27 МБ). Response пересобирается с сохранением
  исходных заголовков, но **без `Content-Length`/`Content-Encoding`** (иначе
  усечение/повторная распаковка распакованных байт).
- Знаменатель: манифест несжатых размеров → иначе `Content-Length` (точно только
  без сжатия) → иначе неопределённый «крип». Всё — с монотонным клампом.
- Скачивание занимает 0..90 %; 90..99 — «крип» на фазе компиляции/монтирования;
  100 % и фейд — по первому кадру Compose (`main.kt` → `__mvHideLoader`).
- Тик анимации на `setTimeout`, а не `requestAnimationFrame` (rAF замирает в
  фоновой вкладке).

Dev-сервер отдаёт wasm **без сжатия** (см.
[`webpack.config.d/mv-dev-server.js`](webpack.config.d/mv-dev-server.js)): на
localhost транспорт мгновенный, а несжатый ответ несёт `Content-Length` →
индикатор точен без манифеста. Production — наоборот: предсжатие + манифест.
