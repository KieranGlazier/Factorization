package factorization.misc;

import com.google.common.base.Strings;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class BuffNametags {
    @SubscribeEvent(priority = EventPriority.LOW)
    public void buffedNametag(EntityInteractEvent event) {
        ItemStack is = event.entityPlayer.getHeldItem();
        if (is == null) return;
        if (is.getItem() != Items.name_tag) return;
        if (!(event.target instanceof EntityLiving)) return;
        final EntityLiving ent = (EntityLiving) event.target;
        final String origName = ent.getCustomNameTag();
        if (!Strings.isNullOrEmpty(origName)) return;
        NBTTagCompound tag = ent.getEntityData();
        final String name = "FZMiscBuffNametags";
        if (tag.hasKey(name)) return;
        tag.setBoolean(name, true);
        ent.tasks.addTask(0, new EntityAIBase() {
            boolean buffApplied = false; // Might not be necessary.
            
            @Override
            public boolean shouldExecute() {
                if (buffApplied) return false;
                buffApplied = true;
                if (ent.getCustomNameTag().equals(origName)) return false;
                float delta = 2*3;
                float origHealth = ent.getMaxHealth();
                float newMaxHealth = origHealth + delta;
                ent.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(newMaxHealth);
                ent.heal(delta);

                EnumParticleTypes particleType = EnumParticleTypes.HEART;
                if (ent instanceof IMob) {
                    particleType = EnumParticleTypes.SMOKE_NORMAL;
                }
                if (ent.worldObj instanceof WorldServer) {
                    WorldServer ws = (WorldServer) ent.worldObj;
                    ws.spawnParticle(particleType, false, ent.posX, ent.posY, ent.posZ, 8, 0.0, 0.0, 0.0, 0.0);
                }
                return false;
            }
        });
    }
}