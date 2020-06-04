package factorization.util;

import com.google.common.collect.Multimap;
import factorization.api.Coord;
import factorization.shared.Core;
import factorization.weird.barrel.TileEntityDayBarrel;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.BaseAttributeMap;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.ServersideAttributeMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class FzUtil {

    public static <E extends Enum> E shiftEnum(E current, E values[], int delta) { // 'cycleEnum'
        int next = current.ordinal() + delta;
        if (next < 0) {
            return values[values.length - 1];
        }
        if (next >= values.length) {
            return values[0];
        }
        return values[next];
    }
    
    
    //Liquid tank handling


    public static int getWorldDimension(World world) {
        return world.provider.getDimensionId();
    }

    public static World getWorld(int dimensionId) {
        return DimensionManager.getWorld(dimensionId);
    }

    @SideOnly(Side.CLIENT)
    public static void copyStringToClipboard(String text) {
        StringSelection stringselection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringselection, null);
    }
    
    public static <E> ArrayList<E> copyWithoutNull(Collection<E> orig) {
        ArrayList<E> ret = new ArrayList<E>();
        if (orig == null) return ret;
        for (E e : orig) {
            if (e != null) ret.add(e);
        }
        return ret;
    }

    public static void closeNoisily(String msg, InputStream is) {
        if (is == null) return;
        try {
            is.close();
        } catch (IOException e) {
            Core.logSevere(msg);
            e.printStackTrace();
        }
    }
    
    public static boolean stringsEqual(String a, String b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    public static void spawn(Entity ent) {
        if (ent == null) return;
        ent.worldObj.spawnEntityInWorld(ent);
    }

    public static double rateDamage(ItemStack is) {
        if (is == null) return 0;
        Multimap<String, AttributeModifier> attrs = is.getItem().getAttributeModifiers(is);
        if (attrs == null) return 0;
        BaseAttributeMap test = new ServersideAttributeMap();
        test.applyAttributeModifiers(attrs);
        IAttributeInstance attr = test.getAttributeInstance(SharedMonsterAttributes.attackDamage);
        if (attr == null) return 0;
        return attr.getAttributeValue();
    }

    public static ItemStack getReifiedBarrel(Coord at) {
        if (at == null) return null;
        if (at.w == null) return null;
        TileEntityDayBarrel barrel = at.getTE(TileEntityDayBarrel.class);
        if (barrel == null) return null;
        return barrel.item;
    }

    public static String toRpm(double velocity) {
        return (int) (Math.toDegrees(velocity) * 10 / 3) + " RPM";
    }

    // Enh, really belongs in NumUtil maybe?
    // Probably UnitUtil, with the map compass stuff as well
    private static class UnitBase {
        final long ratio;
        final String unit;

        private UnitBase(long ratio, String unit) {
            this.ratio = ratio;
            this.unit = "factorization.unit." + unit;
        }
    }

    @SuppressWarnings("PointlessArithmeticExpression")
    public static UnitBase unit_time[] = new UnitBase[] {
            new UnitBase(1L * 20 * 60 * 60 * 24 * 365 * 1000 * 1000, "time.eons"),
            new UnitBase(1L * 20 * 60 * 60 * 24 * 365 * 1000, "time.millenia"),
            new UnitBase(1L * 20 * 60 * 60 * 24 * 365 * 100, "time.centuries"),
            new UnitBase(1L * 20 * 60 * 60 * 24 * 365, "time.years"),
            new UnitBase(1L * 20 * 60 * 60 * 24 * 30, "time.months"), // Mostly! :D
            new UnitBase(1L * 20 * 60 * 60 * 24 * 7, "time.weeks"),
            new UnitBase(1L * 20 * 60 * 60 * 24, "time.irldays"),
            new UnitBase(1L * 20 * 60 * 60, "time.hours"),
            //new UnitBase(1L * 20 * 60 * 20, "time.mcdays"), // skipped due to confusingness
            new UnitBase(1L * 20 * 60, "time.minutes"),
            new UnitBase(1L * 20, "time.seconds"),
            new UnitBase(1L, "time.ticks"),
    };
    @SuppressWarnings("PointlessArithmeticExpression")
    public static UnitBase unit_distance_px[] = new UnitBase[] {
            new UnitBase(1L * 16 * 1000, "distance.kilometers"),
            new UnitBase(1L * 16 * 16, "distance.chunks"),
            new UnitBase(1L * 16, "distance.blocks"),
            new UnitBase(1L, "distance.pixels"),
    };

    private static UnitBase best(UnitBase[] bases, long value) {
        boolean wasAbove = false;
        for (UnitBase base : bases) {
            if (base.ratio <= value && wasAbove) {
                return base;
            } else if (base.ratio >= value) {
                wasAbove = true;
            }
        }
        return bases[bases.length - 1];
    }

    public static String unitify(String unitName, long value, int max_len) {
        UnitBase[] base = null;
        if (unitName.equals("time")) {
            base = unit_time;
        } else if (unitName.equals("distance")) {
            base = unit_distance_px;
        } else {
            return "Unknown unit " + unitName + "@" + value;
        }
        return unitify(base, value, max_len);
    }

    public static String unitify(UnitBase[] bases, long value, int max_len) {
        String r = "";
        while (max_len-- != 0) {
            UnitBase best = best(bases, value);
            long l = value / best.ratio;
            value -= best.ratio * l;
            if (l > 0) {
                if (!r.isEmpty()) r += " ";
                String unit = LangUtil.translateExact(best.unit + "." + l);
                if (unit != null) {
                    r += unit;
                } else {
                    r += l + " " + LangUtil.translateThis(best.unit);
                }
            } else if (value == 0 && !r.isEmpty()) {
                return r;
            }
            if (best.ratio == 1 || max_len == 0) break;
        }
        return r;
    }

    public static String unitTranslateTimeTicks(long value, int max_len) {
        return "§UNIT§ time " + max_len + " " + value;
    }

    public static String unitTranslateDistancePixels(long value, int max_len) {
        return "§UNIT§ distance " + max_len + " " + value;
    }

    public static void debugBytes(String header, byte[] d) {
        System.out.println(header + " #" + d.length);
        for (byte b : d) {
            System.out.print(" " + Integer.toString(b));
        }
        System.out.println();
    }

    public static void setCoreParent(FMLPreInitializationEvent event) {
        final String FZ = "factorization";
        if (Loader.isModLoaded(FZ)) {
            event.getModMetadata().parent = FZ;
        }
    }

    public static <T extends TileEntity> T getTE(IBlockAccess w, BlockPos at, Class<? extends T> klass) {
        TileEntity te = w.getTileEntity(at);
        if (klass.isInstance(te)) return (T) te;
        return null;
    }

    public static boolean sameState(IBlockState abs, IBlockState bbs) {
        return abs == bbs;
    }

    public static void initItem(Item it, String name, Core.TabType tabType) {
        it.setUnlocalizedName("factorization:" + name.replace('.', '/'));
        Core.tab(it, tabType);
    }

    @SideOnly(Side.CLIENT)
    public static TextureAtlasSprite getIcon(ItemStack it) {
        NORELEASE.fixme("These belong in RenderUtil");
        return Minecraft.getMinecraft().getRenderItem().getItemModelMesher().getItemModel(it).getParticleTexture();
    }

    @SideOnly(Side.CLIENT)
    public static IBakedModel getModel(ItemStack is) {
        return Minecraft.getMinecraft().getRenderItem().getItemModelMesher().getItemModel(is);
    }

    @SideOnly(Side.CLIENT)
    public static TextureAtlasSprite getIcon(Block b) {
        return getIcon(new ItemStack(b));
    }

    public static IProperty[] props(IProperty... ret) {
        return ret;
    }

    public static IUnlistedProperty[] uprops(IUnlistedProperty... ret) {
        return ret;
    }

    public static Block getTraceHelper() {
        return new Block(Material.rock);
    }

    public static GameRules getRules(World world) {
        return world.getGameRules();
    }

    public static boolean doTileDrops(World world) {
        return getRules(world).getBoolean("doTileDrops");
    }

    public static <E> E chooseOne(Collection<E> collection) {
        //noinspection LoopStatementThatDoesntLoop
        for (E e : collection) {
            return e;
        }
        return null;
    }

    public static <E> boolean intersects(Set<E> a, Set<E> b) {
        // I was able to use a.removeAll(b) instead of this.
        if (!(a.size() < b.size())) {
            // The documentation for Sets.intersection() suggests that it's best that the first set be smaller.
            // Hopefully they actually tested that! :D
            // (Could just use that. But, uh, this code is already written...)
            Set<E> c = a;
            a = b;
            b = c;
        }
        for (E e : a) {
            if (b.contains(e)) return true;
        }
        return false;
    }
}
