package com.debili.racemod;

public class DragonkinRace implements Race {
    @Override
    public String getId() {
        return "dragonkin";
    }

    @Override
    public String getName() {
        return "Драгонкин";
    }

    @Override
    public boolean ignoresFallDamage() {
        return false;
    }
}