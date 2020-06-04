package factorization.misc;

import factorization.api.Coord;
import factorization.shared.Core;
import factorization.util.PlayerUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent;

import java.util.ArrayList;

public class Embarkener {
    public Embarkener() {
        addLogBarkRecipes();
    }
    
    int wood_rendertype = Blocks.log.getRenderType();
    boolean isWoodish(Block block) {
        if (block == null) return false;
        return block.getMaterial() == Material.wood && block instanceof BlockLog && block.getRenderType() == wood_rendertype;
    }
    
    void addLogBarkRecipes() {
        int count = 0;
        for (Block block : (Iterable<Block>) Block.blockRegistry) {
            if (isWoodish(block)) {
                for (int md = 0; md < 4; md++) {
                    count++;
                    ItemStack log = new ItemStack(block, 1, md);
                    ItemStack barked = new ItemStack(block, 4, md | 0xC);
                    barked.setStackDisplayName("Bark"); // FIXME: Localization fail :/
                    Core.registry.vanillaShapelessRecipe(barked, log, log, log, log);
                    ItemStack barked1 = barked.copy();
                    barked1.stackSize = 1;
                    Core.registry.vanillaShapelessRecipe(log, barked1);
                }
            }
        }
        Core.logInfo("Added %s 'barking' recipes for blocks that look sort of like wood logs; this can be disabled in the config file.", count);
    }
    
    ArrayList<EmbarkenEvent> embarkenQueue = new ArrayList(); // Not LinkedList; won't have > 10 events per tick.
    
    static class EmbarkenEvent {
        Coord target;
        int orig_stacksize;
        ItemStack stack;
        EntityPlayer player;
        BlockLog expectedBlock;
        
        public EmbarkenEvent(Coord target, int orig_stacksize, ItemStack stack, EntityPlayer player, BlockLog expectedBlock) {
            this.target = target;
            this.orig_stacksize = orig_stacksize;
            this.stack = stack;
            this.player = player;
            this.expectedBlock = expectedBlock;
        }
        
        void handle() {
            if (stack.stackSize >= orig_stacksize && !PlayerUtil.isPlayerCreative(player)) return;
            if (player.isDead) return;
            if (!target.blockExists()) return;
            if (target.getBlock() != expectedBlock) return;
            target.setMd(target.getMd() | 0xC);
        }
    }
    
    @SubscribeEvent
    public void enbarkenWood(PlayerInteractEvent event) {
        //FIXME: A block place event would be nice...
        //Unfortunately it flashes on the client before it gets corrected.
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        if (event.entityPlayer == null) return;
        ItemStack is = event.entityPlayer.getHeldItem();
        if (is == null) return;
        if ((is.getItemDamage() & 0xC) != 0xC) return;
        if (!(is.getItem() instanceof ItemBlock)) return;
        Block theBlock = Block.getBlockFromItem(is.getItem());
        if (!isWoodish(theBlock)) return;
        Coord target = new Coord(event.entityPlayer.worldObj, event.pos);
        target.adjust(event.face);
        if (!target.isReplacable()) return;
        embarkenQueue.add(new EmbarkenEvent(target, is.stackSize, is, event.entityPlayer, (BlockLog) theBlock));
    }
    
    @SubscribeEvent
    public void tickEnd(ServerTickEvent event) {
        if (event.phase != Phase.END) return;
        for (EmbarkenEvent e : embarkenQueue) {
            e.handle();
        }
        embarkenQueue.clear();
    }
    
    
}
