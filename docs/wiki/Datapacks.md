# Datapacks

BEI reads three folders from datapacks. The server sends them to a player when they join and when the datapacks reload (`/reload`).

## Themes: `data/<namespace>/bei_themes/<name>.json`

```json
{
  "name": "Neon",
  "base": "bei:dark",
  "colors": {
    "bg": "#0b0014", "card": "#1a0830", "card_hover": "#2a1050", "field": "#07000d",
    "accent": "#ff2bd6", "text": "#f4e8ff", "dim": "#9a7fb8",
    "good": "#5dffb0", "bad": "#ff5d7a", "warn": "#ffd45d",
    "border": "#5a2a8a", "tooltip": "#0b0014"
  }
}
```

- The id is `namespace:name`; the settings' Theme row shows `name`.
- `base` is `bei:dark` (default) or `bei:light`; colors you leave out come from it.
- Colors are `#RRGGBB`. Backdrop transparency still comes from the player's opacity setting.

## Groups: `data/<namespace>/bei_groups/<name>.json`

```json
{ "name": "Wool", "icon": "minecraft:white_wool", "items": ["#minecraft:wool", "minecraft:string"] }
```

## Hidden items: `data/<namespace>/bei_hidden/<name>.json`

```json
{ "items": ["minecraft:debug_stick", "@somemod"] }
```

- Each entry is an item id, an item tag (`#minecraft:logs`) or a whole mod (`@modid`).
- A group needs at least two items. An item belongs to the first group (by file id) that lists it; `icon` is optional.
- BEI ships example groups for wool, planks, logs and saplings.

## Limits

Files over 32 KB (themes) or 64 KB (groups, hidden) are skipped with a warning in the server log, so one packet stays under the game's size cap.
