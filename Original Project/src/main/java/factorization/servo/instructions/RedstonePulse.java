package factorization.servo.instructions;

import factorization.api.Coord;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.IDataSerializable;
import factorization.servo.CpuBlocking;
import factorization.servo.Instruction;
import factorization.servo.ServoMotor;
import factorization.shared.TileEntityCommon;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

import java.io.IOException;

public class RedstonePulse extends Instruction {

    @Override
    public IDataSerializable putData(String prefix, DataHelper data) throws IOException {
        return this;
    }

    @Override
    public void motorHit(ServoMotor motor) {
        if (motor.worldObj.isRemote) {
            return;
        }
        TileEntityCommon tef = motor.getCurrentPos().getTE(TileEntityCommon.class);
        if (tef == null) {
            return; //Just back away, very slowly...
        }
        tef.pulse();
    }

    @Override
    public boolean onClick(EntityPlayer player, Coord block, EnumFacing side) {
        return false;
    }

    @Override
    public boolean onClick(EntityPlayer player, ServoMotor motor) {
        return false;
    }

    @Override
    public String getName() {
        return "fz.instruction.pulse";
    }

    @Override
    protected Object getRecipeItem() {
        return new ItemStack(Blocks.stone_pressure_plate);
    }
    
    @Override
    public CpuBlocking getBlockingBehavior() {
        return CpuBlocking.BLOCK_UNTIL_NEXT_ENTRY;
    }
}
