package com.example.echolocation;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class EcholocationClient implements ClientModInitializer {
    public static final String MOD_ID = "echolocation";

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) {
                EchoManager.clear();
                return;
            }
            if (client.isPaused()) return;
            EchoManager.tick(client.level);
        });
        EchoRenderer.register();
    }
}
