# Mission Editor menu icons

This folder contains the production SVG icons for Mission Editor menus and
toolbars.

Design rules:

- 24x24 rendered size.
- `viewBox="0 0 240 240"` to preserve the traced geometry.
- `fill="currentColor"` for theme coloring.
- No embedded PNGs or raster payloads inside the SVG files.
- Generic editor metaphors, not copied Figma brand marks.

Canonical icons:

- Workspace: `app-menu`, `source`, `screens`, `inspector`.
- Source tabs: `markdown`, `assets`, `layers`.
- Canvas tools: `select`, `hand-pan`, `marquee`, `frame`, `component`,
  `rectangle`, `pen`, `text`, `link`, `code`.
- Inspector: `position`, `layout`, `fill`, `stroke`, `typography`,
  `visibility`, `lock`.
- Assets/output: `gradient`, `color-selector`, `zoom-fit`, `export`.
