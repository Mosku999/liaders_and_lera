package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DragonFlightServerEvents {

    public static final String FLY_TIME_KEY = "DragonKinFlyTime";
    public static final String COOLDOWN_KEY = "DragonKinFlyCooldown";
    private static final String WAS_FLYING_KEY = "DragonKinWasFlying";
    private static final String FALL_START_Y_KEY = "DragonKinFallStartY";

    private static final int MAX_FLY_TIME = 40;   // 2 секунды (40 тиков)
    private static final int COOLDOWN_TIME = 200; // 10 секунд (200 тиков)

    private static final UUID EXHAUSTION_SPEED_UUID =
            UUID.fromString("1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d");

    private static final AttributeModifier NO_SPRINT_SPEED = new AttributeModifier(
            EXHAUSTION_SPEED_UUID,
            "Dragon flight exhaustion speed",
            -0.40,
            AttributeModifier.Operation.MULTIPLY_BASE
    );

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide) return;
        if (player.isCreative() || player.isSpectator()) return;

        CompoundTag nbt = player.getPersistentData();
        String raceId = nbt.getString("race_id");
        AttributeInstance speedAttribute = player.getAttribute(Attributes.MOVEMENT_SPEED);

        if (!"dragonkin".equals(raceId)) {
            clearFlightState(player, nbt, speedAttribute);
            return;
        }

        int flyTime = nbt.getInt(FLY_TIME_KEY);
        int cooldown = nbt.getInt(COOLDOWN_KEY);

        if (cooldown > 0) {
            cooldown--;
            nbt.putInt(COOLDOWN_KEY, cooldown);

            if (player.isSprinting()) {
                player.setSprinting(false);
            }
            if (speedAttribute != null && speedAttribute.getModifier(EXHAUSTION_SPEED_UUID) == null) {
                speedAttribute.addTransientModifier(NO_SPRINT_SPEED);
            }
            if (player.getAbilities().mayfly || player.getAbilities().flying) {
                disableFlightAndForceFall(player);
            }

            Vec3 velocity = player.getDeltaMovement();
            if (velocity.y > 0.0D && velocity.y < 0.5D) {
                player.setDeltaMovement(velocity.x, 0.0D, velocity.z);
                player.hasImpulse = true;
            }

            if (cooldown == 0) {
                if (speedAttribute != null) {
                    speedAttribute.removeModifier(EXHAUSTION_SPEED_UUID);
                }
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            }
        }

        if (player.getAbilities().flying) {
            if (cooldown > 0) {
                disableFlightAndForceFall(player);
                return;
            }

            nbt.putBoolean(WAS_FLYING_KEY, true);
            if (!nbt.contains(FALL_START_Y_KEY) || player.getY() > nbt.getDouble(FALL_START_Y_KEY)) {
                nbt.putDouble(FALL_START_Y_KEY, player.getY());
            }

            flyTime++;
            nbt.putInt(FLY_TIME_KEY, flyTime);

            if (flyTime >= MAX_FLY_TIME) {
                disableFlightAndForceFall(player);
                nbt.putInt(COOLDOWN_KEY, COOLDOWN_TIME);
                nbt.putInt(FLY_TIME_KEY, 0);
            }
        } else {
            if (player.onGround()) {
                if (nbt.getBoolean(WAS_FLYING_KEY)) {
                    if (nbt.contains(FALL_START_Y_KEY)) {
                        double startY = nbt.getDouble(FALL_START_Y_KEY);
                        double endY = player.getY();
                        float totalFallHeight = (float) (startY - endY);

                        if (totalFallHeight > 3.0F) {
                            float damage = totalFallHeight - 3.0F;
                            player.hurt(player.damageSources().fall(), damage);
                        }
                        nbt.remove(FALL_START_Y_KEY);
                    }

                    if (nbt.getInt(COOLDOWN_KEY) <= 0 && nbt.getInt(FLY_TIME_KEY) > 0) {
                        nbt.putInt(COOLDOWN_KEY, COOLDOWN_TIME);
                    }
                    nbt.putBoolean(WAS_FLYING_KEY, false);
                }

                if (flyTime > 0) {
                    nbt.putInt(FLY_TIME_KEY, 0);
                }
            } else if (nbt.getBoolean(WAS_FLYING_KEY) && nbt.contains(FALL_START_Y_KEY)) {
                if (player.getY() > nbt.getDouble(FALL_START_Y_KEY)) {
                    nbt.putDouble(FALL_START_Y_KEY, player.getY());
                }
            }

            if (cooldown <= 0) {
                if (!player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                }
            } else if (player.getAbilities().mayfly) {
                player.getAbilities().mayfly = false;
                player.onUpdateAbilities();
            }
        }
    }

    private static void disableFlightAndForceFall(Player player) {
        player.getAbilities().flying = false;
        player.getAbilities().mayfly = false;
        player.onUpdateAbilities();
    }

    private static void clearFlightState(Player player, CompoundTag nbt, AttributeInstance speedAttribute) {
        boolean hadState = nbt.getInt(FLY_TIME_KEY) > 0
                || nbt.getInt(COOLDOWN_KEY) > 0
                || nbt.getBoolean(WAS_FLYING_KEY)
                || nbt.contains(FALL_START_Y_KEY);

        if (hadState) {
            nbt.putInt(FLY_TIME_KEY, 0);
            nbt.putInt(COOLDOWN_KEY, 0);
            nbt.putBoolean(WAS_FLYING_KEY, false);
            nbt.remove(FALL_START_Y_KEY);
        }

        if (speedAttribute != null && speedAttribute.getModifier(EXHAUSTION_SPEED_UUID) != null) {
            speedAttribute.removeModifier(EXHAUSTION_SPEED_UUID);
        }

        if (player.getAbilities().flying || player.getAbilities().mayfly) {
            player.getAbilities().flying = false;
            player.getAbilities().mayfly = false;
            player.onUpdateAbilities();
        }
    }
}