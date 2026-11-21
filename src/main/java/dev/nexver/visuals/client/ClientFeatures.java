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
    private static boolean hudEnabled = true;
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
            hudEnabled = Boolean.parseBoolean(p.getProperty("hud.enabled", "true"));
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
        p.setProperty("hud.enabled", Boolean.toString(hudEnabled));
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
            if (mc.player == null || mc.options.hideGui || mc.screen != null || !hudEnabled) return;
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
        private static final int PANEL = 0xF20D111B, SURFACE = 0xFF141A26;
        private static final int ROW = 0xFF191F2C, ROW_HOVER = 0xFF222B3B;
        private static final int MUTED = 0xFF778399, TEXT = 0xFFE8EDF6;
        private static final String[] VISUALS = {
                "Aspect Ratio|Формат игрового изображения|-1",
                "Crosshair|Настраиваемый прицел|1",
                "Full Bright|Полная яркость|2",
                "China Hit|Эффект попадания|-1",
                "Custom Hand|Настройка руки|-1",
                "Hit Color|Цвет попадания|-1",
                "Hitbox Customizer|Визуализация хитбоксов|-1",
                "Jump Circles|Круги при прыжке|-1",
                "Particles|Частицы|-1",
                "Render Tweaks|Настройки рендера|-1",
                "Scope Overlay|Прицел с увеличением|-1",
                "Tracer Lines|Декоративные линии|-1"
        };
        private static final String[] HUD = {
                "Item Overlay|Предмет в руке|15",
                "Keystrokes|Клавиши движения|13",
                "Potions|Эффекты зелий|14",
                "Watermark|Никнейм, пинг и FPS|-1",
                "SpaceHUD|Игровая информация|-1",
                "FPS Counter|Частота кадров|10",
                "Coordinates|Координаты игрока|11",
                "Ping|Задержка соединения|12"
        };
        private static final String[] UTILITIES = {
                "HUD Background|Фон элементов HUD|20",
                "HUD Toggle|Быстрое скрытие HUD|21"
        };

        private int page = 0;
        private boolean searchOpen = false;
        private boolean settingsOpen = false;
        private String searchText = "";
        private final long openedAt = Util.getMillis();

        private NexverMenu() { super(Component.literal("NEXVER VISUALS")); }

        private int pw() { return Math.min(720, width - 28); }
        private int ph() { return Math.min(382, height - 24); }
        private int px() { return (width - pw()) / 2; }
        private int py() { return (height - ph()) / 2; }
        private int innerX() { return px() + 22; }
        private int innerW() { return pw() - 44; }

        @Override
        public void render(GuiGraphics g, int mx, int my, float partialTick) {
            g.fill(0, 0, width, height, 0x99040810);
            int x=px(), y=py(), w=pw(), h=ph();
            g.fill(x-3,y-3,x+w+3,y+h+3,0x44000000);
            g.fill(x,y,x+w,y+h,PANEL);
            g.fill(x+1,y+1,x+w-1,y+h-1,SURFACE);
            g.fill(x,y,x+w,y+2,accent);

            drawHeader(g,mx,my);
            g.fill(x+18,y+57,x+w-18,y+58,0xFF293344);
            drawModules(g,mx,my);
            drawBottomBar(g);
            if(settingsOpen) drawSettings(g,mx,my);

            float a=Mth.clamp((Util.getMillis()-openedAt)/180.0f,0.0f,1.0f);
            if(a<1.0f) g.fill(x,y,x+w,y+h,((int)(0x18*(1.0f-a)))<<24);
            super.render(g,mx,my,partialTick);
        }

        private void drawHeader(GuiGraphics g,int mx,int my) {
            int x=px(),y=py();
            g.fill(x+20,y+17,x+40,y+37,accent);
            g.drawString(font,"N",x+26,y+23,0xFF101522,false);
            g.drawString(font,"NEXVER",x+48,y+17,TEXT,false);
            g.drawString(font,"VISUALS",x+48+font.width("NEXVER")+6,y+17,accent,false);

            String[] tabs={"ВИЗУАЛЫ","HUD","УТИЛИТЫ"};
            int[] widths={88,52,83};
            int tx=x+190;
            for(int i=0;i<tabs.length;i++){
                boolean selected=page==i;
                int tw=widths[i];
                if(selected) g.fill(tx-10,y+12,tx+tw,y+42,0xFF202B3B);
                else if(inside(mx,my,tx-10,y+12,tw+10,30)) g.fill(tx-10,y+12,tx+tw,y+42,0xFF192331);
                g.drawString(font,tabs[i],tx,y+23,selected?TEXT:MUTED,false);
                if(selected) g.fill(tx-10,y+40,tx+tw,y+42,accent);
                tx+=tw+22;
            }

            int right=x+pw()-126;
            if(searchOpen){
                g.fill(right-105,y+14,right+2,y+39,0xFF202938);
                g.drawString(font,searchText.isEmpty()?"Поиск...":searchText,right-98,y+23,searchText.isEmpty()?MUTED:TEXT,false);
            } else {
                g.drawString(font,"⌕",right-8,y+20,inside(mx,my,right-13,y+12,22,28)?TEXT:MUTED,false);
            }
            g.drawString(font,"⚙",right+20,y+20,settingsOpen?accent:MUTED,false);
            g.fill(right+56,y+16,right+76,y+36,0xFF303B4D);
            g.fill(right+71,y+31,right+77,y+37,0xFF65D68A);
        }

        private String[] modulesForPage() { return page==0?VISUALS:page==1?HUD:UTILITIES; }

        private void drawModules(GuiGraphics g,int mx,int my) {
            int x=innerX(), y=py()+78, gap=8, colW=(innerW()-gap)/2;
            String[] entries=modulesForPage();
            int row=0;
            for(String entry:entries) {
                String[] parts=entry.split("\\|",3);
                if(!searchText.isEmpty() && !(parts[0].toLowerCase().contains(searchText.toLowerCase()) ||
                        parts[1].toLowerCase().contains(searchText.toLowerCase()))) continue;
                int col=row%2, r=row/2;
                int cx=x+col*(colW+gap), cy=y+r*39;
                drawModule(g,mx,my,cx,cy,colW,parts[0],parts[1],Integer.parseInt(parts[2]));
                row++;
            }
            if(row==0) g.drawString(font,"Ничего не найдено",x+8,y+12,MUTED,false);
        }

        private void drawModule(GuiGraphics g,int mx,int my,int x,int y,int w,String name,String desc,int id) {
            boolean active=isActive(id);
            boolean ready=id>=0;
            boolean hover=inside(mx,my,x,y,w,33);
            g.fill(x,y,x+w,y+33,hover?ROW_HOVER:ROW);
            g.drawString(font,name,x+10,y+4,TEXT,false);
            g.drawString(font,desc,x+10,y+18,MUTED,false);
            if(ready) {
                int sx=x+w-42;
                g.fill(sx,y+8,sx+29,y+24,active?accent:0xFF353E4D);
                g.fill(active?sx+16:sx+2,y+10,active?sx+27:sx+13,y+22,0xFFF3F5FA);
            } else {
                String tag="СКОРО";
                int tw=font.width(tag);
                g.drawString(font,tag,x+w-tw-10,y+12,0xFF69758A,false);
            }
        }

        private boolean isActive(int id) {
            switch(id) {
                case 1:return showCrosshair;
                case 2:return fullBright;
                case 10:return showFps;
                case 11:return showCoordinates;
                case 12:return showPing;
                case 13:return showKeystrokes;
                case 14:return showPotions;
                case 15:return showItemOverlay;
                case 20:return hudBackground;
                case 21:return hudEnabled;
                default:return false;
            }
        }

        private void drawBottomBar(GuiGraphics g) {
            int x=px()+20,y=py()+ph()-31;
            g.fill(x,y,px()+pw()-20,y+1,0xFF293344);
            g.drawString(font,"NEXVER VISUALS  •  0.4.0",x,y+10,MUTED,false);
            g.drawString(font,"RIGHT SHIFT  •  ЗАКРЫТЬ",px()+pw()-150,y+10,MUTED,false);
        }

        private void drawSettings(GuiGraphics g,int mx,int my) {
            int x=px()+pw()-206,y=py()+60;
            g.fill(x+2,y+3,x+194,y+154,0xAA000000);
            g.fill(x,y,x+190,y+150,0xFF171F2C);
            g.drawString(font,"ОФОРМЛЕНИЕ",x+12,y+12,TEXT,false);
            g.drawString(font,"ЦВЕТ АКЦЕНТА",x+12,y+34,MUTED,false);
            int[] colors={0xFF65D6FF,0xFF9B83FF,0xFFFF78B7,0xFF75D6A0,0xFFFFB86B};
            for(int i=0;i<colors.length;i++){
                int cx=x+12+i*32;
                g.fill(cx,y+55,cx+23,y+78,colors[i]);
                if(accent==colors[i]) g.fill(cx-2,y+53,cx+25,y+55,0xFFFFFFFF);
            }
            g.drawString(font,"ИНТЕРФЕЙС",x+12,y+91,TEXT,false);
            g.drawString(font,"Полупрозрачная панель",x+12,y+108,MUTED,false);
            g.drawString(font,"Поиск модулей",x+12,y+126,MUTED,false);
        }

        private boolean inside(double mx,double my,int x,int y,int w,int h) {
            return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;
        }

        @Override
        public boolean mouseClicked(double mx,double my,int button) {
            if(button!=0) return super.mouseClicked(mx,my,button);
            int x=px(),y=py(),right=x+pw()-126;
            if(settingsOpen) {
                int sx=px()+pw()-206, sy=py()+60;
                int[] colors={0xFF65D6FF,0xFF9B83FF,0xFFFF78B7,0xFF75D6A0,0xFFFFB86B};
                for(int i=0;i<colors.length;i++) if(inside(mx,my,sx+12+i*32,sy+55,23,23)){accent=colors[i];saveConfig();return true;}
                if(!inside(mx,my,sx,sy,190,150)) settingsOpen=false;
                return true;
            }
            if(inside(mx,my,right+14,y+10,34,34)){settingsOpen=true;return true;}
            if(inside(mx,my,right-112,y+10,112,34)){searchOpen=!searchOpen;if(!searchOpen)searchText="";return true;}
            if(inside(mx,my,x+180,y+8,105,38)){page=0;return true;}
            if(inside(mx,my,x+290,y+8,65,38)){page=1;return true;}
            if(inside(mx,my,x+360,y+8,110,38)){page=2;return true;}

            int ix=innerX(), iy=py()+78, gap=8, colW=(innerW()-gap)/2, row=0;
            for(String entry:modulesForPage()) {
                String[] parts=entry.split("\\|",3);
                if(!searchText.isEmpty() && !(parts[0].toLowerCase().contains(searchText.toLowerCase()) ||
                        parts[1].toLowerCase().contains(searchText.toLowerCase()))) continue;
                int cx=ix+(row%2)*(colW+gap), cy=iy+(row/2)*39;
                if(inside(mx,my,cx,cy,colW,33)) {
                    int id=Integer.parseInt(parts[2]);
                    switch(id) {
                        case 1:showCrosshair=!showCrosshair;break;
                        case 2:setFullBright(Minecraft.getInstance(),!fullBright);break;
                        case 10:showFps=!showFps;break;
                        case 11:showCoordinates=!showCoordinates;break;
                        case 12:showPing=!showPing;break;
                        case 13:showKeystrokes=!showKeystrokes;break;
                        case 14:showPotions=!showPotions;break;
                        case 15:showItemOverlay=!showItemOverlay;break;
                        case 20:hudBackground=!hudBackground;break;
                        case 21:hudEnabled=!hudEnabled;break;
                        default:return true;
                    }
                    saveConfig();
                    return true;
                }
                row++;
            }
            return true;
        }

        @Override
        public boolean charTyped(char codePoint,int modifiers) {
            if(searchOpen && !Character.isISOControl(codePoint) && searchText.length()<24) {
                searchText+=codePoint;
                return true;
            }
            return super.charTyped(codePoint,modifiers);
        }

        @Override
        public boolean keyPressed(int keyCode,int scanCode,int modifiers) {
            if(searchOpen && keyCode==GLFW.GLFW_KEY_BACKSPACE && !searchText.isEmpty()) {
                searchText=searchText.substring(0,searchText.length()-1);
                return true;
            }
            if(keyCode==GLFW.GLFW_KEY_RIGHT_SHIFT||keyCode==GLFW.GLFW_KEY_ESCAPE) {
                if(searchOpen){searchOpen=false;searchText="";return true;}
                onClose();return true;
            }
            return super.keyPressed(keyCode,scanCode,modifiers);
        }

        @Override public boolean isPauseScreen(){return false;}
    }
}