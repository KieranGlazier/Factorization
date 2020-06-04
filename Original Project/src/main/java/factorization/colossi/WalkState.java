package factorization.colossi;

import factorization.api.Coord;
import factorization.api.Quaternion;
import factorization.colossi.ColossusController.BodySide;
import factorization.colossi.ColossusController.LimbType;
import factorization.fzds.interfaces.IDimensionSlice;
import factorization.fzds.interfaces.Interpolation;
import factorization.fzds.interfaces.transform.Pure;
import factorization.fzds.interfaces.transform.TransformData;
import factorization.util.SpaceUtil;
import net.minecraft.block.Block;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

public enum WalkState implements IStateMachine<WalkState> {
    IDLE {
        final Technique[] idle_interrupters = new Technique[] {};

        @Override
        protected Technique[] getInterrupters() {
            return idle_interrupters;
        }

        @Override
        public WalkState tick(ColossusController controller, int age) {
            return controller.atTarget() ? this : TURN;
        }
    },
    TURN {
        final Technique[] turn_interrupters = new Technique[] {
                Technique.DEATH_FALL
        };

        @Override
        protected Technique[] getInterrupters() {
            return turn_interrupters;
        }

        @Override
        public void onEnterState(ColossusController controller, WalkState prevState) {
            checkRotation(controller);
            final double arms_angle = Math.PI * 0.45;
            for (LimbInfo limb : controller.limbs) {
                IDimensionSlice idc = limb.idc.getEntity();
                if (idc == null) continue;
                if (limb.type == LimbType.ARM) {
                    if ((limb.side == BodySide.LEFT) ^ controller.turningDirection == 1) {
                        limb.target(new Quaternion(), 1 * controller.getSpeedScale());
                    } else {
                        double arm_angle = arms_angle * (limb.side == BodySide.LEFT ? +1 : -1);
                        Quaternion ar = Quaternion.getRotationQuaternionRadians(arm_angle, EnumFacing.EAST);
                        limb.target(ar, 1 * controller.getSpeedScale());
                    }
                    limb.creak();
                }
            }
        }
        
        @Override
        public WalkState tick(ColossusController controller, int age) {
            if (interruptWalk(controller)) return IDLE;
            if (controller.atTarget() || controller.targetChanged()) return IDLE;
            playStepSounds(controller, age);
            if (!controller.body.hasOrders()) return checkRotation(controller);
            
            // System no longer supports joint displacement, but if it did:
            // double lift_height = 1.5F/16F;
            double base_twist = Math.PI * 2 * 0.03;
            double phase_length = 36; //18;
            for (LimbInfo limb : controller.limbs) {
                // Twist the legs while the body turns
                IDimensionSlice idc = limb.idc.getEntity();
                if (idc == null) continue;
                if (limb.isTurning()) continue;
                if (limb.type != LimbType.LEG) continue;
                double nextRotation = base_twist;
                double nextRotationTime = phase_length;
                
                // This is how it *ought* to work, but there's some weird corner case that I can't figure out. -_-
                // So the turning direction is tracked by a variable instead, which is less robust.
                // double dr = body.getRotation().dotProduct(down) - currentRotation.dotProduct(down);
                // nextRotation *= -Math.signum(dr);1
                
                limb.lastTurnDirection *= -1;
                Interpolation interp = Interpolation.SMOOTH;
                if (limb.lastTurnDirection == 0) {
                    limb.lastTurnDirection = (byte) (controller.turningDirection * (limb.limbSwingParity() ? 1 : -1));
                }
                if (limb.lastTurnDirection == 1 ^ limb.limbSwingParity()) {
                    interp = Interpolation.CUBIC;
                }
                nextRotation *= limb.lastTurnDirection;
                
                Quaternion nr = Quaternion.getRotationQuaternionRadians(nextRotation, EnumFacing.DOWN);
                if (limb.lastTurnDirection == controller.turningDirection) {
                    // Lift a leg up a tiny bit
                    nr.incrMultiply(Quaternion.getRotationQuaternionRadians(Math.toRadians(2), EnumFacing.SOUTH));
                }
                limb.setTargetRotation(nr, (int) (nextRotationTime * controller.getSpeedScale()), interp);
                limb.creak();
            }
            
            return this;
        }
        
        WalkState checkRotation(ColossusController controller) {
            if (controller.atTarget()) return IDLE;
            IDimensionSlice body = controller.body;
            Vec3 target = controller.getTarget().createVector();
            target = SpaceUtil.setY(target, controller.posY);
            TransformData<Pure> transform = body.getTransform();
            Vec3 me = transform.getPos();
            Vec3 delta = me.subtract(target);
            double angle = Math.atan2(delta.xCoord, delta.zCoord) - Math.PI / 2;
            Quaternion target_rotation = Quaternion.getRotationQuaternionRadians(angle, EnumFacing.UP);
            Quaternion current_rotation = transform.getRot();
            int size = controller.leg_size + 1;
            double rotation_distance = (((Math.toDegrees(target_rotation.getAngleBetween(current_rotation)) % 360) + 360) % 360) / 360;
            double rotation_speed = 80;
            double rotation_time = rotation_distance * rotation_speed;
            if (rotation_time >= 10) {
                controller.bodyLimbInfo.setTargetRotation(target_rotation, (int) (rotation_time * controller.getSpeedScale()), Interpolation.SMOOTH);
                // Now bodyLimbInfo.isTurning() is set.
                controller.turningDirection = angle > 0 ? 1 : -1;
                for (LimbInfo li : controller.limbs) {
                    li.lastTurnDirection = 0;
                }
            } else if (rotation_time > 0.001) {
                transform.setRot(target_rotation);
                body.getVel().setRot(new Quaternion());
            } else {
                return FORWARD;
            }
            return TURN;
        }
        
        @Override
        public void onExitState(ColossusController controller, WalkState nextState) {
            controller.turningDirection = 0;
            controller.resetLimbs(20, Interpolation.SMOOTH);

            controller.body.getVel().mulPos(new Vec3(1, 0, 1));
            // Might not be quite where this belongs. Stop moving after block climbing.
        }
    },
    FORWARD {
        final Technique[] forward_interrupters = new Technique[] {
                Technique.DEATH_FALL,
                Technique.HIT_WITH_LIMB,
        };

        @Override
        protected Technique[] getInterrupters() {
            return forward_interrupters;
        }

        @Override
        public void onEnterState(ColossusController controller, WalkState prevState) {
            if (controller.atTarget()) return;
            IDimensionSlice body = controller.body;
            Vec3 target = controller.getTarget().createVector();
            target = SpaceUtil.setY(target, controller.posY);
            Vec3 me = body.getTransform().getPos();
            Vec3 delta = me.subtract(target);
            double walk_speed = Math.min(MAX_WALK_SPEED, delta.lengthVector()) * controller.getSpeedScale();
            delta = delta.normalize();
            body.getVel().setPos(delta.xCoord * walk_speed, 0, delta.zCoord * walk_speed);
            controller.walked += walk_speed;
            controller.resetLimbs(20, Interpolation.SMOOTH);
        }
        
        private final double max_leg_swing_degrees = 22.5;
        private final double max_leg_swing_radians = Math.toRadians(max_leg_swing_degrees);
        private final Quaternion arm_hang = Quaternion.getRotationQuaternionRadians(Math.toRadians(5), EnumFacing.EAST);
        private final int SPEED = 2;
        private final double MAX_WALK_SPEED = SPEED / 20.0;
        
        
        @Override
        public WalkState tick(ColossusController controller, int age) {
            if (interruptWalk(controller)) return IDLE;
            if (controller.atTarget() || controller.targetChanged()) return IDLE;
            playStepSounds(controller, age);
            
            
            final double legCircumference = 2 * Math.PI * controller.leg_size;
            final double swingTime = legCircumference * 360 / (2 * max_leg_swing_degrees * SPEED);
            
            
            for (LimbInfo limb : controller.limbs) {
                if (limb.type != LimbType.LEG && limb.type != LimbType.ARM) continue;
                if (limb.isTurning() && !(limb.type == LimbType.LEG && age == 0)) continue;
                IDimensionSlice idc = limb.idc.getEntity();
                if (idc == null) continue;
                double nextRotationTime = swingTime;
                int p = limb.limbSwingParity() ? 1 : -1;
                if (limb.lastTurnDirection == 0) {
                    // We were standing straight; begin with half a swing
                    nextRotationTime /= 2;
                    limb.lastTurnDirection = (byte) p;
                } else {
                    // Swing the other direction
                    limb.lastTurnDirection *= -1;
                    p = limb.lastTurnDirection;
                }
                if (controller.walked == 0) {
                    p = 0;
                }
                Quaternion nextRotation = Quaternion.getRotationQuaternionRadians(max_leg_swing_radians * p, EnumFacing.NORTH);
                if (limb.type == LimbType.ARM) {
                    if (limb.side == BodySide.LEFT) {
                        nextRotation.incrMultiply(arm_hang);
                    } else {
                        nextRotation.incrMultiply(arm_hang.conjugate());
                    }
                }
                limb.setTargetRotation(nextRotation, (int) (nextRotationTime * controller.getSpeedScale()), Interpolation.SMOOTH);
                limb.creak();
            }
            
            return this;
        }

        @Override
        public void onExitState(ColossusController controller, WalkState nextState) {
            controller.resetLimbs(20, Interpolation.SMOOTH);
            IDimensionSlice body = controller.body;
            // Reached our destination
            body.getVel().setPos(0, 0 /* Might not be quite where this belongs. Stop moving after block climbing. */, 0);
        }
    }
    ;

    @Override
    public abstract WalkState tick(ColossusController controller, int age);

    @Override
    public void onEnterState(ColossusController controller, WalkState prevState) { }

    @Override
    public void onExitState(ColossusController controller, WalkState nextState) { }

    protected abstract Technique[] getInterrupters();

    boolean interruptWalk(ColossusController controller) {
        for (Technique tech : getInterrupters()) {
            if (!tech.usable(controller)) continue;
            if (controller.ai_controller.getState() == tech) break; // unlikely? Probably wouldn't be a huge problem anyways.
            controller.ai_controller.forceState(tech);
            controller.setTarget(null);
            return true;
        }
        return false;
    }

    void playStepSounds(ColossusController controller, int age) {
        if (age % 35 != 0) return;
        for (LimbInfo limb : controller.limbs) {
            if (limb.type != LimbType.LEG) continue;
            IDimensionSlice idc = limb.idc.getEntity();
            if (idc == null) continue;
            Coord min = idc.getMinCorner();
            Coord max = idc.getMaxCorner();
            Coord.sort(min, max);
            max.y = min.y;
            Vec3 shadowFoot = min.centerVec(max);
            Vec3 realFoot = idc.shadow2real(shadowFoot);

            Coord stomped = new Coord(controller.worldObj, realFoot);
            stomped.y--;

            if (stomped.isAir()) continue;
            Block.SoundType sound = stomped.getBlock().stepSound;
            if (sound == null) continue;
            idc.getRealWorld().playSoundEffect(realFoot.xCoord, realFoot.yCoord, realFoot.zCoord, sound.getStepSound(), sound.getFrequency() * 0.9F, sound.getVolume() * 1.1F);
        }
    }
}
