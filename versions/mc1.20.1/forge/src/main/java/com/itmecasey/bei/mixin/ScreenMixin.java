package com.itmecasey.bei.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The world behind inventories and other container screens stays as bright as in play: vanilla's full-screen dim is skipped. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true)
    private void bei$noDim(GuiGraphics graphics, CallbackInfo ci) {
        if ((Object) this instanceof AbstractContainerScreen<?> && Minecraft.getInstance().level != null) {
            ci.cancel();
        }
    }
}
