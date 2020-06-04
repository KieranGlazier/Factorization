package factorization.weird.barrel;

import factorization.algos.ReservoirSampler;
import factorization.common.FactoryType;
import factorization.idiocy.DumbExtendedProperty;
import factorization.shared.BlockClass;
import factorization.shared.BlockFactorization;
import factorization.shared.Core;
import factorization.util.FzUtil;
import factorization.util.NORELEASE;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

public class BlockBarrel extends BlockFactorization {
    public static final Material materialBarrel = new Material(MapColor.woodColor) {{
        setAdventureModeExempt();
        NORELEASE.fixme("Test adventure mode barrel breaking");
    }};
    public BlockBarrel() {
        super(materialBarrel);
    }

    @Override
    public FactoryType getFactoryType(int md) {
        return FactoryType.DAYBARREL;
    }

    @Override
    public BlockClass getClass(IBlockAccess world, BlockPos pos) {
        return BlockClass.Barrel;
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return 0;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState();
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileEntityDayBarrel();
    }

    public static final IUnlistedProperty<BarrelCacheInfo> BARREL_INFO = new DumbExtendedProperty<BarrelCacheInfo>("info", BarrelCacheInfo.class);

    @Override
    protected BlockState createBlockState() {
        return new ExtendedBlockState(this, FzUtil.props(), FzUtil.uprops(BARREL_INFO));
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntityDayBarrel barrel = (TileEntityDayBarrel) world.getTileEntity(pos);
        IExtendedBlockState extendedBS = (IExtendedBlockState) super.getExtendedState(state, world, pos);
        if (barrel == null) {
            barrel = (TileEntityDayBarrel) FactoryType.DAYBARREL.getRepresentative();
            assert barrel != null;
            barrel.cleanBarrel();
        }
        return extendedBS.withProperty(BARREL_INFO, BarrelCacheInfo.from(barrel));
    }

    @Override
    public void getSubBlocks(Item me, CreativeTabs tab, List<ItemStack> itemList) {
        if (todaysBarrels != null) {
            itemList.addAll(todaysBarrels);
            return;
        }
        if (Core.registry.daybarrel == null) {
            return;
        }
        Calendar cal = Calendar.getInstance();
        int doy = cal.get(Calendar.DAY_OF_YEAR) - 1 /* start at 0, not 1 */;

        ReservoirSampler<ItemStack> barrelPool = new ReservoirSampler<ItemStack>(1, new Random(doy));
        todaysBarrels = new ArrayList<ItemStack>();

        for (ItemStack barrel : TileEntityDayBarrel.barrel_items) {
            TileEntityDayBarrel.Type type = TileEntityDayBarrel.getUpgrade(barrel);
            if (type == TileEntityDayBarrel.Type.NORMAL) {
                barrelPool.give(barrel);
            } else if (type == TileEntityDayBarrel.Type.CREATIVE) {
                todaysBarrels.add(barrel);
            }
        }

        TileEntityDayBarrel rep = new TileEntityDayBarrel();
        for (ItemStack barrel : barrelPool.getSamples()) {
            rep.loadFromStack(barrel);
            for (TileEntityDayBarrel.Type type : TileEntityDayBarrel.Type.values()) {
                if (type == TileEntityDayBarrel.Type.CREATIVE) continue;
                if (type == TileEntityDayBarrel.Type.LARGER) continue;
                rep.type = type;
                todaysBarrels.add(rep.getPickedBlock());
            }
        }
    }

    ArrayList<ItemStack> todaysBarrels = null;

    @Override
    public boolean canRenderInLayer(EnumWorldBlockLayer layer) {
        return layer == EnumWorldBlockLayer.TRANSLUCENT || layer == EnumWorldBlockLayer.SOLID;
    }
}
