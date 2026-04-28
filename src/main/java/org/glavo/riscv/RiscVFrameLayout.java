package org.glavo.riscv;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.jetbrains.annotations.NotNullByDefault;

/// Defines Truffle frame slots used by the hot RISC-V execution path.
@NotNullByDefault
final class RiscVFrameLayout {
    /// The first static frame slot containing writable integer registers.
    private static final int X_BASE = 0;

    /// The number of writable integer registers, excluding hardwired `x0`.
    private static final int X_COUNT = 31;

    /// The total number of static frame slots used by this layout.
    private static final int SLOT_COUNT = X_BASE + X_COUNT;

    /// Prevents instantiation.
    private RiscVFrameLayout() {
    }

    /// Creates a frame descriptor with static long slots for `x1..x31`.
    static FrameDescriptor newDescriptor() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder(SLOT_COUNT);
        builder.addSlots(SLOT_COUNT, FrameSlotKind.Static);
        return builder.build();
    }

    /// Returns the static frame slot for a writable integer register.
    static int xSlot(int register) {
        if (register < 1 || register > 31) {
            throw new AssertionError("Invalid frame register index: " + register);
        }
        return X_BASE + register - 1;
    }

    /// Reads an integer register from the frame, returning zero for `x0`.
    static long readX(VirtualFrame frame, int register) {
        return register == 0 ? 0 : frame.getLongStatic(xSlot(register));
    }

    /// Writes an integer register to the frame, ignoring writes to `x0`.
    static void writeX(VirtualFrame frame, int register, long value) {
        if (register != 0) {
            frame.setLongStatic(xSlot(register), value);
        }
    }

    /// Loads the architectural integer registers into the execution frame.
    @ExplodeLoop
    static void loadIntegerRegisters(VirtualFrame frame, MachineState state) {
        for (int register = 1; register <= 31; register++) {
            frame.setLongStatic(xSlot(register), state.decodedRegister(register));
        }
    }

    /// Spills the execution frame integer registers back into architectural state.
    @ExplodeLoop
    static void spillIntegerRegisters(VirtualFrame frame, MachineState state) {
        for (int register = 1; register <= 31; register++) {
            state.setDecodedRegister(register, frame.getLongStatic(xSlot(register)));
        }
    }
}
