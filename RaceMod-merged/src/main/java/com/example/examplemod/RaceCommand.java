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

        dispatcher.register(Commands.literal("gnome")
                .executes(context -> setRace(
                        context.getSource().getPlayerOrException(), "gnome", "Гном"
                ))
        );

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
    }

    private static int setRace(Player player, String id, String name) {
        player.getPersistentData().putString("race_id", id);
        // Пустое значение заставляет обработчик применить способности новой расы.
        player.getPersistentData().putString("race_applied", "");
        player.getPersistentData().putInt("race_timer", 0);
        player.sendSystemMessage(Component.literal(
                "Вы выбрали класс: " + name + ". Автоматическая смена через 30 секунд."
        ));
        return 1;
    }
}
