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
                String duration = MobEffectInstance.FORMAT_DURATION.format(effect, 1.0F);
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
        private int page = 0;
        private final long openedAt = Util.getMillis();

        private NexverMenu() { super(Component.literal("NEXVER VISUALS")); }
        private int panelWidth() { return Math.min(610, this.width - 24); }
        private int panelHeight() { return Math.min(390, this.height - 24); }
        private int left() { return (this.width - panelWidth()) / 2; }
        private int top() { return (this.height - panelHeight()) / 2; }
        private int sideWidth() { return 142; }
        private int contentLeft() { return left() + sideWidth() + 20; }
        private int contentWidth() { return panelWidth() - sideWidth() - 40; }

        @Override
        public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
            gui.fill(0, 0, width, height, 0xB9000000);
            int l = left(), t = top(), w = panelWidth(), h = panelHeight();
            gui.fill(l - 3, t - 3, l + w + 3, t + h + 3, 0x50000000);
            gui.fill(l, t, l + w, t + h, BG);
            gui.fill(l + 1, t + 1, l + w - 1, t + h - 1, PANEL);
            gui.fill(l, t, l + w, t + 2, accent);
            gui.fill(l + sideWidth(), t + 2, l + sideWidth() + 1, t + h, 0xFF253244);
            drawSidebar(gui, mouseX, mouseY);
            drawHeader(gui);
            if (page == 0) drawHudPage(gui, mouseX, mouseY);
            else if (page == 1) drawVisualPage(gui, mouseX, mouseY);
            else drawSettingsPage(gui, mouseX, mouseY);
            float progress = Mth.clamp((Util.getMillis() - openedAt) / 170.0f, 0.0f, 1.0f);
            if (progress < 1.0f) gui.fill(l, t, l + w, t + h, ((int)(0x25 * (1.0f - progress))) << 24);
            super.render(gui, mouseX, mouseY, partialTick);
        }

        private void drawSidebar(GuiGraphics gui, int mx, int my) {
            int l = left(), t = top();
            gui.drawString(font, "NEXVER", l + 17, t + 19, WHITE, false);
            gui.drawString(font, "VISUALS CLIENT", l + 17, t + 33, MUTED, false);
            drawNav(gui, mx, my, 0, t + 68, "ИНТЕРФЕЙС", "01");
            drawNav(gui, mx, my, 1, t + 105, "ВИЗУАЛЫ", "02");
            drawNav(gui, mx, my, 2, t + 142, "НАСТРОЙКИ", "03");
            gui.fill(l + 15, t + panelHeight() - 49, l + sideWidth() - 15, t + panelHeight() - 48, 0xFF293648);
            gui.drawString(font, "ВЕРСИЯ 0.3.0", l + 17, t + panelHeight() - 35, MUTED, false);
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
            int x = contentLeft(), y = top() + 18;
            String title = page == 0 ? "МОДУЛИ HUD" : page == 1 ? "ВИЗУАЛЬНЫЕ ЭФФЕКТЫ" : "НАСТРОЙКИ";
            String subtitle = page == 0 ? "Информация и элементы на экране" :
                    page == 1 ? "Внешний вид игры" : "Управление и оформление Nexver";
            gui.drawString(font, title, x, y, WHITE, false);
            gui.drawString(font, subtitle, x, y + 15, MUTED, false);
            int statusX = left() + panelWidth() - 82;
            gui.fill(statusX, y + 1, statusX + 7, y + 8, 0xFF65D68A);
            gui.drawString(font, "ВКЛЮЧЕН", statusX + 12, y, 0xFF9CB4A7, false);
            gui.fill(contentLeft(), top() + 48, left() + panelWidth() - 18, top() + 49, 0xFF293648);
        }

        private void drawHudPage(GuiGraphics gui, int mx, int my) {
            int x = contentLeft(), y = top() + 62, gap = 8;
            int cardW = (contentWidth() - gap) / 2;
            drawCard(gui,mx,my,x,y,cardW,"СЧЁТЧИК FPS","Текущая частота кадров",showFps,0);
            drawCard(gui,mx,my,x+cardW+gap,y,cardW,"КООРДИНАТЫ","Позиция в мире",showCoordinates,1);
            drawCard(gui,mx,my,x,y+70,cardW,"СЕТЕВОЙ ПИНГ","Задержка соединения",showPing,2);
            drawCard(gui,mx,my,x+cardW+gap,y+70,cardW,"КЛАВИШИ","Отображение WASD",showKeystrokes,3);
            drawCard(gui,mx,my,x,y+140,cardW,"ЭФФЕКТЫ ЗЕЛИЙ","Иконки и длительность",showPotions,4);
            drawCard(gui,mx,my,x+cardW+gap,y+140,cardW,"ПРЕДМЕТ В РУКЕ","Название и количество",showItemOverlay,5);
            gui.drawString(font, "Настройки сохраняются автоматически", x, top()+panelHeight()-20, MUTED, false);
        }

        private void drawVisualPage(GuiGraphics gui, int mx, int my) {
            int x=contentLeft(), y=top()+62, gap=8, cardW=(contentWidth()-gap)/2;
            drawCard(gui,mx,my,x,y,cardW,"СВОЙ ПРИЦЕЛ","Цветной минималистичный прицел",showCrosshair,6);
            drawCard(gui,mx,my,x+cardW+gap,y,cardW,"ПОЛНАЯ ЯРКОСТЬ","Переключатель K",fullBright,7);
            gui.drawString(font,"Другие визуальные модули появятся в следующих обновлениях",x,y+94,MUTED,false);
        }

        private void drawSettingsPage(GuiGraphics gui, int mx, int my) {
            int x=contentLeft(), y=top()+65;
            gui.drawString(font,"ЦВЕТ АКЦЕНТА",x,y,WHITE,false);
            gui.drawString(font,"Выбери цвет элементов интерфейса",x,y+15,MUTED,false);
            int[] colors={0xFF65D6FF,0xFF9B83FF,0xFFFF78B7,0xFF75D6A0,0xFFFFB86B};
            String[] names={"ЛЁД","ФИОЛЕТОВЫЙ","РОЗОВЫЙ","МЯТНЫЙ","ЯНТАРНЫЙ"};
            for(int i=0;i<colors.length;i++){
                int cy=y+47+i*35;
                boolean selected=accent==colors[i], hover=inside(mx,my,x,cy-5,contentWidth()-8,28);
                gui.fill(x,cy-5,x+contentWidth()-8,cy+23,selected?0xFF243448:hover?CARD_HOVER:CARD);
                gui.fill(x+10,cy+2,x+24,cy+16,colors[i]);
                gui.drawString(font,names[i],x+34,cy+5,WHITE,false);
                if(selected) gui.drawString(font,"ВЫБРАН",x+contentWidth()-68,cy+5,colors[i],false);
            }
            gui.drawString(font,"Открыть меню: RIGHT SHIFT   •   Яркость: K",x,top()+panelHeight()-20,MUTED,false);
        }

        private void drawCard(GuiGraphics gui,int mx,int my,int x,int y,int w,String title,String desc,boolean enabled,int id) {
            boolean hover=inside(mx,my,x,y,w,62);
            gui.fill(x,y,x+w,y+62,hover?CARD_HOVER:CARD);
            gui.fill(x,y,x+2,y+62,enabled?accent:0xFF344255);
            gui.drawString(font,title,x+10,y+10,WHITE,false);
            gui.drawString(font,desc,x+10,y+27,MUTED,false);
            gui.fill(x+w-34,y+12,x+w-8,y+26,enabled?accent:0xFF3A485B);
            gui.fill(enabled?x+w-19:x+w-32,y+14,enabled?x+w-10:x+w-23,y+24,0xFFF4F7FB);
            gui.drawString(font,enabled?"ВКЛ":"ВЫКЛ",x+10,y+45,enabled?accent:MUTED,false);
        }

        private boolean inside(double mx,double my,int x,int y,int w,int h) {
            return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;
        }

        @Override
        public boolean mouseClicked(double mx,double my,int button) {
            if(button!=0) return super.mouseClicked(mx,my,button);
            int navX=left()+10;
            if(mx>=navX&&mx<=left()+sideWidth()-10){
                if(my>=top()+60&&my<=top()+94){page=0;return true;}
                if(my>=top()+98&&my<=top()+132){page=1;return true;}
                if(my>=top()+135&&my<=top()+169){page=2;return true;}
            }
            if(page==0||page==1){
                int x=contentLeft(),y=top()+62,gap=8,cardW=(contentWidth()-gap)/2;
                int id=-1;
                if(page==0){
                    if(inside(mx,my,x,y,cardW,62))id=0;
                    else if(inside(mx,my,x+cardW+gap,y,cardW,62))id=1;
                    else if(inside(mx,my,x,y+70,cardW,62))id=2;
                    else if(inside(mx,my,x+cardW+gap,y+70,cardW,62))id=3;
                    else if(inside(mx,my,x,y+140,cardW,62))id=4;
                    else if(inside(mx,my,x+cardW+gap,y+140,cardW,62))id=5;
                }else{
                    if(inside(mx,my,x,y,cardW,62))id=6;
                    else if(inside(mx,my,x+cardW+gap,y,cardW,62))id=7;
                }
                switch(id){
                    case 0: showFps=!showFps; break;
                    case 1: showCoordinates=!showCoordinates; break;
                    case 2: showPing=!showPing; break;
                    case 3: showKeystrokes=!showKeystrokes; break;
                    case 4: showPotions=!showPotions; break;
                    case 5: showItemOverlay=!showItemOverlay; break;
                    case 6: showCrosshair=!showCrosshair; break;
                    case 7: setFullBright(Minecraft.getInstance(),!fullBright); break;
                    default: break;
                }
                if(id>=0)saveConfig();
            }else{
                int x=contentLeft(),y=top()+65+47;
                int[] colors={0xFF65D6FF,0xFF9B83FF,0xFFFF78B7,0xFF75D6A0,0xFFFFB86B};
                for(int i=0;i<colors.length;i++)if(inside(mx,my,x,y+i*35,contentWidth()-8,28)){accent=colors[i];saveConfig();break;}
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
