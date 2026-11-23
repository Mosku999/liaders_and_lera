
package com.debili.racemod;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RaceMod.MODID)
public class FatCommand {

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("fat")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();

                            player.getPersistentData().putString("race_id", "fat");

                            player.sendSystemMessage(
                                    Component.literal("Теперь ты Жиробасс!")
                            );

                            return Command.SINGLE_SUCCESS;
                        })
        );
    }
}
