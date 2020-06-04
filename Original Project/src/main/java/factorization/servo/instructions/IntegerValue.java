package factorization.servo.instructions;

import factorization.api.Coord;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.IDataSerializable;
import factorization.servo.Instruction;
import factorization.servo.ServoMotor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

import java.io.IOException;

public class IntegerValue extends Instruction {
    private int val = 1;

    @Override
    public IDataSerializable putData(String prefix, DataHelper data) throws IOException {
        setVal(data.asSameShare(prefix + "val").putInt(getVal()));
        return this;
    }

    @Override
    protected Object getRecipeItem() {
        return new ItemStack(Blocks.iron_bars);
    }

    @Override
    public void motorHit(ServoMotor motor) {
        motor.getArgStack().push(getVal());
    }

    @Override
    public String getName() {
        return "fz.instruction.integervalue";
    }
    
    @Override
    public boolean onClick(EntityPlayer player, Coord block, EnumFacing side) {
        if (playerHasProgrammer(player)) {
            if (getVal() == -1) {
                setVal(1);
                return true;
            } else if (getVal() == 1) {
                setVal(0);
                return true;
            } else if (getVal() == 0) {
                setVal(-1);
                return true;
            }
        }
        return false;
    }
    
    @Override
    public String getInfo() {
        return "" + getVal();
    }

    public int getVal() {
        return val;
    }

    public void setVal(int val) {
        this.val = val;
    }
}
