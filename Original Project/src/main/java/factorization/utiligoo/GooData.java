package factorization.utiligoo;

import factorization.api.Coord;
import factorization.notify.Notice;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.WeakHashMap;

public class GooData extends WorldSavedData {
    public GooData(String dataName) {
        super(dataName);
    }

    int dimensionId;
    int[] coords = new int[0];
    
    int change_counts = 0;
    WeakHashMap<Entity, Integer> player_updates = new WeakHashMap<Entity, Integer>();
    int last_traced_index = -1;
    
    @Override
    public void readFromNBT(NBTTagCompound tag) {
        if (!tag.hasKey("dimensionId")) {
            return;
        }
        dimensionId = tag.getInteger("dimensionId");
        coords = tag.getIntArray("coordData");
    }
    
    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setString("mapname", mapName);
        tag.setInteger("dimensionId", dimensionId);
        tag.setIntArray("coordData", coords);
    }
    
    @Override
    public void setDirty(boolean dirty) {
        super.setDirty(dirty);
        if (dirty) {
            change_counts++;
        }
    }
    
    boolean isPlayerOutOfDate(Entity player) {
        if (!(player instanceof EntityPlayer)) return false;
        Integer update_count = player_updates.get(player);
        if (update_count == null || update_count != change_counts) {
            player_updates.put(player, change_counts);
            return true;
        }
        return false;
    }
    
    void wipe(ItemStack is, World world) {
        coords = new int[0];
        dimensionId = 0;
        if (!is.hasDisplayName()) {
            deleteDataFile(world);
            is.setItemDamage(0);
        } else {
            markDirty();
        }
    }
    
    static final String fz_goo = "fz_goo";
    
    static String getGooName(ItemStack is) {
        return fz_goo + "_" + is.getItemDamage();
    }
    
    static GooData getGooData(ItemStack is, World world) {
        GooData data = (GooData) world.loadItemData(GooData.class, getGooName(is));
        if (data == null && !world.isRemote) {
            int unique_id = world.getUniqueDataId(fz_goo);
            if (unique_id == 0) {
                // '0' conflicts with the empty ID, so skip it
                unique_id = world.getUniqueDataId(fz_goo);
            }
            is.setItemDamage(unique_id);
            String name = getGooName(is);
            data = new GooData(name);
            data.markDirty();
            world.setItemData(name, data);
        }
        return data;
    }
    
    static GooData getNullGooData(ItemStack is, World world) {
        if (is == null) return null;
        if (!(is.getItem() instanceof ItemGoo)) return null;
        if (is.getItemDamage() == 0) return null;
        return (GooData) world.loadItemData(GooData.class, getGooName(is));
    }
    
    private void deleteDataFile(World world) {
        File file = world.getSaveHandler().getMapFileFromName(mapName);
        if (file != null && file.exists()) {
            file.delete();
        }
        world.getMapStorage().loadedDataList.remove(this);
        world.getMapStorage().loadedDataMap.remove(this);
    }
    
    void removeIndices(ArrayList<Integer> indices, ItemStack is, World world) {
        int[] all = new int[indices.size()];
        int i = 0;
        for (Integer index : indices) {
            all[i++] = index;
        }
        coords = ArrayUtils.removeAll(coords, all);
        if (coords.length == 0) {
            wipe(is, world);
        } else {
            markDirty();
        }
    }
    
    boolean checkWorld(EntityPlayer player, Coord complainAt) {
        if (coords.length == 0) return false;
        if (dimensionId == player.worldObj.provider.getDimensionId()) return false;
        if (complainAt != null) {
            new Notice(complainAt, "item.factorization:utiligoo.wrongDimension").send(player);
        }
        return true;
    }
}