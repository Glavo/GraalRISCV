package org.glavo.riscv;

import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import org.jetbrains.annotations.NotNullByDefault;

/// Bytecode DSL root used to execute one decoded RISC-V basic block.
@GenerateBytecode(
        languageClass = RiscVLanguage.class,
        enableUncachedInterpreter = false,
        allowUnsafe = false)
@NotNullByDefault
public abstract class RiscVBytecodeBlockRootNode extends RootNode implements BytecodeRootNode {
    /// Creates a bytecode block root with the generated frame descriptor.
    protected RiscVBytecodeBlockRootNode(RiscVLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    /// Executes a control, CSR, ecall, or ebreak instruction.
    @Operation
    @ConstantOperand(type = RiscVInstructionSemantics.class)
    public static final class ExecuteControl {
        /// Applies the decoded instruction to the machine state.
        @Specialization
        static void execute(RiscVInstructionSemantics instruction, MachineState state) {
            instruction.execute(state);
        }
    }

    /// Executes an integer load instruction.
    @Operation
    @ConstantOperand(type = RiscVInstructionSemantics.class)
    public static final class ExecuteLoad {
        /// Applies the decoded instruction to the machine state.
        @Specialization
        static void execute(RiscVInstructionSemantics instruction, MachineState state) {
            instruction.execute(state);
        }
    }

    /// Executes an integer or floating-point store instruction.
    @Operation
    @ConstantOperand(type = RiscVInstructionSemantics.class)
    public static final class ExecuteStore {
        /// Applies the decoded instruction to the machine state.
        @Specialization
        static void execute(RiscVInstructionSemantics instruction, MachineState state) {
            instruction.execute(state);
        }
    }

    /// Executes an integer arithmetic instruction.
    @Operation
    @ConstantOperand(type = RiscVInstructionSemantics.class)
    public static final class ExecuteInteger {
        /// Applies the decoded instruction to the machine state.
        @Specialization
        static void execute(RiscVInstructionSemantics instruction, MachineState state) {
            instruction.execute(state);
        }
    }

    /// Executes an RV64M multiply or divide instruction.
    @Operation
    @ConstantOperand(type = RiscVInstructionSemantics.class)
    public static final class ExecuteMultiplyDivide {
        /// Applies the decoded instruction to the machine state.
        @Specialization
        static void execute(RiscVInstructionSemantics instruction, MachineState state) {
            instruction.execute(state);
        }
    }

    /// Executes a floating-point arithmetic, conversion, load, or move instruction.
    @Operation
    @ConstantOperand(type = RiscVInstructionSemantics.class)
    public static final class ExecuteFloatingPoint {
        /// Applies the decoded instruction to the machine state.
        @Specialization
        static void execute(RiscVInstructionSemantics instruction, MachineState state) {
            instruction.execute(state);
        }
    }

    /// Executes an RV64A atomic instruction.
    @Operation
    @ConstantOperand(type = RiscVInstructionSemantics.class)
    public static final class ExecuteAtomic {
        /// Applies the decoded instruction to the machine state.
        @Specialization
        static void execute(RiscVInstructionSemantics instruction, MachineState state) {
            instruction.execute(state);
        }
    }

    /// Reads the next PC materialized in the machine state by the block body.
    @Operation
    public static final class ReadPc {
        /// Returns the current guest program counter.
        @Specialization
        static long read(MachineState state) {
            return state.pc();
        }
    }
}
