package dev.coreviewer.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class ReplayKeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void coreviewer$shortcuts(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (dev.coreviewer.replay.ReplayController.handleKey(action, event)) ci.cancel();
    }
}
