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
<<<<<<< HEAD
        return 0.07F;
=======
        return 0.7F;
>>>>>>> f1dd476e3902ad26adb803017d660bededed9552
    }

    // Скорость ходьбы на 30% ниже обычной.
    // RaceEventHandler применяет это как MULTIPLY_TOTAL.
    @Override
    public double getMovementSpeedMultiplier() {
        return -0.30D;
    }
<<<<<<< HEAD
=======

    @Override
    public boolean canOnlyEatMeat() {
        return true; // Зомби может есть только мясо
    }

    @Override
    public boolean isImmuneToHunger() {
        return true; // Зомби не получает голод от гнилой плоти
    }
    @Override
    public boolean burnsInSunlight() {
        return true; // Зомби горит на солнце
    }

    @Override
    public double getMaxHealthBonus() {
        return 10.0D; // Добавляем 5 сердец (10 единиц здоровья)
    }
>>>>>>> f1dd476e3902ad26adb803017d660bededed9552
}
