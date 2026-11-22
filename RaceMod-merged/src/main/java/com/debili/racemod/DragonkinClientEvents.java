package com.debili.racemod;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RaceMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DragonkinClientEvents {

    // --- Настройка замедления взмахов ---
    // 385 мс ≈ 2.6 удара в секунду. Если хочешь медленнее, увеличь число (например, 500).
    private static final long ATTACK_COOLDOWN_MS = 385;
    private static long lastSwingTime = 0;

    // ==========================================
    // 1. DRAGONKINSLOWNESS: ЗАМЕДЛЕНИЕ ВЗМАХОВ РУКОЙ
    // ==========================================
    @SubscribeEvent
    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();

        // Проверяем: ЛКМ, это именно клик (не отпускание), и игрок существует
        if (event.getButton() == 0 && event.getAction() != 0 && mc.player != null) {

            // Проверяем, что игрок — дракон
            String raceId = mc.player.getPersistentData().getString("race_id");
            if (!"dragonkin".equals(raceId)) return;

            // Если смотрим на блок — не мешаем копать (иначе сломается ванильное зажатие ЛКМ)
            if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
                return;
            }

            long currentTime = System.currentTimeMillis();

            // Если кликаешь слишком быстро — отменяем взмах
            if (currentTime - lastSwingTime < ATTACK_COOLDOWN_MS) {
                event.setCanceled(true); // Рука не дёрнется, клик заблокирован
            } else {
                lastSwingTime = currentTime; // Разрешаем удар и запоминаем время
            }
        }
    }

    // ==========================================
    // 2. FIREIMMUNITY: УБИРАЕМ ОГОНЬ С ЭКРАНА
    // ==========================================
    @SubscribeEvent
    public static void onRenderOverlay(RenderBlockScreenEffectEvent event) {
        // Нас интересует только огонь
        if (event.getOverlayType() != RenderBlockScreenEffectEvent.OverlayType.FIRE) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Проверяем, что игрок — дракон
        String raceId = mc.player.getPersistentData().getString("race_id");
        if ("dragonkin".equals(raceId)) {
            event.setCanceled(true); // Полностью убираем огонь с экрана
        }
    }
}