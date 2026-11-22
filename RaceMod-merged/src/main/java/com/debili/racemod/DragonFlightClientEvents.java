package com.debili.racemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RaceMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class DragonFlightClientEvents {

    private static boolean wasJumpPressed = false;
    private static int jumpTicks = 0;
    private static int pressCount = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator() || player.isCreative()) return;

        // Работает ТОЛЬКО у дракона
        String raceId = player.getPersistentData().getString("race_id");
        if (!"dragonkin".equals(raceId)) {
            pressCount = 0;
            jumpTicks = 0;
            return;
        }

        if (jumpTicks > 0) {
            jumpTicks--;
            if (jumpTicks == 0) {
                pressCount = 0;
            }
        }

        boolean isJumpPressed = mc.options.keyJump.isDown();

        if (isJumpPressed && !wasJumpPressed) {
            if (player.onGround()) {
                pressCount = 0;
                jumpTicks = 0;
            } else {
                pressCount++;
                if (pressCount == 1) {
                    jumpTicks = 7;
                } else if (pressCount == 2 && jumpTicks > 0) {
                    int cooldown = player.getPersistentData().getInt(DragonFlightServerEvents.COOLDOWN_KEY);

                    if (cooldown <= 0 && !player.onClimbable() && !player.isInWater()) {
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                    }
                    pressCount = 0;
                    jumpTicks = 0;
                }
            }
        }

        wasJumpPressed = isJumpPressed;
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        if (player == null || player.isCreative() || player.isSpectator()) return;

        // Работает ТОЛЬКО у дракона
        String raceId = player.getPersistentData().getString("race_id");
        if (!"dragonkin".equals(raceId)) return;

        int cooldown = player.getPersistentData().getInt(DragonFlightServerEvents.COOLDOWN_KEY);
        if (cooldown > 0) {
            event.getInput().jumping = false;
            player.setSprinting(false);
        }
    }
}