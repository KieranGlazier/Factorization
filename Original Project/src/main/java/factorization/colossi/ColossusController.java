package factorization.colossi;

import factorization.api.Coord;
import factorization.api.ICoordFunction;
import factorization.api.Quaternion;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.Share;
import factorization.fzds.BasicTransformOrder;
import factorization.fzds.ITransformOrder;
import factorization.fzds.interfaces.DeltaCapability;
import factorization.fzds.interfaces.IDCController;
import factorization.fzds.interfaces.IDimensionSlice;
import factorization.fzds.interfaces.Interpolation;
import factorization.fzds.interfaces.transform.Pure;
import factorization.fzds.interfaces.transform.TransformData;
import factorization.shared.Core;
import factorization.shared.EntityFz;
import factorization.util.LangUtil;
import factorization.util.SpaceUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;

public class ColossusController extends EntityFz implements IBossDisplayData, IDCController {
    enum BodySide { LEFT, RIGHT, CENTER, UNKNOWN_BODY_SIDE };
    enum LimbType {
        BODY, ARM, LEG, UNKNOWN_LIMB_TYPE;
        
        public boolean isArmOrLeg() { return this == ARM || this == LEG; }
    };
    LimbInfo[] limbs;
    IDimensionSlice body;
    LimbInfo bodyLimbInfo;
    public final StateMachineExecutor walk_controller = new StateMachineExecutor(this, "walk", WalkState.IDLE);
    public final StateMachineExecutor ai_controller = new StateMachineExecutor(this, "tech", Technique.STATE_MACHINE_ENTRY);
    boolean setup = false;
    int arm_size = 0, arm_length = 0;
    int leg_size = 0, leg_length = 0;
    int body_width = 0;
    //int cracked_body_blocks = 0;
    private Coord home = null;
    private boolean been_hurt = false;
    boolean confused = false;
    static String creeper_tag = "fz:colossus_spawned_creeper";
    
    
    static Technique[] offensives, idlers, defensives;
    static {
        ArrayList<Technique> _offensives = new ArrayList();
        ArrayList<Technique> _idlers = new ArrayList();
        ArrayList<Technique> _defensives = new ArrayList();
        for (Technique tech : Technique.values()) {
            ArrayList<Technique> use;
            switch (tech.getKind()) {
            default: continue;
            case DEFENSIVE: use = _defensives; break;
            case IDLER: use = _idlers; break;
            case OFFENSIVE: use = _offensives; break;
            }
            use.add(tech);
        }
        offensives = _offensives.toArray(new Technique[0]);
        idlers = _idlers.toArray(new Technique[0]);
        defensives = _defensives.toArray(new Technique[0]);
    }
    
    private Coord path_target = null;
    int turningDirection = 0;
    boolean target_changed = false;
    double walked = 0;
    int last_step_direction = -100;
    BodySide spin_direction = BodySide.UNKNOWN_BODY_SIDE;
    
    transient int last_pos_hash = -1;
    double target_y = Double.NaN;
    
    int target_count = 0;
    transient Entity target_entity;

    // Vanilla uses 0-4 as of 1.8. Let's just give them plenty of room.
    private static final int _destroyed_cracked_block_id = 10;
    private static final int _unbroken_cracked_block_id = 11;
    
    public ColossusController(World world) {
        super(world);
        path_target = null;
        ignoreFrustumCheck = true;
    }

    @Override
    protected void entityInit() {
        dataWatcher.addObject(_destroyed_cracked_block_id, 0);
        dataWatcher.addObject(_unbroken_cracked_block_id, 0);
    }

    public ColossusController(World world, LimbInfo[] limbInfo, int arm_size, int arm_length, int leg_size, int leg_length, int width) {
        this(world);
        this.limbs = limbInfo;
        for (LimbInfo li : limbs) {
            IDimensionSlice idc = li.idc.getEntity();
            idc.setController(this);
            if (li.type == LimbType.BODY) {
                body = idc;
                bodyLimbInfo = li;
            } else if (li.type == LimbType.LEG) {
                idc.permit(DeltaCapability.VIOLENT_COLLISIONS);
            }
        }
        this.arm_size = arm_size;
        this.arm_length = arm_length;
        this.leg_size = leg_size;
        this.leg_length = leg_length;
        this.body_width = width;
        calcLimbParity();
        setTotalCracks(getNaturalCrackCount() + 1);
    }
    
    protected void calcLimbParity() {
        byte left_leg_parity = 0, left_arm_parity = 0, right_leg_parity = 0, right_arm_parity = 0;
        discover_limb_parities: for (LimbInfo li : limbs) {
            byte use_parity = 0;
            switch (li.type) {
            default: continue discover_limb_parities;
            case ARM:
                if (li.side == BodySide.RIGHT) {
                    use_parity = right_arm_parity++;
                } else if (li.side == BodySide.LEFT) {
                    use_parity = left_arm_parity++;
                } else {
                    continue;
                }
                break;
            case LEG:
                if (li.side == BodySide.RIGHT) {
                    use_parity = right_leg_parity++;
                } else if (li.side == BodySide.LEFT) {
                    use_parity = left_leg_parity++;
                } else {
                    continue;
                }
                break;
            }
            li.parity = use_parity;
        }
    }
    
    void loadLimbs() {
        int i = 0;
        for (LimbInfo li : limbs) {
            IDimensionSlice idc = li.idc.getEntity();
            if (idc == null) {
                // It's possible to get in a state where all the DSEs are dead...
                return;
            }
            idc.setController(this);
            if (li.type == LimbType.BODY) {
                body = idc;
                bodyLimbInfo = li;
            }
            idc.setPartName(li.side + " " + li.type + "#" + (i++));
        }
        setup = true;
    }

    public void putData(DataHelper data) throws IOException {
        int limb_count = data.as(Share.PRIVATE, "limbCount").putInt(limbs == null ? 0 : limbs.length);
        if (data.isReader()) {
            limbs = new LimbInfo[limb_count];
        }
        for (int i = 0; i < limbs.length; i++) {
            if (data.isReader()) {
                limbs[i] = new LimbInfo(worldObj);
            }
            limbs[i].putData(data, i);
        }
        walk_controller.serialize("walk", data);
        ai_controller.serialize("ai", data);
        
        
        leg_size = data.as(Share.VISIBLE, "legSize").putInt(leg_size);
        arm_size = data.as(Share.VISIBLE, "armSize").putInt(arm_size);
        arm_length = data.as(Share.VISIBLE, "armLength").putInt(arm_length);
        body_width = data.as(Share.VISIBLE, "bodyWidth").putInt(body_width);
        if (data.isReader() && data.isNBT() && body_width < 1) {
            body_width = 5; // Well, whatever!
        }
        home = data.as(Share.PRIVATE, "home").putIDS(home);
        if (data.as(Share.PRIVATE, "has_path_target").putBoolean(path_target != null)) {
            path_target = data.as(Share.PRIVATE, "path_target").putIDS(path_target);
        }
        setTotalCracks(data.as(Share.VISIBLE, "cracks").putInt(getTotalCracks()));
        setDestroyedCracks(data.as(Share.VISIBLE, "broken").putInt(getDestroyedCracks()));
        turningDirection = data.as(Share.PRIVATE, "turningDirection").putInt(turningDirection);
        target_changed = data.as(Share.PRIVATE, "targetChanged").putBoolean(target_changed);
        walked = data.as(Share.PRIVATE, "walked").putDouble(walked);
        target_count = data.as(Share.PRIVATE, "target_count").putInt(target_count);
        target_y = data.as(Share.PRIVATE, "target_y").putDouble(target_y);
        last_step_direction = data.as(Share.PRIVATE, "last_step_direction").putInt(last_step_direction);
        been_hurt = data.as(Share.PRIVATE, "been_hurt").putBoolean(been_hurt);
        spin_direction = data.as(Share.PRIVATE, "spin_dir").putEnum(spin_direction);
        confused = data.as(Share.PRIVATE, "confused").putBoolean(confused);
    }
    
    <E extends Enum<E>> EnumSet<E> putEnumSet(DataHelper data, String prefix, EnumSet<E> set, Class<E> elementType) throws IOException {
        if (!data.isNBT()) return set;
        if (data.isWriter()) {
            for (E e : set) {
                data.asSameShare(prefix + e.name()).putBoolean(true);
            }
            return set;
        } else {
            EnumSet<E> ret = EnumSet.noneOf(elementType);
            for (E e : elementType.getEnumConstants()) {
                if (data.asSameShare(prefix + e.name()).putBoolean(false)) {
                    ret.add(e);
                }
            }
            return ret;
        }
    }
    
    @Override
    public void onEntityUpdate() {
        if (worldObj.isRemote) {
            client_ticks++;
            return;
        }
        if (!setup) {
            loadLimbs();
            if (home == null) {
                home = new Coord(this);
            }
            if (!setup) return;
        }
        if (body == null || body.isDead()) {
            setDead();
            return;
        }
        walk_controller.tick();
        updateBlockClimb();
        ai_controller.tick();
        /*if (atTarget()) {
            int d = 16;
            int dx = worldObj.rand.nextInt(d) - d/2;
            int dz = worldObj.rand.nextInt(d) - d/2;
            path_target = home.copy().add(dx * 2, 0, dz * 2);
        }*/
        SpaceUtil.toEntPos(this, body.getTransform().getPos());
    }

    public boolean atTarget() {
        if (path_target == null) return true;
        double d = 0.1;
        Vec3 bodyPos = body.getTransform().getPos();
        double dx = path_target.x - bodyPos.xCoord;
        double dz = path_target.z - bodyPos.zCoord;
        
        if (dx < d && dx > -d && dz < d && dz > -d) {
            return true;
        }
        return false;
    }
    
    public void setTarget(Coord at) {
        if (at == null && path_target == null) return;
        if (at != null && path_target != null && at.equals(path_target)) return;
        if (at != null) {
            double dist = new Coord(this).distance(at);
        }
        path_target = at;
        target_changed = true;
    }
    
    public Coord getTarget() {
        return path_target;
    }
    
    boolean targetChanged() {
        boolean ret = target_changed;
        target_changed = false;
        return ret;
    }
    
    public void goHome() {
        setTarget(home.copy());
    }
    
    public Coord getHome() {
        return home;
    }
    
    public void resetLimbs(int time, Interpolation interp) {
        for (LimbInfo li : limbs) {
            if (li.type == LimbType.BODY) continue;
            li.lastTurnDirection = 0;
            IDimensionSlice idc = li.idc.getEntity();
            if (idc == null) continue;
            idc.getVel().setRot(new Quaternion());
            li.reset((int)(time * getSpeedScale()), interp);
        }
    }
    
    public int getNaturalCrackCount() {
        int ls = leg_size + 1;
        int count = ls * ls;
        return count;
    }

    @Override
    public float getMaxHealth() {
        return getTotalCracks() * 3;
    }
    
    public int getDestroyedCracks() {
        return dataWatcher.getWatchableObjectInt(_destroyed_cracked_block_id);
    }
    
    public void setDestroyedCracks(int newCount) {
        dataWatcher.updateObject(_destroyed_cracked_block_id, newCount);
    }

    public void setHacked() {
        setDestroyedCracks(getTotalCracks());
    }
    
    public int getTotalCracks() {
        return dataWatcher.getWatchableObjectInt(_unbroken_cracked_block_id);
    }
    
    public void setTotalCracks(int newCount) {
        dataWatcher.updateObject(_unbroken_cracked_block_id, newCount);
    }

    @Override
    public float getHealth() {
        float wiggle = 1 - 0.1F * (current_name / (float) max_names);
        return (getTotalCracks() - getDestroyedCracks()) * wiggle;
    }
    
    public void crackBroken() {
        setDestroyedCracks(getDestroyedCracks() + 1);
        been_hurt = true;
    }
    
    static int max_names = -1;
    transient int current_name = 0;
    transient int client_ticks = 0;

    @Override
    public IChatComponent getDisplayName() {
        if (getDestroyedCracks() == 0) {
            return new ChatComponentTranslation("colossus.name.null");
        }
        if (max_names == -1) {
            try {
                max_names = Integer.parseInt(LangUtil.translate("colossus.name.count"));
            } catch (NumberFormatException e) {
                max_names = 1;
            } 
        }
        if (getHealth() <= 0) {
            return new ChatComponentTranslation("colossus.name.true");
        }
        if ((client_ticks % 300 < 60 && client_ticks % 4 == 0) || client_ticks % 50 == 0) {
            current_name = worldObj.rand.nextInt(max_names);
        }
        return new ChatComponentTranslation("colossus.name." + current_name);
    }
    
    void updateBlockClimb() {
        if (walk_controller.state != WalkState.FORWARD) {
            // Could sorta do it while TURNING as well, but it seems glitchier
            last_pos_hash = -1;
            return;
        }
        if (ticksExisted <= 5) return; // Make sure limbs are in position
        if (getHealth() <= 0) {
            return;
        }
        int currentPosHash = new Coord(this).hashCode();
        TransformData<Pure> vel = body.getVel();
        if (currentPosHash != last_pos_hash) {
            last_pos_hash = currentPosHash;
            int dy = new HeightCalculator().calc();
            double new_target = (int) (posY) + dy;
            if (new_target != (int) target_y) {
                target_y = new_target;
            }
            if (last_step_direction != dy) {
                last_step_direction = dy;
                if (dy == 0 && target_y == posY) {
                    vel.setY(0);
                    return;
                }
            }
        }
        double maxV = 3.0/16.0;
        int sign = posY > target_y ? -1 : +1;
        if (posY == target_y) sign = 0;
        double delta = Math.abs(posY - target_y);
        vel.setY(sign * Math.min(maxV, delta));
    }

    class HeightCalculator implements ICoordFunction {
        // If most blocks are solid, move up.
        // If most blocks are supported, stay still
        // If most blocks are unsupported, fall down
        int unsupported, supported, inside;

        int calc() {
            int leg_height = 0;
            for (LimbInfo li : limbs) {
                if (li.type == LimbType.LEG) {
                    leg_height = li.length + 1;
                    break;
                }
            }

            double rage = body.getTransform().getPos().yCoord - leg_height;
            // hiccup? if (Math.abs(rage - Math.round(rage)) < 0.1 && rage != Math.round(rage)) { }
            int y = (int) rage; //Math.ceil(rage);

            for (LimbInfo li : limbs) {
                if (li.type != LimbType.LEG) continue;
                IDimensionSlice idc = li.idc.getEntity();
                Coord at = new Coord(idc.getEntity());
                at.y = y;
                Coord min = at.copy();
                Coord max = at.copy();
                int half = (int) ((leg_size + 1) / 2);
                min.x -= half;
                min.z -= half;
                max.x += half;
                max.z += half;
                Coord.iterateCube(min, max, this);
            }
            int s = leg_size + 1;
            int leg_area = s * s * 2;
            if (unsupported > leg_area * 0.5) {
                if (supported < leg_area * 0.5) {
                    return -1;
                }
            }

            if (inside > leg_area * 0.8) {
                return +1;
            }
            return 0;
        }
        
        @Override
        public void handle(Coord here) {
            if (here.isSolid()) inside++;
            here.y--;
            if (here.isSolid()) {
                supported++;
            } else {
                unsupported++;
            }
        }
    }
    
    boolean checkHurt(boolean reset) {
        if (been_hurt && reset) {
            been_hurt = false;
            float pitch = 4 * getHealth() / (getMaxHealth() + 0.1F);
            worldObj.playSoundAtEntity(this, "factorization:colossus.hurt", 1, pitch);
            return true;
        }
        return been_hurt;
    }
    
    boolean canTargetPlayer(Entity player) {
        double max_dist = 24 * 24 * leg_size;
        double max_home_dist = 32 * 32;
        if (getDistanceSqToEntity(player) > max_dist) return false;
        if (getHome().distanceSq(new Coord(player)) > max_home_dist) return false;
        return true;
    }

    @Override
    public boolean placeBlock(IDimensionSlice idc, EntityPlayer player, Coord at) {
        return false;
    }

    @Override
    public boolean breakBlock(IDimensionSlice idc, EntityPlayer player, Coord at, EnumFacing sideHit) {
        if (at.getBlock() != Core.registry.colossal_block) return false;
        if (at.has(ColossalBlock.VARIANT, ColossalBlock.Md.BODY_CRACKED, ColossalBlock.Md.MASK_CRACKED)) {
            crackBroken();
        }
        return false;
    }

    @Override
    public boolean hitBlock(IDimensionSlice idc, EntityPlayer player, Coord at, EnumFacing sideHit) {
        return false;
    }

    @Override
    public boolean useBlock(IDimensionSlice idc, EntityPlayer player, Coord at, EnumFacing sideHit) {
        return false;
    }

    @Override
    public void idcDied(IDimensionSlice idc) { }

    @Override
    public void beforeUpdate(IDimensionSlice idc) { }

    @Override
    public void afterUpdate(IDimensionSlice idc) { }

    public double getStrikeSpeedScale() {
        return 1;
    }

    public double getSpeedScale() {
        if (peaceful()) return 1;
        return 2;
    }

    @Override
    public boolean onAttacked(IDimensionSlice idc, DamageSource damageSource, float damage) {
        if (damageSource.isExplosion()) {
            confused = true;
        }
        return false;
    }

    @Override
    public CollisionAction collidedWithWorld(World realWorld, AxisAlignedBB realBox, World shadowWorld, AxisAlignedBB shadowBox) {
        return CollisionAction.IGNORE;
    }

    public boolean peaceful() {
        return worldObj.getDifficulty() == EnumDifficulty.PEACEFUL;
    }

    public int getRemainingRotationTime(LimbInfo limb) {
        ITransformOrder order = limb.idc.getEntity().getOrder();
        if (order.isNull()) return 0;
        if (order instanceof BasicTransformOrder) {
            BasicTransformOrder bto = (BasicTransformOrder) order;
            return bto.getTicksRemaining();
        }
        return 0;
        // Or we could return '1' forever? Dunno.
    }

    public int getRemainingRotationTime() {
        return getRemainingRotationTime(bodyLimbInfo);
    }
}
