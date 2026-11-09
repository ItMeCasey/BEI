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

    static Stream<ItemStack> stacks(List<ItemStack> items) {
        return items.stream();
    }
}
