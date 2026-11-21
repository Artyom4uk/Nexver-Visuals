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
        private static final int BG = 0xEE0A0E16, PANEL = 0xFF101722, CARD = 0xFF171F2B;
        private static final int CARD_HOVER = 0xFF202B3A, MUTED = 0xFF7F8DA1, WHITE = 0xFFF0F4FA;
        private int page = 1;
        private final long openedAt = Util.getMillis();

        private NexverMenu() { super(Component.literal("NEXVER VISUALS")); }

        private int panelWidth() { return Math.min(620, this.width - 28); }
        private int panelHeight() { return Math.min(360, this.height - 24); }
        private int left() { return (this.width - panelWidth()) / 2; }
        private int top() { return (this.height - panelHeight()) / 2; }
        private int contentLeft() { return left() + 20; }
        private int contentWidth() { return panelWidth() - 40; }

        @Override
        public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
            gui.fill(0, 0, width, height, 0x99030810);
            int l = left(), t = top(), w = panelWidth(), h = panelHeight();
            gui.fill(l - 2, t - 2, l + w + 2, t + h + 2, 0x50000000);
            gui.fill(l, t, l + w, t + h, BG);
            gui.fill(l + 1, t + 1, l + w - 1, t + h - 1, PANEL);
            gui.fill(l, t, l + w, t + 2, accent);

            drawBrand(gui);
            drawTabs(gui, mouseX, mouseY);
            gui.fill(l + 18, t + 70, l + w - 18, t + 71, 0xFF293444);

            if (page == 0) drawVisualPage(gui, mouseX, mouseY);
            else if (page == 1) drawHudPage(gui, mouseX, mouseY);
            else drawUtilitiesPage(gui, mouseX, mouseY);

            float progress = Mth.clamp((Util.getMillis() - openedAt) / 150.0f, 0.0f, 1.0f);
            if (progress < 1.0f) gui.fill(l, t, l + w, t + h,
                    ((int)(0x20 * (1.0f - progress))) << 24);
            super.render(gui, mouseX, mouseY, partialTick);
        }

        private void drawBrand(GuiGraphics gui) {
            int x = left() + 20, y = top() + 15;
            gui.drawString(font, "NEXVER", x, y, WHITE, false);
            gui.drawString(font, "VISUALS", x + font.width("NEXVER") + 6, y, accent, false);
            gui.drawString(font, "CLIENT  /  0.3.2", x, y + 13, MUTED, false);
            int sx = left() + panelWidth() - 83;
            gui.fill(sx, y + 2, sx + 6, y + 8, 0xFF65D68A);
            gui.drawString(font, "АКТИВЕН", sx + 11, y, 0xFF9CB4A7, false);
        }

        private void drawTabs(GuiGraphics gui, int mx, int my) {
            String[] labels = {"ВИЗУАЛЫ", "HUD", "УТИЛИТЫ"};
            int x = left() + 17, y = top() + 42;
            for (int i = 0; i < labels.length; i++) {
                int tx = x + i * 101;
                boolean selected = page == i;
                boolean hover = inside(mx, my, tx - 4, y - 4, 91, 24);
                if (selected || hover) gui.fill(tx - 4, y - 4, tx + 87, y + 20,
                        selected ? 0xFF202D3D : 0xFF182331);
                gui.drawString(font, labels[i], tx + 7, y + 3,
                        selected ? accent : 0xFFB4C0CE, false);
                if (selected) gui.fill(tx - 4, y + 18, tx + 87, y + 20, accent);
            }
        }

        private void drawPageHeading(GuiGraphics gui, String title, String subtitle) {
            int x = contentLeft(), y = top() + 82;
            gui.drawString(font, title, x, y, WHITE, false);
            gui.drawString(font, subtitle, x, y + 13, MUTED, false);
        }

        private void drawHudPage(GuiGraphics gui, int mx, int my) {
            drawPageHeading(gui, "ЭЛЕМЕНТЫ HUD", "Информация поверх игрового мира");
            int x = contentLeft(), y = top() + 112, gap = 8;
            int cardW = (contentWidth() - gap) / 2;
            drawCard(gui,mx,my,x,y,cardW,"СЧЁТЧИК FPS","Частота кадров",showFps,0);
            drawCard(gui,mx,my,x+cardW+gap,y,cardW,"КООРДИНАТЫ","Позиция в мире",showCoordinates,1);
            drawCard(gui,mx,my,x,y+42,cardW,"СЕТЕВОЙ ПИНГ","Задержка соединения",showPing,2);
            drawCard(gui,mx,my,x+cardW+gap,y+42,cardW,"КЛАВИШИ","WASD и прыжок",showKeystrokes,3);
            drawCard(gui,mx,my,x,y+84,cardW,"ЭФФЕКТЫ ЗЕЛИЙ","Название и длительность",showPotions,4);
            drawCard(gui,mx,my,x+cardW+gap,y+84,cardW,"ПРЕДМЕТ В РУКЕ","Название и количество",showItemOverlay,5);
            drawFooter(gui, "Настройки сохраняются автоматически");
        }

        private void drawVisualPage(GuiGraphics gui, int mx, int my) {
            drawPageHeading(gui, "ВИЗУАЛЬНЫЕ ЭФФЕКТЫ", "Внешний вид игрового интерфейса");
            int x = contentLeft(), y = top() + 112, gap = 8;
            int cardW = (contentWidth() - gap) / 2;
            drawCard(gui,mx,my,x,y,cardW,"СВОЙ ПРИЦЕЛ","Минималистичный цветной прицел",showCrosshair,6);
            gui.fill(x + cardW + gap, y, x + 2 * cardW + gap, y + 36, CARD);
            gui.drawString(font, "ЛОКАЛЬНЫЕ ЭФФЕКТЫ", x + cardW + gap + 10, y + 6, WHITE, false);
            gui.drawString(font, "Новые визуалы добавим", x + cardW + gap + 10, y + 20, MUTED, false);
            drawFooter(gui, "Только локальные визуальные настройки");
        }

        private void drawUtilitiesPage(GuiGraphics gui, int mx, int my) {
            drawPageHeading(gui, "УТИЛИТЫ", "Быстрые переключатели и оформление");
            int x = contentLeft(), y = top() + 112, gap = 8;
            int cardW = (contentWidth() - gap) / 2;
            drawCard(gui,mx,my,x,y,cardW,"ПОЛНАЯ ЯРКОСТЬ","Горячая клавиша K",fullBright,7);
            drawCard(gui,mx,my,x+cardW+gap,y,cardW,"ФОН HUD","Подложка под текст",hudBackground,8);

            gui.drawString(font, "ЦВЕТ АКЦЕНТА", x, y + 53, WHITE, false);
            gui.drawString(font, "Цвет переключателей и подсветки", x, y + 66, MUTED, false);
            int[] colors={0xFF65D6FF,0xFF9B83FF,0xFFFF78B7,0xFF75D6A0,0xFFFFB86B};
            String[] names={"ЛЁД","ФИОЛЕТ","РОЗОВЫЙ","МЯТНЫЙ","ЯНТАРЬ"};
            int swatchY = y + 88;
            for(int i=0;i<colors.length;i++){
                int cx = x + i * 86;
                boolean selected = accent == colors[i];
                gui.fill(cx, swatchY, cx + 78, swatchY + 27, selected ? 0xFF243448 : CARD);
                gui.fill(cx + 7, swatchY + 7, cx + 19, swatchY + 19, colors[i]);
                gui.drawString(font, names[i], cx + 24, swatchY + 10, selected ? WHITE : MUTED, false);
            }
            drawFooter(gui, "МЕНЮ: RIGHT SHIFT     •     ЯРКОСТЬ: K");
        }

        private void drawFooter(GuiGraphics gui, String text) {
            gui.fill(left() + 18, top() + panelHeight() - 31,
                    left() + panelWidth() - 18, top() + panelHeight() - 30, 0xFF293444);
            gui.drawString(font, text, contentLeft(), top() + panelHeight() - 20, MUTED, false);
        }

        private void drawCard(GuiGraphics gui,int mx,int my,int x,int y,int w,String title,String desc,boolean enabled,int id) {
            boolean hover = inside(mx,my,x,y,w,36);
            gui.fill(x,y,x+w,y+36,hover?CARD_HOVER:CARD);
            gui.fill(x,y,x+2,y+36,enabled?accent:0xFF344255);
            gui.drawString(font,title,x+9,y+5,WHITE,false);
            gui.drawString(font,desc,x+9,y+19,MUTED,false);
            int sx = x + w - 34;
            gui.fill(sx,y+10,sx+25,y+25,enabled?accent:0xFF354153);
            gui.fill(enabled?sx+13:sx+2,y+12,enabled?sx+23:sx+12,y+23,0xFFF4F7FB);
        }

        private boolean inside(double mx,double my,int x,int y,int w,int h) {
            return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;
        }

        @Override
        public boolean mouseClicked(double mx,double my,int button) {
            if(button!=0) return super.mouseClicked(mx,my,button);

            int tabX = left() + 13, tabY = top() + 37;
            if (inside(mx,my,tabX,tabY,96,29)) { page=0; return true; }
            if (inside(mx,my,tabX+101,tabY,96,29)) { page=1; return true; }
            if (inside(mx,my,tabX+202,tabY,96,29)) { page=2; return true; }

            int x=contentLeft(), y=top()+112, gap=8, cardW=(contentWidth()-gap)/2;
            if(page==1){
                int id=-1;
                if(inside(mx,my,x,y,cardW,36)) id=0;
                else if(inside(mx,my,x+cardW+gap,y,cardW,36)) id=1;
                else if(inside(mx,my,x,y+42,cardW,36)) id=2;
                else if(inside(mx,my,x+cardW+gap,y+42,cardW,36)) id=3;
                else if(inside(mx,my,x,y+84,cardW,36)) id=4;
                else if(inside(mx,my,x+cardW+gap,y+84,cardW,36)) id=5;
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
                if(inside(mx,my,x,y,cardW,36)){showCrosshair=!showCrosshair;saveConfig();}
            } else {
                if(inside(mx,my,x,y,cardW,36)){setFullBright(Minecraft.getInstance(),!fullBright);}
                else if(inside(mx,my,x+cardW+gap,y,cardW,36)){hudBackground=!hudBackground;saveConfig();}
                else {
                    int[] colors={0xFF65D6FF,0xFF9B83FF,0xFFFF78B7,0xFF75D6A0,0xFFFFB86B};
                    int swatchY=y+88;
                    for(int i=0;i<colors.length;i++){
                        int cx=x+i*86;
                        if(inside(mx,my,cx,swatchY,78,27)){accent=colors[i];saveConfig();break;}
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
