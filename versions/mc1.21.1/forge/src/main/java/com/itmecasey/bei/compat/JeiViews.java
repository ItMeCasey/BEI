package com.itmecasey.bei.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Small stand-ins for the JEI objects a category's draw call and a transfer handler read: a recipe's slots, the typed
 * ingredients in them, and the errors a transfer handler answers with.
 */
final class JeiViews {
    private JeiViews() {
    }

    /** One slot of a recipe as a plugin sees it. */
    record SlotSpec(RecipeIngredientRole role, List<ItemStack> items, @Nullable String name) {
    }

    /** The error a transfer handler returned, read back by BEI to decide the plus button. */
    record Failure(IRecipeTransferError.Type type, @Nullable Component message) {
    }

    static ITypedIngredient<ItemStack> typed(ItemStack stack) {
        return JeiBridge.proxy(ITypedIngredient.class, (self, method, args) -> switch (method.getName()) {
            case "getIngredient" -> stack;
            case "getType" -> VanillaTypes.ITEM_STACK;
            case "getItemStack" -> Optional.of(stack);
            default -> JeiBridge.fallback(method.getReturnType());
        });
    }

    static IRecipeSlotView slot(SlotSpec spec) {
        List<ItemStack> items = spec.items();
        return JeiBridge.proxy(IRecipeSlotView.class, (self, method, args) -> switch (method.getName()) {
            case "getRole" -> spec.role();
            case "getSlotName" -> Optional.ofNullable(spec.name());
            case "isEmpty" -> items.isEmpty();
            case "getAllIngredients" -> items.stream().map(JeiViews::typed);
            case "getDisplayedIngredient" -> items.isEmpty() ? Optional.empty() : Optional.of(typed(items.get(0)));
            case "getDisplayedItemStack" -> items.isEmpty() ? Optional.empty() : Optional.of(items.get(0));
            case "getItemStacks" -> items.stream();
            case "getIngredients" -> items.stream();
            default -> JeiBridge.fallback(method.getReturnType());
        });
    }

    static IRecipeSlotsView slots(List<SlotSpec> specs) {
        List<IRecipeSlotView> views = new ArrayList<>();
        for (SlotSpec spec : specs) {
            views.add(slot(spec));
        }
        return JeiBridge.proxy(IRecipeSlotsView.class, (self, method, args) -> {
            switch (method.getName()) {
                case "getSlotViews":
                    if (args.length == 1) {
                        List<IRecipeSlotView> filtered = new ArrayList<>();
                        for (IRecipeSlotView view : views) {
                            if (view.getRole() == args[0]) {
                                filtered.add(view);
                            }
                        }
                        return filtered;
                    }
                    return views;
                case "findSlotByName":
                    for (IRecipeSlotView view : views) {
                        if (view.getSlotName().equals(Optional.ofNullable(args[0] instanceof String s ? s : null))) {
                            return Optional.of(view);
                        }
                    }
                    return Optional.empty();
                default:
                    return JeiBridge.fallback(method.getReturnType());
            }
        });
    }

    static IRecipeTransferError error(IRecipeTransferError.Type type, @Nullable Component message) {
        return JeiBridge.proxy(IRecipeTransferError.class, (self, method, args) -> switch (method.getName()) {
            case "getType" -> type;
            case "getButtonHighlightColor" -> 0x80FF0000;
            case "bei$failure" -> new Failure(type, message);
            default -> JeiBridge.fallback(method.getReturnType());
        });
    }

    /** The failure behind an error object, or null when it is not one this bridge made. */
    static @Nullable Failure failure(Object error) {
        if (error == null) {
            return null;
        }
        try {
            IRecipeTransferError typedError = (IRecipeTransferError) error;
            // Errors made here carry their message; errors a plugin built itself are shown generically.
            Object type = typedError.getType();
            if (type instanceof IRecipeTransferError.Type kind) {
                return new Failure(kind, MESSAGES.get(error));
            }
        } catch (RuntimeException | LinkageError e) {
            // Fall through to the generic failure.
        }
        return new Failure(IRecipeTransferError.Type.INTERNAL, null);
    }

    /** Messages of the errors made here, by error object. */
    private static final java.util.Map<Object, Component> MESSAGES = new java.util.WeakHashMap<>();

    static IRecipeTransferError userError(Component message) {
        IRecipeTransferError error = error(IRecipeTransferError.Type.USER_FACING, message);
        synchronized (MESSAGES) {
            MESSAGES.put(error, message);
        }
        return error;
    }

    /** The helper a transfer handler gets to build its errors. */
    static IRecipeTransferHandlerHelper helper() {
        return JeiBridge.proxy(IRecipeTransferHandlerHelper.class, (self, method, args) -> {
            String name = method.getName();
            if (name.equals("createInternalError")) {
                return error(IRecipeTransferError.Type.INTERNAL, null);
            }
            if (name.equals("getGuiSlotIndexToIngredientMap") && args != null && args.length == 1) {
                return ingredientMap(args[0]);
            }
            if (name.startsWith("createUserError")) {
                for (Object arg : args) {
                    if (arg instanceof Component message) {
                        return userError(message);
                    }
                }
                return userError(Component.literal("Cannot transfer this recipe"));
            }
            return JeiBridge.fallback(method.getReturnType());
        });
    }

    /**
     * The recipe's ingredients keyed by their index in the recipe's input slots (the order BEI hands plugins in
     * {@code getSlotViews(INPUT)}); empty ingredients are left out. Transfer handlers such as AE2's use it to find
     * which ingredients the player or the network is missing.
     */
    static java.util.Map<Integer, Object> ingredientMap(Object holder) {
        java.util.Map<Integer, Object> map = new java.util.HashMap<>();
        try {
            Object recipe = holder;
            java.lang.reflect.Method value = JeiTransfers.find(holder.getClass(), "value", 0);
            if (value != null) {
                recipe = value.invoke(holder);
            }
            List<?> ingredients = ingredientList(recipe);
            for (int i = 0; i < ingredients.size(); i++) {
                Object ingredient = ingredients.get(i);
                if (ingredient != null && !isEmptyIngredient(ingredient)) {
                    map.put(i, ingredient);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return map;
        }
        return map;
    }

    private static List<?> ingredientList(Object recipe) throws ReflectiveOperationException {
        java.lang.reflect.Method list = JeiTransfers.find(recipe.getClass(), "getIngredients", 0);
        if (list != null) {
            return (List<?>) list.invoke(recipe);
        }
        java.lang.reflect.Method info = JeiTransfers.find(recipe.getClass(), "placementInfo", 0);
        if (info != null) {
            Object placement = info.invoke(recipe);
            java.lang.reflect.Method ingredients = JeiTransfers.find(placement.getClass(), "ingredients", 0);
            if (ingredients != null) {
                return (List<?>) ingredients.invoke(placement);
            }
        }
        return List.of();
    }

    private static boolean isEmptyIngredient(Object ingredient) {
        try {
            java.lang.reflect.Method empty = JeiTransfers.find(ingredient.getClass(), "isEmpty", 0);
            return empty != null && Boolean.TRUE.equals(empty.invoke(ingredient));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    static Stream<ItemStack> stacks(List<ItemStack> items) {
        return items.stream();
    }
}
