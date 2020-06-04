package factorization.servo.instructions;

import factorization.api.Coord;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.IDataSerializable;
import factorization.api.datahelpers.Share;
import factorization.servo.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

import java.io.IOException;

public class Jump extends Instruction {
    byte mode = Executioner.JMP_NEXT_INSTRUCTION;
    @Override
    public IDataSerializable putData(String prefix, DataHelper data) throws IOException {
        mode = data.as(Share.MUTABLE, "mode").putByte(mode);
        return this;
    }

    @Override
    protected Object getRecipeItem() {
        return new ItemStack(Blocks.piston);
    }

    @Override
    public void motorHit(ServoMotor motor) {
        if (mode == Executioner.JMP_NEXT_INSTRUCTION) {
            Boolean b = motor.getArgStack().popType(Boolean.class);
            motor.executioner.markDirty();
            if (b == null) {
                motor.putError("Jump: Stack Underflow of Boolean");
                return;
            }
            if (b) {
                ServoStack ss = motor.getInstructionsStack();
                if (ss.getSize() > 0) {
                    ss.pop();
                } else {
                    motor.executioner.jmp = mode;
                }
            }
        } else {
            motor.executioner.jmp = mode;
        }
    }

    @Override
    public boolean onClick(EntityPlayer player, Coord block, EnumFacing side) {
        if (!playerHasProgrammer(player)) {
            return super.onClick(player, block, side);
        }
        if (mode == Executioner.JMP_NEXT_INSTRUCTION) {
            mode = Executioner.JMP_NEXT_TILE;
        } else {
            mode = Executioner.JMP_NEXT_INSTRUCTION;
        }
        return true;
    }
    
    @Override
    public String getInfo() {
        if (mode == Executioner.JMP_NEXT_INSTRUCTION) {
            return "Jump next";
        } else if (mode == Executioner.JMP_NEXT_TILE) {
            return "Unconditional Skip";
        } else {
            return "?";
        }
    }

    @Override
    public String getName() {
        return "fz.instruction.jmp";
    }
    
    @Override
    public CpuBlocking getBlockingBehavior() {
        return CpuBlocking.BLOCK_FOR_TICK;
    }

}
