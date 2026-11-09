package com.itmecasey.bei.compat;

import com.itmecasey.bei.Bei;
import com.itmecasey.bei.client.ViewerFacade;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The parts of the JEI runtime that stand for JEI's own screens (the ingredient list, bookmarks, the recipes screen,
 * edit mode, ingredient visibility), answered from BEI's matching screens. A plugin that reads the search text, asks what
 * is under the mouse, bookmarks an item, opens the recipes of an item or checks whether an item is hidden gets BEI's answer.
 */
final class JeiScreens {
    private JeiScreens() {
    }

    private static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static boolean isItems(Object type) {
        return type == VanillaTypes.ITEM_STACK;
    }

    private static Optional<Object> typedOrEmpty(@Nullable ItemStack stack) {
        return stack == null || stack.isEmpty() ? Optional.empty() : Optional.of(JeiViews.typed(stack));
    }

    /** The adapter for the runtime object of this type, or null when BEI has no equivalent (the caller logs it). */
    static @Nullable Object adapter(Class<?> type, Object helpers) {
        return switch (type.getSimpleName()) {
            case "IIngredientFilter" -> filter(type);
            case "IIngredientListOverlay" -> listOverlay(type);
            case "IBookmarkOverlay" -> bookmarkOverlay(type);
            case "IBookmarkManager" -> bookmarkManager(type);
            case "IRecipesGui" -> recipesGui(type);
            case "IEditModeConfig" -> editMode(type);
            case "IIngredientVisibility" -> visibility(type);
            case "IScreenHelper" -> screenHelper(type);
            default -> null;
        };
    }

    private static Object filter(Class<?> type) {
        return JeiBridge.proxyFirst(type, (self, method, args) -> switch (method.getName()) {
            case "getFilterText" -> ViewerFacade.searchText();
            case "setFilterText" -> {
                ViewerFacade.setSearchText(args[0] == null ? "" : args[0].toString());
                yield null;
            }
            case "getFilteredItemStacks" -> ViewerFacade.filtered();
            case "getFilteredIngredients" -> isItems(args[0]) ? ViewerFacade.filtered() : List.of();
            default -> JeiBridge.DEFER;
        });
    }

    private static Object listOverlay(Class<?> type) {
        return JeiBridge.proxyFirst(type, (self, method, args) -> switch (method.getName()) {
            case "getIngredientUnderMouse" -> args.length == 0 ? typedOrEmpty(ViewerFacade.gridStack())
                    : isItems(args[0]) ? ViewerFacade.gridStack() : null;
            case "isListDisplayed" -> ViewerFacade.listShown();
            case "hasKeyboardFocus" -> ViewerFacade.searchFocused();
            case "getVisibleIngredients" -> isItems(args[0]) ? ViewerFacade.filtered() : List.of();
            default -> JeiBridge.DEFER;
        });
    }

    private static Object bookmarkOverlay(Class<?> type) {
        return JeiBridge.proxyFirst(type, (self, method, args) -> switch (method.getName()) {
            case "getIngredientUnderMouse" -> args.length == 0 ? typedOrEmpty(ViewerFacade.bookmarkStack())
                    : isItems(args[0]) ? ViewerFacade.bookmarkStack() : null;
            case "getItemStackUnderMouse" -> {
                ItemStack stack = ViewerFacade.bookmarkStack();
                yield stack == null ? ItemStack.EMPTY : stack;
            }
            default -> JeiBridge.DEFER;
        });
    }

    private static Object bookmarkManager(Class<?> type) {
        return JeiBridge.proxyFirst(type, (self, method, args) -> {
            boolean ingredient = args.length == 1;
            if (!ingredient) {
                return switch (method.getName()) {
                    case "contains", "add", "remove" -> false;
                    default -> JeiBridge.DEFER;
                };
            }
            ItemStack stack = JeiBridge.item(args[0]);
            if (stack.isEmpty()) {
                return false;
            }
            return switch (method.getName()) {
                case "contains" -> ViewerFacade.bookmarked(id(stack));
                case "add" -> ViewerFacade.setBookmarked(id(stack), true);
                case "remove" -> ViewerFacade.setBookmarked(id(stack), false);
                default -> JeiBridge.DEFER;
            };
        });
    }

    private static Object recipesGui(Class<?> type) {
        return JeiBridge.proxyFirst(type, (self, method, args) -> {
            switch (method.getName()) {
                case "show":
                    if (args.length == 1) {
                        Object focus = args[0] instanceof List<?> list ? (list.isEmpty() ? null : list.get(0)) : args[0];
                        openFocus(focus);
                    }
                    return null;
                case "showTypes":
                    if (args.length == 1 && args[0] instanceof List<?> types) {
                        List<String> uids = new ArrayList<>();
                        for (Object value : types) {
                            if (value instanceof mezz.jei.api.recipe.RecipeType<?> recipeType) {
                                uids.add(recipeType.getUid().toString());
                            }
                        }
                        if (!uids.isEmpty()) {
                            ViewerFacade.showCategories(uids);
                        }
                    }
                    return null;
                case "showRecipes":
                    if (args.length >= 1 && args[0] instanceof mezz.jei.api.recipe.category.IRecipeCategory<?> category) {
                        ViewerFacade.showCategories(List.of(category.getRecipeType().getUid().toString()));
                    }
                    return null;
                case "getIngredientUnderMouse":
                    return Optional.empty();
                case "getParentScreen":
                    return Optional.empty();
                default:
                    return JeiBridge.DEFER;
            }
        });
    }

    /** Opens BEI's recipe popup for a JEI focus: recipes that make the item for an output focus, uses otherwise. */
    private static void openFocus(@Nullable Object focus) {
        if (focus == null) {
            return;
        }
        try {
            Class<?> iface = Class.forName("mezz.jei.api.recipe.IFocus", false, JeiBridge.class.getClassLoader());
            Object typed = iface.getMethod("getTypedValue").invoke(focus);
            Object role = iface.getMethod("getRole").invoke(focus);
            ItemStack stack = JeiBridge.item(typed);
            if (!stack.isEmpty()) {
                ViewerFacade.showRecipes(stack.getItem(), role != RecipeIngredientRole.OUTPUT);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Bei.LOGGER.debug("Could not open recipes for a JEI focus", e);
        }
    }

    private static Object editMode(Class<?> type) {
        return JeiBridge.proxyFirst(type, (self, method, args) -> {
            ItemStack stack = args.length > 0 ? JeiBridge.item(args[0]) : ItemStack.EMPTY;
            switch (method.getName()) {
                case "isIngredientHiddenUsingConfigFile":
                    return !stack.isEmpty() && ViewerFacade.hiddenInEditMode(id(stack));
                case "getIngredientHiddenUsingConfigFile":
                    return Set.of();
                case "hideIngredientUsingConfigFile":
                    if (!stack.isEmpty()) {
                        ViewerFacade.setHiddenInEditMode(id(stack), true);
                    }
                    return null;
                case "showIngredientUsingConfigFile":
                    if (!stack.isEmpty()) {
                        ViewerFacade.setHiddenInEditMode(id(stack), false);
                    }
                    return null;
                default:
                    return JeiBridge.DEFER;
            }
        });
    }

    private static boolean visible(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        String id = id(stack);
        return !JeiHidden.itemSpecs().contains(id) && !ViewerFacade.hiddenInEditMode(id);
    }

    private static Object visibility(Class<?> type) {
        return JeiBridge.proxyFirst(type, (self, method, args) -> {
            switch (method.getName()) {
                case "isIngredientVisible":
                    return visible(JeiBridge.item(args.length == 1 || args.length == 2 && !(args[0] instanceof mezz.jei.api.ingredients.IIngredientType)
                            ? args[0] : args[1]));
                case "hideIngredients":
                case "unhideIngredients": {
                    boolean hide = method.getName().equals("hideIngredients");
                    for (Object value : JeiBridge.values(args[1])) {
                        ItemStack stack = JeiBridge.item(value);
                        if (!stack.isEmpty()) {
                            if (hide) {
                                JeiHidden.hideItem(id(stack));
                            } else {
                                JeiHidden.showItem(id(stack));
                            }
                        }
                    }
                    return null;
                }
                case "registerListener":
                    return null;
                default:
                    return JeiBridge.DEFER;
            }
        });
    }

    private static Object screenHelper(Class<?> type) {
        return JeiBridge.proxyFirst(type, (self, method, args) -> switch (method.getName()) {
            case "getGuiExclusionAreas" -> JeiBridge.extraAreaObjects(args[0]).stream();
            case "getGhostIngredientHandlers" -> JeiBridge.ghostHandlers(args[0]);
            case "getGhostIngredientHandler" -> {
                List<Object> handlers = JeiBridge.ghostHandlers(args[0]);
                yield handlers.isEmpty() ? Optional.empty() : Optional.of(handlers.get(0));
            }
            case "getClickableIngredientUnderMouse", "getGuiClickableArea" -> Stream.empty();
            case "getGuiProperties" -> Optional.empty();
            default -> JeiBridge.DEFER;
        });
    }

    static List<Object> list(Collection<?> values) {
        return new ArrayList<>(values);
    }
}
