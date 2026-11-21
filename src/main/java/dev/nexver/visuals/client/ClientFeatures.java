package dev.nexver.visuals.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
            if (mc.player == null || mc.level == null) return;

            while (OPEN_MENU.consumeClick()) {
                mc.setScreen(new NexverMenu());
            }
        }

        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) return;

            GuiGraphics gui = event.getGuiGraphics();
            int x = 8;
            int y = 8;
            int line = 0;

            if (showFps) {
                drawHudLine(gui, x, y + line++ * 12, "FPS  " + mc.getFps());
            }
            if (showCoordinates) {
                var pos = mc.player.blockPosition();
                drawHudLine(gui, x, y + line++ * 12,
                        "XYZ  " + pos.getX() + " / " + pos.getY() + " / " + pos.getZ());
            }
            if (showPing && mc.getConnection() != null) {
                var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if (info != null) {
                    drawHudLine(gui, x, y + line * 12, "PING  " + info.getLatency() + " ms");
                }
            }
        }

        private static void drawHudLine(GuiGraphics gui, int x, int y, String value) {
            int width = Minecraft.getInstance().font.width(value);
            gui.fill(x - 4, y - 3, x + width + 5, y + 10, 0xB8101420);
            gui.fill(x - 4, y - 3, x - 2, y + 10, 0xFF62D6FF);
            gui.drawString(Minecraft.getInstance().font, value, x, y, 0xFFEAF6FF, false);
        }
    }

    private static final class NexverMenu extends Screen {
        private NexverMenu() {
            super(Component.literal("NEXVER  /  VISUALS"));
        }

        @Override
        public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(gui);
            int panelWidth = 230;
            int panelHeight = 178;
            int left = (this.width - panelWidth) / 2;
            int top = (this.height - panelHeight) / 2;

            gui.fill(left, top, left + panelWidth, top + panelHeight, 0xF0141B2A);
            gui.fill(left, top, left + panelWidth, top + 2, 0xFF62D6FF);
            gui.drawString(this.font, "NEXVER", left + 14, top + 13, 0xFF62D6FF, false);
            gui.drawString(this.font, "VISUAL CONTROL PANEL", left + 14, top + 27, 0xFF91A8BD, false);

            drawOption(gui, left, top + 52, "FPS COUNTER", showFps);
            drawOption(gui, left, top + 82, "COORDINATES", showCoordinates);
            drawOption(gui, left, top + 112, "PING DISPLAY", showPing);

            gui.drawString(this.font, "Click an option to toggle  •  Right Shift to close",
                    left + 14, top + 154, 0xFF91A8BD, false);
            super.render(gui, mouseX, mouseY, partialTick);
        }

        private void drawOption(GuiGraphics gui, int left, int y, String label, boolean enabled) {
            int right = left + 216;
            gui.fill(left + 12, y - 5, right, y + 17, 0xFF202B3D);
            gui.drawString(this.font, label, left + 20, y + 2, 0xFFEAF6FF, false);
            String state = enabled ? "ON" : "OFF";
            int color = enabled ? 0xFF62D6FF : 0xFF8390A0;
            gui.drawString(this.font, state, right - 28, y + 2, color, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                int panelWidth = 230;
                int left = (this.width - panelWidth) / 2;
                int top = (this.height - 178) / 2;
                if (mouseX >= left + 12 && mouseX <= left + 216) {
                    if (mouseY >= top + 47 && mouseY <= top + 74) showFps = !showFps;
                    if (mouseY >= top + 77 && mouseY <= top + 104) showCoordinates = !showCoordinates;
                    if (mouseY >= top + 107 && mouseY <= top + 134) showPing = !showPing;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

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
