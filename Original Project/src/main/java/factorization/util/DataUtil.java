package factorization.util;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StringUtils;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.registry.GameData;

import java.io.DataInput;
import java.io.IOException;
import java.util.Map;

public final class DataUtil {
    public static final ItemStack NULL_ITEM = new ItemStack((Item) null, 0, 0); // Forge may throw a huge hissy fit over this at some point.
    private static final Joiner COMMA_JOINER = Joiner.on(',');
    private static final Function<Map.Entry<IProperty, Comparable>, String> MAP_ENTRY_TO_STRING = new Function<Map.Entry<IProperty, Comparable>, String>() {
        public String apply(Map.Entry<IProperty, Comparable> entry) {
            if (entry == null) {
                return "<NULL>";
            } else {
                IProperty iproperty = entry.getKey();
                return iproperty.getName() + "=" + iproperty.getName(entry.getValue());
            }
        }
    };

    public static void writeTank(NBTTagCompound tag, FluidTank tank, String name) {
        FluidStack ls = tank.getFluid();
        if (ls == null) {
            return;
        }
        NBTTagCompound liquid_tag = new NBTTagCompound();
        ls.writeToNBT(liquid_tag);
        tag.setTag(name, liquid_tag);
    }

    public static void readTank(NBTTagCompound tag, FluidTank tank, String name) {
        NBTTagCompound liquid_tag = tag.getCompoundTag(name);
        FluidStack ls = FluidStack.loadFluidStackFromNBT(liquid_tag);
        tank.setFluid(ls);
    }

    static public NBTTagCompound readTag(DataInput input, NBTSizeTracker tracker) throws IOException {
        return CompressedStreamTools.read(input, tracker);
    }

    static public ItemStack readStack(DataInput input, NBTSizeTracker tracker) throws IOException {
        ItemStack is = ItemStack.loadItemStackFromNBT(readTag(input, tracker));
        if (is == null || is.getItem() == null) {
            return null;
        }
        return is;
    }

    public static int MAX_TAG_SIZE = 20 * 1024; // NORELEASE: JVM option?

    public static NBTSizeTracker newTracker() {
        return new NBTSizeTracker(MAX_TAG_SIZE);
    }

    public static NBTTagCompound readTag(DataInput input) throws IOException {
        return readTag(input, newTracker());
    }

    public static ItemStack readStack(DataInput input) throws IOException {
        return readStack(input, newTracker());
    }

    static public NBTTagCompound readTag(ByteBuf input) throws IOException {
        return ByteBufUtils.readTag(input);
    }

    static public ItemStack readStack(ByteBuf input) throws IOException {
        return ByteBufUtils.readItemStack(input);
    }


    public static NBTTagCompound item2tag(ItemStack is) {
        NBTTagCompound tag = new NBTTagCompound();
        is.writeToNBT(tag);
        tag.removeTag("id");
        String name = getName(is);
        if (name != null) {
            tag.setString("name", name);
        }
        return tag;
    }

    public static ItemStack tag2item(NBTTagCompound tag, ItemStack defaultValue) {
        if (tag == null || tag.hasNoTags()) return defaultValue.copy();
        if (tag.hasKey("id")) {
            // Legacy
            ItemStack is = ItemStack.loadItemStackFromNBT(tag);
            if (is == null) return defaultValue.copy();
            return is;
        }
        String itemName = tag.getString("name");
        if (StringUtils.isNullOrEmpty(itemName)) return defaultValue.copy();
        byte stackSize = tag.getByte("Count");
        short itemDamage = tag.getShort("Damage");
        Item it = getItemFromName(itemName);
        if (it == null) return defaultValue.copy();
        ItemStack ret = new ItemStack(it, stackSize, itemDamage);
        if (tag.hasKey("tag", Constants.NBT.TAG_COMPOUND)) {
            ret.setTagCompound(tag.getCompoundTag("tag"));
        }
        return ret;
    }

    public static TileEntity cloneTileEntity(TileEntity orig) {
        NBTTagCompound tag = new NBTTagCompound();
        orig.writeToNBT(tag);
        return TileEntity.createAndLoadEntity(tag);
    }

    public static Block getBlock(ItemStack is) {
        if (is == null) return null;
        return Block.getBlockFromItem(is.getItem());
    }

    public static Block getBlock(Item it) {
        return Block.getBlockFromItem(it);
    }

    public static Block getBlock(int id) {
        return Block.getBlockById(id);
    }

    public static Item getItem(int id) {
        return Item.getItemById(id);
    }

    public static Item getItem(Block block) {
        return Item.getItemFromBlock(block);
    }

    public static int getId(Block block) {
        return Block.getIdFromBlock(block);
    }

    public static int getId(Item it) {
        return Item.getIdFromItem(it);
    }

    public static int getId(ItemStack is) {
        if (is == null) return 0;
        return Item.getIdFromItem(is.getItem());
    }

    public static String getName(Item it) {
        if (it == null) return null;
        ResourceLocation nameForObject = GameData.getItemRegistry().getNameForObject(it);
        if (nameForObject == null) return null;
        return nameForObject.toString();
    }

    public static String getName(ItemStack is) {
        return getName(is == null ? null : is.getItem());
    }

    public static String getName(Block b) {
        return GameData.getBlockRegistry().getNameForObject(b).toString();
    }

    public static Block getBlockFromName(String blockName) {
        return Block.blockRegistry.getObject(new ResourceLocation(blockName));
    }

    public static Item getItemFromName(String itemName) {
        return Item.itemRegistry.getObject(new ResourceLocation(itemName));
    }

    public static ItemStack fromState(IBlockState bs) {
        return new ItemStack(bs.getBlock());
    }

    public static String getStatePropertyString(IBlockState state) {
        if (!state.getProperties().isEmpty()) {
            return COMMA_JOINER.join(Iterables.transform(state.getProperties().entrySet(), MAP_ENTRY_TO_STRING));
        } else {
            return "normal";
        }
    }

    public static <T extends Comparable<T>> T getOr(IBlockState bs, IProperty<T> property, T or) {
        Comparable got = bs.getProperties().get(property);
        if (got == null) return or;
        return (T) got;
    }
}
