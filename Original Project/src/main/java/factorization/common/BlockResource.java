package factorization.common;

import factorization.shared.Core;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;

import java.util.List;

public class BlockResource extends Block {
    public static final IProperty<ResourceType> TYPE = PropertyEnum.create("type", ResourceType.class,
            ResourceType.COPPER_ORE,
            ResourceType.DARK_IRON_BLOCK,
            ResourceType.COPPER_BLOCK);

    protected BlockResource() {
        super(Material.rock);
        setHardness(2.0F);
        setUnlocalizedName("factorization.ResourceBlock");
    }

    @Override
    protected BlockState createBlockState() {
        return new BlockState(this, TYPE);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(TYPE).ordinal();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        if (meta < 0 || meta >= ResourceType.values.length) return getDefaultState();
        ResourceType value = ResourceType.values[meta];
        if (value.isValid()) {
            return getDefaultState().withProperty(TYPE, value);
        }
        return getDefaultState();
    }

    public void addCreativeItems(List<ItemStack> itemList) {
        itemList.add(Core.registry.copper_ore_item);
        itemList.add(Core.registry.copper_block_item);
        itemList.add(Core.registry.dark_iron_block_item);
    }
    
    @Override
    public void getSubBlocks(Item par1, CreativeTabs par2CreativeTabs, List<ItemStack> itemList) {
        addCreativeItems(itemList);
    }

    @Override
    public int damageDropped(IBlockState state) {
        return state.getValue(TYPE).ordinal();
    }

    @Override
    public boolean isBeaconBase(IBlockAccess world, BlockPos pos, BlockPos beacon) {
        IBlockState bs = world.getBlockState(pos);
        return bs.getValue(TYPE).isMetal();
    }
}
