package factorization.mechanics;

import factorization.api.Coord;
import factorization.api.ICoordFunction;
import factorization.api.Quaternion;
import factorization.fzds.TransferLib;
import factorization.fzds.interfaces.DimensionSliceEntityBase;
import factorization.fzds.interfaces.IDCController;
import factorization.fzds.interfaces.IDimensionSlice;
import factorization.fzds.interfaces.transform.Pure;
import factorization.fzds.interfaces.transform.TransformData;
import factorization.shared.Core;
import factorization.shared.EntityReference;
import factorization.util.SpaceUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.apache.commons.lang3.ArrayUtils;

/**
 * Allows multiple controllers on an IDC. Nicely sets down the IDC when it has no more controllers.
 */
public class MechanicsController implements IDCController {

    /**
     * Call this to join/create the listener set. Do not use this method after deserialization.
     *
     * @param idc        The IDC to listen to
     * @param constraint The controller to add.
     * @throws IllegalArgumentException if the IDC already has a non-multicast controller.
     */
    static void register(IDimensionSlice idc, IDCController constraint) {
        rejoin(idc, constraint);
        changeCount(idc, +1);
    }

    /**
     * Since the controllers aren't serialized, this is the mechanism to remind the IDC who the controllers are. Use with IDCRef.
     *
     * Best used with autoJoin.
     *
     * @param idc        The IDC to remind who is controller it
     * @param constraint The controller
     * @throws IllegalArgumentException if the IDC already has a non-multicast controller.
     *
     */
    static void rejoin(IDimensionSlice idc, IDCController constraint) {
        IDCController controller = idc.getController();
        if (controller == IDCController.default_controller) {
            idc.setController(controller = new MechanicsController());
        } else if (!(controller instanceof MechanicsController)) {
            throw new IllegalArgumentException("IDC already had a controller, and it is not a MechanicsController! IDC: " + idc + "; controller: " + controller + "; constraint: " + constraint);
        }
        MechanicsController sys = (MechanicsController) controller;
        sys.addConstraint(constraint);
    }

    /**
     * Remove the controller from the multicast set. The IDC will be dropped if there are no more controllers.
     *
     * @param idc        The IDC who is getting the controller removed
     * @param constraint The controller to remove.
     */
    static void deregister(IDimensionSlice idc, IDCController constraint) {
        IDCController controller = idc.getController();
        if (!(controller instanceof MechanicsController)) {
            Core.logWarning("Tried to deregister constraint for IDC that isn't a MechanicsController! IDC: " + idc + "; controller: " + controller + "; constraint: ", constraint);
            return;
        }
        MechanicsController sys = (MechanicsController) controller;
        sys.removeConstraint(constraint);
        if (changeCount(idc, -1) <= 0) {
            dropIDC(idc);
        }
    }

    /**
     * @param idc The IDC to check
     * @return true if the IDC's controller is a MechanicsController, or it can have one applied.
     */
    static boolean usable(IDimensionSlice idc) {
        IDCController controller = idc.getController();
        return controller == IDCController.default_controller || controller instanceof MechanicsController;
    }

    private static int changeCount(IDimensionSlice idc, int d) {
        NBTTagCompound tag = idc.getTag();
        String key = "KinematicSystemEntries";
        int old = tag.getInteger(key);
        int upd = old + d;
        if (upd < 0) upd = 0;
        tag.setInteger(key, upd);
        return upd;
    }

    private static void dropIDC(final IDimensionSlice idc) {
        // TODO: check all forge directions (including UNKNOWN) to count how many clashes there are. Move to the minimal direction before dropping IF the # of clashes is > 10% of the block count
        idc.getTransform().setRot(new Quaternion());
        idc.getVel().setRot(new Quaternion());
        final Coord min = idc.getMinCorner();
        final Coord max = idc.getMaxCorner();
        final Coord real = new Coord(idc.getEntity());
        Coord.iterateCube(min, max, new ICoordFunction() {
            @Override
            public void handle(Coord shadow) {
                if (shadow.isAir()) return;
                real.set(shadow);
                idc.shadow2real(real);
                real.x--;
                real.y--;
                real.z--;
                if (real.isReplacable()) {
                    TransferLib.move(shadow, real, true, true);
                    real.markBlockForUpdate();
                } else {
                    shadow.breakBlock();
                }
            }
        });
        Coord.iterateCube(min, max, new ICoordFunction() {
            @Override
            public void handle(Coord here) {
                here.setAir();
            }
        });
        idc.killIDS();
    }

    public static void push(IDimensionSlice idc, Coord at, Vec3 force) {
        final IDCController idcController = idc.getController();
        if (!(idcController instanceof MechanicsController)) {
            return;
        }
        final MechanicsController controller = (MechanicsController) idcController;
        for (IDCController constraint : controller.constraints) {
            if (constraint instanceof TileEntityHinge) { // Sound design!
                TileEntityHinge hinge = (TileEntityHinge) constraint;
                if (hinge.getCoord().isWeaklyPowered()) return;
                hinge.applyForce(idc, at, force);
                return;
            }
        }

        double mass = MassCalculator.calculateMass(idc);
        /* Whereupon St. Isaac Newton did set down the Holy Law of Nature, that
         * the sum of the forces upon a body is equal to the mass of the body times
         * the acceleration of the body, and whereupon we have calculated the mass
         * of the body, let us therefor grant unto our idc an IMPULSE OF VELOCITY
         * equal to the force multiplied by the inverse of the mass.
         */
        force = SpaceUtil.scale(force, 1 / mass);
        idc.getVel().addPos(force);
    }

    private IDCController[] constraints = new IDCController[0];

    void addConstraint(IDCController constraint) {
        if (ArrayUtils.contains(constraints, constraint)) return;
        constraints = ArrayUtils.add(constraints, constraint);
        if (constraint instanceof TileEntityHinge) {
            // Or could use Arrays.sort
            // This is fine so long as there's only 1 hinge
            int i = constraints.length - 1;
            IDCController first = constraints[0];
            constraints[0] = constraint;
            constraints[i] = first;
        }
    }

    void removeConstraint(IDCController constraint) {
        constraints = ArrayUtils.removeElement(constraints, constraint);
    }

    IDCController[] getConstraints() {
        return constraints;
    }

    @Override
    public boolean placeBlock(IDimensionSlice idc, EntityPlayer player, Coord at) {
        for (IDCController c : constraints) {
            if (c.placeBlock(idc, player, at)) return true;
        }
        return false;
    }

    @Override
    public boolean breakBlock(IDimensionSlice idc, EntityPlayer player, Coord at, EnumFacing sideHit) {
        for (IDCController c : constraints) {
            if (c.breakBlock(idc, player, at, sideHit)) return true;
        }
        return false;
    }

    @Override
    public boolean hitBlock(IDimensionSlice idc, EntityPlayer player, Coord at, EnumFacing sideHit) {
        for (IDCController c : constraints) {
            if (c.hitBlock(idc, player, at, sideHit)) return true;
        }
        return false;
    }

    @Override
    public boolean useBlock(IDimensionSlice idc, EntityPlayer player, Coord at, EnumFacing sideHit) {
        for (IDCController c : constraints) {
            if (c.useBlock(idc, player, at, sideHit)) return true;
        }
        return false;
    }

    @Override
    public void idcDied(IDimensionSlice idc) {
        for (IDCController c : constraints) c.idcDied(idc);
    }

    @Override
    public void beforeUpdate(IDimensionSlice idc) {
        for (IDCController c : constraints) {
            c.beforeUpdate(idc);
        }
    }

    private static final double LINEAR_DAMPENING = 0.98;
    private static final double ROTATIONAL_DAMPENING = 0.98;
    private static final double GRAVITY = -0.08;

    private void updatePhysics(IDimensionSlice idc) {
        if (idc.getRealWorld().isRemote || constraints.length == 0) {
            return;
        }
        if (idc.hasOrders()) return;
        TransformData<Pure> transformVel = idc.getVel();
        boolean anyMotion = transformVel.isZero();
        if (!anyMotion) return;
        /*if (anyMotion) {
            // See EntityLivingBase.moveEntityWithHeading
            //push(idc, MassCalculator.getComCoord(idc), new Vec3(0, GRAVITY, 0));
        }
        if (decay_time > 0) {
            decay_time--;
            return;
        }*/
        transformVel.mulPos(new Vec3(LINEAR_DAMPENING, LINEAR_DAMPENING, LINEAR_DAMPENING));
        Quaternion w = transformVel.getRot();
        if (w.isZero()) return;
        Quaternion wp = new Quaternion().shortSlerp(w, ROTATIONAL_DAMPENING);
        transformVel.setRot(wp);
    }

    @Override
    public void afterUpdate(IDimensionSlice idc) {
        for (IDCController c : constraints) {
            c.afterUpdate(idc);
        }
        updatePhysics(idc);
    }

    @Override
    public boolean onAttacked(IDimensionSlice idc, DamageSource damageSource, float damage) { return false; }

    @Override
    public CollisionAction collidedWithWorld(World realWorld, AxisAlignedBB realBox, World shadowWorld, AxisAlignedBB shadowBox) {
        return CollisionAction.STOP_BEFORE;
    }

    /**
     * Returns an EntityReference that will call MechanicsController.rejoin for you.
     * {@code
     *     final EntityReference<IDeltaChunk> idcRef = MechanicsController.autoJoin(this);
     * }
     * @param controller The controller that you want to rejoin.
     * @return The reference
     */
    static EntityReference<DimensionSliceEntityBase> autoJoin(final IDCController controller) {
        EntityReference<DimensionSliceEntityBase> ret = new EntityReference<DimensionSliceEntityBase>();
        ret.whenFound(new EntityReference.OnFound<DimensionSliceEntityBase>() {
            @Override
            public void found(DimensionSliceEntityBase ent) {
                MechanicsController.rejoin(ent, controller);
            }
        });
        return ret;
    }
}
