package factorization.api;

import factorization.api.adapter.InterfaceAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

/**
 * Modeled after IC2ex's {@link ic2.api.energy.tile.IKineticSource}.
 *
 */
public interface IRotationalEnergySource {
    static InterfaceAdapter<TileEntity, IRotationalEnergySource> adapter = InterfaceAdapter.get(IRotationalEnergySource.class);

    /**
     * @param direction the direction that this can emit energy from. For example, if this is a vertical windmill,
     *                  then the sails would be above this TileEntity, and the power would be accessed using
     *                  <code>availableEnergy(DOWN)</code>.
     * @return true if a connection in that direction can be made.
     */
    boolean canConnect(EnumFacing direction);

    /**
     * @param direction {@see IRotationalEnergySource#canConnect}
     * @return how much power is still available for use this tick. The units are those of torque * angular speed
     * This value should always be positive, even if the velocity is negative.
     */
    double availableEnergy(EnumFacing direction);

    /**
     * Takes the power that is available for this tick.
     * @param direction {@see IRotationalEnergySource#canConnect}
     * @param maxPower The maximum amount of power to deplete. This value must be positive.
     * @return The amount of power that was actually used, limited by actual availability.
     */
    double takeEnergy(EnumFacing direction, double maxPower);

    /**
     * @param direction {@see IRotationalEnergySource#canConnect}
     * @return The angular velocity, in radians per tick. May be negative. If a windmill is causing a shaft to turn
     * clockwise (looking down the shaft from the position of the windmill), then its angular velocity is positive.
     *
     * This value should be kept synchronized with the client, but need not be exact.
     */
    double getVelocity(EnumFacing direction);

    /**
     * Speeds higher than this are going to render badly due to FPS limits. If the speed would go above this,
     * consider increasing availableEnergy() without increasing velocity.
     */
    double MAX_SPEED = Math.PI / 8;

    /**
     * <code>return ((TileEntity) this).isInvalid();</code>
     * the deobfuscator is simply TOO LAME to rename this method for us.
     */
    boolean isTileEntityInvalid();
}
