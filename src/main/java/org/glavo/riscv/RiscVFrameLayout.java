package org.glavo.riscv;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.jetbrains.annotations.NotNullByDefault;

/// Defines Truffle frame slots used by the hot RISC-V execution path.
@NotNullByDefault
final class RiscVFrameLayout {

    /// The total number of static frame slots used by this layout.
    private static final int SLOT_COUNT = 32;

    /// Prevents instantiation.
    private RiscVFrameLayout() {
    }

    /// Creates a frame descriptor with static long slots for `x0..x31`.
    static FrameDescriptor newDescriptor() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder(SLOT_COUNT);
        builder.addSlots(SLOT_COUNT, FrameSlotKind.Static);
        return builder.build();
    }

    /// Reads an integer register from the frame.
    static long readX(VirtualFrame frame, int register) {
        return frame.getLongStatic(register);
    }

    /// Writes an integer register to the frame, ignoring writes to `x0`.
    static void writeX(VirtualFrame frame, int register, long value) {
        if (register != 0) {
            frame.setLongStatic(register, value);
        }
    }

    /// Loads the architectural integer registers into the execution frame.
    @ExplodeLoop
    static void loadIntegerRegisters(VirtualFrame frame, MachineState state) {
        for (int register = 0; register < SLOT_COUNT; register++) {
            frame.setLongStatic(register, state.decodedRegister(register));
        }
    }

    /// Spills the execution frame integer registers back into architectural state.
    @ExplodeLoop
    static void spillIntegerRegisters(VirtualFrame frame, MachineState state) {
        for (int register = 1; register < SLOT_COUNT; register++) {
            state.setDecodedRegister(register, frame.getLongStatic(register));
        }
    }
}
