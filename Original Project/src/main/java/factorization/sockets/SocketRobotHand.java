package factorization.sockets;

import factorization.api.Coord;
import factorization.api.FzOrientation;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.IDataSerializable;
import factorization.api.datahelpers.Share;
import factorization.common.FactoryType;
import factorization.fzds.DeltaChunk;
import factorization.fzds.HammerEnabled;
import factorization.servo.RenderServoMotor;
import factorization.servo.ServoMotor;
import factorization.shared.Core;
import factorization.util.InvUtil;
import factorization.util.InvUtil.FzInv;
import factorization.util.ItemUtil;
import factorization.util.PlayerUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

public class SocketRobotHand extends TileEntitySocketBase {
    boolean wasPowered = false;
    boolean firstTry = false;
    @Override
    public FactoryType getFactoryType() {
        return FactoryType.SOCKET_ROBOTHAND;
    }
    
    @Override
    public FactoryType getParentFactoryType() {
        return FactoryType.SOCKET_EMPTY;
    }
    
    @Override
    public ItemStack getCreatingItem() {
        return new ItemStack(Core.registry.socket_robot_hand);
    }

    @Override
    public IDataSerializable serialize(String prefix, DataHelper data) throws IOException {
        wasPowered = data.as(Share.PRIVATE, "pow").putBoolean(wasPowered);
        return this;
    }

    FzInv backingInventory;

    @Override
    public void genericUpdate(ISocketHolder socket, Coord coord, boolean powered) {
        if (worldObj.isRemote) {
            return;
        }
        if (wasPowered || !powered) {
            wasPowered = powered;
            return;
        }
        wasPowered = true;
        firstTry = true;
        FzOrientation orientation = FzOrientation.fromDirection(facing).getSwapped();
        fakePlayer = null;
        backingInventory = InvUtil.openInventory(getBackingInventory(socket), facing);
        RayTracer tracer = new RayTracer(this, socket, coord, orientation, powered).lookAround().checkEnts().checkFzdsFirst();
        tracer.trace();
        if (fakePlayer != null) {
            PlayerUtil.recycleFakePlayer(fakePlayer);
        }
        fakePlayer = null;
        backingInventory = null;
    }
    
    EntityPlayer fakePlayer;
    
    @Override
    public boolean handleRay(ISocketHolder socket, MovingObjectPosition mop, World mopWorld, boolean mopIsThis, boolean powered) {
        boolean ret = doHandleRay(socket, mop, mopWorld, mopIsThis, powered);
        if (!ret && !mopIsThis
                && mop.typeOfHit == MovingObjectType.BLOCK
                && (!HammerEnabled.ENABLED || worldObj != DeltaChunk.getServerShadowWorld())) {
            return !worldObj.isAirBlock(mop.getBlockPos());
        }
        return ret;
    }
    
    private boolean doHandleRay(ISocketHolder socket, MovingObjectPosition mop, World mopWorld, boolean mopIsThis, boolean powered) {
        if (fakePlayer == null) {
            fakePlayer = getFakePlayer();
        } else if (fakePlayer.worldObj != worldObj) {
            fakePlayer.isDead = true;
            fakePlayer = getFakePlayer();
        }
        EntityPlayer player = fakePlayer;
        if (backingInventory == null) {
            return clickWithoutInventory(player, mop);
        }
        boolean foundAny = false;
        for (int i = 0; i < backingInventory.size(); i++) {
            ItemStack is = backingInventory.get(i);
            if (is == null || is.stackSize <= 0 || !backingInventory.canExtract(i, is)) {
                continue;
            }
            player.inventory.mainInventory[0] = is;
            foundAny = true;
            if (clickWithInventory(i, backingInventory, player, is, mop)) {
                return true;
            }
        }
        if (!foundAny) {
            return clickWithoutInventory(player, mop);
        }
        return false;
    }
    
    private boolean clickWithoutInventory(EntityPlayer player, MovingObjectPosition mop) {
        return clickItem(player, null, mop);
    }

    boolean clickWithInventory(int i, FzInv inv, EntityPlayer player, ItemStack is, MovingObjectPosition mop) {
        ItemStack orig = is == null ? null : is.copy();
        boolean result = clickItem(player, is, mop);
        firstTry = false;
        int newSize = ItemUtil.getStackSize(is);
        is = player.inventory.mainInventory[0];
        // Easiest case: the item is all used up.
        // Worst case: barrel of magic jumping beans that change color.
        // (Or more realistically, a barrel of empty buckets for milking a cow...)
        // To handle: extract the entire stack. It is lost. Attempt to stuff the rest of the inv back in.
        // Anything that can't be stuffed gets dropped on the ground.
        // This could break with funky items/inventories tho.
        if (newSize <= 0 || !ItemUtil.couldMerge(orig, is)) {
            inv.set(i, null); //Bye-bye!
            if (newSize > 0) {
                is = inv.pushInto(i, is);
                if (is == null || is.stackSize <= 0) {
                    player.inventory.mainInventory[0] = null;
                }
            }
        } else {
            // We aren't calling inv.decrStackInSlot.
            inv.set(i, is);
            player.inventory.mainInventory[0] = null;
        }
        Coord here = null;
        for (int j = 0; j < player.inventory.getSizeInventory(); j++) {
            ItemStack toPush = player.inventory.getStackInSlot(j);
            ItemStack toDrop = inv.push(toPush);
            if (toDrop != null && toDrop.stackSize > 0) {
                if (here == null) here = getCoord();
                here.spawnItem(toDrop);
            }
            player.inventory.setInventorySlotContents(j, null);
        }
        inv.onInvChanged();
        return result;
    }
    
    boolean clickItem(EntityPlayer player, ItemStack is, MovingObjectPosition mop) {
        try {
            if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                return mcClick(player, mop, is);
            } else if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
                if (player.interactWith(mop.entityHit)) {
                    return true;
                }
            }
        } catch (Throwable t) {
            CrashReport err = new CrashReport("clicking item", t);
            CrashReportCategory cat = err.makeCategory("clicked item");
            cat.addCrashSection("Item", is);
            cat.addCrashSection("Mop", mop);
            throw new ReportedException(err);
        }
        return false;
    }
    
    boolean mcClick(EntityPlayer player, MovingObjectPosition mop, ItemStack itemstack) {
        //Yoinked and cleaned up from Minecraft.clickMouse and PlayerControllerMP.onPlayerRightClick
        final World world = player.worldObj;
        BlockPos pos = mop.getBlockPos();
        EnumFacing side = mop.sideHit;
        final Vec3 hitVec = mop.hitVec;
        final float dx = (float)hitVec.xCoord - (float)pos.getX();
        final float dy = (float)hitVec.yCoord - (float)pos.getY();
        final float dz = (float)hitVec.zCoord - (float)pos.getZ();
        final Item item = itemstack == null ? null : itemstack.getItem();
        final long origItemHash = ItemUtil.getItemHash(itemstack);

        PlayerInteractEvent event = new PlayerInteractEvent(player, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, pos, side, world);
        if (MinecraftForge.EVENT_BUS.post(event)) {
            return false;
        }
        
        boolean ret = false;
        do {
            //PlayerControllerMP.onPlayerRightClick
            if (firstTry && itemstack != null) {
                ItemStack orig = itemstack.copy();
                if (item.onItemUseFirst(itemstack, player, world, pos, side, dx, dy, dz)) {
                    ret = true;
                    break;
                }
                if (!ItemUtil.identical(itemstack, orig)) {
                    ret = true;
                    break;
                }
            }
            
            if (!player.isSneaking() || itemstack == null || item.doesSneakBypassUse(world, pos, player)) {
                IBlockState blockId = world.getBlockState(pos);
            
                if (blockId != null && blockId.getBlock().onBlockActivated(world, pos, blockId, player, side, dx, dy, dz)) {
                    ret = true;
                    break;
                }
            }
            if (itemstack == null) {
                ret = false;
                break;
            }
            ret = itemstack.onItemUse(player, world, pos, side, dx, dy, dz);
            break;
        } while (false);
        if (itemstack == null) {
            return ret;
        }
        int origSize = ItemUtil.getStackSize(itemstack);
        ItemStack held = player.getHeldItem();
        if (held != itemstack) {
            return true;
        }
        ItemStack mutatedItem = itemstack.useItemRightClick(world, player);
        ret = ret
                || mutatedItem != itemstack
                || origSize != ItemUtil.getStackSize(mutatedItem)
                || !ItemUtil.identical(mutatedItem, itemstack)
                || origItemHash != ItemUtil.getItemHash(mutatedItem);
        player.inventory.mainInventory[player.inventory.currentItem] = mutatedItem;
        return ret;
    }

    @Override
    public boolean activate(EntityPlayer entityplayer, EnumFacing side) {
        if (worldObj.isRemote) {
            return false;
        }
        /*if (getBackingInventory(this) == null) {
            Notify.send(this, "Missing inventory block");
        }*/
        return false;
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public void renderItemOnServo(RenderServoMotor render, ServoMotor motor, ItemStack is, float partial) {
        GL11.glPushMatrix();
        GL11.glTranslatef(-1F/16F, 12F/16F, 0);
        GL11.glRotatef(90, 0, 1, 0);
        GL11.glRotatef(45, 1, 0, 0);
        render.renderItem(is);
        GL11.glPopMatrix();
    }
}
