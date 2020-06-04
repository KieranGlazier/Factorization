package factorization.sockets;

import factorization.api.Coord;
import factorization.api.datahelpers.*;
import factorization.api.energy.ContextTileEntity;
import factorization.api.energy.IWorkerContext;
import factorization.common.FactoryType;
import factorization.common.FzConfig;
import factorization.net.FzNetDispatch;
import factorization.net.StandardMessageType;
import factorization.notify.Notice;
import factorization.servo.LoggerDataHelper;
import factorization.servo.RenderServoMotor;
import factorization.servo.ServoMotor;
import factorization.shared.BlockClass;
import factorization.shared.Core;
import factorization.shared.Sound;
import factorization.shared.TileEntityCommon;
import factorization.util.*;
import factorization.util.InvUtil.FzInv;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_LIGHTING;

public abstract class TileEntitySocketBase extends TileEntityCommon implements ISocketHolder, IDataSerializable, ITickable {
    /*
     * Some notes for when we get these moving on servos:
     * 		These vars need to be set: worldObj, [xyz]Coord, facing
     * 		Some things might call this's ISocketHolder methods rather than the passed in ISocketHolder's methods
     */
    public EnumFacing facing = EnumFacing.UP;
    public ItemStack[] parts = new ItemStack[3];

    @Override
    public final BlockClass getBlockClass() {
        return BlockClass.Socket;
    }

    @Override
    public void onPlacedBy(EntityPlayer player, ItemStack is, EnumFacing side, float hitX, float hitY, float hitZ) {
        super.onPlacedBy(player, is, side, hitX, hitY, hitZ);
        facing = side;
    }

    public void migrate1to2() {
        // Could delete this?
        ArrayList<ItemStack> legacyParts = new ArrayList<ItemStack>(3);
        TileEntitySocketBase self = this;
        while (true) {
            legacyParts.add(self.getCreatingItem());
            FactoryType ft = self.getParentFactoryType();
            if (ft == null) break;
            self = (TileEntitySocketBase) ft.getRepresentative();
            if (self == null) break;
        }
        Collections.reverse(legacyParts);
        for (int i = 0; i < legacyParts.size(); i++) {
            parts[i] = legacyParts.get(i);
        }
    }

    @Override
    public final void putData(DataHelper data) throws IOException {
        facing = data.as(Share.VISIBLE, "fc").putEnum(facing);
        parts = data.as(Share.VISIBLE, "socketParts").putItemArray(parts);
        serialize("", data);
    }

    /**
     * @return false if clean; true if there was something wrong
     */
    public boolean sanitize(ServoMotor motor) {
        LoggerDataHelper dh = new LoggerDataHelper(motor);
        try {
            serialize("", dh);
        } catch (IOException e) {
            e.printStackTrace();
            return true;
        }
        return dh.hadError;
    }

    protected AxisAlignedBB getEntityBox(ISocketHolder socket, Coord c, EnumFacing top, double d) {
        int one = 1;
        AxisAlignedBB ab = new AxisAlignedBB(
                c.x + top.getDirectionVec().getX(), c.y + top.getDirectionVec().getY(), c.z + top.getDirectionVec().getZ(),
                c.x + one + top.getDirectionVec().getX(), c.y + one + top.getDirectionVec().getY(), c.z + one + top.getDirectionVec().getZ());
        if (d != 0) {
            ab = ab.expand(d, d, d);
            Vec3 min = SpaceUtil.getMin(ab);
            Vec3 max = SpaceUtil.getMax(ab).add(SpaceUtil.componentMultiply(SpaceUtil.dup(d), new Vec3(top.getDirectionVec())));
            ab = SpaceUtil.newBox(min, max);
        }
        return ab;
    }
    
    @Override
    public final EnumFacing[] getValidRotations() {
        return full_rotation_array;
    }

    @Override
    public final boolean rotate(EnumFacing axis) {
        if (getClass() != SocketEmpty.class) {
            return false;
        }
        if (axis == facing) {
            return false;
        }
        facing = axis;
        return true;
    }
    
    @Override
    public boolean isBlockSolidOnSide(EnumFacing side) {
        return side == facing.getOpposite();
    }
    
    @Override
    public final void sendMessage(StandardMessageType msgType, Object ...msg) {
        broadcastMessage(null, msgType, msg);
    }

    protected boolean isBlockPowered() {
        if (FzConfig.sockets_ignore_front_redstone) {
            for (EnumFacing fd : EnumFacing.VALUES) {
                if (fd == facing) continue;
                if (worldObj.getRedstonePower(pos.offset(fd), fd) > 0) {
                    return true;
                }
            }
            return false;
        } else {
            return worldObj.getStrongPower(pos) > 0;
        }
    }
    
    @Override
    public boolean dumpBuffer(List<ItemStack> buffer) {
        if (buffer.size() == 0) {
            return false;
        }
        ItemStack is = buffer.get(0);
        if (is == null) {
            buffer.remove(0);
            return true;
        }
        Coord here = getCoord();
        here.adjust(facing.getOpposite());
        IInventory invTe = here.getTE(IInventory.class);
        if (invTe == null) {
            return true;
        }
        FzInv inv = InvUtil.openInventory(invTe, facing);
        if (inv == null) {
            return true;
        }
        int origSize = is.stackSize;
        int newSize = 0;
        if (inv.push(is) == null) {
            buffer.remove(0);
        } else {
            newSize = is.stackSize;
        }
        if (origSize != newSize) {
            markDirty();
        }
        return !buffer.isEmpty();
    }
    
    @Override
    public void update() {
        genericUpdate(this, getCoord(), isBlockPowered());
    }
    
    @Override
    protected void onRemove() {
        super.onRemove();
        uninstall();
        FactoryType ft = getFactoryType();
        Coord at = getCoord();
        while (ft != null) {
            TileEntitySocketBase sb = (TileEntitySocketBase) ft.getRepresentative();
            ItemStack is = sb.getCreatingItem();
            if (is != null) {
                at.spawnItem(ItemStack.copyItemStack(is));
            }
            ft = sb.getParentFactoryType();
        }
    }
    
    @Override
    public ItemStack getPickedBlock() {
        if (this instanceof SocketEmpty) {
            return FactoryType.SOCKET_EMPTY.itemStack();
        }
        ItemStack ret = null;
        for (ItemStack is : parts) {
            if (is != null) {
                ret = is;
            }
        }

        if (ret == null) {
            ret = getCreatingItem();
        }
        return ItemStack.copyItemStack(ret);
    }
    
    @Override
    public ItemStack getDroppedBlock() {
        return FactoryType.SOCKET_EMPTY.itemStack();
    }
    
    private static float[] pitch = new float[] {90, -90, 0, 0, 0, 0, 0};
    private static float[] yaw = new float[] {0, 0, 180, 0, 90, -90, 0};
    
    protected EntityPlayer getFakePlayer() {
        EntityPlayer player = PlayerUtil.makePlayer(getCoord(), "socket");
        player.worldObj = worldObj;
        player.prevPosX = player.posX = pos.getX() + 0.5 + facing.getDirectionVec().getX();
        player.prevPosY = player.posY = pos.getY() + 0.5 - player.getEyeHeight() + facing.getDirectionVec().getY();
        player.prevPosZ = player.posZ = pos.getZ() + 0.5 + facing.getDirectionVec().getZ();
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            player.inventory.mainInventory[i] = null;
        }
        
        int i = facing.ordinal();
        player.rotationPitch = player.prevRotationPitch = pitch[i];
        player.rotationYaw = player.prevRotationYaw = yaw[i];
        player.limbSwingAmount = 0;
        
        return player;
    }
    
    protected IInventory getBackingInventory(ISocketHolder socket) {
        if (socket == this) {
            TileEntity te = worldObj.getTileEntity(pos.subtract(facing.getDirectionVec()));
            if (te instanceof IInventory) {
                return (IInventory) te;
            }
            return null;
        } else if (socket instanceof IInventory) {
            return (IInventory) socket;
        }
        return null;
    }

    @Override
    public Vec3 getServoPos() {
        return getCoord().createVector();
    }

    //Overridable code
    
    public void genericUpdate(ISocketHolder socket, Coord coord, boolean powered) { }
    
    @Override
    public abstract FactoryType getFactoryType();
    public abstract ItemStack getCreatingItem();
    public abstract FactoryType getParentFactoryType();
    
    @SideOnly(Side.CLIENT)
    @Override
    public boolean handleMessageFromServer(Enum messageType, ByteBuf input) throws IOException {
        if (super.handleMessageFromServer(messageType, input)) {
            return true;
        }
        if (messageType == StandardMessageType.OpenDataHelperGui) {
            if (!worldObj.isRemote) {
                return false;
            }
            DataInByteBuf dip = new DataInByteBuf(input, Side.CLIENT);
            serialize("", dip);
            Minecraft.getMinecraft().displayGuiScreen(new GuiDataConfig(this));
            return true;
        }
        return false;
    }
    
    @Override
    public boolean handleMessageFromClient(Enum messageType, ByteBuf input) throws IOException {
        if (super.handleMessageFromClient(messageType, input)) {
            return true;
        }
        if (messageType == StandardMessageType.DataHelperEdit) {
            DataInByteBufClientEdited di = new DataInByteBufClientEdited(input);
            this.serialize("", di);
            markDirty();
            return true;
        }
        return false;
    }
    
    /**
     * return true if mop-searching should stop
     */
    public boolean handleRay(ISocketHolder socket, MovingObjectPosition mop, World mopWorld, boolean mopIsThis, boolean powered) {
        return true;
    }
    
    /**
     * Called when the socket is removed from a servo motor
     */
    public void uninstall() { }
    
    @Override
    public boolean activate(EntityPlayer player, EnumFacing side) {
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() == Core.registry.logicMatrixProgrammer) {
            if (!getFactoryType().hasGui) {
                if (getClass() == SocketEmpty.class) {
                    facing = FzUtil.shiftEnum(facing, EnumFacing.VALUES, 1);
                    if (worldObj.isRemote) {
                        new Coord(this).redraw();
                    }
                    return true;
                }
                return false;
            }
            if (worldObj.isRemote) {
                return true;
            }
            ByteBuf buf = Unpooled.buffer();
            DataOutByteBuf dop = new DataOutByteBuf(buf, Side.SERVER);
            try {
                Core.network.prefixTePacket(buf, this, StandardMessageType.OpenDataHelperGui);
                serialize("", dop);
                Coord coord = getCoord();
                Core.network.broadcastPacket(player, coord, FzNetDispatch.generate(buf));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        } else if (held != null) {
            boolean isValidItem = false;
            if (ItemUtil.identical(getCreatingItem(), held)) return false;
            for (FactoryType ft : FactoryType.values()) {
                TileEntityCommon tec = ft.getRepresentative();
                if (tec == null) continue;
                if (!(tec instanceof TileEntitySocketBase)) continue;
                TileEntitySocketBase rep = (TileEntitySocketBase) tec;
                final ItemStack creator = rep.getCreatingItem();
                if (creator != null && ItemUtil.couldMerge(held, creator)) {
                    isValidItem = true;
                    if (worldObj.isRemote) {
                        break;
                    }
                    if (rep.getParentFactoryType() != getFactoryType()) {
                        rep.mentionPrereq(this, player);
                        return false;
                    }
                    TileEntitySocketBase upgrade = (TileEntitySocketBase) ft.makeTileEntity();
                    if (upgrade == null) continue;
                    upgrade.addPart(this.parts, held);
                    replaceWith(upgrade, this);
                    upgrade.installedOnSocket();
                    PlayerUtil.cheatDecr(player, held);
                    Sound.socketInstall.playAt(this);
                    return true;
                }
            }
            if (isValidItem) {
                return false;
            }
        }
        return false;
    }

    public void addPart(ItemStack[] base, ItemStack next) {
        int needed = next == null ? 0 : 1;
        for (ItemStack is : base) {
            if (is != null) needed++;
        }
        if (parts.length < needed) parts = new ItemStack[needed];
        int i = 0;
        for (ItemStack is : base) {
            if (is != null) parts[i++] = is;
        }
        if (next != null) {
            next = next.copy();
            next.stackSize = 1;
            parts[i++] = next;
        }
    }
    
    public void mentionPrereq(ISocketHolder holder, EntityPlayer player) {
        FactoryType pft = getParentFactoryType();
        if (pft == null) return;
        TileEntityCommon tec = pft.getRepresentative();
        if (!(tec instanceof TileEntitySocketBase)) return;
        ItemStack is = ((TileEntitySocketBase) tec).getCreatingItem();
        if (is == null) return;
        String msg = "Needs {ITEM_NAME}";
        new Notice(holder, msg).withItem(is).sendTo(player);
    }
    
    protected void replaceWith(TileEntitySocketBase replacement, ISocketHolder socket) {
        replacement.facing = facing;
        if (socket == this) {
            Coord at = getCoord();
            at.removeTE();
            at.setTE(replacement);
            at.syncAndRedraw();
            System.out.println("Became " + at.getTE());
        } else if (socket instanceof ServoMotor) {
            invalidate();
            ServoMotor motor = (ServoMotor) socket;
            motor.socket = replacement;
            motor.syncWithSpawnPacket();
        }
    }
    
    public boolean activateOnServo(EntityPlayer player, ServoMotor motor) {
        if (worldObj == null /* wtf? */ || worldObj.isRemote) {
            return false;
        }
        ItemStack held = player.getHeldItem();
        if (held == null) {
            return false;
        }
        if (held.getItem() != Core.registry.logicMatrixProgrammer) {
            return false;
        }
        if (!getFactoryType().hasGui) {
            return false;
        }
        ByteBuf buf = Unpooled.buffer();
        DataOutByteBuf dop = new DataOutByteBuf(buf, Side.SERVER);
        try {
            Coord coord = getCoord();
            Core.network.prefixEntityPacket(buf, motor, StandardMessageType.OpenDataHelperGuiOnEntity);
            serialize("", dop);
            Core.network.broadcastPacket(player, coord, Core.network.entityPacket(buf));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    public void onEnterNewBlock() { }
    
    @SideOnly(Side.CLIENT)
    public void renderTesr(@Nullable ServoMotor motor, float partial) {
        final Minecraft mc = Minecraft.getMinecraft();
        final RenderItem ri = mc.getRenderItem();
        RenderUtil.scale3(2);
        GL11.glPushMatrix();
        GL11.glTranslated(0.25, 0.25, 0.25);
        for (int i = 0; i < parts.length; i++) {
            ItemStack part = parts[i];
            if (part == null) continue;
            if (motor == null && isStaticPart(i, part)) continue;
            GL11.glPushMatrix();
            stateForPart(i, part, partial);
            ri.renderItem(part, ItemCameraTransforms.TransformType.NONE);
            GL11.glPopMatrix();
        }
        GL11.glPopMatrix();
    }

    @SideOnly(Side.CLIENT)
    public void stateForPart(int index, ItemStack is, float partial) { }

    @SideOnly(Side.CLIENT)
    public void renderInServo(ServoMotor motor, float partial) {
        float s = 12F/16F;
        GL11.glScalef(s, s, s);
        float d = -0.5F;
        float y = -2F/16F;
        GL11.glTranslatef(d, y, d);
        
        GL11.glDisable(GL_LIGHTING);
        GL11.glPushMatrix();
        renderTesr(motor, partial);
        GL11.glPopMatrix();

        // NORELEASE: Need a servo component registry...
        GL11.glTranslatef(-d, -y, -d);
        GL11.glEnable(GL_LIGHTING);
    }
    
    @SideOnly(Side.CLIENT)
    public void renderItemOnServo(RenderServoMotor render, ServoMotor motor, ItemStack is, float partial) {
        GL11.glPushMatrix();
        GL11.glTranslatef(6.5F/16F, 4.5F/16F, 0);
        GL11.glRotatef(90, 0, 1, 0);
        render.renderItem(is);
        GL11.glPopMatrix();
    }

    public void installedOnServo(ServoMotor servoMotor) { }

    public void installedOnSocket() { }

    @Override
    public IWorkerContext getContext() {
        return new ContextTileEntity(this, facing.getOpposite(), null);
    }

    @Override
    public void validate() {
        super.validate();
        onLoaded(this);
    }

    public void onLoaded(ISocketHolder holder) {

    }

    public boolean isStaticPart(int index, ItemStack part) {
        return true;
    }
}
