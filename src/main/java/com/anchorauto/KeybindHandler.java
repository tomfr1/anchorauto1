package com.anchorauto;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class KeybindHandler {
    private final AnchorInteraction interaction = new AnchorInteraction();
    private final KeyBinding activateKey;
    private final KeyBinding guiKey;

    public KeybindHandler() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(AnchorAutoMod.MOD_ID, "main"));
        activateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.anchorauto.activate", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, category));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.anchorauto.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        interaction.tick(client);
        while (activateKey.wasPressed()) {
            interaction.start(client);
        }
        while (guiKey.wasPressed()) {
            client.setScreen(new ConfigScreen(client.currentScreen));
        }
    }
}
