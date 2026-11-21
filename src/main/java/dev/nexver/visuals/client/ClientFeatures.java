package dev.nexver.visuals.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Mod.EventBusSubscriber(modid = "nexver_visuals", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientFeatures {
    private static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.nexver_visuals.open_menu", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT, "key.categories.nexver_visuals");
    private static final KeyMapping TOGGLE_BRIGHTNESS = new KeyMapping(
            "key.nexver_visuals.brightness", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.nexver_visuals");

    private static final Path CONFIG = FMLPaths.CONFIGDIR.get().resolve("nexver-visuals.properties");
    private static boolean showFps = true;
    private static boolean showCoordinates = true;
    private static boolean showPing = true;
    private static boolean hudBackground = true;
    private static boolean showCrosshair = false;
    private static boolean showKeystrokes = true;
    private static boolean showPotions = true;
    private static boolean showItemOverlay = true;
    private static boolean fullBright = false;
    private static double previousGamma = 0.5D;
    private static int accent = 0xFF65D6FF;

    static { loadConfig(); }
    private ClientFeatures() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
        event.register(TOGGLE_BRIGHTNESS);
    }

    private static void loadConfig() {
        if (!Files.exists(CONFIG)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG)) {
            p.load(in);
            showFps = Boolean.parseBoolean(p.getProperty("hud.fps", "true"));
            showCoordinates = Boolean.parseBoolean(p.getProperty("hud.coordinates", "true"));
            showPing = Boolean.parseBoolean(p.getProperty("hud.ping", "true"));
            hudBackground = Boolean.parseBoolean(p.getProperty("hud.background", "true"));
            showCrosshair = Boolean.parseBoolean(p.getProperty("visual.crosshair", "false"));
            showKeystrokes = Boolean.parseBoolean(p.getProperty("hud.keystrokes", "true"));
            showPotions = Boolean.parseBoolean(p.getProperty("hud.potions", "true"));
            showItemOverlay = Boolean.parseBoolean(p.getProperty("hud.item", "true"));
            fullBright = Boolean.parseBoolean(p.getProperty("visual.fullbright", "false"));
            accent = Integer.parseUnsignedInt(p.getProperty("appearance.accent", "FF65D6FF"), 16);
        } catch (Exception ignored) {}
    }

    private static void saveConfig() {
        Properties p = new Properties();
        p.setProperty("hud.fps", Boolean.toString(showFps));
        p.setProperty("hud.coordinates", Boolean.toString(showCoordinates));
        p.setProperty("hud.ping", Boolean.toString(showPing));
        p.setProperty("hud.background", Boolean.toString(hudBackground));
        p.setProperty("visual.crosshair", Boolean.toString(showCrosshair));
        p.setProperty("hud.keystrokes", Boolean.toString(showKeystrokes));
        p.setProperty("hud.potions", Boolean.toString(showPotions));
        p.setProperty("hud.item", Boolean.toString(showItemOverlay));
        p.setProperty("visual.fullbright", Boolean.toString(fullBright));
        p.setProperty("appearance.accent", String.format("%08X", accent));
        try {
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG)) {
                p.store(out, "Nexver Visuals settings");
            }
        } catch (Exception ignored) {}
    }

    private static void setFullBright(Minecraft mc, boolean enabled) {
        if (enabled && !fullBright) previousGamma = mc.options.gamma().get();
        fullBright = enabled;
        mc.options.gamma().set(enabled ? 1.0D : previousGamma);
        saveConfig();
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
            while (TOGGLE_BRIGHTNESS.consumeClick()) setFullBright(mc, !fullBright);
        }

        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
            GuiGraphics gui = event.getGuiGraphics();
            int x = 10, y = 10, line = 0;

            if (showFps) drawHudLine(gui, x, y + line++ * 14, "ФПС", String.valueOf(mc.getFps()));
            if (showCoordinates) {
                var pos = mc.player.blockPosition();
                drawHudLine(gui, x, y + line++ * 14, "КООРД", pos.getX() + "  " + pos.getY() + "  " + pos.getZ());
            }
            if (showPing && mc.getConnection() != null) {
                var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if (info != null) drawHudLine(gui, x, y + line * 14, "ПИНГ", info.getLatency() + " мс");
            }

            if (showKeystrokes) drawKeystrokes(gui, mc);
            if (showPotions) drawPotions(gui, mc);
            if (showItemOverlay) drawItem(gui, mc);
            if (showCrosshair) drawCrosshair(gui, mc);
        }

        private static void drawHudLine(GuiGraphics gui, int x, int y, String label, String value) {
            Minecraft mc = Minecraft.getInstance();
            int labelWidth = mc.font.width(label + "  ");
            int width = labelWidth + mc.font.width(value);
            if (hudBackground) gui.fill(x - 6, y - 4, x + width + 3, y + 11, 0xB9101420);
            gui.fill(x - 6, y - 4, x - 3, y + 11, accent);
            gui.drawString(mc.font, label, x, y, accent, false);
            gui.drawString(mc.font, value, x + labelWidth, y, 0xFFF0F5FC, false);
        }

        private static void drawKeystrokes(GuiGraphics gui, Minecraft mc) {
            int x = 10, y = mc.getWindow().getGuiScaledHeight() - 72, s = 19, gap = 3;
            drawKey(gui, mc, x + s + gap, y, "W", mc.options.keyUp.isDown());
            drawKey(gui, mc, x, y + s + gap, "A", mc.options.keyLeft.isDown());
            drawKey(gui, mc, x + s + gap, y + s + gap, "S", mc.options.keyDown.isDown());
            drawKey(gui, mc, x + (s + gap) * 2, y + s + gap, "D", mc.options.keyRight.isDown());
            drawKey(gui, mc, x, y + (s + gap) * 2, "ПРЫЖОК", mc.options.keyJump.isDown(), s * 3 + gap * 2);
        }

        private static void drawKey(GuiGraphics gui, Minecraft mc, int x, int y, String label, boolean pressed) {
            drawKey(gui, mc, x, y, label, pressed, 19);
        }

        private static void drawKey(GuiGraphics gui, Minecraft mc, int x, int y, String label, boolean pressed, int width) {
            gui.fill(x, y, x + width, y + 19, pressed ? accent : 0xCC172231);
            gui.fill(x, y, x + width, y + 1, pressed ? 0xFFFFFFFF : 0xFF344255);
            int textWidth = mc.font.width(label);
            gui.drawString(mc.font, label, x + (width - textWidth) / 2, y + 6,
                    pressed ? 0xFF101722 : 0xFFEAF0F8, false);
        }

        private static void drawPotions(GuiGraphics gui, Minecraft mc) {
            int x = mc.getWindow().getGuiScaledWidth() - 112;
            int y = 10;
            for (MobEffectInstance effect : mc.player.getActiveEffects()) {
                String name = effect.getEffect().getDisplayName().getString();
                int ticks = effect.getDuration();
                String duration = ticks < 0 ? "∞" : String.format("%d:%02d", ticks / 1200, (ticks / 20) % 60);
                String text = name + " " + duration;
                gui.fill(x - 5, y - 3, x + 108, y + 12, 0xB9101420);
                gui.fill(x - 5, y - 3, x - 2, y + 12, accent);
                gui.drawString(mc.font, text, x, y, 0xFFF0F5FC, false);
                y += 17;
            }
        }

        private static void drawItem(GuiGraphics gui, Minecraft mc) {
            ItemStack stack = mc.player.getMainHandItem();
            if (stack.isEmpty()) return;
            String text = stack.getHoverName().getString() + " ×" + stack.getCount();
            int w = mc.font.width(text);
            int x = (mc.getWindow().getGuiScaledWidth() - w) / 2;
            int y = mc.getWindow().getGuiScaledHeight() - 30;
            if (hudBackground) gui.fill(x - 7, y - 4, x + w + 7, y + 12, 0xB9101420);
            gui.drawString(mc.font, text, x, y, 0xFFF0F5FC, false);
        }

        private static void drawCrosshair(GuiGraphics gui, Minecraft mc) {
            int cx = mc.getWindow().getGuiScaledWidth() / 2;
            int cy = mc.getWindow().getGuiScaledHeight() / 2;
            int color = accent;
            gui.fill(cx - 1, cy - 7, cx + 1, cy - 3, color);
            gui.fill(cx - 1, cy + 3, cx + 1, cy + 7, color);
            gui.fill(cx - 7, cy - 1, cx - 3, cy + 1, color);
            gui.fill(cx + 3, cy - 1, cx + 7, cy + 1, color);
            gui.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
        }
    }

    private static final class NexverMenu extends Screen {
        private static final int BG = 0xF20B0F18, PANEL = 0xFF111824, CARD = 0xFF182231;
        private static final int CARD_HOVER = 0xFF202E40, MUTED = 0xFF8291A5, WHITE = 0xFFF0F5FC;
        private int page = 1;
        private final long openedAt = Util.getMillis();

        private NexverMenu() { super(Component.literal("NEXVER VISUALS")); }

        private int panelWidth() { return Math.min(690, this.width - 24); }
        private int panelHeight() { return Math.min(410, this.height - 24); }
        private int left() { return (this.width - panelWidth()) / 2; }
        private int top() { return (this.height - panelHeight()) / 2; }
        private int contentLeft() { return left() + 20; }
        private int contentWidth() { return panelWidth() - 40; }

        @Override
        public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
            gui.fill(0, 0, width, height, 0xB9000000);
            int l = left(), t = top(), w = panelWidth(), h = panelHeight();
            gui.fill(l - 3, t - 3, l + w + 3, t + h + 3, 0x50000000);
            gui.fill(l, t, l + w, t + h, BG);
            gui.fill(l + 1, t + 1, l + w - 1, t + h - 1, PANEL);
            gui.fill(l, t, l + w, t + 2, accent);
            drawBrand(gui);
            drawTabs(gui, mouseX, mouseY);
            gui.fill(l + 18, t + 68, l + w - 18, t + 69, 0xFF293648);

            if (page == 0) drawVisualPage(gui, mouseX, mouseY);
            else if (page == 1) drawHudPage(gui, mouseX, mouseY);
            else drawUtilitiesPage(gui, mouseX, mouseY);

            float progress = Mth.clamp((Util.getMillis() - openedAt) / 170.0f, 0.0f, 1.0f);
            if (progress < 1.0f) gui.fill(l, t, l + w, t + h, ((int)(0x25 * (1.0f - progress))) << 24);
            super.render(gui, mouseX, mouseY, partialTick);
        }

        private void drawBrand(GuiGraphics gui) {
            int x = left() + 20, y = top() + 17;
            gui.drawString(font, "NEXVER", x, y, WHITE, false);
            gui.drawString(font, "VISUALS", x + font.width("NEXVER") + 7, y, accent, false);
            gui.drawString(font, "CLIENT  /  0.3.1", x, y + 14, MUTED, false);
            int sx = left() + panelWidth() - 94;
            gui.fill(sx, y + 2, sx + 7, y + 9, 0xFF65D68A);
            gui.drawString(font, "АКТИВЕН", sx + 13, y + 1, 0xFF9CB4A7, false);
        }

        private void drawTabs(GuiGraphics gui, int mx, int my) {
            String[] labels = {"ВИЗУАЛЫ", "HUD", "УТИЛИТЫ"};
            int x = left() + 20, y = top() + 43;
            for (int i = 0; i < labels.length; i++) {
                int tx = x + i * 112;
                boolean selected = page == i;
                boolean hover = inside(mx, my, tx - 5, y - 5, 100, 25);
                if (selected || hover) gui.fill(tx - 5, y - 5, tx + 95, y + 20,
                        selected ? 0xFF24364A : 0xFF1B2939);
                gui.drawString(font, labels[i], tx + 8, y + 3, selected ? accent : 0xFFB4C0CE, false);
                if (selected) gui.fill(tx - 5, y + 18, tx + 95, y + 20, accent);
            }
        }

        private void drawPageHeading(GuiGraphics gui, String title, String subtitle) {
            int x = contentLeft(), y = top() + 82;
            gui.drawString(font, title, x, y, WHITE, false);
            gui.drawString(font, subtitle, x, y + 14, MUTED, false);
        }

        private void drawHudPage(GuiGraphics gui, int mx, int my) {
            drawPageHeading(gui, "ЭЛЕМЕНТЫ HUD", "Информация, которую можно вывести поверх игры");
            int x = contentLeft(), y = top() + 119, gap = 10;
            int cardW = (contentWidth() - gap) / 2;
            drawCard(gui,mx,my,x,y,cardW,"СЧЁТЧИК FPS","Частота кадров",showFps,0);
            drawCard(gui,mx,my,x+cardW+gap,y,cardW,"КООРДИНАТЫ","Позиция в мире",showCoordinates,1);
            drawCard(gui,mx,my,x,y+48,cardW,"СЕТЕВОЙ ПИНГ","Задержка соединения",showPing,2);
            drawCard(gui,mx,my,x+cardW+gap,y+48,cardW,"КЛАВИШИ","WASD и прыжок",showKeystrokes,3);
            drawCard(gui,mx,my,x,y+96,cardW,"ЭФФЕКТЫ ЗЕЛИЙ","Название и длительность",showPotions,4);
            drawCard(gui,mx,my,x+cardW+gap,y+96,cardW,"ПРЕДМЕТ В РУКЕ","Название и количество",showItemOverlay,5);
            drawFooter(gui, "Изменения сохраняются автоматически");
        }

        private void drawVisualPage(GuiGraphics gui, int mx, int my) {
            drawPageHeading(gui, "ВИЗУАЛЬНЫЕ ЭФФЕКТЫ", "Настройки внешнего вида игрового интерфейса");
            int x = contentLeft(), y = top() + 119, gap = 10;
            int cardW = (contentWidth() - gap) / 2;
            drawCard(gui,mx,my,x,y,cardW,"СВОЙ ПРИЦЕЛ","Минималистичный цветной прицел",showCrosshair,6);
            gui.fill(x + cardW + gap, y, x + 2 * cardW + gap, y + 42, CARD);
            gui.drawString(font, "ЛОКАЛЬНЫЕ ЭФФЕКТЫ", x + cardW + gap + 10, y + 8, WHITE, false);
            gui.drawString(font, "Новые визуалы — позже", x + cardW + gap + 10, y + 23, MUTED, false);
            drawFooter(gui, "Только визуальные настройки клиента");
        }

        private void drawUtilitiesPage(GuiGraphics gui, int mx, int my) {
            drawPageHeading(gui, "УТИЛИТЫ", "Быстрые переключатели и оформление");
            int x = contentLeft(), y = top() + 119, gap = 10;
            int cardW = (contentWidth() - gap) / 2;
            drawCard(gui,mx,my,x,y,cardW,"ПОЛНАЯ ЯРКОСТЬ","Горячая клавиша K",fullBright,7);
            drawCard(gui,mx,my,x+cardW+gap,y,cardW,"ФОН HUD","Подложка под текст",hudBackground,8);

            gui.drawString(font, "ЦВЕТ АКЦЕНТА", x, y + 62, WHITE, false);
            gui.drawString(font, "Выбери цвет переключателей и подсветки", x, y + 77, MUTED, false);
            int[] colors={0xFF65D6FF,0xFF9B83FF,0xFFFF78B7,0xFF75D6A0,0xFFFFB86B};
            String[] names={"ЛЁД","ФИОЛЕТОВЫЙ","РОЗОВЫЙ","МЯТНЫЙ","ЯНТАРНЫЙ"};
            int swatchX = x, swatchY = y + 103;
            for(int i=0;i<colors.length;i++){
                int cx = swatchX + i * 91;
                boolean selected = accent == colors[i];
                gui.fill(cx, swatchY, cx + 82, swatchY + 34, selected ? 0xFF243448 : CARD);
                gui.fill(cx + 8, swatchY + 9, cx + 22, swatchY + 23, colors[i]);
                gui.drawString(font, names[i], cx + 28, swatchY + 13, selected ? WHITE : MUTED, false);
            }
            drawFooter(gui, "Меню: RIGHT SHIFT     •     Яркость: K");
        }

        private void drawFooter(GuiGraphics gui, String text) {
            gui.drawString(font, text, contentLeft(), top() + panelHeight() - 22, MUTED, false);
        }

        private void drawCard(GuiGraphics gui,int mx,int my,int x,int y,int w,String title,String desc,boolean enabled,int id) {
            boolean hover = inside(mx,my,x,y,w,42);
            gui.fill(x,y,x+w,y+42,hover?CARD_HOVER:CARD);
            gui.fill(x,y,x+2,y+42,enabled?accent:0xFF344255);
            gui.drawString(font,title,x+10,y+7,WHITE,false);
            gui.drawString(font,desc,x+10,y+22,MUTED,false);
            int sx = x + w - 38;
            gui.fill(sx,y+12,sx+28,y+28,enabled?accent:0xFF3A485B);
            gui.fill(enabled?sx+15:sx+2,y+14,enabled?sx+26:sx+13,y+26,0xFFF4F7FB);
        }

        private boolean inside(double mx,double my,int x,int y,int w,int h) {
            return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;
        }

        @Override
        public boolean mouseClicked(double mx,double my,int button) {
            if(button!=0) return super.mouseClicked(mx,my,button);

            int tabX = left() + 15, tabY = top() + 38;
            if (inside(mx,my,tabX,tabY,105,30)) { page=0; return true; }
            if (inside(mx,my,tabX+112,tabY,105,30)) { page=1; return true; }
            if (inside(mx,my,tabX+224,tabY,105,30)) { page=2; return true; }

            int x=contentLeft(), y=top()+119, gap=10, cardW=(contentWidth()-gap)/2;
            if(page==1){
                int id=-1;
                if(inside(mx,my,x,y,cardW,42)) id=0;
                else if(inside(mx,my,x+cardW+gap,y,cardW,42)) id=1;
                else if(inside(mx,my,x,y+48,cardW,42)) id=2;
                else if(inside(mx,my,x+cardW+gap,y+48,cardW,42)) id=3;
                else if(inside(mx,my,x,y+96,cardW,42)) id=4;
                else if(inside(mx,my,x+cardW+gap,y+96,cardW,42)) id=5;
                switch(id){
                    case 0: showFps=!showFps; break;
                    case 1: showCoordinates=!showCoordinates; break;
                    case 2: showPing=!showPing; break;
                    case 3: showKeystrokes=!showKeystrokes; break;
                    case 4: showPotions=!showPotions; break;
                    case 5: showItemOverlay=!showItemOverlay; break;
                    default: break;
                }
                if(id>=0) saveConfig();
            } else if(page==0){
                if(inside(mx,my,x,y,cardW,42)){showCrosshair=!showCrosshair;saveConfig();}
            } else {
                if(inside(mx,my,x,y,cardW,42)){setFullBright(Minecraft.getInstance(),!fullBright);}
                else if(inside(mx,my,x+cardW+gap,y,cardW,42)){hudBackground=!hudBackground;saveConfig();}
                else {
                    int[] colors={0xFF65D6FF,0xFF9B83FF,0xFFFF78B7,0xFF75D6A0,0xFFFFB86B};
                    int swatchY=y+103;
                    for(int i=0;i<colors.length;i++){
                        int cx=x+i*91;
                        if(inside(mx,my,cx,swatchY,82,34)){accent=colors[i];saveConfig();break;}
                    }
                }
            }
            return true;
        }

        @Override public boolean isPauseScreen(){return false;}
        @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers){
            if(keyCode==GLFW.GLFW_KEY_RIGHT_SHIFT||keyCode==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}
            return super.keyPressed(keyCode,scanCode,modifiers);
        }
    }
}
}