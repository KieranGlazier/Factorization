package factorization.shared;

import factorization.api.Coord;
import factorization.net.StandardMessageType;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;

public enum Sound {
    // it might be kinda cool to have this be configable?
    rightClick("random.click", 1.0, 1.25),
    leftClick("random.click", 1.0, 0.75),
    stamperUse("tile.piston.in", 0.1, 1.1, false),
    acidBurn("random.fizz", 1, 1, true),
    caliometricDigest("random.burp", 1, 0.5, true),
    barrelPunt("mob.zombie.infect", 0.9, 1.5, true),
    barrelPunt2("mob.zombie.infect", 0.9, 3.5, true),
    socketInstall("dig.wood", 1.0, 1.0, true),
    servoInstall("mob.slime.attack", 1.0, 1.0, true),
    artifactForged("random.anvil_use", 1.0, 0.25, true),
    legendariumInsert("factorization:legendarium.insert", 0.75, 1.0, true),
    extruderExtrude("random.fizz", .2, 0.5, true),
    extruderBreak("random.fizz", .2, 0.5, true),
    geyserBlast("random.fizz", .2, 0.5, true),
    ;
    String src;
    float volume, pitch;
    int index;
    boolean share;

    static class sound {
        static ArrayList<Sound> list = new ArrayList<Sound>();
    }

    void init(String src, double volume, double pitch) {
        this.src = src;
        this.volume = (float) volume;
        this.pitch = (float) pitch;
        this.index = sound.list.size();
        sound.list.add(this);
        
    }

    Sound(String src, double volume, double pitch) {
        init(src, volume, pitch);
        this.share = false;
    }

    Sound(String src, double volume, double pitch, boolean share) {
        init(src, volume, pitch);
        this.share = share;
    }

    void share(World w, BlockPos pos) {
        if (!share) {
            return;
        }
        if (w.isRemote) {
            return;
        }
        Core.network.broadcastMessageToBlock(null, new Coord(w, pos), StandardMessageType.PlaySound, index);
    }

    public static void receive(Coord coord, ByteBuf input) {
        int index = input.readInt();
        EntityPlayer player = Core.proxy.getClientPlayer();
        if (player == null) {
            return;
        }
        sound.list.get(index).playAt(coord);
    }
    
    public void playAt(Coord c) {
        playAt(c.w, c.x, c.y, c.z);
    }

    @Deprecated // Doesn't work.
    public void playAt(Entity ent) {
        ent.worldObj.playSoundAtEntity(ent, src, volume, pitch);
        share(ent.worldObj, new BlockPos((int) ent.posX, (int) (ent.posY - ent.getYOffset()), (int) ent.posZ));
    }

    public void playAt(World world, double x, double y, double z) {
        if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
            world.playSound(x, y, z, src, volume, pitch, false);
        }
        BlockPos pos = new BlockPos((int) x, (int) y, (int) z);
        share(world, pos);
    }

    public void playAt(TileEntity ent) {
        playAt(ent.getWorld(), ent.getPos().getX(), ent.getPos().getY(), ent.getPos().getZ());
    }

    public void play() {
        Core.proxy.playSoundFX(src, volume, pitch);
    }
}
