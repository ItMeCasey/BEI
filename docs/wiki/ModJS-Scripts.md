# ModJS Scripts

With [ModJS](https://github.com/ItMeCasey/ModJS) installed, its client scripts (`RecipeViewerEvents`) control BEI. BEI reaches ModJS by reflection, so it needs no ModJS jar and does nothing without it. Scripts are read when a world is joined.

```js
RecipeViewerEvents.hideItems(e => e.hide('minecraft:dirt', '#minecraft:wool', '@create'));
RecipeViewerEvents.groupItems(e => e.group('Gems', ['minecraft:diamond', '#c:gems'], { icon: 'minecraft:diamond' }));
RecipeViewerEvents.addInformation(e => e.add('minecraft:diamond', 'Found deep underground.'));
RecipeViewerEvents.hideCategories(e => e.hide('minecraft:campfire_cooking'));
```

- **hideItems**: hides items from the browser (id, `#tag` or `@mod`).
- **groupItems**: makes browser groups; script groups come after datapack groups.
- **addInformation**: adds an "Information" button with your text to those items' recipe popup.
- **hideCategories**: hides recipes by recipe type id (`minecraft:crafting` covers every crafting type). On 26.1.2 this needs the server to have BEI, since BEI learns each recipe's type from it.
- ModJS's unifier: items it replaces are hidden so only the canonical item shows.
