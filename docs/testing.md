# Testing

## 2026.11.18 / 2026.11.19: no hints in the interface

- Unit tests (`gradlew test`, through the Gradle slot) pass on NeoForge 26.3 and Forge 26.3.
- Real-client screen tour (`BEI_SCREENS`, isolated launcher) on NeoForge 26.3 and Forge 26.3: frames in `docs/frames/neoforge-26.3` and `docs/frames/forge-26.3` (browser, search, group and item tooltips, variants, recipe popup, stations, uses, settings, themes, cheat mode, planner). The settings popup, recipe popup and group tooltip show names and values only; no leftover hint text.
- The other eight builds compile; they carry the same change.
