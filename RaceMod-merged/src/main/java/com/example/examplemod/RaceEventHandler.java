package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.food.FoodProperties;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public class RaceEventHandler {
    // 20 игровых тиков = 1 секунда. 600 тиков = 30 секунд.
    // Для 10 минут поменяйте 600 на 12000.
    public static final int SWITCH_INTERVAL_TICKS = 600;

    private static final UUID RACE_SPEED_ID =
            UUID.fromString("6a2f5a10-8c7d-4b12-9a01-111111111111");
    private static final UUID RACE_HEALTH_ID =
            UUID.fromString("6a2f5a10-8c7d-4b12-9a01-222222222222");

    private static final Race GNOME = new GnomeRace();
    private static final Race CATBOY = new CatboyRace();
    private static final Race ZOMBI = new Zombi();

    private static Race getRace(String id) {
        if ("catboy".equals(id)) {
            return CATBOY;
        }
        if ("zombi".equals(id)) {
            return ZOMBI;
        }
        return GNOME;
    }

    // Порядок автоматической смены: Гном -> Кошкомальчик -> Зомби -> Гном.
    private static String nextRaceId(String currentId) {
        if ("gnome".equals(currentId)) {
            return "catboy";
        }
        if ("catboy".equals(currentId)) {
            return "zombi";
        }
        return "gnome";
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide) return;

        CompoundTag data = player.getPersistentData();

        // При первом появлении игрок начинает за Гнома.
        if (!data.contains("race_id")) {
            data.putString("race_id", "gnome");
            data.putString("race_applied", "");
            data.putInt("race_timer", 0);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Ваш первый класс: Гном. Класс меняется каждые 30 секунд."
            ));
        }

        int timer = data.getInt("race_timer") + 1;
        if (timer >= SWITCH_INTERVAL_TICKS) {
            String nextId = nextRaceId(data.getString("race_id"));
            data.putString("race_id", nextId);
            timer = 0;

            Race nextRace = getRace(nextId);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Новый класс: " + nextRace.getName()
            ));
        }
        data.putInt("race_timer", timer);

        Race race = getRace(data.getString("race_id"));
        applyRaceAttributes(player, race);

        // Ночное зрение поддерживается, пока активен Кошкомальчик.
        if (race.hasNightVision()) {
            MobEffectInstance nightVision = player.getEffect(MobEffects.NIGHT_VISION);
            if (nightVision == null || nightVision.getDuration() < 100) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.NIGHT_VISION, 220, 0, true, false, false
                ));
            }
        }

        // Урон от воды: одно сердечко в секунду.
        if (race.takesWaterDamage() && player.isInWater() && player.tickCount % 20 == 0) {
            player.hurt(player.damageSources().drown(), 2.0F);
        }

        // Кошкомальчик не может носить броню: снимаем её и выбрасываем.
        if (race.cannotWearArmor()) {
            for (int i = 0; i < player.getInventory().armor.size(); i++) {
                ItemStack armor = player.getInventory().armor.get(i);
                if (!armor.isEmpty()) {
                    ItemStack dropped = armor.copy();
                    player.getInventory().armor.set(i, ItemStack.EMPTY);
                    player.drop(dropped, true);
                }
            }
        }
    }

    private static void applyRaceAttributes(Player player, Race race) {
        CompoundTag data = player.getPersistentData();
        String previousRace = data.getString("race_applied");

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);

        boolean needsUpdate = !race.getId().equals(previousRace)
                || (speed != null && race.getMovementSpeedMultiplier() != 0.0D
                    && speed.getModifier(RACE_SPEED_ID) == null)
                || (health != null && race.getMaxHealthBonus() != 0.0D
                    && health.getModifier(RACE_HEALTH_ID) == null);

        if (!needsUpdate) return;

        if (previousRace.equals("catboy") && !race.hasNightVision()) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }

        if (speed != null) {
            speed.removeModifier(RACE_SPEED_ID);
            if (race.getMovementSpeedMultiplier() != 0.0D) {
                speed.addTransientModifier(new AttributeModifier(
                        RACE_SPEED_ID,
                        "Race movement speed",
                        race.getMovementSpeedMultiplier(),
                        AttributeModifier.Operation.MULTIPLY_TOTAL
                ));
            }
        }

        if (health != null) {
            health.removeModifier(RACE_HEALTH_ID);
            if (race.getMaxHealthBonus() != 0.0D) {
                health.addTransientModifier(new AttributeModifier(
                        RACE_HEALTH_ID,
                        "Race max health",
                        race.getMaxHealthBonus(),
                        AttributeModifier.Operation.ADDITION
                ));
            }
            player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
        }

        data.putString("race_applied", race.getId());
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        Race race = getRace(player.getPersistentData().getString("race_id"));
        float multiplier = race.getMiningSpeedMultiplier();

        if (multiplier != 1.0F) {
            event.setNewSpeed(event.getOriginalSpeed() * multiplier);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player) {
            Race race = getRace(player.getPersistentData().getString("race_id"));
            if (race.ignoresFallDamage()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onEatFish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        Race race = getRace(player.getPersistentData().getString("race_id"));
        if (!race.getsExtraFoodFromFish()) return;

        ItemStack item = event.getItem();
        boolean isFish = item.is(Items.COD)
                || item.is(Items.SALMON)
                || item.is(Items.TROPICAL_FISH)
                || item.is(Items.PUFFERFISH)
                || item.is(Items.COOKED_COD)
                || item.is(Items.COOKED_SALMON);

        if (!isFish) return;

        FoodProperties food = item.getItem().getFoodProperties(item, player);
        if (food != null) {
            player.getFoodData().eat(food.getNutrition(), food.getSaturationModifier());
        }
    }
}
