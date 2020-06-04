package factorization.sockets;

import factorization.api.Coord;
import factorization.api.FzOrientation;
import factorization.api.Quaternion;
import factorization.fzds.DeltaChunk;
import factorization.fzds.HammerEnabled;
import factorization.fzds.interfaces.IDimensionSlice;
import factorization.servo.TileEntityServoRail;
import factorization.util.SpaceUtil;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class RayTracer {
    final TileEntitySocketBase base;
    final ISocketHolder socket;
    final Coord trueCoord;
    final FzOrientation trueOrientation;
    final boolean powered;

    boolean lookAround = false;
    boolean onlyFirstBlock = false;
    boolean checkEnts = false;
    boolean checkFzds = true;
    boolean checkFzdsFirst = false;
    boolean fzdsMovesTe = true;

    AxisAlignedBB entBox = null;

    public RayTracer(TileEntitySocketBase base, ISocketHolder socket, Coord at, FzOrientation orientation, boolean powered) {
        this.base = base;
        this.socket = socket;
        this.trueCoord = at;
        this.trueOrientation = orientation;
        this.powered = powered;
    }

    public RayTracer onlyFrontBlock() {
        onlyFirstBlock = true;
        return this;
    }

    public RayTracer lookAround() {
        lookAround = true;
        return this;
    }

    public RayTracer checkEnts() {
        checkEnts = true;
        return this;
    }

    public RayTracer checkFzdsFirst() {
        checkFzdsFirst = true;
        checkFzds = false;
        return this;
    }

    boolean fzdsPass = false;

    boolean checkReal() {
        fzdsPass = false;
        return runPass(trueOrientation, trueCoord, null);
    }

    boolean checkFzds() {
        if (!HammerEnabled.ENABLED) return false;
        fzdsPass = true;
        if (trueCoord.w != DeltaChunk.getServerShadowWorld()) return false;

        Coord shadowBaseLocation = new Coord(base);

        for (IDimensionSlice idc : DeltaChunk.getSlicesContainingPoint(trueCoord)) {
            FzOrientation orientation = shadowOrientation(idc);
            if (orientation == null) continue;
            Coord at = new Coord(base);
            Vec3 v = idc.shadow2real(at.toMiddleVector().add(new Vec3(trueOrientation.top.getDirectionVec())));
            idc.shadow2real(at);
            double x = v.xCoord;
            double y = v.yCoord;
            double z = v.zCoord;
            Coord target = new Coord(at.w, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
            target.adjust(orientation.facing.getOpposite());
            orientation = orientation.getSwapped();

            try {
                if (fzdsMovesTe) {
                    target.setAsTileEntityLocation(base);
                }
                if (runPass(orientation, target, idc)) return true;
            } finally {
                if (fzdsMovesTe) {
                    shadowBaseLocation.setAsTileEntityLocation(base);
                }
            }
        }
        return false;
    }

    public boolean trace() {
        if (checkFzdsFirst && checkFzds()) return true;
        entBox = null;
        if (checkReal()) return true;
        entBox = null;
        return checkFzds && checkFzds();
    }

    FzOrientation shadowOrientation(IDimensionSlice idc) {
        Quaternion rot = idc.getTransform().getRot();
        Vec3 topVec = SpaceUtil.fromDirection(trueOrientation.top);
        Vec3 faceVec = SpaceUtil.fromDirection(trueOrientation.facing);
        rot.applyRotation(topVec);
        rot.applyRotation(faceVec);
        EnumFacing top = SpaceUtil.round(topVec, null);
        EnumFacing facing = SpaceUtil.round(faceVec, top);
        FzOrientation to = FzOrientation.fromDirection(top);
        if (to == null) {
            return FzOrientation.fromDirection(facing);
        }
        FzOrientation tofo = to.pointTopTo(facing);
        if (tofo == null) {
            return to;
        }
        return tofo;
    }


    boolean runPass(FzOrientation orientation, Coord coord, IDimensionSlice idc) {
        final EnumFacing top = orientation.top;
        final EnumFacing face = orientation.facing;
        final EnumFacing right = SpaceUtil.rotate(face, top);

        if (checkEnts) {
            if (entBox == null) {
                entBox = base.getEntityBox(socket, coord, top, 0);
                if (idc != null) {
                    entBox = idc.shadow2real(entBox);
                    //AabbDebugger.addBox(entBox);
                }
            }
            for (Entity entity : getEntities(coord, top, idc)) {
                if (entity == socket) {
                    continue;
                }
                if (base.handleRay(socket, new MovingObjectPosition(entity), coord.w, false, powered)) {
                    return true;
                }
            }
        }

        Coord targetBlock = coord.add(top);
        if (mopBlock(targetBlock, top.getOpposite())) return true; //nose-to-nose with the servo
        if (onlyFirstBlock) return false;
        if (mopBlock(targetBlock.add(top), top.getOpposite())) return true; //a block away
        if (mopBlock(coord, top)) return true;
        if (!lookAround) return false;
        if (mopBlock(targetBlock.add(face), face.getOpposite())) return true; //running forward
        if (mopBlock(targetBlock.add(face.getOpposite()), face)) return true; //running backward
        if (mopBlock(targetBlock.add(right), right.getOpposite())) return true; //to the servo's right
        if (mopBlock(targetBlock.add(right.getOpposite()), right)) return true; //to the servo's left

        return false;
    }

    boolean mopBlock(Coord target, EnumFacing side) {
        if (base != socket && target.getTE(TileEntityServoRail.class) != null) return false;
        boolean isThis = base == socket && target.isAt(base);
        Vec3 hitVec = new Vec3(base.getPos().getX() + side.getDirectionVec().getX(), base.getPos().getY() + side.getDirectionVec().getY(), base.getPos().getZ() + side.getDirectionVec().getZ());
        return base.handleRay(socket, target.createMop(side, hitVec), target.w, isThis, powered);
    }

    Iterable<Entity> getEntities(Coord coord, EnumFacing top, IDimensionSlice idc) {
        if (idc == null) {
            Entity ent = null;
            if (socket instanceof Entity) {
                ent = (Entity) socket;
            }
            return (Iterable<Entity>) coord.w.getEntitiesWithinAABBExcludingEntity(ent, entBox);
        }
        // Sorta like MetaBox.convertShadowBoxToRealBox ... but lazier.
        Vec3 min = SpaceUtil.newVec();
        Vec3 max = SpaceUtil.newVec();
        SpaceUtil.setMin(entBox, min);
        SpaceUtil.setMax(entBox, max);
        AxisAlignedBB realBox = SpaceUtil.newBox();
        SpaceUtil.setMin(entBox, idc.shadow2real(min));
        SpaceUtil.setMax(entBox, idc.shadow2real(max)); // IDC re-uses the same copy of this vector, hence these contortions.
        return (Iterable<Entity>) coord.w.getEntitiesWithinAABBExcludingEntity(null, realBox);
    }
}
