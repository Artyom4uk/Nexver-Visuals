package dev.nexver.visuals.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "nexver_visuals", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientFeatures {
    private static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.nexver_visuals.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            "key.categories.nexver_visuals"
    );

    private static boolean showFps = true;
    private static boolean showCoordinates = true;
    private static boolean showPing = true;
    private static boolean hudBackground = true;
    private static int accent = 0xFF65D6FF;

    private ClientFeatures() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
    }

    @Mod.EventBusSubscriber(modid = "nexver_visuals", value = Dist.CLIENT)
    public static final class ForgeEvents {
        private ForgeEvents() {}

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (OPEN_MENU.consumeClick()) {
                if (mc.screen instanceof NexverMenu) mc.setScreen(null);
                else mc.setScreen(new NexverMenu());
            }
        }

        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) return;

            GuiGraphics gui = event.getGuiGraphics();
            int x = 10;
            int y = 10;
            int line = 0;

            if (showFps) drawHudLine(gui, x, y + line++ * 14, "FPS", String.valueOf(mc.getFps()));
            if (showCoordinates) {
                var pos = mc.player.blockPosition();
                drawHudLine(gui, x, y + line++ * 14, "XYZ",
                        pos.getX() + "  " + pos.getY() + "  " + pos.getZ());
            }
            if (showPing && mc.getConnection() != null) {
                var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if (info != null) drawHudLine(gui, x, y + line * 14, "PING", info.getLatency() + " ms");
            }
        }

        private static void drawHudLine(GuiGraphics gui, int x, int y, String label, String value) {
            Minecraft mc = Minecraft.getInstance();
            String text = label + "  " + value;
            int width = mc.font.width(text);
            if (hudBackground) gui.fill(x - 6, y - 4, x + width + 8, y + 11, 0xB9101420);
            gui.fill(x - 6, y - 4, x - 3, y + 11, accent);
            gui.drawString(mc.font, label, x, y, accent, false);
            gui.drawString(mc.font, value, x + mc.font.width(label + "  "), y, 0xFFF0F5FC, false);
        }
    }

    private static final class NexverMenu extends Screen {
        private static final int BG = 0xF20B0F18;
        private static final int PANEL = 0xFF111824;
        private static final int CARD = 0xFF182231;
        private static final int CARD_HOVER = 0xFF202E40;
        private static final int MUTED = 0xFF8291A5;
        private static final int WHITE = 0xFFF0F5FC;
        private int page = 0;
        private long openedAt;

        private NexverMenu() {
            super(Component.literal("NEXVER VISUALS"));
            this.openedAt = Util.getMillis();
        }

        private int panelWidth() { return Math.min(570, this.width - 24); }
        private int panelHeight() { return Math.min(360, this.height - 24); }
        private int left() { return (this.width - panelWidth()) / 2; }
        private int top() { return (this.height - panelHeight()) / 2; }
        private int sideWidth() { return 132; }
        private int contentLeft() { return left() + sideWidth() + 20; }
        private int contentWidth() { return panelWidth() - sideWidth() - 40; }

        @Override
        public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
            gui.fill(0, 0, this.width, this.height, 0xB9000000);

            int l = left();
            int t = top();
            int w = panelWidth();
            int h = panelHeight();

            // Soft shadow and layered window surface
            gui.fill(l - 3, t - 3, l + w + 3, t + h + 3, 0x50000000);
            gui.fill(l, t, l + w, t + h, BG);
            gui.fill(l + 1, t + 1, l + w - 1, t + h - 1, PANEL);
            gui.fill(l, t, l + w, t + 2, accent);
            gui.fill(l + sideWidth(), t + 2, l + sideWidth() + 1, t + h, 0xFF253244);

            drawSidebar(gui, mouseX, mouseY);
            drawHeader(gui);
            if (page == 0) drawHudPage(gui, mouseX, mouseY);
            else drawVisualPage(gui, mouseX, mouseY);

            // Small opening fade/slide, without blocking input
            float progress = Mth.clamp((Util.getMillis() - openedAt) / 170.0f, 0.0f, 1.0f);
            if (progress < 1.0f) {
                int cover = (int) (0x35 * (1.0f - progress));
                gui.fill(l, t, l + w, t + h, (cover << 24));
            }
            super.render(gui, mouseX, mouseY, partialTick);
        }

        private void drawSidebar(GuiGraphics gui, int mx, int my) {
            int l = left();
            int t = top();
            gui.drawString(font, "NEXVER", l + 17, t + 19, WHITE, false);
            gui.drawString(font, "VISUALS CLIENT", l + 17, t + 33, MUTED, false);

            drawNav(gui, mx, my, 0, t + 68, "HUD", "01");
            drawNav(gui, mx, my, 1, t + 105, "APPEARANCE", "02");

            gui.fill(l + 15, t + panelHeight() - 49, l + sideWidth() - 15, t + panelHeight() - 48, 0xFF293648);
            gui.drawString(font, "BUILD  0.1.0", l + 17, t + panelHeight() - 35, MUTED, false);
        }

        private void drawNav(GuiGraphics gui, int mx, int my, int id, int y, String label, String number) {
            int x = left() + 10;
            boolean selected = page == id;
            boolean hover = mx >= x && mx <= left() + sideWidth() - 10 && my >= y - 8 && my <= y + 17;
            if (selected || hover) {
                gui.fill(x, y - 8, left() + sideWidth() - 10, y + 17, selected ? 0xFF24364A : 0xFF1B2939);
                if (selected) gui.fill(x, y - 8, x + 2, y + 17, accent);
            }
            gui.drawString(font, number, x + 9, y, selected ? accent : MUTED, false);
            gui.drawString(font, label, x + 34, y, selected ? WHITE : 0xFFB4C0CE, false);
        }

        private void drawHeader(GuiGraphics gui) {
            int x = contentLeft();
            int y = top() + 18;
            gui.drawString(font, page == 0 ? "HUD MODULES" : "APPEARANCE", x, y, WHITE, false);
            gui.drawString(font, page == 0 ? "Manage your on-screen information" : "Make the interface yours",
                    x, y + 15, MUTED, false);
            int statusX = left() + panelWidth() - 88;
            gui.fill(statusX, y + 1, statusX + 7, y + 8, 0xFF65D68A);
            gui.drawString(font, "CLIENT ON", statusX + 12, y, 0xFF9CB4A7, false);
            gui.fill(contentLeft(), top() + 48, left() + panelWidth() - 18, top() + 49, 0xFF293648);
        }

        private void drawHudPage(GuiGraphics gui, int mx, int my) {
            int x = contentLeft();
            int y = top() + 65;
            int gap = 10;
            int cardW = (contentWidth() - gap) / 2;
            drawModuleCard(gui, mx, my, x, y, cardW, "FPS COUNTER", "Live frame rate", "PERFORMANCE", showFps, 0);
            drawModuleCard(gui, mx, my, x + cardW + gap, y, cardW, "COORDINATES", "Your current position", "WORLD INFO", showCoordinates, 1);
            drawModuleCard(gui, mx, my, x, y + 91, cardW, "PING DISPLAY", "Network response time", "NETWORK", showPing, 2);
            drawModuleCard(gui, mx, my, x + cardW + gap, y + 91, cardW, "HUD BACKGROUND", "Dark widget backing", "STYLE", hudBackground, 3);

            gui.drawString(font, "Click a card to enable or disable it", x, top() + panelHeight() - 24, MUTED, false);
        }

        private void drawModuleCard(GuiGraphics gui, int mx, int my, int x, int y, int w,
                                    String title, String desc, String tag, boolean enabled, int id) {
            boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + 78;
            gui.fill(x, y, x + w, y + 78, hover ? CARD_HOVER : CARD);
            gui.fill(x, y, x + 2, y + 78, enabled ? accent : 0xFF344255);
            gui.drawString(font, tag, x + 12, y + 10, MUTED, false);
            gui.drawString(font, title, x + 12, y + 27, WHITE, false);
            gui.drawString(font, desc, x + 12, y + 43, MUTED, false);
            drawToggle(gui, x + w - 39, y + 12, enabled);
            gui.drawString(font, enabled ? "ENABLED" : "DISABLED",
                    x + 12, y + 62, enabled ? accent : MUTED, false);
        }

        private void drawToggle(GuiGraphics gui, int x, int y, boolean enabled) {
            int color = enabled ? accent : 0xFF3A485B;
            gui.fill(x, y, x + 27, y + 14, color);
            gui.fill(enabled ? x + 15 : x + 2, y + 2, enabled ? x + 25 : x + 12, y + 12, 0xFFF4F7FB);
        }

        private void drawVisualPage(GuiGraphics gui, int mx, int my) {
            int x = contentLeft();
            int y = top() + 67;
            gui.drawString(font, "ACCENT COLOR", x, y, WHITE, false);
            gui.drawString(font, "Choose the highlight used across Nexver", x, y + 15, MUTED, false);

            int[] colors = {0xFF65D6FF, 0xFF9B83FF, 0xFFFF78B7, 0xFF75D6A0, 0xFFFFB86B};
            String[] names = {"ICE", "VIOLET", "PINK", "MINT", "AMBER"};
            for (int i = 0; i < colors.length; i++) {
                int cy = y + 47 + i * 35;
                boolean selected = accent == colors[i];
                boolean hover = mx >= x && mx <= x + contentWidth() - 8 && my >= cy - 5 && my <= cy + 23;
                gui.fill(x, cy - 5, x + contentWidth() - 8, cy + 23,
                        selected ? 0xFF243448 : (hover ? CARD_HOVER : CARD));
                gui.fill(x + 10, cy + 2, x + 24, cy + 16, colors[i]);
                gui.drawString(font, names[i], x + 34, cy + 5, WHITE, false);
                if (selected) gui.drawString(font, "SELECTED", x + contentWidth() - 73, cy + 5, colors[i], false);
            }
            gui.drawString(font, "Changes apply to the menu and HUD", x, top() + panelHeight() - 24, MUTED, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

            int navX = left() + 10;
            if (mouseX >= navX && mouseX <= left() + sideWidth() - 10) {
                if (mouseY >= top() + 60 && mouseY <= top() + 94) { page = 0; return true; }
                if (mouseY >= top() + 98 && mouseY <= top() + 132) { page = 1; return true; }
            }

            if (page == 0) {
                int x = contentLeft();
                int y = top() + 65;
                int gap = 10;
                int cardW = (contentWidth() - gap) / 2;
                if (inside(mouseX, mouseY, x, y, cardW, 78)) showFps = !showFps;
                else if (inside(mouseX, mouseY, x + cardW + gap, y, cardW, 78)) showCoordinates = !showCoordinates;
                else if (inside(mouseX, mouseY, x, y + 91, cardW, 78)) showPing = !showPing;
                else if (inside(mouseX, mouseY, x + cardW + gap, y + 91, cardW, 78)) hudBackground = !hudBackground;
            } else {
                int x = contentLeft();
                int y = top() + 67 + 42;
                int[] colors = {0xFF65D6FF, 0xFF9B83FF, 0xFFFF78B7, 0xFF75D6A0, 0xFFFFB86B};
                for (int i = 0; i < colors.length; i++) {
                    int cy = y + i * 35;
                    if (inside(mouseX, mouseY, x, cy - 5, contentWidth() - 8, 28)) {
                        accent = colors[i];
                        break;
                    }
                }
            }
            return true;
        }

        private boolean inside(double mx, double my, int x, int y, int w, int h) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }

        @Override
        public boolean isPauseScreen() { return false; }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.onClose();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}
