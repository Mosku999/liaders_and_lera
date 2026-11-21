package com.example.examplemod;

import net.minecraft.world.entity.player.Player;

/**
 * Общий интерфейс класса персонажа.
 * Новые классы можно добавлять отдельными Java-файлами.
 */
public interface Race {
    String getId();
    String getName();

    default float getMiningSpeedMultiplier() {
        return 1.0F;
    }

    default double getMovementSpeedMultiplier() {
        return 0.0D;
    }

    /** Может ли раса есть только мясо? */
    default boolean canOnlyEatMeat() {
        return false;
    }

    /** Иммунитет к эффекту голода (например, от гнилой плоти) */
    default boolean isImmuneToHunger() {
        return false;
    }

    /** Горит ли раса на солнце? */
    default boolean burnsInSunlight() {
        return false;
    }

    /** Прибавка к максимальному здоровью. Для 5 сердечек используется -10. */
    default double getMaxHealthBonus() {
        return 0.0D;
    }

    default boolean hasNightVision() {
        return false;
    }

    default boolean takesWaterDamage() {
        return false;
    }

    default boolean ignoresFallDamage() {
        return false;
    }

    default boolean cannotWearArmor() {
        return false;
    }

    default boolean getsExtraFoodFromFish() {
        return false;
    }
}
