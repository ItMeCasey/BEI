# BEI branding

Logo, banners and section headers for Barely Enough Items, drawn in the same style as SMWTI.

- `icon/`: the logo (a tilted book with BEI on the cover), pixel art scaled up without smoothing, drawn by `source/make_icon.py`. `icon_<size>.png` for every size; use `icon_512.png` for CurseForge (square, at least 400x400).
- `banners/`: `banner.png` (description banner), `social.png`, `features.png` (gallery card) and `h_*.png` section headers.
- `source/`: `banners.html` draws everything (original pixel font and sprites, no Minecraft textures); `render_banners.py` screenshots it.

Regenerate from the repository root (needs Python, Pillow and Google Chrome):

```
python branding/source/make_icon.py
python branding/source/render_banners.py
```

## Palette

| Name | Hex | Use |
|---|---|---|
| Night | `#0f0e1a` to `#1a1830` | Backgrounds |
| Edge | `#2e2a46` | Borders |
| Cyan | `#2ec4d6` (light `#b4f6ff`, dark `#1b7f95`) | Wordmark, accents |
| Blue | `#4a9bff` | Search text |
| Book | `#3e4296` (spine `#222458`) | Logo cover |
| Gold | `#f2b33d` | Logo letters |
| Text | `#f4f2ff`, muted `#a9a4c8` | Copy |
