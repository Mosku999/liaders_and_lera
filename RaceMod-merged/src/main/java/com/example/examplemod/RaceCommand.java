package com.example.examplemod;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public class RaceCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("catboy")
                .executes(context -> setRace(
                        context.getSource().getPlayerOrException(), "catboy", "Кошкомальчик"
                ))
        );

        // Обе команды работают: /zombi и /zombie
        dispatcher.register(Commands.literal("zombi")
                .executes(context -> setRace(
                        context.getSource().getPlayerOrException(), "zombi", "Зомби"
                ))
        );

        dispatcher.register(Commands.literal("zombie")
                .executes(context -> setRace(
                        context.getSource().getPlayerOrException(), "zombi", "Зомби"
                ))
        );

        // Команды для Драгонкина: /dragonkin и /dragon
        dispatcher.register(Commands.literal("dragonkin")
                .executes(context -> setRace(
                        context.getSource().getPlayerOrException(), "dragonkin", "Драгонкин"
                ))
        );

        dispatcher.register(Commands.literal("dragon")
                .executes(context -> setRace(
                        context.getSource().getPlayerOrException(), "dragonkin", "Драгонкин"
                ))
        );
    }

    private static int setRace(Player player, String id, String name) {
        String previousId = player.getPersistentData().getString("race_id");

        player.getPersistentData().putString("race_id", id);
        // Пустое значение заставляет обработчик применить способности новой расы.
        player.getPersistentData().putString("race_applied", "");
        player.getPersistentData().putInt("race_timer", 0);

        if ("dragonkin".equals(previousId) && !"dragonkin".equals(id)) {
            player.getPersistentData().putInt(DragonFlightServerEvents.FLY_TIME_KEY, 0);
            player.getPersistentData().putInt(DragonFlightServerEvents.COOLDOWN_KEY, 0);
            player.getAbilities().flying = false;
            player.getAbilities().mayfly = false;
            player.onUpdateAbilities();
        }

        player.sendSystemMessage(Component.literal(
                "Вы выбрали класс: " + name + ". Автоматическая смена через 10 минут."
        ));
        return 1;
    }
}