package factorization.sockets;

import factorization.api.Coord;
import factorization.api.datahelpers.*;
import factorization.shared.Core;
import factorization.net.FzNetDispatch;
import factorization.net.StandardMessageType;
import factorization.util.FzUtil;
import factorization.util.LangUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class GuiDataConfig extends GuiScreen {
    IDataSerializable ids;
    TileEntity te;
    Entity containingEntity;
    
    ArrayList<Field> fields = new ArrayList<Field>();
    int posLabel, posControl;
    boolean changed;
    boolean orig_f1_state;

    static GuiButton usefulButton(int id, int xPos, int yPos, String text) {
        return new GuiButton(id, xPos, yPos - 4, Minecraft.getMinecraft().fontRendererObj.getStringWidth(text) + 8, 20, text);
    }
    
    class Field {
        String name;
        Object object;
        Class objectType;
        int posY;
        String label;
        int color = 0xFFFFFF;
        
        ArrayList<GuiButton> buttons = new ArrayList<GuiButton>();
        int buttonPos = 0;
        
        public Field(String name, Object object, int posY) {
            this.name = name;
            this.object = object;
            this.objectType = object.getClass();
            this.posY = posY;
            this.label = LangUtil.translate(getTrans());
            if (this.getClass() == Field.class) {
                color = 0xAAAAAA;
            }
        }
        
        String getTrans() {
            return "data.label." + ids.getClass().getSimpleName() + "." + name;
        }
        
        void initGui() {
            buttonPos = posControl;
            buttons.clear();
        }
        
        GuiButton button(int delta, String text) {
            GuiButton button = usefulButton(delta, buttonPos, posY - 2, text);
            buttons.add(button);
            buttonPos += fontRendererObj.getStringWidth(button.displayString + 10);
            return button;
        }
        
        void render(int mouseX, int mouseY) {
            renderLabel();
            renderControl(mouseX, mouseY);
            for (GuiButton button : buttons) {
                button.drawButton(mc, mouseX, mouseY);
            }
        }
        
        void renderLabel() {
            drawString(fontRendererObj, label, posLabel, posY, color);
        }
        
        void renderControl(int mouseX, int mouseY) { }
        
        int getLabelWidth() {
            return fontRendererObj.getStringWidth(label) + 80;
        }
        
        void mouseClick(int mouseX, int mouseY, boolean rightClick) {
            for (GuiButton button : buttons) {
                if (button.isMouseOver()) {
                    buttonPressed(button, rightClick);
                    break;
                }
            }
        }
        
        void put(Map<String, Object> map) {
            map.put(name, object);
        }
        
        void keyTyped(int keysym, char ch) {}
        void buttonPressed(GuiButton button, boolean rightClick) {}
    }
    
    class BooleanField extends Field {
        boolean val;
        GuiButton button;
        public BooleanField(String name, Object object, int posY) {
            super(name, object, posY);
            val = (Boolean) object;
        }
        
        @Override
        void initGui() {
            super.initGui();
            button = button(0, LangUtil.tryTranslate(getTrans() + "." + val, "" + val));
        }
        
        @Override
        void buttonPressed(GuiButton button, boolean rightClick) {
            val = !val;
            object = val;
            initGui();
        }
    }
    
    class NumberField extends Field {
        long val;
        int labelPos;
        
        public NumberField(String name, Object object, int posY) {
            super(name, object, posY);
            Number n = (Number) object;
            val = n.intValue();
        }
        
        String transVal() {
            return LangUtil.tryTranslate(getTrans() + "." + val, "" + val);
        }
        
        @Override
        void initGui() {
            super.initGui();
            button(-10, "-10");
            button(-1, "-");
            labelPos = buttonPos;
            buttonPos += fontRendererObj.getStringWidth(transVal()) + 5;
            button(1, "+");
            button(10, "+10");
        }
        
        @Override
        void renderControl(int mouseX, int mouseY) {
            drawString(fontRendererObj, transVal(), this.labelPos, posY, color);
        }
        
        @Override
        void keyTyped(int keysym, char ch) {
            if (keysym == Keyboard.KEY_BACK) {
                val /= 10;
            } else {
                try {
                    int digit = Integer.parseInt(Character.toString(ch));
                    val = val*10 + digit;
                } catch (NumberFormatException e) {}
            }
            object = val;
            initGui();
        }
        
        @Override
        void buttonPressed(GuiButton button, boolean rightClick) {
            val += button.id;
            object = val;
            initGui();
        }
        
        @Override
        void put(Map<String, Object> map) {
            Object obj = object;
            if (objectType == Long.class) {
                obj = new Long((long) val);
            } else if (objectType == Integer.class) {
                obj = new Integer((int) val);
            } else if (objectType == Short.class) {
                obj = new Short((short) val);
            } else if (objectType == Byte.class) {
                obj = new Byte((byte) val);
            } //else: gonna crash, uh-oh...
            map.put(name, obj);
        }
    }
    
    class EnumField<E extends Enum> extends Field {
        E val;
        GuiButton button;
        public EnumField(String name, E object, int posY) {
            super(name, object, posY);
            val = object;
        }
        
        @Override
        void initGui() {
            super.initGui();
            button = button(0, LangUtil.tryTranslate(getTrans() + "." + val, "" + val));
        }
        
        @Override
        void buttonPressed(GuiButton button, boolean rightClick) {
            int ord = val.ordinal();
            E[] family = (E[]) val.getClass().getEnumConstants();
            if (rightClick) {
                ord = ord == 0 ? family.length - 1 : ord - 1;
            } else {
                ord = ord + 1 == family.length ? 0 : ord + 1;
            }
            object = val = family[ord];
            initGui();
        }
        
        @Override
        void put(Map<String, Object> map) {
            map.put(name, val);
        }
    }
    
    public GuiDataConfig(IDataSerializable ids) {
        this.ids = ids;
        this.te = (TileEntity) ids;
        mc = Minecraft.getMinecraft();
        orig_f1_state = mc.gameSettings.hideGUI;
        mc.gameSettings.hideGUI = true;
    }
    
    public GuiDataConfig(IDataSerializable ids, Entity container) {
        this.ids = ids;
        this.te = (TileEntity) ids;
        this.containingEntity = container;
        mc = Minecraft.getMinecraft();
        orig_f1_state = mc.gameSettings.hideGUI;
        mc.gameSettings.hideGUI = true;
    }
    
    void closeScreen() {
        mc.displayGuiScreen(null);
    }
    
    
    boolean fields_initialized = false;
    boolean fields_valid = false;
    
    @Override
    public void initGui() {
        super.initGui();
        if (!fields_initialized) {
            //This can't happen in the constructor because we can't close the GUI from the constructor.
            fields_initialized = true;
            try {
                initFields();
            } catch (IOException e) {
                e.printStackTrace();
                closeScreen();
            }
            fields_valid = true;
        }
        posLabel = 40;
        posControl = posLabel + 20;
        for (Field field : fields) {
            posControl = Math.max(posControl, field.getLabelWidth() + 20);
        }
        
        for (Field field : fields) {
            field.initGui();
        }
    }
    
    void initFields() throws IOException {
        fields.clear();
        ids.serialize("", new MergedDataHelper() {
            int count = 0;
            
            @Override
            protected boolean shouldStore(Share share) {
                return share.is_public && share.client_can_edit;
            }
            
            @Override
            protected <E> E putImplementation(E o) throws IOException {
                int fieldStart = 100;
                int fieldHeight = 24;
                int posY = count*fieldHeight + fieldStart;
                if (o instanceof Boolean) {
                    fields.add(new BooleanField(name, o, posY));
                } else if (o instanceof Number) {
                    fields.add(new NumberField(name, o, posY));
                } else if (o instanceof Enum) {
                    fields.add(new EnumField<Enum>(name, (Enum) o, posY));
                } else {
                    fields.add(new Field(name, o, posY));
                }
                count++;
                return o;
            }
            
            @Override
            public boolean isReader() {
                return true;
            }
        });
    }
    
    boolean validate(ArrayList<Field> fields) {
        HashMap<String, Object> data = new HashMap();
        for (Field f : fields) {
            f.put(data);
        }
        DataValidator dv = new DataValidator(data);
        try {
            ids.serialize("", dv);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return dv.isValid();
    }
    
    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        //drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partial);
        
        TextureManager tex = Minecraft.getMinecraft().renderEngine;
        tex.bindTexture(Core.itemAtlas);

        TextureAtlasSprite lmp = FzUtil.getIcon(new ItemStack(Core.registry.logicMatrixProgrammer));

        int w = 256;
        int xSize = w, ySize = xSize;
        int left = (width - 6) / 2;
        int top = (height - ySize) / 2;
        
        drawTexturedModalRect(left, top, lmp, xSize, ySize);
        
        for (Field field : fields) {
            field.render(mouseX, mouseY);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        for (Field field : fields) {
            field.mouseClick(mouseX, mouseY, button == 1);
        }
        valueChanged();
        super.mouseClickMove(mouseX, mouseY, button, timeSinceLastClick);
    }
    
    void valueChanged() {
        DataBackup origValues = new DataBackup();
        try {
            ids.serialize("", origValues);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        
        applyChangesToEntity();
        
        origValues.restoring();
        try {
            ids.serialize("", origValues);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }
    
    void applyChangesToEntity() {
        if (validate(fields)) {
            fields_valid = true;
        } else {
            fields_valid = false;
        }
        
        fields_initialized = false;
        initGui();
        changed = true;
    }
    
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }


    @Override
    protected void keyTyped(char chr, int keySym) throws IOException {
        super.keyTyped(chr, keySym);
        if (keySym == 1) {
            return;
        }
    }
    
    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        mc.gameSettings.hideGUI = orig_f1_state;
        if (!fields_valid || !changed) {
            return;
        }
        applyChangesToEntity();
        try {
            sendPacket();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }
    
    void sendPacket() throws IOException {
        ByteBuf buf = Unpooled.buffer();
        DataHelper dop = new DataOutByteBufEdited(buf);
        Coord here = new Coord(te);
        if (containingEntity == null) {
            Core.network.prefixTePacket(buf, te, StandardMessageType.DataHelperEdit);
            ids.serialize("", dop);
            Core.network.broadcastPacket(mc.thePlayer, here, FzNetDispatch.generate(buf));
        } else {
            Core.network.prefixEntityPacket(buf, containingEntity, StandardMessageType.DataHelperEditOnEntity);
            ids.serialize("", dop);
            Core.network.broadcastPacket(mc.thePlayer, here, Core.network.entityPacket(buf));
        }
    }
}
