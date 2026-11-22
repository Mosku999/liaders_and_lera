
package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public class RaceEventHandler {

    public static final int SWITCH_INTERVAL_TICKS = 600;

    private static final UUID RACE_SPEED_ID =
            UUID.fromString("6a2f5a10-8c7d-4b12-9a01-111111111111");
    private static final UUID RACE_HEALTH_ID =
            UUID.fromString("6a2f5a10-8c7d-4b12-9a01-222222222222");

    private static final Race GNOME = new GnomeRace();
    private static final Race CATBOY = new CatboyRace();
    private static final Race ZOMBI = new Zombi();
    private static final Race FAT = new FatRace();

    private static Race getRace(String id) {
        if ("catboy".equals(id)) return CATBOY;
        if ("zombi".equals(id)) return ZOMBI;
        if ("fat".equals(id)) return FAT;
        return GNOME;
    }

    // Гном -> Кошкомальчик -> Зомби -> Жиробас -> Гном
    private static String nextRaceId(String currentId) {
        if ("gnome".equals(currentId)) return "catboy";
        if ("catboy".equals(currentId)) return "zombi";
        if ("zombi".equals(currentId)) return "fat";
        return "gnome";
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide) return;

        CompoundTag data = player.getPersistentData();

        if (!data.contains("race_id")) {
            data.putString("race_id", "gnome");
            data.putString("race_applied", "");
            data.putInt("race_timer", 0);
            player.sendSystemMessage(Component.literal(
                    "Ваш первый класс: Гном. Класс меняется каждые 30 секунд."
            ));
        }

        int timer = data.getInt("race_timer") + 1;

        if (timer >= SWITCH_INTERVAL_TICKS) {
            String nextId = nextRaceId(data.getString("race_id"));
            data.putString("race_id", nextId);
            timer = 0;

            Race nextRace = getRace(nextId);
            player.sendSystemMessage(Component.literal(
                    "Новый класс: " + nextRace.getName()
            ));
        }

        data.putInt("race_timer", timer);

        Race race = getRace(data.getString("race_id"));

        // СПОСОБНОСТИ ЖИРОБАСА
        if (race.getId().equals("fat")) {

            // Быстрее расходуется голод
            player.causeFoodExhaustion(0.05F);

            boolean wasOnGround = data.getBoolean("fat_was_on_ground");

            // Проверяем момент приземления
            if (data.contains("fat_was_on_ground")
                    && !wasOnGround && player.onGround()) {

                // 2 сердца дополнительного урона
                player.hurt(player.damageSources().fall(), 4.0F);

                // Разрушаем до 6 блоков под игроком
                BlockPos center = player.blockPosition().below();

                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 0; z++) {
                        BlockPos pos = center.offset(x, 0, z);
                        BlockState state = player.level().getBlockState(pos);

                        if (!state.isAir()
                                && !state.hasBlockEntity()
                                && state.getDestroySpeed(player.level(), pos) >= 0) {
                            player.level().destroyBlock(pos, true, player);
                        }
                    }
                }

                // Отбрасываем мобов в радиусе 4 блоков
                for (Mob mob : player.level().getEntitiesOfClass(
                        Mob.class,
                        player.getBoundingBox().inflate(4.0D, 2.0D, 4.0D))) {

                    double dx = mob.getX() - player.getX();
                    double dz = mob.getZ() - player.getZ();
                    double distance = Math.sqrt(dx * dx + dz * dz);

                    if (distance > 0.1D) {
                        mob.push(
                                dx / distance * 1.8D,
                                0.65D,
                                dz / distance * 1.8D
                        );
                    }
                }
            }
        }

        // Запоминаем положение игрока
        data.putBoolean("fat_was_on_ground", player.onGround());

        // Усиленный прыжок Кошкомальчика
        if (race.getId().equals("catboy")) {
            MobEffectInstance jump = player.getEffect(MobEffects.JUMP);

            if (jump == null || (jump.getAmplifier() == 0 && jump.getDuration() < 100)) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.JUMP, 220, 0, true, false, false
                ));
            }
        } else if (data.getString("race_applied").equals("catboy")) {
            player.removeEffect(MobEffects.JUMP);
        }

        applyRaceAttributes(player, race);

        // Ночное зрение Кошкомальчика
        if (race.hasNightVision()) {
            MobEffectInstance nightVision = player.getEffect(MobEffects.NIGHT_VISION);

            if (nightVision == null || nightVision.getDuration() < 100) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.NIGHT_VISION, 220, 0, true, false, false
                ));
            }
        }

        // Урон от воды
        if (race.takesWaterDamage()
                && player.isInWater()
                && player.tickCount % 20 == 0) {
            player.hurt(player.damageSources().drown(), 2.0F);
        }

        // Кошкомальчик не может носить броню
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

        // Горение Зомби на солнце
        if (race.burnsInSunlight()) {
            if (player.level().isDay()
                    && player.level().canSeeSky(player.blockPosition())
                    && !player.isInWater()) {

                if (player.getRemainingFireTicks() <= 0) {
                    player.setSecondsOnFire(5);
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
                || (speed != null
                && race.getMovementSpeedMultiplier() != 0.0D
                && speed.getModifier(RACE_SPEED_ID) == null)
                || (health != null
                && race.getMaxHealthBonus() != 0.0D
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
            player.getFoodData().eat(
                    food.getNutrition(),
                    food.getSaturationModifier()
            );
        }
    }

    @SubscribeEvent
    public static void onStartEating(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Race race = getRace(player.getPersistentData().getString("race_id"));

        if (!race.canOnlyEatMeat()) return;

        ItemStack item = event.getItem();

        if (!isMeat(item)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal(
                    "Эта раса может есть только мясо!"
            ));
        }
    }

    @SubscribeEvent
    public static void onFinishEating(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Race race = getRace(player.getPersistentData().getString("race_id"));

        if (!race.isImmuneToHunger()) return;

        if (event.getItem().is(Items.ROTTEN_FLESH)) {
            player.removeEffect(MobEffects.HUNGER);
        }
    }

    private static boolean isMeat(ItemStack stack) {
        return stack.is(Items.ROTTEN_FLESH)
                || stack.is(Items.BEEF)
                || stack.is(Items.COOKED_BEEF)
                || stack.is(Items.PORKCHOP)
                || stack.is(Items.COOKED_PORKCHOP)
                || stack.is(Items.CHICKEN)
                || stack.is(Items.COOKED_CHICKEN)
                || stack.is(Items.MUTTON)
                || stack.is(Items.COOKED_MUTTON)
                || stack.is(Items.RABBIT)
                || stack.is(Items.COOKED_RABBIT)
                || stack.is(Items.COD)
                || stack.is(Items.SALMON);
    }

    @SubscribeEvent
    public static void onZombieTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof Player player)) return;

        if (event.getEntity().getType().getCategory() != MobCategory.MONSTER) return;

        String raceId = player.getPersistentData().getString("race_id");

        if ("zombi".equals(raceId)) {
            event.setCanceled(true);
        }
    }
}
