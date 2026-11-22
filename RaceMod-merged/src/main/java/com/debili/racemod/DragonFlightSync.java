package com.debili.racemod;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = RaceMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DragonFlightSync {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RaceMod.MODID, "dragon_flight_sync"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int messageId = 0;

    public static void register() {
        CHANNEL.registerMessage(
                messageId++,
                SyncPacket.class,
                SyncPacket::encode,
                SyncPacket::decode,
                SyncPacket::handle
        );
    }

    public static void sendToPlayer(ServerPlayer player, String raceId, int cooldownTicks) {
        CHANNEL.sendTo(
                new SyncPacket(raceId, cooldownTicks),
                player.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT
        );
    }

    @SubscribeEvent
    public static void onPlayerTickSync(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        // Синхронизируем каждые 5 тиков, чтобы не спамить трафиком
        if (serverPlayer.tickCount % 5 == 0) {
            String raceId = serverPlayer.getPersistentData().getString("race_id");
            int cooldown = serverPlayer.getPersistentData().getInt(DragonFlightServerEvents.COOLDOWN_KEY);
            sendToPlayer(serverPlayer, raceId, cooldown);
        }
    }

    public static class SyncPacket {
        private final String raceId;
        private final int cooldownTicks;

        public SyncPacket(String raceId, int cooldownTicks) {
            this.raceId = raceId;
            this.cooldownTicks = cooldownTicks;
        }

        public static void encode(SyncPacket pkt, FriendlyByteBuf buf) {
            buf.writeUtf(pkt.raceId);
            buf.writeInt(pkt.cooldownTicks);
        }

        public static SyncPacket decode(FriendlyByteBuf buf) {
            return new SyncPacket(buf.readUtf(), buf.readInt());
        }

        public static void handle(SyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.getPersistentData().putString("race_id", pkt.raceId);
                    mc.player.getPersistentData().putInt(DragonFlightServerEvents.COOLDOWN_KEY, pkt.cooldownTicks);
                }
            });
            ctx.setPacketHandled(true);
        }
    }
}