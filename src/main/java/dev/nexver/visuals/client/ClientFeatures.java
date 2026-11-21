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
    private static int accent = 0xFF8B5CF6;

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
            accent = Integer.parseUnsignedInt(p.getProperty("appearance.accent", "FF8B5CF6"), 16);
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
        private static final int PANEL = 0xF20D1018;
        private static final int SURFACE = 0xF9161A25;
        private static final int ROW = 0xD91B202C;
        private static final int ROW_HOVER = 0xEF252B3A;
        private static final int TEXT = 0xFFF1F2F8;
        private static final int MUTED = 0xFF85899B;
        private static final int PURPLE = 0xFF8B5CF6;
        private static final String[] VISUALS = {
                "Aspect Ratio|Формат игрового изображения|-1",
                "China Hit|Эффект попадания|-1",
                "Crosshair|Настраиваемый прицел|1",
                "Custom Hand|Настройка руки|-1",
                "Full Bright|Полная яркость|2",
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
                "Keybinds|Список активных клавиш|-1",
                "Brightness|Быстрое управление яркостью|2",
                "HUD Toggle|Быстрое скрытие HUD|21",
                "HUD Background|Фон элементов HUD|20"
        };

        private int page = 0;
        private boolean searchOpen = false;
        private boolean settingsOpen = false;
        private String searchText = "";
        private int scroll = 0;
        private final long openedAt = Util.getMillis();

        private NexverMenu() { super(Component.literal("NEXVER VISUALS")); }

        private int pw() { return Math.min(760, width - 32); }
        private int ph() { return Math.min(410, height - 28); }
        private int px() { return (width - pw()) / 2; }
        private int py() { return (height - ph()) / 2; }
        private int contentX() { return px() + 22; }
        private int contentW() { return pw() - 44; }
        private int contentTop() { return py() + 100; }
        private int contentBottom() { return py() + ph() - 48; }
        private int rowHeight() { return 39; }
        private int rowGap() { return 8; }

        @Override
        public void render(GuiGraphics g, int mx, int my, float partialTick) {
            g.fill(0, 0, width, height, 0x99040810);
            int x=px(), y=py(), w=pw(), h=ph();
            g.fill(x-5,y-5,x+w+5,y+h+5,0x44000000);
            g.fill(x,y,x+w,y+h,PANEL);
            g.fill(x+1,y+1,x+w-1,y+h-1,SURFACE);
            g.fill(x,y,x+w,y+2,PURPLE);

            drawHeader(g,mx,my);
            g.fill(x+20,y+57,x+w-20,y+58,0xFF292D3A);
            drawSectionLabel(g);
            g.enableScissor(x+12,contentTop(),x+w-12,contentBottom());
            drawModules(g,mx,my);
            g.disableScissor();
            drawFooter(g);
            if(settingsOpen) drawSettings(g,mx,my);

            float a=Mth.clamp((Util.getMillis()-openedAt)/170.0f,0.0f,1.0f);
            if(a<1.0f) g.fill(x,y,x+w,y+h,((int)(0x18*(1.0f-a)))<<24);
            super.render(g,mx,my,partialTick);
        }

        private void drawHeader(GuiGraphics g,int mx,int my) {
            int x=px(),y=py();
            g.fill(x+20,y+17,x+43,y+40,PURPLE);
            g.drawString(font,"N",x+28,y+24,0xFFFFFFFF,false);
            g.drawString(font,"NEXVER",x+52,y+17,TEXT,false);
            g.drawString(font,"VISUALS",x+52+font.width("NEXVER")+6,y+17,PURPLE,false);
            g.drawString(font,"CLIENT  /  0.5.0",x+52,y+31,MUTED,false);

            String[] tabs={"ВИЗУАЛЫ","HUD","УТИЛИТЫ"};
            int[] widths={91,48,78};
            int tx=x+pw()/2-145;
            for(int i=0;i<tabs.length;i++){
                boolean selected=page==i;
                int tw=widths[i];
                if(selected) {
                    g.fill(tx-12,y+12,tx+tw+10,y+43,0xFF252033);
                    g.fill(tx-12,y+41,tx+tw+10,y+43,PURPLE);
                } else if(inside(mx,my,tx-12,y+12,tw+22,31)) {
                    g.fill(tx-12,y+12,tx+tw+10,y+43,0xFF1D2230);
                }
                g.drawString(font,tabs[i],tx,y+23,selected?TEXT:MUTED,false);
                tx+=tw+26;
            }

            int right=x+pw()-126;
            if(searchOpen) {
                g.fill(right-105,y+14,right+4,y+39,0xFF222735);
                g.drawString(font,searchText.isEmpty()?"Поиск модулей":searchText,right-98,y+23,searchText.isEmpty()?MUTED:TEXT,false);
            } else {
                g.drawString(font,"⌕",right-8,y+20,inside(mx,my,right-13,y+12,22,28)?TEXT:MUTED,false);
            }
            g.drawString(font,"⚙",right+20,y+20,settingsOpen?PURPLE:MUTED,false);
            g.fill(right+56,y+16,right+76,y+36,0xFF303747);
            g.fill(right+70,y+30,right+77,y+37,0xFF72D6A0);
        }

        private void drawSectionLabel(GuiGraphics g) {
            String title=page==0?"ВИЗУАЛЫ":page==1?"ЭЛЕМЕНТЫ HUD":"УТИЛИТЫ";
            String subtitle=page==0?"Визуальные настройки игрового клиента":
                    page==1?"Информация и виджеты поверх игрового мира":"Быстрые инструменты и управление";
            g.drawString(font,title,contentX(),py()+70,TEXT,false);
            g.drawString(font,subtitle,contentX(),py()+84,MUTED,false);
        }

        private String[] modulesForPage() { return page==0?VISUALS:page==1?HUD:UTILITIES; }

        private void drawModules(GuiGraphics g,int mx,int my) {
            int x=contentX(), y=contentTop(), gap=12, colW=(contentW()-gap)/2;
            String[] entries=modulesForPage();
            int row=0;
            for(String entry:entries) {
                String[] p=entry.split("\\|",3);
                if(!matches(p)) continue;
                int col=row%2, r=row/2;
                int cx=x+col*(colW+gap), cy=y+r*(rowHeight()+rowGap())-scroll;
                if(cy+rowHeight()<contentTop() || cy>contentBottom()) { row++; continue; }
                drawModule(g,mx,my,cx,cy,colW,p[0],p[1],Integer.parseInt(p[2]));
                row++;
            }
            if(row==0) g.drawString(font,"Ничего не найдено",x+8,y+14,MUTED,false);
        }

        private boolean matches(String[] p) {
            return searchText.isEmpty() || p[0].toLowerCase().contains(searchText.toLowerCase()) ||
                    p[1].toLowerCase().contains(searchText.toLowerCase());
        }

        private void drawModule(GuiGraphics g,int mx,int my,int x,int y,int w,String name,String desc,int id) {
            boolean active=isActive(id);
            boolean ready=id>=0;
            boolean hover=inside(mx,my,x,y,w,rowHeight()) && !settingsOpen;
            g.fill(x,y,x+w,y+rowHeight(),hover?ROW_HOVER:ROW);
            g.fill(x,y,x+2,y+rowHeight(),active?PURPLE:0xFF343A49);
            g.drawString(font,name,x+12,y+7,TEXT,false);
            g.drawString(font,desc,x+12,y+23,MUTED,false);
            if(ready) {
                int sx=x+w-43, sy=y+11;
                g.fill(sx,sy,sx+31,sy+17,active?PURPLE:0xFF414654);
                g.fill(active?sx+17:sx+2,sy+2,active?sx+29:sx+14,sy+15,0xFFF5F5FA);
            } else {
                String tag="СКОРО";
                int tw=font.width(tag);
                g.drawString(font,tag,x+w-tw-12,y+15,0xFF73798B,false);
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

        private void drawFooter(GuiGraphics g) {
            int x=px()+20,y=py()+ph()-35;
            g.fill(x,y,px()+pw()-20,y+1,0xFF292D3A);
            g.drawString(font,"NEXVER VISUALS  •  0.5.0",x,y+12,MUTED,false);
            String hint="RIGHT SHIFT  •  ЗАКРЫТЬ";
            g.drawString(font,hint,px()+pw()-20-font.width(hint),y+12,MUTED,false);
        }

        private void drawSettings(GuiGraphics g,int mx,int my) {
            int x=px()+pw()-206,y=py()+58;
            g.fill(x+3,y+4,x+197,y+164,0xAA000000);
            g.fill(x,y,x+194,y+160,0xFF171B27);
            g.fill(x,y,x+194,y+2,PURPLE);
            g.drawString(font,"НАСТРОЙКИ",x+12,y+13,TEXT,false);
            g.drawString(font,"ЦВЕТ АКЦЕНТА",x+12,y+37,MUTED,false);
            int[] colors={0xFF8B5CF6,0xFF65D6FF,0xFFFF78B7,0xFF75D6A0,0xFFFFB86B};
            for(int i=0;i<colors.length;i++){
                int cx=x+12+i*33;
                g.fill(cx,y+58,cx+24,y+82,colors[i]);
                if(accent==colors[i]) g.fill(cx-2,y+56,cx+26,y+58,0xFFFFFFFF);
            }
            g.drawString(font,"HUD",x+12,y+102,TEXT,false);
            g.drawString(font,"Общий переключатель",x+12,y+120,MUTED,false);
            g.drawString(font,hudEnabled?"ВКЛЮЧЁН":"ВЫКЛЮЧЕН",x+12,y+139,hudEnabled?PURPLE:MUTED,false);
        }

        private boolean inside(double mx,double my,int x,int y,int w,int h) {
            return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;
        }

        @Override
        public boolean mouseClicked(double mx,double my,int button) {
            if(button!=0) return super.mouseClicked(mx,my,button);
            int x=px(),y=py(),right=x+pw()-126;
            if(settingsOpen) {
                int sx=px()+pw()-206, sy=py()+58;
                int[] colors={0xFF8B5CF6,0xFF65D6FF,0xFFFF78B7,0xFF75D6A0,0xFFFFB86B};
                for(int i=0;i<colors.length;i++) if(inside(mx,my,sx+12+i*33,sy+58,24,24)){accent=colors[i];saveConfig();return true;}
                if(inside(mx,my,sx+8,sy+112,178,38)){hudEnabled=!hudEnabled;saveConfig();return true;}
                if(!inside(mx,my,sx,sy,194,160)) settingsOpen=false;
                return true;
            }
            if(inside(mx,my,right+14,y+10,34,34)){settingsOpen=true;return true;}
            if(inside(mx,my,right-112,y+10,112,34)){searchOpen=!searchOpen;if(!searchOpen)searchText="";scroll=0;return true;}

            int tabX=x+pw()/2-157;
            if(inside(mx,my,tabX,y+9,115,36)){page=0;scroll=0;return true;}
            if(inside(mx,my,tabX+117,y+9,72,36)){page=1;scroll=0;return true;}
            if(inside(mx,my,tabX+192,y+9,112,36)){page=2;scroll=0;return true;}

            int ix=contentX(), iy=contentTop(), gap=12, colW=(contentW()-gap)/2, row=0;
            for(String entry:modulesForPage()) {
                String[] p=entry.split("\\|",3);
                if(!matches(p)) continue;
                int cx=ix+(row%2)*(colW+gap), cy=iy+(row/2)*(rowHeight()+rowGap())-scroll;
                if(inside(mx,my,cx,cy,colW,rowHeight()) && cy>=contentTop() && cy+rowHeight()<=contentBottom()+1) {
                    int id=Integer.parseInt(p[2]);
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
        public boolean mouseScrolled(double mx,double my,double delta) {
            int count=0;
            for(String entry:modulesForPage()) if(matches(entry.split("\\|",3))) count++;
            int rows=(count+1)/2;
            int max=Math.max(0,rows*(rowHeight()+rowGap())-(contentBottom()-contentTop()));
            scroll=Mth.clamp(scroll-(int)(delta*rowHeight()),0,max);
            return true;
        }

        @Override
        public boolean charTyped(char codePoint,int modifiers) {
            if(searchOpen && !Character.isISOControl(codePoint) && searchText.length()<24) {
                searchText+=codePoint;scroll=0;return true;
            }
            return super.charTyped(codePoint,modifiers);
        }

        @Override
        public boolean keyPressed(int keyCode,int scanCode,int modifiers) {
            if(searchOpen && keyCode==GLFW.GLFW_KEY_BACKSPACE && !searchText.isEmpty()) {
                searchText=searchText.substring(0,searchText.length()-1);scroll=0;return true;
            }
            if(keyCode==GLFW.GLFW_KEY_RIGHT_SHIFT||keyCode==GLFW.GLFW_KEY_ESCAPE) {
                if(settingsOpen){settingsOpen=false;return true;}
                if(searchOpen){searchOpen=false;searchText="";return true;}
                onClose();return true;
            }
            return super.keyPressed(keyCode,scanCode,modifiers);
        }

        @Override public boolean isPauseScreen(){return false;}
    }
}
