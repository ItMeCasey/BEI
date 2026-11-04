# Plugin API

Mods add their own recipe categories, recipes and card looks with a class marked `@BeiPlugin`. BEI finds it by scanning mod classes, so your mod needs no call into BEI and works without BEI installed. Compile against the API jar (`./gradlew apiJar` builds one with only `com.itmecasey.bei.api`).

```java
@BeiPlugin("mymachines")            // optional: only load when this mod is installed
public class MyPlugin implements IBeiPlugin {
    private RecipeCategory grinding;

    @Override
    public void registerCategories(ICategoryRegistration registration) {
        grinding = RecipeCategory.builder(ResourceLocation.fromNamespaceAndPath("mymachines", "grinding"))
                .title(Component.literal("Grinding"))
                .station(new ItemStack(MyItems.GRINDER.get()))   // its icon becomes the category's button
                .size(110, 36)                                   // card area in pixels
                .input(8, 9).input(28, 9).output(86, 9)          // slot positions inside the card area
                .painter(new CardPainter() {                     // optional extras: arrows, bars, text
                    public void paint(GuiGraphics g, CustomRecipe recipe, int mx, int my) {
                        g.fill(50, 16, 78, 20, 0xFF4F9DFF);
                    }
                    public List<Component> tooltip(CustomRecipe recipe, int mx, int my) {
                        return mx >= 50 && mx < 78 ? List.of(Component.literal("Takes 4 seconds")) : null;
                    }
                })
                .build();
        registration.register(grinding);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.add(grinding, CustomRecipe.builder()
                .input(new ItemStack(Items.COBBLESTONE))
                .input(new ItemStack(Items.DIAMOND))
                .output(new ItemStack(Items.GRAVEL, 4))
                .build());
    }
}
```

The sample uses the 1.21.1 names `ResourceLocation` and `GuiGraphics`; on 26.1.2 they are `Identifier` and `GuiGraphicsExtractor`.

## Rules

- Plugins run on the client only.
- `registerCategories` runs once, the first time the player joins a world. **Build item stacks inside it (or later), not in static fields**: stacks cannot be made while the game starts.
- `registerRecipes` runs each time the recipe list is built, so recipes may depend on the world.
- The n-th input slot you declare shows the recipe's n-th `input(...)`; likewise for outputs. An `input(...)` may list several stacks; BEI cycles through them.
- `painter` draws inside the card area, (0, 0) at its top left, under the item slots.
- Custom recipes appear under their station button, in "uses" lookups and in the Master Project tracker.
- A plugin that fails to load or throws is logged and skipped; it cannot break BEI or other plugins.
