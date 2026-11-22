package com.debili.racemod;

public class CatboyRace implements Race {
    @Override
    public String getId() {
        return "catboy";
    }

    @Override
    public String getName() {
        return "Кошкомальчик";
    }

    @Override
    public double getMovementSpeedMultiplier() {
        return 0.20D;
    }

    @Override
    public double getMaxHealthBonus() {
        return -10.0D; // 20 HP -> 10 HP (5 сердечек)
    }

    @Override
    public boolean hasNightVision() {
        return true;
    }

    @Override
    public boolean takesWaterDamage() {
        return true;
    }

    @Override
    public boolean ignoresFallDamage() {
        return true;
    }

    @Override
    public boolean cannotWearArmor() {
        return true;
    }

    @Override
    public boolean getsExtraFoodFromFish() {
        return true;
    }
}
