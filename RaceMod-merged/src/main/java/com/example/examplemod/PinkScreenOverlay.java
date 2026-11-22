package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = ExampleMod.MODID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public class PinkScreenOverlay {

    // Цвет 0x70FF69B4: первые две цифры (70) — прозрачность, FF69B4 — розовый (HotPink)
    private static final int PINK_OVERLAY_COLOR = 0x70FF69B4;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;

        // Показываем розовый экран только когда активен класс catboy
        String raceId = minecraft.player.getPersistentData().getString("race_id");
        if (!"catboy".equals(raceId)) return;

        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();

        event.getGuiGraphics().fill(0, 0, width, height, PINK_OVERLAY_COLOR);
    }
}