
package com.debili.racemod;

public class Zombi implements Race {

    @Override
    public String getId() {
        return "zombi";
    }

    @Override
    public String getName() {
        return "Зомби";
    }

    // Зомби копает очень медленно.
    @Override
    public float getMiningSpeedMultiplier() {
        return 0.07F;
    }

    // Скорость ходьбы на 30% ниже обычной.
    @Override
    public double getMovementSpeedMultiplier() {
        return -0.30D;
    }

    @Override
    public boolean canOnlyEatMeat() {
        return true;
    }

    @Override
    public boolean isImmuneToHunger() {
        return true;
    }

    @Override
    public boolean burnsInSunlight() {
        return true;
    }

    @Override
    public double getMaxHealthBonus() {
        return 10.0D;
    }
}
