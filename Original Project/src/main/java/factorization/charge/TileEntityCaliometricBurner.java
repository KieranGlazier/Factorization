package factorization.charge;

import com.google.common.collect.HashMultimap;
import factorization.api.Coord;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.Share;
import factorization.common.FactoryType;
import factorization.notify.Notice;
import factorization.shared.BlockClass;
import factorization.shared.Sound;
import factorization.shared.TileEntityFactorization;
import factorization.util.DataUtil;
import factorization.util.InvUtil;
import factorization.util.ItemUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

import java.io.IOException;

public class TileEntityCaliometricBurner extends TileEntityFactorization {
    public static class FoodInfo {
        public final ItemStack match;
        public final int heal;
        public final double sat;

        public FoodInfo(ItemStack match, int heal, double sat) {
            this.match = match;
            this.heal = heal;
            this.sat = sat;
        }
    }

    public static void register(ItemStack food, int heal, double sat) {
        special_foods.put(food.getItem(), new FoodInfo(food, heal, sat));
    }

    public static void register(Item food, int heal, double sat) {
        special_foods.put(food, new FoodInfo(ItemUtil.makeWildcard(food), heal, sat));
    }

    public static FoodInfo lookup(ItemStack food) {
        if (food == null) return null;
        for (FoodInfo info : special_foods.get(food.getItem())) {
            if (info.match == null) return info;
            if (ItemUtil.wildcardSimilar(info.match, food)) return info;
        }
        return null;
    }

    private static final HashMultimap<Item, FoodInfo> special_foods = HashMultimap.create();
    static {
        register(DataUtil.getItem(Blocks.cake), 12, 0.1);
    }

    ItemStack stomache;
    int foodQuality = 0;
    int ticksUntilNextDigestion = 0;
    
    @Override
    public FactoryType getFactoryType() {
        return FactoryType.CALIOMETRIC_BURNER;
    }

    @Override
    public BlockClass getBlockClass() {
        return BlockClass.Machine;
    }

    @Override
    public void putData(DataHelper data) throws IOException {
        super.putData(data);
        stomache = data.as(Share.PRIVATE, "stomache").putItemStack(stomache);
        foodQuality = data.as(Share.PRIVATE, "food").putInt(foodQuality);
        ticksUntilNextDigestion = data.as(Share.PRIVATE, "digest").putInt(ticksUntilNextDigestion);
    }
    
    @Override
    public int getSizeInventory() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        if (i == 0) {
            return stomache;
        }
        return null;
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack itemstack) {
        if (i == 0) {
            stomache = itemstack;
        }
    }
    
    @Override
    public int getInventoryStackLimit() {
        return 4;
    }

    @Override
    public boolean isItemValidForSlot(int i, ItemStack itemstack) {
        if (itemstack == null) {
            return false;
        }
        return getFoodValue(itemstack) > 0;
    }

    private static final int[] nomslots = new int[] {0}, emptySlots = new int[] {};

    @Override
    public int[] getSlotsForFace(EnumFacing side) {
        if (side == null || side.getDirectionVec().getY() != 0) {
            return emptySlots; //Food goes in through the teeth
        }
        return nomslots;
    }

    @Override
    protected void doLogic() {
        needLogic();
        Coord here = getCoord();
        if (ticksUntilNextDigestion > 0 && foodQuality > 0) {
            for (Coord c : here.getRandomNeighborsAdjacent()) {
                TileEntitySolarBoiler boiler = c.getTE(TileEntitySolarBoiler.class);
                if (boiler == null) {
                    continue;
                }
                boiler.applyHeat(foodQuality);
                break;
            }
        }
        ticksUntilNextDigestion--;
        if (ticksUntilNextDigestion <= 0 && !here.isPowered()) {
            foodQuality = consumeFood();
        }
    }
    
    @Override
    protected int getLogicSpeed() {
        return 1;
    }
    
    int consumeFood() {
        stomache = ItemUtil.normalize(stomache);
        if (stomache == null) {
            return 0;
        }
        int noms = getFoodValue(stomache);
        stomache = ItemUtil.normalDecr(stomache);
        markDirty();
        Sound.caliometricDigest.playAt(this);
        ticksUntilNextDigestion = 20*10*noms;
        return 16;
    }
    
    int getFoodValue(ItemStack is) {
        if (is == null) {
            return 0;
        }
        Item it = is.getItem();
        int heal = 0;
        double sat = 0;

        FoodInfo fi = lookup(is);
        if (fi != null) {
            heal = fi.heal;
            sat = fi.sat;
        } else if (it instanceof ItemFood) {
            ItemFood nom = (ItemFood) it;
            heal = nom.getHealAmount(is);
            sat = nom.getSaturationModifier(is);
        }
        if (heal < sat) {
            double swapah = heal;
            heal = (int) sat;
            sat = swapah;
        }
        heal += Math.min(0, heal*2*sat);
        int r = (int)(heal*(heal/4F));
        return Math.max(heal, r);
    }
    
    @Override
    public boolean activate(EntityPlayer entityplayer, EnumFacing side) {
        if (worldObj.isRemote) {
            return true;
        }
        ItemStack is = entityplayer.getHeldItem();
        if (is == null) {
            info(entityplayer);
            return false;
        }
        is = InvUtil.openInventory(this, EnumFacing.NORTH).push(is);
        entityplayer.setCurrentItemOrArmor(0, is);
        info(entityplayer);
        markDirty();
        return true;
    }
    
    void info(EntityPlayer entityplayer) {
        String append = "";
        if (ticksUntilNextDigestion > 0) {
            int n = (ticksUntilNextDigestion/20);
            int min = n/60;
            int s = n % 60;
            append = "\n" + min + ":";
            if (s < 10) {
                append += "0";
            }
            append += s;
        }
        boolean any = false;
        for (Coord c : getCoord().getRandomNeighborsAdjacent()) {
            TileEntitySolarBoiler boiler = c.getTE(TileEntitySolarBoiler.class);
            if (boiler != null) {
                any = true;
                break;
            }
        }
        if (!any) {
            append += "\n" + "No adjacent boiler!";
        }
        if (stomache == null || stomache.stackSize == 0) {
            new Notice(this, "Empty" + append).send(entityplayer);
            return;
        }
        new Notice(this, stomache.stackSize + " {ITEM_NAME}" + append).withItem(stomache).send(entityplayer);
    }

    @Override
    public boolean canExtractItem(int index, ItemStack stack, EnumFacing direction) {
        return false;
    }

    @Override
    public void clear() {
        stomache = null;
        foodQuality = 0;
        ticksUntilNextDigestion = 0;
    }
}
