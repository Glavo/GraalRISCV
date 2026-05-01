// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv;

import org.glavo.riscv.exception.ProgramExitException;
import org.glavo.riscv.memory.Memory;
import org.glavo.riscv.parser.DecodedBlock;
import org.glavo.riscv.parser.ElfImage;
import org.glavo.riscv.parser.RiscVDecoder;
import org.glavo.riscv.runtime.GuestSyscalls;
import org.glavo.riscv.runtime.MachineState;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/// Tests RVV 1.0 vector configuration, CSR, memory, integer, fixed-point, and floating-point behavior.
@NotNullByDefault
public final class VectorInstructionTest {
    /// The guest address used for vector instruction tests.
    private static final long TEST_PC = Memory.DEFAULT_BASE_ADDRESS;

    /// The `vl` vector CSR address.
    private static final int VL_CSR = 0xc20;

    /// The `vtype` vector CSR address.
    private static final int VTYPE_CSR = 0xc21;

    /// The `vlenb` vector CSR address.
    private static final int VLENB_CSR = 0xc22;

    /// The `vcsr` vector CSR address.
    private static final int VCSR_CSR = 0x00f;

    /// The `vill` bit for RV64 `vtype`.
    private static final long VTYPE_VILL = Long.MIN_VALUE;

    /// The Linux syscall register.
    private static final int SYSCALL_REGISTER = 17;

    /// The Linux `exit` syscall number.
    private static final long EXIT_SYSCALL = 93;

    /// The RVA23U64 default vector length in bits.
    private static final int VLEN_BITS = 128;

    /// Verifies `vsetvli`, `vsetivli`, `vsetvl`, and vector CSRs.
    @Test
    public void vectorConfigurationInstructionsUpdateCsrs() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtype(32, 1)),
                    vsetivli(6, 3, vtype(16, 1)),
                    vsetvl(7, 10, 11),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, 10);
            machine.state().setRegister(11, vtype(8, 2));

            runDecodedProgram(machine);

            assertEquals(4, machine.state().register(5));
            assertEquals(3, machine.state().register(6));
            assertEquals(10, machine.state().register(7));
            assertEquals(10, machine.state().readControlStatusRegister(VL_CSR));
            assertEquals(vtype(8, 2), machine.state().readControlStatusRegister(VTYPE_CSR));
            assertEquals(VLEN_BITS / Byte.SIZE, machine.state().readControlStatusRegister(VLENB_CSR));
        }
    }

    /// Verifies vector fixed-point control CSR aliases.
    @Test
    public void vectorControlStatusRegisterAliasesUpdateState() {
        try (TestMachine machine = TestMachine.create()) {
            machine.state().writeControlStatusRegister(VCSR_CSR, 0x5);

            assertEquals(0x5, machine.state().readControlStatusRegister(VCSR_CSR));
        }
    }

    /// Verifies unsupported vector types set `vill` and reset `vl`.
    @Test
    public void unsupportedVectorTypeSetsVill() {
        try (TestMachine machine = TestMachine.create()) {
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtypeRaw(64, 5)),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, 1);

            runDecodedProgram(machine);

            assertEquals(0, machine.state().register(5));
            assertEquals(0, machine.state().readControlStatusRegister(VL_CSR));
            assertEquals(VTYPE_VILL, machine.state().readControlStatusRegister(VTYPE_CSR));
        }
    }

    /// Verifies unit-stride vector loads, integer arithmetic, and stores.
    @Test
    public void vectorLoadAddAndStoreExecute() {
        try (TestMachine machine = TestMachine.create()) {
            long input = TEST_PC + 256;
            long output = TEST_PC + 512;
            for (int index = 0; index < 4; index++) {
                machine.memory().writeInt(input + (long) index * Integer.BYTES, index + 1);
            }
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtype(32, 1)),
                    vle(32, 1, 6),
                    vaddVi(2, 1, 5),
                    vse(32, 2, 7),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, 4);
            machine.state().setRegister(6, input);
            machine.state().setRegister(7, output);

            runDecodedProgram(machine);

            for (int index = 0; index < 4; index++) {
                assertEquals(index + 6, machine.memory().readInt(output + (long) index * Integer.BYTES));
            }
        }
    }

    /// Verifies vector-vector, vector-scalar, and masked integer operations.
    @Test
    public void vectorIntegerArithmeticFormsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            long input = TEST_PC + 256;
            long output = TEST_PC + 512;
            for (int index = 0; index < 8; index++) {
                machine.memory().writeShort(input + (long) index * Short.BYTES, (short) (index + 1));
            }
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtype(16, 1)),
                    vle(16, 1, 6),
                    vaddVv(2, 1, 1),
                    vsubVx(3, 2, 11),
                    vorVvMasked(4, 3, 1),
                    vse(16, 4, 7),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, 8);
            machine.state().setRegister(11, 1);
            machine.state().setRegister(6, input);
            machine.state().setRegister(7, output);
            machine.state().vectorUnit().writeElement(0, 0, 0b0101_0101);

            runDecodedProgram(machine);

            for (int index = 0; index < 8; index++) {
                int expected = (index & 1) == 0 ? ((index + 1) * 2 - 1) | (index + 1) : 0;
                assertEquals(expected, machine.memory().readUnsignedShort(output + (long) index * Short.BYTES));
            }
        }
    }

    /// Verifies additional single-width integer arithmetic and merge operations.
    @Test
    public void vectorExtendedIntegerArithmeticExecutes() {
        try (TestMachine machine = TestMachine.create()) {
            long input = TEST_PC + 256;
            int[] values = {1, 2, 3, 4, 0x80, 0xf0, 0x7f, 0xff};
            for (int index = 0; index < values.length; index++) {
                machine.memory().writeByte(input + index, (byte) values[index]);
            }
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtype(8, 1)),
                    vle(8, 1, 6),
                    vminuVx(2, 1, 11),
                    vmaxVx(3, 1, 12),
                    vsrlVi(4, 1, 1),
                    vsraVi(5, 1, 1),
                    vmergeVvm(6, 2, 3),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, values.length);
            machine.state().setRegister(11, 3);
            machine.state().setRegister(12, -2);
            machine.state().setRegister(6, input);
            machine.state().vectorUnit().writeElement(0, 0, 0b0101_0101);

            runDecodedProgram(machine);

            int[] expectedMinUnsigned = {1, 2, 3, 3, 3, 3, 3, 3};
            int[] expectedMaxSigned = {1, 2, 3, 4, 0xfe, 0xfe, 0x7f, 0xff};
            int[] expectedShiftRight = {0, 1, 1, 2, 0x40, 0x78, 0x3f, 0x7f};
            int[] expectedArithmeticShiftRight = {0, 1, 1, 2, 0xc0, 0xf8, 0x3f, 0xff};
            int[] expectedMerge = {1, 2, 3, 3, 0xfe, 3, 0x7f, 3};
            assertVectorBytes(machine.state(), 2, expectedMinUnsigned);
            assertVectorBytes(machine.state(), 3, expectedMaxSigned);
            assertVectorBytes(machine.state(), 4, expectedShiftRight);
            assertVectorBytes(machine.state(), 5, expectedArithmeticShiftRight);
            assertVectorBytes(machine.state(), 6, expectedMerge);
        }
    }

    /// Verifies mask-producing compares and mask logical operations.
    @Test
    public void vectorCompareAndMaskLogicalInstructionsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            long input = TEST_PC + 256;
            int[] values = {1, 2, 3, 4, 0x80, 0xf0, 0x7f, 0xff};
            for (int index = 0; index < values.length; index++) {
                machine.memory().writeByte(input + index, (byte) values[index]);
            }
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtype(8, 1)),
                    vle(8, 1, 6),
                    vmsltuVx(2, 1, 11),
                    vmsgtVi(3, 1, 0),
                    vmandMm(4, 2, 3),
                    vmxorMm(5, 2, 3),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, values.length);
            machine.state().setRegister(11, 4);
            machine.state().setRegister(6, input);

            runDecodedProgram(machine);

            assertEquals(0b0000_0111, machine.state().vectorUnit().readElement(2, 0));
            assertEquals(0b0100_1111, machine.state().vectorUnit().readElement(3, 0));
            assertEquals(0b0000_0111, machine.state().vectorUnit().readElement(4, 0));
            assertEquals(0b0100_1000, machine.state().vectorUnit().readElement(5, 0));
        }
    }

    /// Verifies single-width vector multiply, divide, and remainder operations.
    @Test
    public void vectorMultiplyDivideAndRemainderInstructionsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            long input = TEST_PC + 256;
            int[] values = {12, 13, 0xfff6, 0x8000, 5, 0};
            for (int index = 0; index < values.length; index++) {
                machine.memory().writeShort(input + (long) index * Short.BYTES, (short) values[index]);
            }
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtype(16, 1)),
                    vle(16, 1, 6),
                    vmulVx(2, 1, 11),
                    vdivuVx(3, 1, 11),
                    vdivVx(4, 1, 11),
                    vremVx(5, 1, 11),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, values.length);
            machine.state().setRegister(11, 5);
            machine.state().setRegister(6, input);

            runDecodedProgram(machine);

            assertVectorElements(machine.state(), 2, 60, 65, 0xffce, 0x8000, 25, 0);
            assertVectorElements(machine.state(), 3, 2, 2, 13105, 6553, 1, 0);
            assertVectorElements(machine.state(), 4, 2, 2, 0xfffe, 0xe667, 1, 0);
            assertVectorElements(machine.state(), 5, 2, 3, 0, 0xfffd, 0, 0);
        }
    }

    /// Verifies single-width vector high-half multiply operations.
    @Test
    public void vectorMultiplyHighInstructionsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            long input = TEST_PC + 256;
            long[] values = {0xffff_ffffL, 0x8000_0000L, 0x7fff_ffffL, 2};
            for (int index = 0; index < values.length; index++) {
                machine.memory().writeInt(input + (long) index * Integer.BYTES, (int) values[index]);
            }
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtype(32, 1)),
                    vle(32, 1, 6),
                    vmulhuVx(2, 1, 11),
                    vmulhVx(3, 1, 12),
                    vmulhsuVx(4, 1, 11),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, values.length);
            machine.state().setRegister(11, 2);
            machine.state().setRegister(12, -2);
            machine.state().setRegister(6, input);

            runDecodedProgram(machine);

            assertVectorElements(machine.state(), 2, 1, 1, 0, 0);
            assertVectorElements(machine.state(), 3, 0, 1, 0xffff_ffffL, 0xffff_ffffL);
            assertVectorElements(machine.state(), 4, 0xffff_ffffL, 0xffff_ffffL, 0, 0);
        }
    }

    /// Verifies strided vector loads and stores.
    @Test
    public void vectorStridedLoadAndStoreExecute() {
        try (TestMachine machine = TestMachine.create()) {
            long input = TEST_PC + 256;
            long output = TEST_PC + 512;
            for (int index = 0; index < 4; index++) {
                machine.memory().writeShort(input + (long) index * 4, (short) (index + 1));
            }
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtype(16, 1)),
                    vlse(16, 1, 6, 11),
                    vaddVi(2, 1, 3),
                    vsse(16, 2, 7, 12),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, 4);
            machine.state().setRegister(6, input);
            machine.state().setRegister(7, output);
            machine.state().setRegister(11, 4);
            machine.state().setRegister(12, 4);

            runDecodedProgram(machine);

            for (int index = 0; index < 4; index++) {
                assertEquals(index + 4, machine.memory().readUnsignedShort(output + (long) index * 4));
            }
        }
    }

    /// Verifies indexed vector loads and stores with index EEW equal to SEW.
    @Test
    public void vectorIndexedLoadAndStoreExecute() {
        try (TestMachine machine = TestMachine.create()) {
            long offsets = TEST_PC + 256;
            long input = TEST_PC + 512;
            long output = TEST_PC + 768;
            for (int index = 0; index < 4; index++) {
                machine.memory().writeShort(offsets + (long) index * Short.BYTES, (short) (index * 4));
                machine.memory().writeShort(input + (long) index * 4, (short) (index + 1));
            }
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtype(16, 1)),
                    vle(16, 1, 6),
                    vluxei(16, 2, 7, 1),
                    vaddVi(3, 2, 2),
                    vsuxei(16, 3, 8, 1),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, 4);
            machine.state().setRegister(6, offsets);
            machine.state().setRegister(7, input);
            machine.state().setRegister(8, output);

            runDecodedProgram(machine);

            for (int index = 0; index < 4; index++) {
                assertEquals(index + 3, machine.memory().readUnsignedShort(output + (long) index * 4));
            }
        }
    }

    /// Verifies segment, mask, whole-register, and mixed-EEW vector memory operations.
    @Test
    public void vectorExtendedMemoryInstructionsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            long segmentInput = TEST_PC + 256;
            long segmentOutput = TEST_PC + 512;
            long maskInput = TEST_PC + 768;
            long maskOutput = TEST_PC + 800;
            long wholeInput = TEST_PC + 1024;
            long wholeOutput = TEST_PC + 1280;
            byte[] segmentValues = {1, 10, 2, 20, 3, 30, 4, 40};
            byte[] wholeValues = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
            for (int index = 0; index < segmentValues.length; index++) {
                machine.memory().writeByte(segmentInput + index, segmentValues[index]);
            }
            for (int index = 0; index < wholeValues.length; index++) {
                machine.memory().writeByte(wholeInput + index, wholeValues[index]);
            }
            machine.memory().writeByte(maskInput, (byte) 0b1010_1100);
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtype(8, 1)),
                    vlseg(8, 2, 6, 2),
                    vaddVi(2, 2, 1),
                    vsseg(8, 2, 7, 2),
                    vlm(4, 12),
                    vsm(4, 13),
                    vlre(8, 8, 14, 1),
                    vsre(8, 8, 15, 1),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, 4);
            machine.state().setRegister(6, segmentInput);
            machine.state().setRegister(7, segmentOutput);
            machine.state().setRegister(12, maskInput);
            machine.state().setRegister(13, maskOutput);
            machine.state().setRegister(14, wholeInput);
            machine.state().setRegister(15, wholeOutput);

            runDecodedProgram(machine);

            byte[] expectedSegment = {2, 10, 3, 20, 4, 30, 5, 40};
            for (int index = 0; index < expectedSegment.length; index++) {
                assertEquals(expectedSegment[index] & 0xff, machine.memory().readUnsignedByte(segmentOutput + index));
            }
            assertEquals(0b1010_1100, machine.memory().readUnsignedByte(maskOutput));
            for (int index = 0; index < wholeValues.length; index++) {
                assertEquals(wholeValues[index] & 0xff, machine.memory().readUnsignedByte(wholeOutput + index));
            }
        }
    }

    /// Verifies widening, narrowing, fixed-point, and extension integer operations.
    @Test
    public void vectorMixedWidthAndFixedPointIntegerInstructionsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            long narrowInput = TEST_PC + 256;
            long extendedInput = TEST_PC + 512;
            long output = TEST_PC + 768;
            int[] narrowValues = {1, 2, 3, 4};
            int[] byteValues = {250, 2, 3, 4};
            for (int index = 0; index < narrowValues.length; index++) {
                machine.memory().writeShort(narrowInput + (long) index * Short.BYTES, (short) narrowValues[index]);
                machine.memory().writeByte(extendedInput + index, (byte) byteValues[index]);
            }
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtype(16, 1)),
                    vle(16, 1, 6),
                    vwadduVx(2, 1, 11),
                    vnsrlWi(3, 2, 1),
                    vle(8, 4, 7),
                    vsextVf2(5, 4),
                    vse(16, 3, 8),
                    vse(16, 5, 9),
                    vsetvli(5, 10, vtype(8, 1)),
                    vle(8, 6, 12),
                    vsadduVi(7, 6, 10),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, 4);
            machine.state().setRegister(6, narrowInput);
            machine.state().setRegister(7, extendedInput);
            machine.state().setRegister(8, output);
            machine.state().setRegister(9, output + 32);
            machine.state().setRegister(11, 9);
            machine.state().setRegister(12, extendedInput);

            runDecodedProgram(machine);

            assertEquals(5, machine.memory().readUnsignedShort(output));
            assertEquals(5, machine.memory().readUnsignedShort(output + Short.BYTES));
            assertEquals(0xfffa, machine.memory().readUnsignedShort(output + 32));
            assertEquals(2, machine.memory().readUnsignedShort(output + 34));
            assertVectorBytes(machine.state(), 7, 255, 12, 13, 14);
            assertEquals(1, machine.state().readControlStatusRegister(VCSR_CSR) & 1);
        }
    }

    /// Verifies RVA23U64 mandatory `Zvbb`/`Zvkb` vector bit-manipulation instructions.
    @Test
    public void vectorBitManipulationInstructionsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            long input = TEST_PC + 256;
            long masks = TEST_PC + 512;
            long wideOutput = TEST_PC + 768;
            long[] values = {0x0123_4567L, 0x8000_0000L, 0x10, 0xffff_ffffL};
            long[] maskValues = {0x00ff_00ffL, 0xffff_ffffL, 0x0f, 0x5555_5555L};
            for (int index = 0; index < values.length; index++) {
                machine.memory().writeInt(input + (long) index * Integer.BYTES, (int) values[index]);
                machine.memory().writeInt(masks + (long) index * Integer.BYTES, (int) maskValues[index]);
            }
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtype(32, 1)),
                    vle(32, 1, 6),
                    vle(32, 2, 7),
                    vandnVv(3, 1, 2),
                    vbrevV(4, 1),
                    vbrev8V(5, 1),
                    vrev8V(6, 1),
                    vclzV(7, 1),
                    vctzV(8, 1),
                    vcpopV(9, 1),
                    vrorVi(10, 1, 7),
                    vrolVx(11, 1, 11),
                    vwsllVi(12, 1, 3),
                    vse(64, 12, 12),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, values.length);
            machine.state().setRegister(6, input);
            machine.state().setRegister(7, masks);
            machine.state().setRegister(11, 4);
            machine.state().setRegister(12, wideOutput);

            runDecodedProgram(machine);

            assertVectorElements(machine.state(), 3, 0x0100_4500L, 0, 0x10, 0xaaaa_aaaaL);
            assertVectorElements(machine.state(), 4, 0xe6a2_c480L, 0x1, 0x0800_0000L, 0xffff_ffffL);
            assertVectorElements(machine.state(), 5, 0x80c4_a2e6L, 0x0100_0000L, 0x8, 0xffff_ffffL);
            assertVectorElements(machine.state(), 6, 0x6745_2301L, 0x80, 0x1000_0000L, 0xffff_ffffL);
            assertVectorElements(machine.state(), 7, 7, 0, 27, 0);
            assertVectorElements(machine.state(), 8, 0, 31, 4, 0);
            assertVectorElements(machine.state(), 9, 12, 1, 1, 32);
            assertVectorElements(machine.state(), 10, 0xce02_468aL, 0x0100_0000L, 0x2000_0000L, 0xffff_ffffL);
            assertVectorElements(machine.state(), 11, 0x1234_5670L, 0x8, 0x100, 0xffff_ffffL);
            assertEquals(0x091a_2b38L, machine.memory().readLong(wideOutput));
            assertEquals(0x4_0000_0000L, machine.memory().readLong(wideOutput + Long.BYTES));
            assertEquals(0x80L, machine.memory().readLong(wideOutput + 2L * Long.BYTES));
            assertEquals(0x7_ffff_fff8L, machine.memory().readLong(wideOutput + 3L * Long.BYTES));
        }
    }

    /// Verifies RVA23U64 mandatory `Zvfhmin` half/single vector conversions.
    @Test
    public void vectorHalfPrecisionMinimumConversionsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            long halfInput = TEST_PC + 256;
            long singleOutput = TEST_PC + 512;
            long singleInput = TEST_PC + 768;
            long halfOutput = TEST_PC + 1024;
            int[] halfValues = {0x3c00, 0xc000, 0x7e00, 0};
            int[] singleValues = {
                    Float.floatToRawIntBits(1.0f),
                    Float.floatToRawIntBits(-2.0f),
                    Float.floatToRawIntBits(0.5f),
                    Float.floatToRawIntBits(Float.POSITIVE_INFINITY)
            };
            for (int index = 0; index < halfValues.length; index++) {
                machine.memory().writeShort(halfInput + (long) index * Short.BYTES, (short) halfValues[index]);
                machine.memory().writeInt(singleInput + (long) index * Integer.BYTES, singleValues[index]);
            }
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtype(16, 1)),
                    vle(16, 1, 6),
                    vfwcvtFFV(2, 1),
                    vse(32, 2, 7),
                    vle(32, 4, 8),
                    vfncvtFFW(6, 4),
                    vse(16, 6, 9),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, halfValues.length);
            machine.state().setRegister(6, halfInput);
            machine.state().setRegister(7, singleOutput);
            machine.state().setRegister(8, singleInput);
            machine.state().setRegister(9, halfOutput);

            runDecodedProgram(machine);

            assertEquals(Float.floatToRawIntBits(1.0f), machine.memory().readInt(singleOutput));
            assertEquals(Float.floatToRawIntBits(-2.0f), machine.memory().readInt(singleOutput + Integer.BYTES));
            assertEquals(0x7fc0_0000, machine.memory().readInt(singleOutput + 2L * Integer.BYTES));
            assertEquals(0, machine.memory().readInt(singleOutput + 3L * Integer.BYTES));
            assertEquals(0x3c00, machine.memory().readUnsignedShort(halfOutput));
            assertEquals(0xc000, machine.memory().readUnsignedShort(halfOutput + Short.BYTES));
            assertEquals(0x3800, machine.memory().readUnsignedShort(halfOutput + 2L * Short.BYTES));
            assertEquals(0x7c00, machine.memory().readUnsignedShort(halfOutput + 3L * Short.BYTES));
        }
    }

    /// Verifies integer reduction operations.
    @Test
    public void vectorReductionInstructionsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            long input = TEST_PC + 256;
            long accumulator = TEST_PC + 512;
            for (int index = 0; index < 4; index++) {
                machine.memory().writeShort(input + (long) index * Short.BYTES, (short) (index + 1));
            }
            machine.memory().writeShort(accumulator, (short) 10);
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtype(16, 1)),
                    vle(16, 1, 6),
                    vle(16, 2, 7),
                    vredsumVs(3, 1, 2),
                    vredmaxuVs(4, 1, 2),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, 4);
            machine.state().setRegister(6, input);
            machine.state().setRegister(7, accumulator);

            runDecodedProgram(machine);

            assertEquals(20, machine.state().vectorUnit().readElement(3, 0));
            assertEquals(10, machine.state().vectorUnit().readElement(4, 0));
        }
    }

    /// Verifies vector add-with-carry and carry-mask generation.
    @Test
    public void vectorCarryInstructionsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            long left = TEST_PC + 256;
            long right = TEST_PC + 512;
            int[] leftValues = {0xff, 0, 5, 8};
            int[] rightValues = {1, 1, 250, 255};
            for (int index = 0; index < leftValues.length; index++) {
                machine.memory().writeByte(left + index, (byte) leftValues[index]);
                machine.memory().writeByte(right + index, (byte) rightValues[index]);
            }
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtype(8, 1)),
                    vle(8, 1, 6),
                    vle(8, 2, 7),
                    vmadcVvm(3, 1, 2),
                    vadcVvm(4, 1, 2),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, leftValues.length);
            machine.state().setRegister(6, left);
            machine.state().setRegister(7, right);
            machine.state().vectorUnit().writeElement(0, 0, 0b0101);

            runDecodedProgram(machine);

            assertEquals(0b1101, machine.state().vectorUnit().readElement(3, 0));
            assertVectorBytes(machine.state(), 4, 1, 1, 0, 7);
        }
    }

    /// Verifies gather, slide, and compress operations.
    @Test
    public void vectorGatherSlideAndCompressInstructionsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            long input = TEST_PC + 256;
            int[] values = {10, 20, 30, 40, 50, 60, 70, 80};
            for (int index = 0; index < values.length; index++) {
                machine.memory().writeByte(input + index, (byte) values[index]);
            }
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtype(8, 1)),
                    vle(8, 1, 6),
                    vrgatherVi(2, 1, 2),
                    vslideupVi(3, 1, 2),
                    vslidedownVi(4, 1, 3),
                    vslide1upVx(5, 1, 11),
                    vslide1downVx(6, 1, 12),
                    vcompressVm(7, 1, 0),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, values.length);
            machine.state().setRegister(6, input);
            machine.state().setRegister(11, 99);
            machine.state().setRegister(12, 77);
            machine.state().vectorUnit().writeElement(0, 0, 0b1010_1001);

            runDecodedProgram(machine);

            assertVectorBytes(machine.state(), 2, 30, 30, 30, 30, 30, 30, 30, 30);
            assertVectorBytes(machine.state(), 3, 0, 0, 10, 20, 30, 40, 50, 60);
            assertVectorBytes(machine.state(), 4, 40, 50, 60, 70, 80, 0, 0, 0);
            assertVectorBytes(machine.state(), 5, 99, 10, 20, 30, 40, 50, 60, 70);
            assertVectorBytes(machine.state(), 6, 20, 30, 40, 50, 60, 70, 80, 77);
            assertVectorBytes(machine.state(), 7, 10, 40, 60, 80);
        }
    }

    /// Verifies mask population, first-set, and scalar/vector move operations.
    @Test
    public void vectorMaskScalarAndMoveInstructionsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            long input = TEST_PC + 256;
            int[] values = {0xfe, 2, 3, 4, 5, 6, 7, 8};
            for (int index = 0; index < values.length; index++) {
                machine.memory().writeByte(input + index, (byte) values[index]);
            }
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtype(8, 1)),
                    vle(8, 1, 6),
                    vmsgtVi(2, 1, 3),
                    vcpopM(12, 2),
                    vfirstM(13, 2),
                    vmvXs(14, 1),
                    vmvSx(3, 11),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, values.length);
            machine.state().setRegister(6, input);
            machine.state().setRegister(11, 0x7f);

            runDecodedProgram(machine);

            assertEquals(5, machine.state().register(12));
            assertEquals(3, machine.state().register(13));
            assertEquals(-2L, machine.state().register(14));
            assertEquals(0x7f, machine.state().vectorUnit().readElement(3, 0));
        }
    }

    /// Verifies mask prefix, iota, and vector index operations.
    @Test
    public void vectorMaskUnaryInstructionsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            long input = TEST_PC + 256;
            int[] values = {0xfe, 2, 3, 4, 5, 6, 7, 8};
            for (int index = 0; index < values.length; index++) {
                machine.memory().writeByte(input + index, (byte) values[index]);
            }
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtype(8, 1)),
                    vle(8, 1, 6),
                    vmsgtVi(2, 1, 3),
                    vmsbfM(3, 2),
                    vmsifM(4, 2),
                    vmsofM(5, 2),
                    viotaM(6, 2),
                    vidV(7),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, values.length);
            machine.state().setRegister(6, input);

            runDecodedProgram(machine);

            assertEquals(0b0000_0111, machine.state().vectorUnit().readElement(3, 0));
            assertEquals(0b0000_1111, machine.state().vectorUnit().readElement(4, 0));
            assertEquals(0b0000_1000, machine.state().vectorUnit().readElement(5, 0));
            assertVectorBytes(machine.state(), 6, 0, 0, 0, 0, 1, 2, 3, 4);
            assertVectorBytes(machine.state(), 7, 0, 1, 2, 3, 4, 5, 6, 7);
        }
    }

    /// Verifies basic vector floating-point arithmetic and comparison operations.
    @Test
    public void vectorFloatingPointInstructionsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            long left = TEST_PC + 256;
            long right = TEST_PC + 512;
            float[] leftValues = {1.0f, 2.0f, 3.0f, 4.0f};
            float[] rightValues = {4.0f, 3.0f, 2.0f, 1.0f};
            for (int index = 0; index < leftValues.length; index++) {
                machine.memory().writeInt(left + (long) index * Integer.BYTES, Float.floatToRawIntBits(leftValues[index]));
                machine.memory().writeInt(right + (long) index * Integer.BYTES, Float.floatToRawIntBits(rightValues[index]));
            }
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtype(32, 1)),
                    vle(32, 1, 6),
                    vle(32, 2, 7),
                    vfaddVv(3, 1, 2),
                    vfmulVf(4, 1, 11),
                    vmfltVv(5, 1, 2),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, leftValues.length);
            machine.state().setRegister(6, left);
            machine.state().setRegister(7, right);
            machine.state().setFloatingPointRegister(11, Float.floatToRawIntBits(2.0f));

            runDecodedProgram(machine);

            assertVectorElements(machine.state(), 3,
                    Float.floatToRawIntBits(5.0f),
                    Float.floatToRawIntBits(5.0f),
                    Float.floatToRawIntBits(5.0f),
                    Float.floatToRawIntBits(5.0f));
            assertVectorElements(machine.state(), 4,
                    Float.floatToRawIntBits(2.0f),
                    Float.floatToRawIntBits(4.0f),
                    Float.floatToRawIntBits(6.0f),
                    Float.floatToRawIntBits(8.0f));
            assertEquals(0b0011, machine.state().vectorUnit().readElement(5, 0));
        }
    }

    /// Verifies advanced vector floating-point reduction, FMA, unary, and widening operations.
    @Test
    public void vectorAdvancedFloatingPointInstructionsExecute() {
        try (TestMachine machine = TestMachine.create()) {
            long left = TEST_PC + 256;
            long right = TEST_PC + 512;
            long accumulator = TEST_PC + 768;
            long wideOutput = TEST_PC + 1024;
            float[] leftValues = {1.0f, 2.0f, 3.0f, 4.0f};
            float[] rightValues = {2.0f, 2.0f, 2.0f, 2.0f};
            for (int index = 0; index < leftValues.length; index++) {
                machine.memory().writeInt(left + (long) index * Integer.BYTES, Float.floatToRawIntBits(leftValues[index]));
                machine.memory().writeInt(right + (long) index * Integer.BYTES, Float.floatToRawIntBits(rightValues[index]));
                machine.memory().writeInt(accumulator + (long) index * Integer.BYTES, Float.floatToRawIntBits(1.0f));
            }
            loadInstructions(
                    machine.memory(),
                    vsetvli(5, 10, vtype(32, 1)),
                    vle(32, 1, 6),
                    vle(32, 2, 7),
                    vle(32, 3, 8),
                    vfmaccVv(3, 1, 2),
                    vfredusumVs(4, 1, 3),
                    vfsqrtV(5, 3),
                    vfclassV(6, 3),
                    vfwaddVv(8, 1, 2),
                    vfcvtXFV(9, 1),
                    vse(64, 8, 12),
                    ElfTestImages.ecall());
            prepareExit(machine.state());
            machine.state().setRegister(10, leftValues.length);
            machine.state().setRegister(6, left);
            machine.state().setRegister(7, right);
            machine.state().setRegister(8, accumulator);
            machine.state().setRegister(12, wideOutput);

            runDecodedProgram(machine);

            assertVectorElements(machine.state(), 3,
                    Float.floatToRawIntBits(3.0f),
                    Float.floatToRawIntBits(5.0f),
                    Float.floatToRawIntBits(7.0f),
                    Float.floatToRawIntBits(9.0f));
            assertEquals(Float.floatToRawIntBits(13.0f), machine.state().vectorUnit().readElement(4, 0));
            assertEquals(Float.floatToRawIntBits((float) Math.sqrt(3.0f)), machine.state().vectorUnit().readElement(5, 0));
            assertEquals(1 << 6, machine.state().vectorUnit().readElement(6, 0));
            assertEquals(Double.doubleToRawLongBits(3.0d), machine.memory().readLong(wideOutput));
            assertVectorElements(machine.state(), 9, 1, 2, 3, 4);
        }
    }

    /// Sets the syscall register so a trailing `ecall` exits the decoded test program.
    private static void prepareExit(MachineState state) {
        state.setRegister(SYSCALL_REGISTER, EXIT_SYSCALL);
    }

    /// Runs decoded blocks until the test program exits.
    private static void runDecodedProgram(TestMachine machine) {
        for (int index = 0; index < 8; index++) {
            try {
                DecodedBlock block = RiscVDecoder.decodeBlock(machine.memory(), machine.state().pc());
                DecodedBlockTestExecutor.execute(machine.state(), block);
            } catch (ProgramExitException exception) {
                return;
            }
        }
        fail("Decoded vector program did not exit");
    }

    /// Asserts vector byte elements.
    private static void assertVectorBytes(MachineState state, int register, int... expected) {
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], state.vectorUnit().readElement(register, index));
        }
    }

    /// Asserts vector elements.
    private static void assertVectorElements(MachineState state, int register, long... expected) {
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], state.vectorUnit().readElement(register, index));
        }
    }

    /// Loads little-endian instruction words at the vector test address.
    private static void loadInstructions(Memory memory, int... instructions) {
        ByteBuffer code = ByteBuffer.allocate(instructions.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int instruction : instructions) {
            code.putInt(instruction);
        }
        memory.load(TEST_PC, code.array(), 0, code.capacity());
    }

    /// Encodes a `vtype` immediate.
    private static int vtype(int sew, int lmul) {
        int vsew = switch (sew) {
            case 8 -> 0;
            case 16 -> 1;
            case 32 -> 2;
            case 64 -> 3;
            default -> throw new IllegalArgumentException("Unsupported SEW: " + sew);
        };
        int vlmul = switch (lmul) {
            case 1 -> 0;
            case 2 -> 1;
            case 4 -> 2;
            case 8 -> 3;
            default -> throw new IllegalArgumentException("Unsupported LMUL: " + lmul);
        };
        return (1 << 7) | (1 << 6) | (vsew << 3) | vlmul;
    }

    /// Encodes a raw `vtype` immediate with a caller-supplied `vlmul` field.
    private static int vtypeRaw(int sew, int vlmul) {
        int vsew = switch (sew) {
            case 8 -> 0;
            case 16 -> 1;
            case 32 -> 2;
            case 64 -> 3;
            default -> throw new IllegalArgumentException("Unsupported SEW: " + sew);
        };
        return (1 << 7) | (1 << 6) | (vsew << 3) | vlmul;
    }

    /// Encodes `vsetvli`.
    private static int vsetvli(int rd, int rs1, int vtype) {
        return (vtype << 20) | (rs1 << 15) | (7 << 12) | (rd << 7) | 0x57;
    }

    /// Encodes `vsetivli`.
    private static int vsetivli(int rd, int avl, int vtype) {
        return (3 << 30) | (vtype << 20) | (avl << 15) | (7 << 12) | (rd << 7) | 0x57;
    }

    /// Encodes `vsetvl`.
    private static int vsetvl(int rd, int rs1, int rs2) {
        return (1 << 31) | (rs2 << 20) | (rs1 << 15) | (7 << 12) | (rd << 7) | 0x57;
    }

    /// Encodes an unmasked unit-stride vector load.
    private static int vle(int sew, int vd, int rs1) {
        return (1 << 25) | (width(sew) << 12) | (rs1 << 15) | (vd << 7) | 0x07;
    }

    /// Encodes an unmasked unit-stride vector store.
    private static int vse(int sew, int vs3, int rs1) {
        return (1 << 25) | (width(sew) << 12) | (rs1 << 15) | (vs3 << 7) | 0x27;
    }

    /// Encodes an unmasked strided vector load.
    private static int vlse(int sew, int vd, int rs1, int rs2) {
        return (2 << 26) | (1 << 25) | (rs2 << 20) | (rs1 << 15) | (width(sew) << 12) | (vd << 7) | 0x07;
    }

    /// Encodes an unmasked strided vector store.
    private static int vsse(int sew, int vs3, int rs1, int rs2) {
        return (2 << 26) | (1 << 25) | (rs2 << 20) | (rs1 << 15) | (width(sew) << 12) | (vs3 << 7) | 0x27;
    }

    /// Encodes an unmasked unordered indexed vector load.
    private static int vluxei(int sew, int vd, int rs1, int vs2) {
        return (1 << 26) | (1 << 25) | (vs2 << 20) | (rs1 << 15) | (width(sew) << 12) | (vd << 7) | 0x07;
    }

    /// Encodes an unmasked unordered indexed vector store.
    private static int vsuxei(int sew, int vs3, int rs1, int vs2) {
        return (1 << 26) | (1 << 25) | (vs2 << 20) | (rs1 << 15) | (width(sew) << 12) | (vs3 << 7) | 0x27;
    }

    /// Encodes an unmasked unit-stride segment vector load.
    private static int vlseg(int sew, int vd, int rs1, int fields) {
        return vectorMemory(true, sew, vd, rs1, 0, 0, fields, true);
    }

    /// Encodes an unmasked unit-stride segment vector store.
    private static int vsseg(int sew, int vs3, int rs1, int fields) {
        return vectorMemory(false, sew, vs3, rs1, 0, 0, fields, true);
    }

    /// Encodes `vlm.v`.
    private static int vlm(int vd, int rs1) {
        return vectorMemory(true, 8, vd, rs1, 11, 0, 1, true);
    }

    /// Encodes `vsm.v`.
    private static int vsm(int vs3, int rs1) {
        return vectorMemory(false, 8, vs3, rs1, 11, 0, 1, true);
    }

    /// Encodes a whole-register vector load.
    private static int vlre(int sew, int vd, int rs1, int registers) {
        return vectorMemory(true, sew, vd, rs1, 8, 0, registers, true);
    }

    /// Encodes a whole-register vector store.
    private static int vsre(int sew, int vs3, int rs1, int registers) {
        return vectorMemory(false, sew, vs3, rs1, 8, 0, registers, true);
    }

    /// Encodes a vector memory instruction.
    private static int vectorMemory(boolean load, int sew, int register, int rs1, int operand, int mop, int fields, boolean unmasked) {
        return ((fields - 1) << 29)
                | (mop << 26)
                | ((unmasked ? 1 : 0) << 25)
                | (operand << 20)
                | (rs1 << 15)
                | (width(sew) << 12)
                | (register << 7)
                | (load ? 0x07 : 0x27);
    }

    /// Encodes `vadd.vi`.
    private static int vaddVi(int vd, int vs2, int immediate) {
        return vectorInteger(0x00, true, vd, immediate, vs2, 3);
    }

    /// Encodes `vadd.vv`.
    private static int vaddVv(int vd, int vs2, int vs1) {
        return vectorInteger(0x00, true, vd, vs1, vs2, 0);
    }

    /// Encodes `vsub.vx`.
    private static int vsubVx(int vd, int vs2, int rs1) {
        return vectorInteger(0x02, true, vd, rs1, vs2, 4);
    }

    /// Encodes `vminu.vx`.
    private static int vminuVx(int vd, int vs2, int rs1) {
        return vectorInteger(0x04, true, vd, rs1, vs2, 4);
    }

    /// Encodes `vmax.vx`.
    private static int vmaxVx(int vd, int vs2, int rs1) {
        return vectorInteger(0x07, true, vd, rs1, vs2, 4);
    }

    /// Encodes masked `vor.vv`.
    private static int vorVvMasked(int vd, int vs2, int vs1) {
        return vectorInteger(0x0a, false, vd, vs1, vs2, 0);
    }

    /// Encodes `vsrl.vi`.
    private static int vsrlVi(int vd, int vs2, int immediate) {
        return vectorInteger(0x28, true, vd, immediate, vs2, 3);
    }

    /// Encodes `vsra.vi`.
    private static int vsraVi(int vd, int vs2, int immediate) {
        return vectorInteger(0x29, true, vd, immediate, vs2, 3);
    }

    /// Encodes `vmerge.vvm`.
    private static int vmergeVvm(int vd, int vs2, int vs1) {
        return vectorInteger(0x17, false, vd, vs1, vs2, 0);
    }

    /// Encodes `vmsltu.vx`.
    private static int vmsltuVx(int vd, int vs2, int rs1) {
        return vectorInteger(0x1a, true, vd, rs1, vs2, 4);
    }

    /// Encodes `vmsgt.vi`.
    private static int vmsgtVi(int vd, int vs2, int immediate) {
        return vectorInteger(0x1f, true, vd, immediate, vs2, 3);
    }

    /// Encodes `vmand.mm`.
    private static int vmandMm(int vd, int vs2, int vs1) {
        return vectorInteger(0x19, true, vd, vs1, vs2, 2);
    }

    /// Encodes `vmxor.mm`.
    private static int vmxorMm(int vd, int vs2, int vs1) {
        return vectorInteger(0x1b, true, vd, vs1, vs2, 2);
    }

    /// Encodes `vmul.vx`.
    private static int vmulVx(int vd, int vs2, int rs1) {
        return vectorInteger(0x25, true, vd, rs1, vs2, 6);
    }

    /// Encodes `vmulhu.vx`.
    private static int vmulhuVx(int vd, int vs2, int rs1) {
        return vectorInteger(0x24, true, vd, rs1, vs2, 6);
    }

    /// Encodes `vmulh.vx`.
    private static int vmulhVx(int vd, int vs2, int rs1) {
        return vectorInteger(0x27, true, vd, rs1, vs2, 6);
    }

    /// Encodes `vmulhsu.vx`.
    private static int vmulhsuVx(int vd, int vs2, int rs1) {
        return vectorInteger(0x26, true, vd, rs1, vs2, 6);
    }

    /// Encodes `vdivu.vx`.
    private static int vdivuVx(int vd, int vs2, int rs1) {
        return vectorInteger(0x20, true, vd, rs1, vs2, 6);
    }

    /// Encodes `vdiv.vx`.
    private static int vdivVx(int vd, int vs2, int rs1) {
        return vectorInteger(0x21, true, vd, rs1, vs2, 6);
    }

    /// Encodes `vrem.vx`.
    private static int vremVx(int vd, int vs2, int rs1) {
        return vectorInteger(0x23, true, vd, rs1, vs2, 6);
    }

    /// Encodes `vwaddu.vx`.
    private static int vwadduVx(int vd, int vs2, int rs1) {
        return vectorInteger(0x30, true, vd, rs1, vs2, 4);
    }

    /// Encodes `vnsrl.wi`.
    private static int vnsrlWi(int vd, int vs2, int immediate) {
        return vectorInteger(0x2c, true, vd, immediate, vs2, 3);
    }

    /// Encodes `vsext.vf2`.
    private static int vsextVf2(int vd, int vs2) {
        return vectorInteger(0x12, true, vd, 7, vs2, 2);
    }

    /// Encodes `vandn.vv`.
    private static int vandnVv(int vd, int vs2, int vs1) {
        return vectorInteger(0x01, true, vd, vs1, vs2, 0);
    }

    /// Encodes `vbrev.v`.
    private static int vbrevV(int vd, int vs2) {
        return vectorInteger(0x12, true, vd, 10, vs2, 2);
    }

    /// Encodes `vbrev8.v`.
    private static int vbrev8V(int vd, int vs2) {
        return vectorInteger(0x12, true, vd, 8, vs2, 2);
    }

    /// Encodes `vrev8.v`.
    private static int vrev8V(int vd, int vs2) {
        return vectorInteger(0x12, true, vd, 9, vs2, 2);
    }

    /// Encodes `vclz.v`.
    private static int vclzV(int vd, int vs2) {
        return vectorInteger(0x12, true, vd, 12, vs2, 2);
    }

    /// Encodes `vctz.v`.
    private static int vctzV(int vd, int vs2) {
        return vectorInteger(0x12, true, vd, 13, vs2, 2);
    }

    /// Encodes `vcpop.v`.
    private static int vcpopV(int vd, int vs2) {
        return vectorInteger(0x12, true, vd, 14, vs2, 2);
    }

    /// Encodes `vror.vi`.
    private static int vrorVi(int vd, int vs2, int immediate) {
        return vectorInteger(0x14 | (immediate >>> 5 & 1), true, vd, immediate & 0x1f, vs2, 3);
    }

    /// Encodes `vrol.vx`.
    private static int vrolVx(int vd, int vs2, int rs1) {
        return vectorInteger(0x15, true, vd, rs1, vs2, 4);
    }

    /// Encodes `vwsll.vi`.
    private static int vwsllVi(int vd, int vs2, int immediate) {
        return vectorInteger(0x35, true, vd, immediate & 0x1f, vs2, 3);
    }

    /// Encodes `vsaddu.vi`.
    private static int vsadduVi(int vd, int vs2, int immediate) {
        return vectorInteger(0x20, true, vd, immediate, vs2, 3);
    }

    /// Encodes `vredsum.vs`.
    private static int vredsumVs(int vd, int vs2, int vs1) {
        return vectorInteger(0x00, true, vd, vs1, vs2, 2);
    }

    /// Encodes `vredmaxu.vs`.
    private static int vredmaxuVs(int vd, int vs2, int vs1) {
        return vectorInteger(0x06, true, vd, vs1, vs2, 2);
    }

    /// Encodes `vmadc.vvm`.
    private static int vmadcVvm(int vd, int vs2, int vs1) {
        return vectorInteger(0x11, false, vd, vs1, vs2, 0);
    }

    /// Encodes `vadc.vvm`.
    private static int vadcVvm(int vd, int vs2, int vs1) {
        return vectorInteger(0x10, false, vd, vs1, vs2, 0);
    }

    /// Encodes `vrgather.vi`.
    private static int vrgatherVi(int vd, int vs2, int immediate) {
        return vectorInteger(0x0c, true, vd, immediate, vs2, 3);
    }

    /// Encodes `vslideup.vi`.
    private static int vslideupVi(int vd, int vs2, int immediate) {
        return vectorInteger(0x0e, true, vd, immediate, vs2, 3);
    }

    /// Encodes `vslidedown.vi`.
    private static int vslidedownVi(int vd, int vs2, int immediate) {
        return vectorInteger(0x0f, true, vd, immediate, vs2, 3);
    }

    /// Encodes `vslide1up.vx`.
    private static int vslide1upVx(int vd, int vs2, int rs1) {
        return vectorInteger(0x0e, true, vd, rs1, vs2, 6);
    }

    /// Encodes `vslide1down.vx`.
    private static int vslide1downVx(int vd, int vs2, int rs1) {
        return vectorInteger(0x0f, true, vd, rs1, vs2, 6);
    }

    /// Encodes `vcompress.vm`.
    private static int vcompressVm(int vd, int vs2, int vs1) {
        return vectorInteger(0x17, true, vd, vs1, vs2, 2);
    }

    /// Encodes `vcpop.m`.
    private static int vcpopM(int rd, int vs2) {
        return vectorInteger(0x10, true, rd, 16, vs2, 2);
    }

    /// Encodes `vfirst.m`.
    private static int vfirstM(int rd, int vs2) {
        return vectorInteger(0x10, true, rd, 17, vs2, 2);
    }

    /// Encodes `vmv.x.s`.
    private static int vmvXs(int rd, int vs2) {
        return vectorInteger(0x10, true, rd, 0, vs2, 2);
    }

    /// Encodes `vmv.s.x`.
    private static int vmvSx(int vd, int rs1) {
        return vectorInteger(0x10, true, vd, rs1, 0, 6);
    }

    /// Encodes `vmsbf.m`.
    private static int vmsbfM(int vd, int vs2) {
        return vectorInteger(0x14, true, vd, 1, vs2, 2);
    }

    /// Encodes `vmsof.m`.
    private static int vmsofM(int vd, int vs2) {
        return vectorInteger(0x14, true, vd, 2, vs2, 2);
    }

    /// Encodes `vmsif.m`.
    private static int vmsifM(int vd, int vs2) {
        return vectorInteger(0x14, true, vd, 3, vs2, 2);
    }

    /// Encodes `viota.m`.
    private static int viotaM(int vd, int vs2) {
        return vectorInteger(0x14, true, vd, 16, vs2, 2);
    }

    /// Encodes `vid.v`.
    private static int vidV(int vd) {
        return vectorInteger(0x14, true, vd, 17, 0, 2);
    }

    /// Encodes `vfadd.vv`.
    private static int vfaddVv(int vd, int vs2, int vs1) {
        return vectorInteger(0x00, true, vd, vs1, vs2, 1);
    }

    /// Encodes `vfmul.vf`.
    private static int vfmulVf(int vd, int vs2, int rs1) {
        return vectorInteger(0x24, true, vd, rs1, vs2, 5);
    }

    /// Encodes `vmflt.vv`.
    private static int vmfltVv(int vd, int vs2, int vs1) {
        return vectorInteger(0x1b, true, vd, vs1, vs2, 1);
    }

    /// Encodes `vfmacc.vv`.
    private static int vfmaccVv(int vd, int vs1, int vs2) {
        return vectorInteger(0x2c, true, vd, vs1, vs2, 1);
    }

    /// Encodes `vfredusum.vs`.
    private static int vfredusumVs(int vd, int vs2, int vs1) {
        return vectorInteger(0x01, true, vd, vs1, vs2, 1);
    }

    /// Encodes `vfsqrt.v`.
    private static int vfsqrtV(int vd, int vs2) {
        return vectorInteger(0x13, true, vd, 0, vs2, 1);
    }

    /// Encodes `vfclass.v`.
    private static int vfclassV(int vd, int vs2) {
        return vectorInteger(0x13, true, vd, 16, vs2, 1);
    }

    /// Encodes `vfwadd.vv`.
    private static int vfwaddVv(int vd, int vs2, int vs1) {
        return vectorInteger(0x30, true, vd, vs1, vs2, 1);
    }

    /// Encodes `vfcvt.x.f.v`.
    private static int vfcvtXFV(int vd, int vs2) {
        return vectorInteger(0x12, true, vd, 1, vs2, 1);
    }

    /// Encodes `vfwcvt.f.f.v`.
    private static int vfwcvtFFV(int vd, int vs2) {
        return vectorInteger(0x12, true, vd, 12, vs2, 1);
    }

    /// Encodes `vfncvt.f.f.w`.
    private static int vfncvtFFW(int vd, int vs2) {
        return vectorInteger(0x12, true, vd, 20, vs2, 1);
    }

    /// Encodes a vector integer instruction.
    private static int vectorInteger(int funct6, boolean unmasked, int vd, int rs1, int vs2, int funct3) {
        return (funct6 << 26)
                | ((unmasked ? 1 : 0) << 25)
                | (vs2 << 20)
                | (rs1 << 15)
                | (funct3 << 12)
                | (vd << 7)
                | 0x57;
    }

    /// Returns the vector memory width encoding for a SEW.
    private static int width(int sew) {
        return switch (sew) {
            case 8 -> 0;
            case 16 -> 5;
            case 32 -> 6;
            case 64 -> 7;
            default -> throw new IllegalArgumentException("Unsupported SEW: " + sew);
        };
    }

    /// Owns a vector test machine and its closeable resources.
    ///
    /// @param memory the guest memory under test
    /// @param syscalls the syscall handler attached to the machine state
    /// @param state the mutable architectural state under test
    @NotNullByDefault
    private record TestMachine(
            Memory memory,
            GuestSyscalls syscalls,
            MachineState state) implements AutoCloseable {
        /// Creates a test machine initialized at the vector test address.
        private static TestMachine create() {
            Memory memory = new Memory(Memory.DEFAULT_BASE_ADDRESS, 4096, null);
            GuestSyscalls syscalls = new GuestSyscalls(
                    memory,
                    new ByteArrayInputStream(new byte[0]),
                    new ByteArrayOutputStream(),
                    new ByteArrayOutputStream(),
                    memory.baseAddress());
            MachineState state = new MachineState(
                    memory,
                    0,
                    false,
                    ElfImage.ABSENT_ADDRESS,
                    ElfImage.ABSENT_ADDRESS,
                    syscalls);
            state.setPc(TEST_PC);
            return new TestMachine(memory, syscalls, state);
        }

        /// Closes the syscall handler and guest memory backing this test machine.
        @Override
        public void close() {
            try {
                syscalls.close();
            } finally {
                memory.close();
            }
        }
    }
}
