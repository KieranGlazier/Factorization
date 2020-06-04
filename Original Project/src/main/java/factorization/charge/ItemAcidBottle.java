package factorization.charge;

import factorization.shared.Core;
import factorization.shared.Core.TabType;
import factorization.shared.ISensitiveMesh;
import factorization.shared.ItemFactorization;
import factorization.shared.Sound;
import factorization.util.InvUtil;
import factorization.util.ItemUtil;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.util.List;

public class ItemAcidBottle extends ItemFactorization implements ISensitiveMesh {

    public ItemAcidBottle() {
        super("acid", TabType.CHARGE);
        setMaxStackSize(16);
        setHasSubtypes(true);
    }

    @Override
    public int getColorFromItemStack(ItemStack stack, int renderPass) {
        if (renderPass == 0) {
            if (stack.getItemDamage() > 0) {
                return 0xFBD9B7;
            }
            return 0xB7EBFB;
        }
        return super.getColorFromItemStack(stack, renderPass);
    }
    
    @Override
    public String getUnlocalizedName(ItemStack stack) {
        String name = super.getUnlocalizedName(stack);
        if (stack.getItemDamage() > 0) {
            name += "_regia";
        }
        return name;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack par1ItemStack) {
        return 32;
    }

    @Override
    public EnumAction getItemUseAction(ItemStack par1ItemStack) {
        return EnumAction.DRINK;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack is, World w, EntityPlayer player) {
        player.setItemInUse(is, getMaxItemUseDuration(is));
        return is;
    }

    static public DamageSource acidDrinker = new AcidDamage();

    @Override
    public String getMeshName(@Nonnull ItemStack is) {
        return is.getItemDamage() > 0 ? "aquaRegia" : "sulfuricAcid";
    }

    @Nonnull
    @Override
    public List<ItemStack> getMeshSamples() {
        return ItemUtil.getSubItems(this);
    }

    static class AcidDamage extends DamageSource {

        protected AcidDamage() {
            super("acidDrinker");
            setDamageBypassesArmor();
        }
    }

    @Override
    public ItemStack onItemUseFinish(ItemStack is, World w, EntityPlayer player) {
        is.stackSize--;
        Sound.acidBurn.playAt(player);
        if (w.isRemote) {
            return is;
        }
        player.attackEntityFrom(acidDrinker, is.getItemDamage() > 0 ? 15 : 10);
        player.getFoodStats().addStats(-20, 0);
        ItemStack bottle = new ItemStack(Items.glass_bottle);
        ItemUtil.setLore(bottle, "I drank acid and\nall I got was this\nlousy bottle!".split("\n"));
        InvUtil.givePlayerItem(player, bottle);
        return is;
    }

    @Override
    public void getSubItems(Item id, CreativeTabs tab, List list) {
        super.getSubItems(id, tab, list);
        list.add(Core.registry.aqua_regia);
    }
}
