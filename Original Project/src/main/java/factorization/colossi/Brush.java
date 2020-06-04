package factorization.colossi;

import factorization.api.Coord;
import factorization.api.DeltaCoord;
import factorization.util.NumUtil;
import net.minecraft.block.state.IBlockState;

import java.util.Random;

public class Brush {
    public static enum BrushMask {
        ONLY_AIR, ONLY_NON_AIR, ALL;
        
        boolean applies(Coord at) {
            switch (this) {
            case ONLY_AIR: return at.isAir();
            case ONLY_NON_AIR: return !at.isAir();
            default:
            case ALL: return true;
            }
        }
    }
    
    IBlockState fill;
    BrushMask mask;
    DeltaCoord[] points;
    Random rand;
    
    public Brush(IBlockState fill, BrushMask mask, Random rand) {
        this.fill = fill;
        this.mask = mask;
        this.rand = rand;
        points = new DeltaCoord[] {new DeltaCoord(0, 0, 0)};
    }
    
    public Brush(IBlockState fill, BrushMask mask, Random rand, DeltaCoord... points) {
        this.fill = fill;
        this.mask = mask;
        this.rand = rand;
        this.points = points;
    }
    
    void paint(Coord target) {
        Coord at = target.copy();
        for (DeltaCoord dc : points) {
            target.set(at);
            target.adjust(dc);
            if (mask.applies(at)) {
                at.set(fill, true);
            }
        }
    }
    
    void drag(Coord start, Coord end) {
        double length = Math.sqrt(start.distanceSq(end));
        Coord mid = start.copy();
        for (int i = 0; i < length; i++) {
            mid.x = (int) Math.round(NumUtil.interp(start.x, start.x, i / length));
            mid.y = (int) Math.round(NumUtil.interp(start.y, start.y, i / length));
            mid.z = (int) Math.round(NumUtil.interp(start.z, start.z, i / length));
            paint(mid);
        }
    }
}
