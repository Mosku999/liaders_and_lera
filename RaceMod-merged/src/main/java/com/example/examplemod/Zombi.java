package com.example.examplemod;

public class Zombi implements Race {

    @Override
    public String getId() {
        return "zombi";
    }

    @Override
    public String getName() {
        return "Зомби";
    }

    // Зомби копает в 0.07 раза от обычной скорости.
    @Override
    public float getMiningSpeedMultiplier() {
        return 0.07F;
    }

    // Скорость ходьбы на 30% ниже обычной.
    // RaceEventHandler применяет это как MULTIPLY_TOTAL.
    @Override
    public double getMovementSpeedMultiplier() {
        return -0.30D;
    }
}
