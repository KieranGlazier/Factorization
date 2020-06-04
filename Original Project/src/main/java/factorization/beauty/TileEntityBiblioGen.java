package factorization.beauty;

import factorization.api.Coord;
import factorization.api.ICoordFunction;
import factorization.api.IMeterInfo;
import factorization.api.IRotationalEnergySource;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.Share;
import factorization.common.FactoryType;
import factorization.notify.Notice;
import factorization.notify.Style;
import factorization.shared.BlockClass;
import factorization.shared.BlockFactorization;
import factorization.shared.TileEntityCommon;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;

import java.io.IOException;

public class TileEntityBiblioGen extends TileEntityCommon implements IRotationalEnergySource, IMeterInfo, ITickable {
    int bookCount = -1;
    double angle = 0, prev_angle = 0;
    double availablePower = 0;
    static int LIBRARY_RADIUS = 24;
    static double POWER_PER_BOOK = Math.PI / 9200;

    @Override
    public BlockClass getBlockClass() {
        return BlockClass.Machine;
    }

    @Override
    public FactoryType getFactoryType() {
        return FactoryType.BIBLIO_GEN;
    }

    @Override
    public void putData(DataHelper data) throws IOException {
        bookCount = data.as(Share.VISIBLE, "bookCount").putInt(bookCount);
        availablePower = data.as(Share.PRIVATE, "availablePower").putDouble(availablePower);
    }

    void countBooks() {
        BookCounter bookCounter = new BookCounter(this.getCoord());
        int old_count = bookCount;
        bookCount = bookCounter.count();
        if (bookCounter.interference != null) {
            new Notice(bookCounter.interference, "x").withStyle(Style.LONG, Style.DRAWFAR, Style.SCALE_SIZE).sendToAll();
            new Notice(this, "factorization:bibliogen.interference").sendToAll();
        }
        if (bookCount != old_count) {
            broadcastMessage(null, getDescriptionPacket());
        }
    }

    @Override
    public boolean activate(EntityPlayer entityplayer, EnumFacing side) {
        if (entityplayer.isSneaking()) {
            bookCount = -1;
            return true;
        }
        return false;
    }

    @Override
    public void update() {
        if (worldObj.isRemote) {
            prev_angle = angle;
            angle += getVelocity(EnumFacing.DOWN);
        }
        long now = worldObj.getTotalWorldTime() + hashCode();
        if (bookCount == -1 || now % 2000 == 0) {
            countBooks();
        }
        availablePower = bookCount * POWER_PER_BOOK;
    }

    @Override
    public String getInfo() {
        return "Books: " + bookCount;
    }

    @Override
    public boolean canConnect(EnumFacing direction) {
        return direction == EnumFacing.DOWN;
    }

    @Override
    public double availableEnergy(EnumFacing direction) {
        if (direction != EnumFacing.DOWN) return 0;
        return availablePower;
    }

    @Override
    public double takeEnergy(EnumFacing direction, double maxPower) {
        if (direction != EnumFacing.DOWN) return 0;
        return availablePower;
    }

    @Override
    public boolean isTileEntityInvalid() {
        return this.isInvalid();
    }

    @Override
    public double getVelocity(EnumFacing direction) {
        if (direction != EnumFacing.DOWN) return 0;
        double v = bookCount * POWER_PER_BOOK;
        if (v > MAX_SPEED) v = MAX_SPEED;
        return v;
    }

    class BookCounter implements ICoordFunction {
        int books = 1; // We're a book
        TileEntity interference = null;
        final Coord min, max;

        BookCounter(Coord start) {
            min = start.add(-LIBRARY_RADIUS, -LIBRARY_RADIUS, -LIBRARY_RADIUS);
            max = start.add(+LIBRARY_RADIUS, +LIBRARY_RADIUS, +LIBRARY_RADIUS);
        }

        int count() {
            Coord.iterateCube(min, max, this);
            if (interference != null) return 0;
            return books;
        }

        @Override
        public void handle(Coord here) {
            Block block = here.getBlock();
            if (block instanceof BlockFactorization) {
                TileEntity te = here.getTE(TileEntityBiblioGen.class);
                if (te != null && te != TileEntityBiblioGen.this) {
                    interference = te;
                }
            }
            books += block.getEnchantPowerBonus(here.w, here.toBlockPos()) > 0 ? 1 : 0;
        }
    }

    @Override
    public void setBlockBounds(Block b) {
        b.setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 0.75F, 1.0F);
    }
}
