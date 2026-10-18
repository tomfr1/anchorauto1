package com.anchorauto;

import net.fabricmc.api.ClientModInitializer;

public class AnchorAutoMod implements ClientModInitializer {
    public static final String MOD_ID = "anchorauto";

    @Override
    public void onInitializeClient() {
        AnchorAutoConfig.load();
        new KeybindHandler().register();
    }
}
