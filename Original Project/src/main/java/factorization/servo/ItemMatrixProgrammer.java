package factorization.servo;

import factorization.api.Coord;
import factorization.api.FzColor;
import factorization.notify.Notice;
import factorization.notify.Style;
import factorization.shared.Core;
import factorization.shared.Core.TabType;
import factorization.shared.ISensitiveMesh;
import factorization.shared.ItemFactorization;
import factorization.util.FzUtil;
import factorization.util.ItemUtil;
import factorization.util.PlayerUtil;
import factorization.util.StatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatisticsFile;
import net.minecraft.tileentity.TileEntityNote;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;

public class ItemMatrixProgrammer extends ItemFactorization implements ISensitiveMesh {
    public ItemMatrixProgrammer() {
        super("tool.matrix_programmer", TabType.TOOLS);
        setMaxStackSize(1);
        setContainerItem(this);
        Core.loadBus(this);
    }
    
    // NORELEASE? Missing doesContainerItemLeaveCraftingGrid

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ) {
        Coord c = new Coord(world, pos);
        TileEntityNote noteBlock = c.getTE(TileEntityNote.class);
        if (noteBlock != null) {
            if (world.isRemote) return false;
            byte orig_note = noteBlock.note;
            int delta = player.isSneaking() ? -1 : 1;
            byte new_note = (byte) (orig_note + delta);
            if (new_note < 0) {
                new_note = 24;
            } else if (new_note > 24) {
                new_note = 0;
            }
            noteBlock.note = new_note;
            if (ForgeHooks.onNoteChange(noteBlock, orig_note)) {
                noteBlock.markDirty();
            }
            if (noteBlock.note != orig_note) {
                noteBlock.triggerNote(world, pos);
            }
            new Notice(noteBlock, "noteblock.pitch." + noteBlock.note).withStyle(Style.EXACTPOSITION).send(player);
            return true;
        }
        /*if (!player.isSneaking()) {
            if (Core.dev_environ && !world.isRemote) {
            }
            return false;
        }*/
        TileEntityServoRail rail = c.getTE(TileEntityServoRail.class);
        if (rail == null) {
            return false;
        }
        rail.priority = 0;
        Decorator decor = rail.getDecoration();
        if (player.isSneaking()) {
            if (decor == null) {
                if (rail.color != FzColor.NO_COLOR) {
                    rail.color = FzColor.NO_COLOR;
                    c.redraw();
                    c.syncTE();
                }
                return false;
            }
            if (!decor.isFreeToPlace() && !player.capabilities.isCreativeMode && !world.isRemote) {
                c.spawnItem(decor.toItem());
            }
            rail.setDecoration(null);
            c.redraw();
            return false;
        }
        return false;
    }

    @Override
    public boolean isItemTool(ItemStack is) {
        return true;
    }
    
    @SubscribeEvent
    public void clickPainting(EntityInteractEvent event) {
        ItemStack is = event.entityPlayer.getHeldItem();
        if (is == null) return;
        if (is.getItem() != this) return;
        if (!(event.target instanceof EntityPainting)) {
            return;
        }
        int d = event.entityPlayer.isSneaking() ? -1 : 1;
        EntityPainting painting = (EntityPainting) event.target;
        if (painting.worldObj.isRemote) {
            return;
        }
        EntityPainting.EnumArt origArt = painting.art;
        EntityPainting.EnumArt art = origArt;
        EnumFacing hangingDirection = painting.facingDirection;
        while (true) {
            art = FzUtil.shiftEnum(art, EntityPainting.EnumArt.values(), d);
            painting.facingDirection = hangingDirection;
            painting.art = art;
            if (art == origArt) {
                return;
            }
            if (!painting.onValidSurface()) {
                continue;
            }
            NBTTagCompound save = new NBTTagCompound();
            painting.writeToNBTOptional(save);
            painting.setDead();
            Entity newPainting = EntityList.createEntityFromNBT(save, event.target.worldObj);
            newPainting.worldObj.spawnEntityInWorld(newPainting);
            break;
        }
    }

    @Override
    public boolean isValidArmor(ItemStack stack, int armorType, Entity entity) {
        return armorType == 0 /* Should be head slot */ && entity instanceof EntityPlayer;
        // Our rendering code only works on players!
    }

    // I vaguely want to be able to do: onArmorTick: set MD = 1, on other tick if not armor: set MD = 0.
    // And this would control the mask-mode.
    // But there's no, like, 'onArmorRemoved' event.

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && player.getCurrentArmor(3 /* Should also be head slot. Notice how it's different from the 0 above? */) == null) {
            if (isBowed(player)) {
                ItemStack mask = stack.splitStack(1);
                player.setCurrentItemOrArmor(4 /* Oh, look at that! An entirely different number for the head slot! */, mask);
                return stack;
            }
        }
        return super.onItemRightClick(stack, world, player);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void removeMask(PlayerInteractEvent event) {
        if (event.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) return;
        final EntityPlayer player = event.entityPlayer;
        if (player.worldObj.isRemote) return;
        if (!player.isSneaking()) return;
        if (player.getHeldItem() != null) return;
        final ItemStack helmet = player.getCurrentArmor(3);
        if (helmet == null || !(helmet.getItem() == this)) return;
        if (!isBowed(player)) return;

        player.setCurrentItemOrArmor(4, null);
        player.setCurrentItemOrArmor(0, helmet);
        event.setCanceled(true);
        Core.proxy.updatePlayerInventory(player); // Only seems necessary for removal specifically.
    }

    boolean isBowed(EntityPlayer player) {
        return player.rotationPitch > 75 && player.isSneaking();
    }

    @Override
    public String getMeshName(@Nonnull ItemStack is) {
        int itemDamage = is.getItemDamage();
        if (itemDamage == 101) return "tool/matrix_programmer_mask";
        if (itemDamage == 100) return "tool/matrix_programmer";
        EntityPlayer player = Core.proxy.getClientPlayer();
        if (player != null) {
            // We do in fact want Object identity here; there could be multiple LMPs floating around.
            if (is == player.getHeldItem() && isBowed(player)) return "tool/matrix_programmer_mask";
            if (is == player.getCurrentArmor(3)) return "tool/matrix_programmer_mask";
        }
        return "tool/matrix_programmer";
    }

    @Nonnull
    @Override
    public List<ItemStack> getMeshSamples() {
        return Arrays.asList(new ItemStack(this, 1, 100), new ItemStack(this, 1, 101));
    }
}
