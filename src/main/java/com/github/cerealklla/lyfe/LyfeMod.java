package com.github.cerealklla.lyfe;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.github.cerealklla.lyfe.gathering.GatheringListener;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// The value here must match the modId entry in META-INF/neoforge.mods.toml (sourced from mod_id in gradle.properties)
@Mod(LyfeMod.MODID)
public class LyfeMod {
    public static final String MODID = "lyfe";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LyfeMod(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        Skills.bootstrap();

        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new GatheringListener());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Lyfe common setup");
    }

    /**
     * DEBUG ONLY — grants raw materials for testing the sign/map mechanic (design doc Section 9.1)
     * before that mechanic exists: oak signs, oak fences (to place as mounting posts), and blank
     * maps. Deliberately naive: fires on every login, not just a brand-new character, since
     * re-supplying test items each session is a minor inconvenience at worst and precisely
     * detecting "first-ever spawn" adds complexity not worth it for throwaway debug tooling. Must
     * be removed or gated behind a real debug flag before any actual release.
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        player.addItem(new ItemStack(Items.OAK_SIGN, 16));
        player.addItem(new ItemStack(Items.OAK_FENCE, 16));
        player.addItem(new ItemStack(Items.MAP, 8));
        LOGGER.info("Granted debug sign/fence/map testing items to {}", player.getName().getString());
    }
}
