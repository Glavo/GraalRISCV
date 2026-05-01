// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.memory.Memory;
import org.jetbrains.annotations.NotNullByDefault;

import java.math.BigInteger;

/// Stores and executes the implemented subset of the RVV 1.0 vector architectural state.
@NotNullByDefault
public final class VectorUnit {
    /// The default vector register length used by the command-line runtime.
    public static final int DEFAULT_VLEN_BITS = 128;

    /// The minimum vector register length supported by this implementation.
    public static final int MIN_VLEN_BITS = 64;

    /// The maximum vector register length allowed by RVV 1.0.
    public static final int MAX_VLEN_BITS = 65536;

    /// The number of architectural vector registers.
    private static final int VECTOR_REGISTER_COUNT = 32;

    /// The maximum element width implemented by this vector unit.
    private static final int ELEN_BITS = 64;

    /// The `vill` bit for RV64 `vtype`.
    private static final long VTYPE_VILL = Long.MIN_VALUE;

    /// The maximum unsigned AVL marker used by `vsetvli rd, x0, ...`.
    private static final long MAX_UNSIGNED_AVL = -1L;

    /// The OP-V funct3 value for vector-vector integer operations.
    private static final int OPIVV = 0;

    /// The OP-V funct3 value for vector-immediate integer operations.
    private static final int OPIVI = 3;

    /// The OP-V funct3 value for vector-vector mask operations.
    private static final int OPMVV = 2;

    /// The OP-V funct3 value for vector-scalar integer operations.
    private static final int OPIVX = 4;

    /// The OP-V funct3 value for vector-scalar mask operations.
    private static final int OPMVX = 6;

    /// The vector register length in bits.
    private final int vlenBits;

    /// The vector register length in bytes.
    private final int vlenBytes;

    /// The mutable vector register file.
    private final byte[][] registers;

    /// The current `vl` CSR value.
    private long vectorLength;

    /// The current `vtype` CSR value.
    private long vectorType;

    /// The current `vstart` CSR value.
    private long vectorStart;

    /// The current `vxsat` CSR bit.
    private int fixedPointSaturate;

    /// The current `vxrm` CSR value.
    private int fixedPointRoundingMode;

    /// Creates a vector unit with the supplied vector register length.
    ///
    /// @param vlenBits the vector register length in bits
    public VectorUnit(int vlenBits) {
        validateVectorLength(vlenBits);
        this.vlenBits = vlenBits;
        this.vlenBytes = vlenBits / Byte.SIZE;
        this.registers = new byte[VECTOR_REGISTER_COUNT][vlenBytes];
    }

    /// Returns the vector register length in bits.
    public int vlenBits() {
        return vlenBits;
    }

    /// Returns the vector register length in bytes.
    public int vlenBytes() {
        return vlenBytes;
    }

    /// Copies all mutable vector state into another vector unit with the same VLEN.
    ///
    /// @param target the destination vector unit
    public void copyTo(VectorUnit target) {
        if (target.vlenBits != vlenBits) {
            throw new RiscVException("Cannot copy vector state across different VLEN values");
        }
        for (int index = 0; index < VECTOR_REGISTER_COUNT; index++) {
            System.arraycopy(registers[index], 0, target.registers[index], 0, vlenBytes);
        }
        target.vectorLength = vectorLength;
        target.vectorType = vectorType;
        target.vectorStart = vectorStart;
        target.fixedPointSaturate = fixedPointSaturate;
        target.fixedPointRoundingMode = fixedPointRoundingMode;
    }

    /// Returns the raw `vl` CSR value.
    public long vectorLength() {
        return vectorLength;
    }

    /// Returns the raw `vtype` CSR value.
    public long vectorType() {
        return vectorType;
    }

    /// Reads a writable vector CSR.
    ///
    /// @param csr the CSR address
    /// @return the CSR value
    public long readWritableControlStatusRegister(int csr) {
        return switch (csr) {
            case 0x008 -> vectorStart;
            case 0x009 -> fixedPointSaturate;
            case 0x00a -> fixedPointRoundingMode;
            case 0x00f -> fixedPointSaturate | ((long) fixedPointRoundingMode << 1);
            default -> throw new RiscVException("Unsupported vector control status register: 0x"
                    + Integer.toUnsignedString(csr, 16));
        };
    }

    /// Writes a writable vector CSR.
    ///
    /// @param csr the CSR address
    /// @param value the new CSR value
    public void writeWritableControlStatusRegister(int csr, long value) {
        switch (csr) {
            case 0x008 -> vectorStart = value;
            case 0x009 -> fixedPointSaturate = (int) value & 1;
            case 0x00a -> fixedPointRoundingMode = (int) value & 0x3;
            case 0x00f -> {
                fixedPointSaturate = (int) value & 1;
                fixedPointRoundingMode = (int) (value >>> 1) & 0x3;
            }
            default -> throw new RiscVException("Unsupported vector control status register: 0x"
                    + Integer.toUnsignedString(csr, 16));
        }
    }

    /// Executes `vsetvli`.
    ///
    /// @param state the machine state containing scalar registers
    /// @param rd the destination scalar register
    /// @param rs1 the AVL source scalar register
    /// @param vtypeImmediate the immediate `vtype` encoding
    /// @param nextPc the sequential program counter
    public void executeVsetVli(MachineState state, int rd, int rs1, int vtypeImmediate, long nextPc) {
        setVectorConfiguration(state, rd, avlFromRegisterForm(state, rd, rs1), vtypeImmediate, nextPc);
    }

    /// Executes `vsetivli`.
    ///
    /// @param state the machine state containing scalar registers
    /// @param rd the destination scalar register
    /// @param avlImmediate the immediate AVL value
    /// @param vtypeImmediate the immediate `vtype` encoding
    /// @param nextPc the sequential program counter
    public void executeVsetIVli(MachineState state, int rd, int avlImmediate, int vtypeImmediate, long nextPc) {
        setVectorConfiguration(state, rd, avlImmediate & 0x1fL, vtypeImmediate, nextPc);
    }

    /// Executes `vsetvl`.
    ///
    /// @param state the machine state containing scalar registers
    /// @param rd the destination scalar register
    /// @param rs1 the AVL source scalar register
    /// @param rs2 the scalar register containing the new `vtype`
    /// @param nextPc the sequential program counter
    public void executeVsetVl(MachineState state, int rd, int rs1, int rs2, long nextPc) {
        setVectorConfiguration(state, rd, avlFromRegisterForm(state, rd, rs1), state.decodedRegister(rs2), nextPc);
    }

    /// Executes a vector load supported by the current memory subset.
    ///
    /// @param state the machine state containing scalar registers
    /// @param memory the guest memory
    /// @param raw the raw instruction bits
    /// @param vd the destination vector register
    /// @param rs1 the scalar base-address register
    /// @param nextPc the sequential program counter
    public void executeUnitStrideLoad(MachineState state, Memory memory, int raw, int vd, int rs1, long nextPc) {
        VectorMemoryShape shape = decodeMemoryShape(raw);
        requireMemoryElementMatchesSew(shape.elementBytes());
        requireRegisterGroup(vd);
        if (shape.indexed()) {
            requireRegisterGroup(shape.indexRegister());
        }
        long baseAddress = state.decodedRegister(rs1);
        long stride = shape.strided() ? state.decodedRegister(shape.strideRegister()) : shape.elementBytes();
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                long address = shape.indexed() ? baseAddress + readElement(shape.indexRegister(), element) : baseAddress + element * stride;
                writeElement(vd, element, readMemoryElement(memory, address, shape.elementBytes()));
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes a vector store supported by the current memory subset.
    ///
    /// @param state the machine state containing scalar registers
    /// @param memory the guest memory
    /// @param raw the raw instruction bits
    /// @param vs3 the source vector register
    /// @param rs1 the scalar base-address register
    /// @param nextPc the sequential program counter
    public void executeUnitStrideStore(MachineState state, Memory memory, int raw, int vs3, int rs1, long nextPc) {
        VectorMemoryShape shape = decodeMemoryShape(raw);
        requireMemoryElementMatchesSew(shape.elementBytes());
        requireRegisterGroup(vs3);
        if (shape.indexed()) {
            requireRegisterGroup(shape.indexRegister());
        }
        long baseAddress = state.decodedRegister(rs1);
        long stride = shape.strided() ? state.decodedRegister(shape.strideRegister()) : shape.elementBytes();
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                long address = shape.indexed() ? baseAddress + readElement(shape.indexRegister(), element) : baseAddress + element * stride;
                writeMemoryElement(memory, address, shape.elementBytes(), readElement(vs3, element));
                state.afterStore(address, shape.elementBytes());
            }
        }
        state.clearReservation();
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes an integer OP-V arithmetic instruction.
    ///
    /// @param state the machine state containing scalar registers
    /// @param raw the raw instruction bits
    /// @param vd the destination vector register
    /// @param rs1 the scalar or vector source field
    /// @param vs2 the second vector source register
    /// @param nextPc the sequential program counter
    public void executeIntegerArithmetic(MachineState state, int raw, int vd, int rs1, int vs2, long nextPc) {
        if ((vectorType & VTYPE_VILL) != 0) {
            throw new RiscVException("Vector instruction executed with vill set");
        }
        int funct3 = (raw >>> 12) & 0x7;
        int funct6 = (raw >>> 26) & 0x3f;
        int vs1 = rs1;
        boolean compare = isIntegerCompare(funct6);
        boolean maskLogical = isMaskLogical(funct3, funct6);
        if (maskLogical) {
            executeMaskLogical(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (!compare) {
            requireRegisterGroup(vd);
        }
        requireRegisterGroup(vs2);
        if (funct3 == OPIVV || funct3 == OPMVV) {
            requireRegisterGroup(vs1);
        } else if (funct3 != OPIVX && funct3 != OPIVI && funct3 != OPMVX) {
            throw new RiscVException("Unsupported vector integer operand format: " + funct3);
        }
        requireSupportedIntegerFormat(funct6, funct3, raw);

        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (funct6 == 0x17 || isActive(raw, element)) {
                long left = readElement(vs2, element);
                long right = switch (funct3) {
                    case OPIVV, OPMVV -> readElement(vs1, element);
                    case OPIVX, OPMVX -> state.decodedRegister(rs1);
                    case OPIVI -> signExtend(rs1, 5);
                    default -> throw new AssertionError("Unexpected vector integer operand format: " + funct3);
                };
                if (compare) {
                    writeMaskBit(vd, element, executeIntegerCompare(funct6, left, right));
                } else {
                    writeElement(vd, element, executeIntegerOperation(funct6, funct3, raw, element, left, right));
                }
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Returns one element from a vector register group.
    ///
    /// @param register the first vector register in the group
    /// @param element the element index
    /// @return the zero-extended element value
    public long readElement(int register, long element) {
        int sewBytes = sewBytes();
        long byteOffset = element * sewBytes;
        long result = 0;
        for (int index = 0; index < sewBytes; index++) {
            result |= (long) readVectorByte(register, byteOffset + index) << (index * Byte.SIZE);
        }
        return result;
    }

    /// Writes one element to a vector register group.
    ///
    /// @param register the first vector register in the group
    /// @param element the element index
    /// @param value the low SEW bits to write
    public void writeElement(int register, long element, long value) {
        int sewBytes = sewBytes();
        long byteOffset = element * sewBytes;
        for (int index = 0; index < sewBytes; index++) {
            writeVectorByte(register, byteOffset + index, (byte) (value >>> (index * Byte.SIZE)));
        }
    }

    /// Validates a configured vector register length.
    ///
    /// @param vlenBits the vector register length in bits
    public static void validateVectorLength(int vlenBits) {
        if (vlenBits < MIN_VLEN_BITS
                || vlenBits > MAX_VLEN_BITS
                || (vlenBits & (vlenBits - 1)) != 0
                || (vlenBits % Byte.SIZE) != 0) {
            throw new RiscVException("riscv.vectorVlen must be a power-of-two bit length between "
                    + MIN_VLEN_BITS + " and " + MAX_VLEN_BITS + ": " + vlenBits);
        }
    }

    /// Computes the AVL value for `vsetvli` and `vsetvl`.
    private long avlFromRegisterForm(MachineState state, int rd, int rs1) {
        if (rs1 != 0) {
            return state.decodedRegister(rs1);
        }
        if (rd != 0) {
            return MAX_UNSIGNED_AVL;
        }
        return vectorLength;
    }

    /// Applies a new vector type and vector length.
    private void setVectorConfiguration(MachineState state, int rd, long avl, long rawVectorType, long nextPc) {
        long normalizedType = rawVectorType & 0xffL;
        if ((rawVectorType & ~0xffL) != 0 || !isSupportedVectorType(normalizedType)) {
            vectorType = VTYPE_VILL;
            vectorLength = 0;
        } else {
            vectorType = normalizedType;
            long maximumLength = maximumVectorLength();
            vectorLength = avl == MAX_UNSIGNED_AVL || Long.compareUnsigned(avl, maximumLength) > 0
                    ? maximumLength
                    : avl;
        }
        state.setDecodedRegister(rd, vectorLength);
        state.setPc(nextPc);
    }

    /// Returns whether a `vtype` value is supported by this implementation.
    private boolean isSupportedVectorType(long value) {
        int vsew = (int) ((value >>> 3) & 0x7);
        int sew = sewBits(vsew);
        if (sew > ELEN_BITS) {
            return false;
        }
        LmulRatio ratio = lmulRatio((int) value & 0x7);
        return ratio.numerator() > 0 && (long) ratio.numerator() * vlenBits >= (long) ratio.denominator() * sew;
    }

    /// Returns the current maximum vector length.
    private long maximumVectorLength() {
        if ((vectorType & VTYPE_VILL) != 0) {
            return 0;
        }
        LmulRatio ratio = lmulRatio((int) vectorType & 0x7);
        return ((long) vlenBits * ratio.numerator()) / ((long) sewBits() * ratio.denominator());
    }

    /// Returns the current selected element width in bits.
    private int sewBits() {
        return sewBits((int) ((vectorType >>> 3) & 0x7));
    }

    /// Returns the selected element width in bits for a `vsew` field.
    private static int sewBits(int vsew) {
        return switch (vsew) {
            case 0 -> 8;
            case 1 -> 16;
            case 2 -> 32;
            case 3 -> 64;
            default -> ELEN_BITS + 1;
        };
    }

    /// Returns the current selected element width in bytes.
    private int sewBytes() {
        return sewBits() / Byte.SIZE;
    }

    /// Returns the current vector register group byte length.
    private long groupBytes() {
        LmulRatio ratio = lmulRatio((int) vectorType & 0x7);
        return ((long) vlenBytes * ratio.numerator()) / ratio.denominator();
    }

    /// Validates that a vector register can name the current register group.
    private void requireRegisterGroup(int register) {
        int groupRegisters = groupRegisters();
        if ((register & (groupRegisters - 1)) != 0 || register + groupRegisters > VECTOR_REGISTER_COUNT) {
            throw new RiscVException("Misaligned vector register group: v" + register + ", LMUL registers=" + groupRegisters);
        }
    }

    /// Returns the number of whole vector registers in the current group.
    private int groupRegisters() {
        LmulRatio ratio = lmulRatio((int) vectorType & 0x7);
        return Math.max(1, ratio.numerator());
    }

    /// Returns the LMUL ratio represented by a `vlmul` field.
    private static LmulRatio lmulRatio(int vlmul) {
        return switch (vlmul & 0x7) {
            case 0 -> new LmulRatio(1, 1);
            case 1 -> new LmulRatio(2, 1);
            case 2 -> new LmulRatio(4, 1);
            case 3 -> new LmulRatio(8, 1);
            case 5 -> new LmulRatio(1, 8);
            case 6 -> new LmulRatio(1, 4);
            case 7 -> new LmulRatio(1, 2);
            default -> new LmulRatio(0, 1);
        };
    }

    /// Decodes a supported vector memory shape.
    private static VectorMemoryShape decodeMemoryShape(int raw) {
        int width = (raw >>> 12) & 0x7;
        int mop = (raw >>> 26) & 0x3;
        int mew = (raw >>> 28) & 0x1;
        int nf = (raw >>> 29) & 0x7;
        int memoryOperand = (raw >>> 20) & 0x1f;
        if (mew != 0 || nf != 0 || mop == 0 && memoryOperand != 0) {
            throw new RiscVException("Unsupported vector memory addressing mode");
        }
        int elementBytes = switch (width) {
            case 0 -> 1;
            case 5 -> 2;
            case 6 -> 4;
            case 7 -> 8;
            default -> throw new RiscVException("Unsupported vector memory width: " + width);
        };
        return new VectorMemoryShape(elementBytes, mop == 2, mop == 1 || mop == 3, memoryOperand);
    }

    /// Requires the encoded memory EEW to match the active SEW.
    private void requireMemoryElementMatchesSew(int elementBytes) {
        if (elementBytes != sewBytes()) {
            throw new RiscVException("Vector memory EEW must match the active SEW in the current implementation");
        }
    }

    /// Returns whether an element is active under the instruction mask.
    private boolean isActive(int raw, long element) {
        boolean unmasked = ((raw >>> 25) & 0x1) != 0;
        return unmasked || maskBit(element);
    }

    /// Reads a mask bit from `v0`.
    private boolean maskBit(long element) {
        return readMaskBit(0, element);
    }

    /// Reads a mask bit from a vector register.
    private boolean readMaskBit(int register, long element) {
        int byteIndex = (int) (element >>> 3);
        int bitIndex = (int) element & 0x7;
        return ((registers[register][byteIndex] >>> bitIndex) & 1) != 0;
    }

    /// Writes a mask bit to a vector register.
    private void writeMaskBit(int register, long element, boolean value) {
        int byteIndex = (int) (element >>> 3);
        int bitIndex = (int) element & 0x7;
        int mask = 1 << bitIndex;
        int current = registers[register][byteIndex] & 0xff;
        registers[register][byteIndex] = (byte) (value ? current | mask : current & ~mask);
    }

    /// Reads one byte from a vector register group.
    private int readVectorByte(int register, long byteOffset) {
        if (byteOffset < 0 || byteOffset >= groupBytes()) {
            throw new RiscVException("Vector element offset outside register group: " + byteOffset);
        }
        int registerOffset = (int) (byteOffset / vlenBytes);
        int byteIndex = (int) (byteOffset % vlenBytes);
        return registers[register + registerOffset][byteIndex] & 0xff;
    }

    /// Writes one byte to a vector register group.
    private void writeVectorByte(int register, long byteOffset, byte value) {
        if (byteOffset < 0 || byteOffset >= groupBytes()) {
            throw new RiscVException("Vector element offset outside register group: " + byteOffset);
        }
        int registerOffset = (int) (byteOffset / vlenBytes);
        int byteIndex = (int) (byteOffset % vlenBytes);
        registers[register + registerOffset][byteIndex] = value;
    }

    /// Reads one little-endian vector memory element.
    private static long readMemoryElement(Memory memory, long address, int elementBytes) {
        return switch (elementBytes) {
            case 1 -> memory.readUnsignedByte(address);
            case 2 -> memory.readUnsignedShort(address);
            case 4 -> memory.readUnsignedInt(address);
            case 8 -> memory.readLong(address);
            default -> throw new AssertionError("Unexpected vector memory element width: " + elementBytes);
        };
    }

    /// Writes one little-endian vector memory element.
    private static void writeMemoryElement(Memory memory, long address, int elementBytes, long value) {
        switch (elementBytes) {
            case 1 -> memory.writeByte(address, (byte) value);
            case 2 -> memory.writeShort(address, (short) value);
            case 4 -> memory.writeInt(address, (int) value);
            case 8 -> memory.writeLong(address, value);
            default -> throw new AssertionError("Unexpected vector memory element width: " + elementBytes);
        }
    }

    /// Executes a supported integer vector ALU operation.
    private long executeIntegerOperation(int funct6, int funct3, int raw, long element, long left, long right) {
        long mask = elementMask();
        long normalizedLeft = left & mask;
        long normalizedRight = right & mask;
        return switch (funct6) {
            case 0x00 -> (normalizedLeft + normalizedRight) & mask;
            case 0x02 -> (normalizedLeft - normalizedRight) & mask;
            case 0x03 -> (normalizedRight - normalizedLeft) & mask;
            case 0x04 -> Long.compareUnsigned(normalizedLeft, normalizedRight) <= 0 ? normalizedLeft : normalizedRight;
            case 0x05 -> signedElement(normalizedLeft) <= signedElement(normalizedRight) ? normalizedLeft : normalizedRight;
            case 0x06 -> Long.compareUnsigned(normalizedLeft, normalizedRight) >= 0 ? normalizedLeft : normalizedRight;
            case 0x07 -> signedElement(normalizedLeft) >= signedElement(normalizedRight) ? normalizedLeft : normalizedRight;
            case 0x09 -> normalizedLeft & normalizedRight;
            case 0x0a -> normalizedLeft | normalizedRight;
            case 0x0b -> normalizedLeft ^ normalizedRight;
            case 0x17 -> executeMerge(raw, element, normalizedLeft, normalizedRight);
            case 0x20 -> divideUnsignedElement(normalizedLeft, normalizedRight);
            case 0x21 -> divideSignedElement(normalizedLeft, normalizedRight);
            case 0x22 -> remainderUnsignedElement(normalizedLeft, normalizedRight);
            case 0x23 -> remainderSignedElement(normalizedLeft, normalizedRight);
            case 0x24 -> multiplyHighUnsigned(normalizedLeft, normalizedRight);
            case 0x25 -> funct3 == OPMVV || funct3 == OPMVX
                    ? normalizedLeft * normalizedRight & mask
                    : normalizedLeft << (normalizedRight & (sewBits() - 1)) & mask;
            case 0x26 -> multiplyHighSignedUnsigned(normalizedLeft, normalizedRight);
            case 0x27 -> multiplyHighSigned(normalizedLeft, normalizedRight);
            case 0x28 -> normalizedLeft >>> (normalizedRight & (sewBits() - 1));
            case 0x29 -> signedElement(normalizedLeft) >> (normalizedRight & (sewBits() - 1)) & mask;
            default -> throw new RiscVException("Unsupported vector integer operation funct6: 0x"
                    + Integer.toUnsignedString(funct6, 16));
        };
    }

    /// Executes a vector merge or move operation.
    private long executeMerge(int raw, long element, long left, long right) {
        boolean unmasked = ((raw >>> 25) & 0x1) != 0;
        return unmasked || maskBit(element) ? right : left;
    }

    /// Executes a mask-producing vector integer compare operation.
    private boolean executeIntegerCompare(int funct6, long left, long right) {
        long mask = elementMask();
        long normalizedLeft = left & mask;
        long normalizedRight = right & mask;
        return switch (funct6) {
            case 0x18 -> normalizedLeft == normalizedRight;
            case 0x19 -> normalizedLeft != normalizedRight;
            case 0x1a -> Long.compareUnsigned(normalizedLeft, normalizedRight) < 0;
            case 0x1b -> signedElement(normalizedLeft) < signedElement(normalizedRight);
            case 0x1c -> Long.compareUnsigned(normalizedLeft, normalizedRight) <= 0;
            case 0x1d -> signedElement(normalizedLeft) <= signedElement(normalizedRight);
            case 0x1e -> Long.compareUnsigned(normalizedLeft, normalizedRight) > 0;
            case 0x1f -> signedElement(normalizedLeft) > signedElement(normalizedRight);
            default -> throw new RiscVException("Unsupported vector integer compare funct6: 0x"
                    + Integer.toUnsignedString(funct6, 16));
        };
    }

    /// Executes unsigned element division.
    private long divideUnsignedElement(long left, long right) {
        if (right == 0) {
            return elementMask();
        }
        return Long.divideUnsigned(left, right) & elementMask();
    }

    /// Executes signed element division.
    private long divideSignedElement(long left, long right) {
        if (right == 0) {
            return elementMask();
        }
        long dividend = signedElement(left);
        long divisor = signedElement(right);
        if (dividend == signedMinimumElement() && divisor == -1) {
            return left & elementMask();
        }
        return dividend / divisor & elementMask();
    }

    /// Executes unsigned element remainder.
    private long remainderUnsignedElement(long left, long right) {
        if (right == 0) {
            return left & elementMask();
        }
        return Long.remainderUnsigned(left, right) & elementMask();
    }

    /// Executes signed element remainder.
    private long remainderSignedElement(long left, long right) {
        if (right == 0) {
            return left & elementMask();
        }
        long dividend = signedElement(left);
        long divisor = signedElement(right);
        if (dividend == signedMinimumElement() && divisor == -1) {
            return 0;
        }
        return dividend % divisor & elementMask();
    }

    /// Returns the high half of an unsigned element multiplication.
    private long multiplyHighUnsigned(long left, long right) {
        int bits = sewBits();
        if (bits == Long.SIZE) {
            return Math.unsignedMultiplyHigh(left, right);
        }
        return left * right >>> bits & elementMask();
    }

    /// Returns the high half of a signed element multiplication.
    private long multiplyHighSigned(long left, long right) {
        int bits = sewBits();
        if (bits == Long.SIZE) {
            return Math.multiplyHigh(left, right);
        }
        return signedElement(left) * signedElement(right) >> bits & elementMask();
    }

    /// Returns the high half of a signed-by-unsigned element multiplication.
    private long multiplyHighSignedUnsigned(long left, long right) {
        int bits = sewBits();
        if (bits == Long.SIZE) {
            BigInteger product = BigInteger.valueOf(left).multiply(unsignedBigInteger(right));
            return product.shiftRight(Long.SIZE).longValue();
        }
        return signedElement(left) * right >> bits & elementMask();
    }

    /// Executes a vector mask logical operation.
    private void executeMaskLogical(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        requireSupportedIntegerFormat((raw >>> 26) & 0x3f, (raw >>> 12) & 0x7, raw);
        long start = vectorStart;
        long length = vectorLength;
        int funct6 = (raw >>> 26) & 0x3f;
        for (long element = start; element < length; element++) {
            boolean left = readMaskBit(vs2, element);
            boolean right = readMaskBit(vs1, element);
            boolean result = switch (funct6) {
                case 0x18 -> left & !right;
                case 0x19 -> left & right;
                case 0x1a -> left | right;
                case 0x1b -> left ^ right;
                case 0x1c -> left | !right;
                case 0x1d -> !(left & right);
                case 0x1e -> !(left | right);
                case 0x1f -> left == right;
                default -> throw new RiscVException("Unsupported vector mask operation funct6: 0x"
                        + Integer.toUnsignedString(funct6, 16));
            };
            writeMaskBit(vd, element, result);
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Requires an integer vector operation to use an implemented operand format.
    private static void requireSupportedIntegerFormat(int funct6, int funct3, int raw) {
        switch (funct6) {
            case 0x00, 0x09, 0x0a, 0x0b, 0x18, 0x19, 0x1c, 0x1d -> {
                if (funct3 != OPIVV && funct3 != OPIVX && funct3 != OPIVI && funct3 != OPMVV) {
                    throw unsupportedVectorIntegerFormat(funct6, funct3);
                }
            }
            case 0x02, 0x04, 0x05, 0x06, 0x07, 0x1a, 0x1b -> {
                if (funct3 != OPIVV && funct3 != OPIVX && funct3 != OPMVV) {
                    throw unsupportedVectorIntegerFormat(funct6, funct3);
                }
            }
            case 0x03, 0x1e, 0x1f -> {
                if (funct3 != OPIVX && funct3 != OPIVI && funct3 != OPMVV) {
                    throw unsupportedVectorIntegerFormat(funct6, funct3);
                }
            }
            case 0x17 -> {
                boolean unmasked = ((raw >>> 25) & 0x1) != 0;
                int vs2 = (raw >>> 20) & 0x1f;
                if (funct3 != OPIVV && funct3 != OPIVX && funct3 != OPIVI || unmasked && vs2 != 0) {
                    throw unsupportedVectorIntegerFormat(funct6, funct3);
                }
            }
            case 0x20, 0x21, 0x22, 0x23, 0x24, 0x26, 0x27 -> {
                if (funct3 != OPMVV && funct3 != OPMVX) {
                    throw unsupportedVectorIntegerFormat(funct6, funct3);
                }
            }
            case 0x25, 0x28, 0x29 -> {
                if (funct6 == 0x25 && (funct3 == OPMVV || funct3 == OPMVX)) {
                    return;
                }
                if (funct3 != OPIVV && funct3 != OPIVX && funct3 != OPIVI) {
                    throw unsupportedVectorIntegerFormat(funct6, funct3);
                }
            }
            default -> throw new RiscVException("Unsupported vector integer operation funct6: 0x"
                    + Integer.toUnsignedString(funct6, 16));
        }
    }

    /// Creates an exception for an unsupported vector integer operand format.
    private static RiscVException unsupportedVectorIntegerFormat(int funct6, int funct3) {
        return new RiscVException("Unsupported vector integer operation format: funct6=0x"
                + Integer.toUnsignedString(funct6, 16) + ", funct3=" + funct3);
    }

    /// Returns whether the operation writes a mask compare result.
    private static boolean isIntegerCompare(int funct6) {
        return funct6 >= 0x18 && funct6 <= 0x1f;
    }

    /// Returns whether the operation is a mask logical instruction.
    private static boolean isMaskLogical(int funct3, int funct6) {
        return funct3 == OPMVV && funct6 >= 0x18 && funct6 <= 0x1f;
    }

    /// Returns a bit mask for the active SEW.
    private long elementMask() {
        int bits = sewBits();
        return bits == Long.SIZE ? -1L : (1L << bits) - 1L;
    }

    /// Sign-extends an element value using the current SEW.
    private long signedElement(long value) {
        return signExtend(value, sewBits());
    }

    /// Returns the signed minimum value for the current SEW.
    private long signedMinimumElement() {
        int bits = sewBits();
        return bits == Long.SIZE ? Long.MIN_VALUE : -(1L << (bits - 1));
    }

    /// Sign-extends a value with the supplied bit width.
    private static long signExtend(long value, int bits) {
        long signBit = 1L << (bits - 1);
        return (value ^ signBit) - signBit;
    }

    /// Converts a 64-bit unsigned integer to a positive `BigInteger`.
    private static BigInteger unsignedBigInteger(long value) {
        BigInteger result = BigInteger.valueOf(value & Long.MAX_VALUE);
        return value < 0 ? result.setBit(Long.SIZE - 1) : result;
    }

    /// Stores an LMUL ratio.
    ///
    /// @param numerator the numerator
    /// @param denominator the denominator
    private record LmulRatio(int numerator, int denominator) {
    }

    /// Stores the decoded shape of a vector memory instruction.
    ///
    /// @param elementBytes the encoded memory element width in bytes
    /// @param strided whether the instruction uses scalar strided addressing
    /// @param indexed whether the instruction uses vector indexed addressing
    /// @param memoryOperand the scalar stride register or vector index register
    private record VectorMemoryShape(int elementBytes, boolean strided, boolean indexed, int memoryOperand) {
        /// Returns the scalar register containing the byte stride.
        private int strideRegister() {
            return memoryOperand;
        }

        /// Returns the vector register containing byte offsets.
        private int indexRegister() {
            return memoryOperand;
        }
    }
}
