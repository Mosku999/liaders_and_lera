package com.debili.racemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = RaceMod.MODID)
public class RaceEventHandler {
    // 20 игровых тиков = 1 секунда. 12 000 тиков = 10 минут (20 * 60 * 10).
    public static final int SWITCH_INTERVAL_TICKS = 12000;

    private static final List<String> ALL_RACE_IDS = List.of("catboy", "zombi", "dragonkin");
    private static final Random RANDOM = new Random();

    private static final UUID RACE_SPEED_ID =
            UUID.fromString("6a2f5a10-8c7d-4b12-9a01-111111111111");
    private static final UUID RACE_HEALTH_ID =
            UUID.fromString("6a2f5a10-8c7d-4b12-9a01-222222222222");

    private static final Race CATBOY = new CatboyRace();
    private static final Race ZOMBI = new Zombi();
    private static final Race DRAGONKIN = new DragonkinRace();

    // --- DragonBlood поля ---
    private static final Map<UUID, Float> accumulatedDamage = new HashMap<>();
    private static final Map<UUID, Long> eruptionTimers = new HashMap<>();
    private static final List<LavaTask> LAVA_TASKS = new ArrayList<>();
    private static final float DAMAGE_THRESHOLD = 6.0f; // 3 сердца

    // --- DragonFireBurp поля ---
    private static final int BURP_COOLDOWN_TICKS = 600;
    private static final int WARNING_BEFORE_TICKS = 40;
    private static final Map<UUID, Integer> fireBurpTicks = new HashMap<>();

    private static Race getRace(String id) {
        if ("zombi".equals(id)) {
            return ZOMBI;
        }
        if ("dragonkin".equals(id)) {
            return DRAGONKIN;
        }
        return CATBOY;
    }

    // Случайный выбор следующего класса (не повторяя текущий).
    private static String nextRaceId(String currentId) {
        List<String> candidates = new ArrayList<>();
        for (String id : ALL_RACE_IDS) {
            if (!id.equals(currentId)) {
                candidates.add(id);
            }
        }
        if (candidates.isEmpty()) {
            return ALL_RACE_IDS.get(RANDOM.nextInt(ALL_RACE_IDS.size()));
        }
        return candidates.get(RANDOM.nextInt(candidates.size()));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide) return;

        CompoundTag data = player.getPersistentData();

        // При первом появлении игрок получает случайный класс.
        if (!data.contains("race_id")) {
            String firstId = ALL_RACE_IDS.get(RANDOM.nextInt(ALL_RACE_IDS.size()));
            Race firstRace = getRace(firstId);
            data.putString("race_id", firstId);
            data.putString("race_applied", "");
            data.putInt("race_timer", 0);
            player.sendSystemMessage(Component.literal(
                    "Ваш первый класс: " + firstRace.getName() + ". Класс меняется каждые 10 минут."
            ));
        }

        int timer = data.getInt("race_timer") + 1;
        if (timer >= SWITCH_INTERVAL_TICKS) {
            String previousId = data.getString("race_id");
            String nextId = nextRaceId(previousId);
            data.putString("race_id", nextId);
            timer = 0;

            if ("dragonkin".equals(previousId) && !"dragonkin".equals(nextId)) {
                fireBurpTicks.remove(player.getUUID());
                player.getAbilities().flying = false;
                player.getAbilities().mayfly = false;
                player.onUpdateAbilities();
            }

            Race nextRace = getRace(nextId);
            player.sendSystemMessage(Component.literal(
                    "Новый класс: " + nextRace.getName()
            ));
        }
        data.putInt("race_timer", timer);

        Race race = getRace(data.getString("race_id"));
        // Усиленный прыжок кошкомальчика
        if (race.getId().equals("catboy")) {
            MobEffectInstance jump = player.getEffect(MobEffects.JUMP);

            if (jump == null || (jump.getAmplifier() == 0 && jump.getDuration() < 100)) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.JUMP,
                        220,
                        0,
                        true,
                        false,
                        false
                ));
            }
        }
        // Убираем наш эффект при смене расы
        else if (data.getString("race_applied").equals("catboy")) {
            player.removeEffect(MobEffects.JUMP);
        }
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
        // ЛОГИКА ГОРЕНИЯ НА СОЛНЦЕ
        if (race.burnsInSunlight()) {
            if (player.level().isDay() && player.level().canSeeSky(player.blockPosition()) && !player.isInWater()) {
                if (player.getRemainingFireTicks() <= 0) {
                    player.setSecondsOnFire(5);
                }
            }
        }

        // ==========================================
        // DRAGONFIREBURP: ОТРЫЖКА ПЛАМЕНЕМ
        // ==========================================
        if (race.getId().equals("dragonkin")) {
            if (player.isCreative() || player.isSpectator()) return;

            UUID uuid = player.getUUID();
            int burpTicks = fireBurpTicks.getOrDefault(uuid, 0) + 1;

            if (burpTicks == (BURP_COOLDOWN_TICKS - WARNING_BEFORE_TICKS)) {
                player.sendSystemMessage(Component.literal("В горле разгорается пламя... Отрыжка через 2 секунды!"));
            }

            if (burpTicks >= BURP_COOLDOWN_TICKS) {
                burpTicks = 0;
                executeFireBurp(player);
            }

            fireBurpTicks.put(uuid, burpTicks);
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

        // Замедление копания для дракона (из DragonkinSlowness)
        if ("dragonkin".equals(race.getId())) {
            multiplier *= 0.65f;
        }

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

    @SubscribeEvent
    public static void onStartEating(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof Player player)) return;

        String raceId = player.getPersistentData().getString("race_id");
        Race race = getRace(raceId);

        if (race == null || !race.canOnlyEatMeat()) return;

        ItemStack item = event.getItem();

        if (!isMeat(item)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("Эта раса может есть только мясо!"));
        }
    }

    @SubscribeEvent
    public static void onFinishEating(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player)) return;

        String raceId = player.getPersistentData().getString("race_id");
        Race race = getRace(raceId);
        if (race == null || !race.isImmuneToHunger()) return;

        if (event.getItem().is(Items.ROTTEN_FLESH)) {
            player.removeEffect(MobEffects.HUNGER);
        }
    }

    private static boolean isMeat(ItemStack stack) {
        return stack.is(Items.ROTTEN_FLESH) || stack.is(Items.BEEF) || stack.is(Items.COOKED_BEEF) ||
                stack.is(Items.PORKCHOP) || stack.is(Items.COOKED_PORKCHOP) ||
                stack.is(Items.CHICKEN) || stack.is(Items.COOKED_CHICKEN) ||
                stack.is(Items.MUTTON) || stack.is(Items.COOKED_MUTTON) ||
                stack.is(Items.RABBIT) || stack.is(Items.COOKED_RABBIT) ||
                stack.is(Items.COD) || stack.is(Items.SALMON);
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

    // ==========================================
    // ЛОГИКА УРОНА ДЛЯ ДРАКОНОРОЖДЕННОГО
    // ==========================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // 1. УМЕНЬШЕНИЕ ВХОДЯЩЕГО УРОНА (защита)
        if (event.getEntity() instanceof Player player) {
            String raceId = player.getPersistentData().getString("race_id");
            if ("dragonkin".equals(raceId)) {
                float originalDamage = event.getAmount();
                event.setAmount(originalDamage * 0.8f);
            }
        }

        // 2. УВЕЛИЧЕНИЕ ИСХОДЯЩЕГО УРОНА (атака)
        if (event.getSource().getEntity() instanceof Player player) {
            String raceId = player.getPersistentData().getString("race_id");
            if ("dragonkin".equals(raceId)) {
                float originalDamage = event.getAmount();
                event.setAmount(originalDamage * 1.2f);
            }
        }

        // --- DragonBlood: накопление урона при получении урона драконом ---
        if (event.getEntity() instanceof Player player) {
            String raceId = player.getPersistentData().getString("race_id");
            if (!"dragonkin".equals(raceId)) return;
            if (player.level().isClientSide()) return;

            UUID playerUUID = player.getUUID();

            if (eruptionTimers.containsKey(playerUUID)) {
                return;
            }

            float currentDamage = event.getAmount();
            float totalDamage = accumulatedDamage.getOrDefault(playerUUID, 0.0f) + currentDamage;

            if (totalDamage >= DAMAGE_THRESHOLD) {
                accumulatedDamage.put(playerUUID, 0.0f);
                eruptionTimers.put(playerUUID, System.currentTimeMillis() + 2000);
                player.sendSystemMessage(Component.literal("§c!Твоя кровь закипает! Лава вырвется через 2 секунды!"));
            } else {
                accumulatedDamage.put(playerUUID, totalDamage);
            }
        }
    }

    // ==========================================
    // FIREIMMUNITY: ИММУНИТЕТ К ОГНЮ (СЕРВЕР)
    // ==========================================
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        // Проверяем, что игрок — дракон
        String raceId = player.getPersistentData().getString("race_id");
        if (!"dragonkin".equals(raceId)) {
            return;
        }
        // Блокируем урон от огня и лавы
        if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true);
        }
    }

    // ==========================================
    // DRAGONBLOOD + DRAGONKINHUNGER: ТИК ИГРОКА
    // ==========================================
    @SubscribeEvent
    public static void onDragonBloodTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }

        UUID playerUUID = player.getUUID();
        String raceId = player.getPersistentData().getString("race_id");

        // --- DragonkinHunger: усиленный голод для дракона ---
        if ("dragonkin".equals(raceId)) {
            if (!player.isCreative() && !player.isSpectator()) {
                if (player.getDeltaMovement().horizontalDistanceSqr() > 0.005) {
                    player.causeFoodExhaustion(0.005f);
                }
            }
        }

        // --- DragonBlood: проверка таймера извержения ---
        if (eruptionTimers.containsKey(playerUUID)) {
            long targetTime = eruptionTimers.get(playerUUID);

            if (System.currentTimeMillis() >= targetTime) {
                eruptionTimers.remove(playerUUID);

                Level level = player.level();
                BlockPos pos = player.blockPosition();

                if (level.getBlockState(pos).isAir() || level.getBlockState(pos).liquid()) {
                    level.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
                    LAVA_TASKS.add(new LavaTask(level, pos, 60));
                }
            }
        }
    }

    // ==========================================
    // DRAGONBLOOD: ТИК СЕРВЕРА (УДАЛЕНИЕ ЛАВЫ, ОЧИСТКА ПАМЯТИ)
    // ==========================================
    @SubscribeEvent
    public static void onDragonBloodServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (int i = LAVA_TASKS.size() - 1; i >= 0; i--) {
            LavaTask task = LAVA_TASKS.get(i);
            task.ticksLeft--;

            if (task.ticksLeft <= 0) {
                if (task.level.getBlockState(task.pos).is(Blocks.LAVA)) {
                    task.level.setBlockAndUpdate(task.pos, Blocks.AIR.defaultBlockState());
                }
                LAVA_TASKS.remove(i);
            }
        }

        if (event.getServer().getTickCount() % 20 == 0 && !accumulatedDamage.isEmpty()) {
            accumulatedDamage.replaceAll((uuid, damage) -> Math.max(0.0f, damage - 0.5f));
            accumulatedDamage.values().removeIf(damage -> damage <= 0.0f);
        }
    }

    // ==========================================
    // DRAGONFIREBURP: ВЫПОЛНЕНИЕ ОТРЫЖКИ
    // ==========================================
    private static void executeFireBurp(Player player) {
        Level level = player.level();
        BlockPos playerPos = player.blockPosition();

        boolean shouldExplode = level.random.nextFloat() < 0.10f;
        if (shouldExplode) {
            level.explode(player, player.getX(), player.getY(), player.getZ(), 2.1f, Level.ExplosionInteraction.MOB);
        }

        Vec3 lookVec = player.getLookAngle();
        int radius = 3;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos targetPos = playerPos.offset(x, y, z);
                    Vec3 blockVec = new Vec3(
                            targetPos.getX() + 0.5 - player.getX(),
                            targetPos.getY() + 0.5 - player.getEyeY(),
                            targetPos.getZ() + 0.5 - player.getZ()
                    );

                    double distance = blockVec.length();
                    if (distance <= 3.5) {
                        double dotProduct = lookVec.dot(blockVec.normalize());
                        if (dotProduct > 0.5) {
                            if (level.isEmptyBlock(targetPos) && BaseFireBlock.canBePlacedAt(level, targetPos, Direction.DOWN)) {
                                level.setBlockAndUpdate(targetPos, Blocks.FIRE.defaultBlockState());
                            }
                        }
                    }
                }
            }
        }

        AABB area = new AABB(playerPos).inflate(3);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (entity != player) {
                Vec3 entityVec = new Vec3(entity.getX() - player.getX(), entity.getY() - player.getY(), entity.getZ() - player.getZ());
                double dotProduct = lookVec.dot(entityVec.normalize());

                if (dotProduct > 0.5) {
                    entity.setSecondsOnFire(5);
                }
            }
        }
    }

    private static class LavaTask {
        final Level level;
        final BlockPos pos;
        int ticksLeft;

        LavaTask(Level level, BlockPos pos, int ticksLeft) {
            this.level = level;
            this.pos = pos;
            this.ticksLeft = ticksLeft;
        }
    }
}