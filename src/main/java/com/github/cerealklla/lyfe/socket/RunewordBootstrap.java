package com.github.cerealklla.lyfe.socket;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/** Loads (or, on first boot, generates) this server's randomized Runeword-rune-sequence mapping -- mirrors {@code craft.VanillaRecipeStripper#onServerStarting} exactly. */
public final class RunewordBootstrap {

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        ServerRunewordStore.loadOrGenerate(event.getServer());
    }
}
