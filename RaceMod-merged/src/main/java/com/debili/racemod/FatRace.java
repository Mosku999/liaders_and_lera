
package com.debili.racemod;

public class FatRace implements Race {

    @Override
    public String getId() {
        return "fat";
    }

    @Override
    public String getName() {
        return "Жиробасс";
    }

    // На 30% медленнее
    @Override
    public double getMovementSpeedMultiplier() {
        return -0.30D;
    }

    // +20 HP: всего 40 HP
    @Override
    public double getMaxHealthBonus() {
        return 20.0D;
    }
}
