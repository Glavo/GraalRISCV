// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.glavo.riscv.exception.RiscVException;
import org.glavo.riscv.memory.Memory;
import org.jetbrains.annotations.NotNullByDefault;

import java.math.BigInteger;

/// Stores and executes the RVV 1.0 vector architectural state implemented by the user-mode runtime.
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

    /// The OP-V funct3 value for vector-vector floating-point operations.
    private static final int OPFVV = 1;

    /// The OP-V funct3 value for vector-immediate integer operations.
    private static final int OPIVI = 3;

    /// The OP-V funct3 value for vector-vector mask operations.
    private static final int OPMVV = 2;

    /// The OP-V funct3 value for vector-scalar integer operations.
    private static final int OPIVX = 4;

    /// The OP-V funct3 value for vector-scalar floating-point operations.
    private static final int OPFVF = 5;

    /// The OP-V funct3 value for vector-scalar mask operations.
    private static final int OPMVX = 6;

    /// The floating-point invalid-operation exception flag.
    private static final int FLOAT_INVALID_OPERATION = 0x10;

    /// The floating-point divide-by-zero exception flag.
    private static final int FLOAT_DIVIDE_BY_ZERO = 0x08;

    /// The floating-point inexact exception flag.
    private static final int FLOAT_INEXACT = 0x01;

    /// Round to nearest, ties to even.
    private static final int ROUND_NEAREST_EVEN = 0;

    /// Round towards zero.
    private static final int ROUND_TOWARDS_ZERO = 1;

    /// Round down.
    private static final int ROUND_DOWN = 2;

    /// Round up.
    private static final int ROUND_UP = 3;

    /// Round to nearest, ties to maximum magnitude.
    private static final int ROUND_NEAREST_MAX_MAGNITUDE = 4;

    /// Use the dynamic floating-point rounding mode.
    private static final int ROUND_DYNAMIC = 7;

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
        requireLegalVectorState();
        VectorMemoryShape shape = decodeMemoryShape(raw, true);
        long baseAddress = state.decodedRegister(rs1);
        if (shape.wholeRegister()) {
            executeWholeRegisterLoad(memory, vd, baseAddress, shape);
            vectorStart = 0;
            state.setPc(nextPc);
            return;
        }
        if (shape.mask()) {
            executeMaskLoad(memory, vd, baseAddress);
            vectorStart = 0;
            state.setPc(nextPc);
            return;
        }
        int dataBytes = shape.indexed() ? sewBytes() : shape.elementBytes();
        long dataGroupBytes = shape.indexed() ? groupBytes() : groupBytesForElementBytes(dataBytes);
        int dataGroupRegisters = groupRegisters(dataGroupBytes);
        requireConsecutiveRegisterGroups(vd, shape.fields(), dataGroupBytes);
        long indexGroupBytes = 0;
        if (shape.indexed()) {
            indexGroupBytes = groupBytesForElementBytes(shape.elementBytes());
            requireRegisterGroup(shape.indexRegister(), indexGroupBytes);
        }
        long stride = shape.strided() ? state.decodedRegister(shape.strideRegister()) : (long) dataBytes * shape.fields();
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                long address = shape.indexed()
                        ? baseAddress + readElement(shape.indexRegister(), element, shape.elementBytes(), indexGroupBytes)
                        : baseAddress + element * stride;
                for (int field = 0; field < shape.fields(); field++) {
                    long value;
                    try {
                        value = readMemoryElement(memory, address + (long) field * dataBytes, dataBytes);
                    } catch (RiscVException exception) {
                        if (!shape.faultOnlyFirst() || element == start) {
                            throw exception;
                        }
                        vectorLength = element;
                        vectorStart = 0;
                        state.setPc(nextPc);
                        return;
                    }
                    writeElement(
                            vd + field * dataGroupRegisters,
                            element,
                            dataBytes,
                            dataGroupBytes,
                            value);
                }
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
        requireLegalVectorState();
        VectorMemoryShape shape = decodeMemoryShape(raw, false);
        long baseAddress = state.decodedRegister(rs1);
        if (shape.wholeRegister()) {
            executeWholeRegisterStore(state, memory, vs3, baseAddress, shape);
            state.clearReservation();
            vectorStart = 0;
            state.setPc(nextPc);
            return;
        }
        if (shape.mask()) {
            executeMaskStore(state, memory, vs3, baseAddress);
            state.clearReservation();
            vectorStart = 0;
            state.setPc(nextPc);
            return;
        }
        int dataBytes = shape.indexed() ? sewBytes() : shape.elementBytes();
        long dataGroupBytes = shape.indexed() ? groupBytes() : groupBytesForElementBytes(dataBytes);
        int dataGroupRegisters = groupRegisters(dataGroupBytes);
        requireConsecutiveRegisterGroups(vs3, shape.fields(), dataGroupBytes);
        long indexGroupBytes = 0;
        if (shape.indexed()) {
            indexGroupBytes = groupBytesForElementBytes(shape.elementBytes());
            requireRegisterGroup(shape.indexRegister(), indexGroupBytes);
        }
        long stride = shape.strided() ? state.decodedRegister(shape.strideRegister()) : (long) dataBytes * shape.fields();
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                long address = shape.indexed()
                        ? baseAddress + readElement(shape.indexRegister(), element, shape.elementBytes(), indexGroupBytes)
                        : baseAddress + element * stride;
                for (int field = 0; field < shape.fields(); field++) {
                    long fieldAddress = address + (long) field * dataBytes;
                    writeMemoryElement(
                            memory,
                            fieldAddress,
                            dataBytes,
                            readElement(vs3 + field * dataGroupRegisters, element, dataBytes, dataGroupBytes));
                    state.afterStore(fieldAddress, dataBytes);
                }
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
        requireLegalVectorState();
        int funct3 = (raw >>> 12) & 0x7;
        int funct6 = (raw >>> 26) & 0x3f;
        int vs1 = rs1;
        if (funct3 == OPFVV || funct3 == OPFVF) {
            executeFloatingPoint(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isReduction(funct3, funct6)) {
            executeReduction(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isWideningReduction(funct3, funct6)) {
            executeWideningReduction(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        boolean compare = isIntegerCompare(funct6);
        boolean maskLogical = isMaskLogical(funct3, funct6);
        if (maskLogical) {
            executeMaskLogical(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isMaskScalarOrMove(funct3, funct6, vs1, vs2)) {
            executeMaskScalarOrMove(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isMaskUnary(funct3, funct6, vs1, vs2)) {
            executeMaskUnary(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isIntegerExtensionUnary(funct3, funct6, vs1)) {
            executeIntegerExtensionUnary(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isIntegerBitManipUnary(funct3, funct6, vs1)) {
            executeIntegerBitManipUnary(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isCarrylessMultiply(funct3, funct6)) {
            executeCarrylessMultiply(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isWholeRegisterMove(funct3, funct6)) {
            executeWholeRegisterMove(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isCarryBorrow(funct6)) {
            executeCarryBorrow(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isGather(funct3, funct6)) {
            executeGather(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isGatherEi16(funct3, funct6)) {
            executeGatherEi16(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isSlide(funct3, funct6)) {
            executeSlide(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isCompress(funct3, funct6)) {
            executeCompress(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isWideningShiftLeftLogical(funct3, funct6)) {
            executeWideningShiftLeftLogical(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isWideningInteger(funct3, funct6)) {
            executeWideningInteger(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isIntegerMultiplyAdd(funct3, funct6)) {
            executeIntegerMultiplyAdd(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isNarrowingInteger(funct3, funct6)) {
            executeNarrowingInteger(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isFixedPointInteger(funct3, funct6)) {
            executeFixedPointInteger(raw, vd, vs1, vs2, nextPc, state);
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

    /// Requires that vector state is configured to a legal `vtype`.
    private void requireLegalVectorState() {
        if ((vectorType & VTYPE_VILL) != 0) {
            throw new RiscVException("Vector instruction executed with vill set");
        }
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

    /// Validates that a vector register can name a register group with the supplied byte length.
    private void requireRegisterGroup(int register, long groupBytes) {
        int groupRegisters = groupRegisters(groupBytes);
        if ((register & (groupRegisters - 1)) != 0 || register + groupRegisters > VECTOR_REGISTER_COUNT) {
            throw new RiscVException("Misaligned vector register group: v" + register + ", LMUL registers=" + groupRegisters);
        }
    }

    /// Validates a consecutive sequence of vector register groups.
    private void requireConsecutiveRegisterGroups(int register, int groups, long groupBytes) {
        int groupRegisters = groupRegisters(groupBytes);
        if ((register & (groupRegisters - 1)) != 0 || register + (long) groupRegisters * groups > VECTOR_REGISTER_COUNT) {
            throw new RiscVException("Misaligned vector register group sequence: v" + register
                    + ", groups=" + groups + ", LMUL registers=" + groupRegisters);
        }
    }

    /// Returns the number of whole vector registers in a register group with the supplied byte length.
    private int groupRegisters(long groupBytes) {
        return Math.max(1, (int) ((groupBytes + vlenBytes - 1) / vlenBytes));
    }

    /// Returns the current vector register group byte length for an operand EEW.
    private long groupBytesForElementBytes(int elementBytes) {
        LmulRatio ratio = lmulRatio((int) vectorType & 0x7);
        long numerator = (long) ratio.numerator() * elementBytes;
        long denominator = (long) ratio.denominator() * sewBytes();
        if (numerator == 0 || numerator * 8 < denominator || numerator > denominator * 8) {
            throw new RiscVException("Vector operand EMUL outside the supported range");
        }
        long bytes = (long) vlenBytes * numerator / denominator;
        if (bytes <= 0 || bytes > (long) vlenBytes * 8) {
            throw new RiscVException("Vector operand register group size outside the supported range");
        }
        return bytes;
    }

    /// Returns the current vector register group byte length for an operand EEW in bits.
    private long groupBytesForElementBits(int elementBits) {
        if (elementBits % Byte.SIZE != 0) {
            throw new RiscVException("Vector element width must be byte-aligned: " + elementBits);
        }
        return groupBytesForElementBytes(elementBits / Byte.SIZE);
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
    private static VectorMemoryShape decodeMemoryShape(int raw, boolean load) {
        int width = (raw >>> 12) & 0x7;
        int mop = (raw >>> 26) & 0x3;
        int mew = (raw >>> 28) & 0x1;
        int nf = (raw >>> 29) & 0x7;
        int memoryOperand = (raw >>> 20) & 0x1f;
        if (mew != 0) {
            throw new RiscVException("Unsupported vector memory width extension");
        }
        int elementBytes = switch (width) {
            case 0 -> 1;
            case 5 -> 2;
            case 6 -> 4;
            case 7 -> 8;
            default -> throw new RiscVException("Unsupported vector memory width: " + width);
        };
        int fields = nf + 1;
        if (mop == 0) {
            if (memoryOperand == 0) {
                return new VectorMemoryShape(elementBytes, fields, false, false, false, false, false, memoryOperand);
            }
            if (load && memoryOperand == 16) {
                return new VectorMemoryShape(elementBytes, fields, false, false, true, false, false, memoryOperand);
            }
            if (memoryOperand == 8) {
                if (fields != 1 && fields != 2 && fields != 4 && fields != 8) {
                    throw new RiscVException("Unsupported whole-register vector transfer count: " + fields);
                }
                return new VectorMemoryShape(elementBytes, fields, false, false, false, true, false, memoryOperand);
            }
            if (memoryOperand == 11 && nf == 0 && width == 0) {
                return new VectorMemoryShape(1, 1, false, false, false, false, true, memoryOperand);
            }
            throw new RiscVException("Unsupported vector unit-stride memory mode: " + memoryOperand);
        }
        return new VectorMemoryShape(elementBytes, fields, mop == 2, mop == 1 || mop == 3, false, false, false, memoryOperand);
    }

    /// Executes a vector mask load.
    private void executeMaskLoad(Memory memory, int vd, long baseAddress) {
        if (vd < 0 || vd >= VECTOR_REGISTER_COUNT) {
            throw new RiscVException("Invalid vector mask load register: v" + vd);
        }
        long bytes = (vectorLength + Byte.SIZE - 1) / Byte.SIZE;
        for (long index = 0; index < bytes; index++) {
            registers[vd][(int) index] = (byte) memory.readUnsignedByte(baseAddress + index);
        }
    }

    /// Executes a vector mask store.
    private void executeMaskStore(MachineState state, Memory memory, int vs3, long baseAddress) {
        if (vs3 < 0 || vs3 >= VECTOR_REGISTER_COUNT) {
            throw new RiscVException("Invalid vector mask store register: v" + vs3);
        }
        long bytes = (vectorLength + Byte.SIZE - 1) / Byte.SIZE;
        for (long index = 0; index < bytes; index++) {
            long address = baseAddress + index;
            memory.writeByte(address, registers[vs3][(int) index]);
            state.afterStore(address, Byte.BYTES);
        }
    }

    /// Executes a whole-register vector load.
    private void executeWholeRegisterLoad(Memory memory, int vd, long baseAddress, VectorMemoryShape shape) {
        requireWholeRegisterGroup(vd, shape.fields());
        for (int register = 0; register < shape.fields(); register++) {
            for (int offset = 0; offset < vlenBytes; offset++) {
                registers[vd + register][offset] = (byte) memory.readUnsignedByte(baseAddress + (long) register * vlenBytes + offset);
            }
        }
    }

    /// Executes a whole-register vector store.
    private void executeWholeRegisterStore(MachineState state, Memory memory, int vs3, long baseAddress, VectorMemoryShape shape) {
        requireWholeRegisterGroup(vs3, shape.fields());
        for (int register = 0; register < shape.fields(); register++) {
            for (int offset = 0; offset < vlenBytes; offset++) {
                long address = baseAddress + (long) register * vlenBytes + offset;
                memory.writeByte(address, registers[vs3 + register][offset]);
                state.afterStore(address, Byte.BYTES);
            }
        }
    }

    /// Validates a whole-register vector register group.
    private static void requireWholeRegisterGroup(int register, int registers) {
        if ((register & (registers - 1)) != 0 || register + registers > VECTOR_REGISTER_COUNT) {
            throw new RiscVException("Misaligned whole-register vector group: v" + register + ", registers=" + registers);
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
        return readVectorByte(register, byteOffset, groupBytes());
    }

    /// Reads one byte from a vector register group with an explicit byte length.
    private int readVectorByte(int register, long byteOffset, long groupBytes) {
        if (byteOffset < 0 || byteOffset >= groupBytes) {
            throw new RiscVException("Vector element offset outside register group: " + byteOffset);
        }
        int registerOffset = (int) (byteOffset / vlenBytes);
        int byteIndex = (int) (byteOffset % vlenBytes);
        return registers[register + registerOffset][byteIndex] & 0xff;
    }

    /// Writes one byte to a vector register group.
    private void writeVectorByte(int register, long byteOffset, byte value) {
        writeVectorByte(register, byteOffset, value, groupBytes());
    }

    /// Writes one byte to a vector register group with an explicit byte length.
    private void writeVectorByte(int register, long byteOffset, byte value, long groupBytes) {
        if (byteOffset < 0 || byteOffset >= groupBytes) {
            throw new RiscVException("Vector element offset outside register group: " + byteOffset);
        }
        int registerOffset = (int) (byteOffset / vlenBytes);
        int byteIndex = (int) (byteOffset % vlenBytes);
        registers[register + registerOffset][byteIndex] = value;
    }

    /// Reads one element from a vector register group with an explicit element width and group byte length.
    private long readElement(int register, long element, int elementBytes, long groupBytes) {
        long byteOffset = element * elementBytes;
        long result = 0;
        for (int index = 0; index < elementBytes; index++) {
            result |= (long) readVectorByte(register, byteOffset + index, groupBytes) << (index * Byte.SIZE);
        }
        return result;
    }

    /// Writes one element to a vector register group with an explicit element width and group byte length.
    private void writeElement(int register, long element, int elementBytes, long groupBytes, long value) {
        long byteOffset = element * elementBytes;
        for (int index = 0; index < elementBytes; index++) {
            writeVectorByte(register, byteOffset + index, (byte) (value >>> (index * Byte.SIZE)), groupBytes);
        }
    }

    /// Reads one element from a vector register group with an explicit element width.
    private long readElementBits(int register, long element, int elementBits) {
        return readElement(register, element, elementBits / Byte.SIZE, groupBytesForElementBits(elementBits));
    }

    /// Writes one element to a vector register group with an explicit element width.
    private void writeElementBits(int register, long element, int elementBits, long value) {
        writeElement(register, element, elementBits / Byte.SIZE, groupBytesForElementBits(elementBits), value);
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
            case 0x01 -> normalizedLeft & ~normalizedRight & mask;
            case 0x02 -> (normalizedLeft - normalizedRight) & mask;
            case 0x03 -> (normalizedRight - normalizedLeft) & mask;
            case 0x04 -> Long.compareUnsigned(normalizedLeft, normalizedRight) <= 0 ? normalizedLeft : normalizedRight;
            case 0x05 -> signedElement(normalizedLeft) <= signedElement(normalizedRight) ? normalizedLeft : normalizedRight;
            case 0x06 -> Long.compareUnsigned(normalizedLeft, normalizedRight) >= 0 ? normalizedLeft : normalizedRight;
            case 0x07 -> signedElement(normalizedLeft) >= signedElement(normalizedRight) ? normalizedLeft : normalizedRight;
            case 0x09 -> normalizedLeft & normalizedRight;
            case 0x0a -> normalizedLeft | normalizedRight;
            case 0x0b -> normalizedLeft ^ normalizedRight;
            case 0x14 -> rotateRightElement(normalizedLeft, rotationAmount(funct6, funct3, raw, normalizedRight));
            case 0x15 -> funct3 == OPIVI
                    ? rotateRightElement(normalizedLeft, rotationAmount(funct6, funct3, raw, normalizedRight))
                    : rotateLeftElement(normalizedLeft, normalizedRight);
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

    /// Executes a vector floating-point operation for SEW 32 or 64.
    private void executeFloatingPoint(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        int funct3 = (raw >>> 12) & 0x7;
        int funct6 = (raw >>> 26) & 0x3f;
        if (isFloatingPointScalarMove(funct3, funct6, vs1, vs2)) {
            if (sewBits() != Float.SIZE && sewBits() != Double.SIZE) {
                throw new RiscVException("Vector floating-point scalar moves require SEW 32 or 64");
            }
            executeFloatingPointScalarMove(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isFloatingPointUnary(funct3, funct6)) {
            executeFloatingPointUnary(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (sewBits() != Float.SIZE && sewBits() != Double.SIZE) {
            throw new RiscVException("Vector floating-point operations require SEW 32 or 64");
        }
        if (isFloatingPointReduction(funct3, funct6)) {
            executeFloatingPointReduction(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isWideningFloatingPointReduction(funct3, funct6)) {
            executeWideningFloatingPointReduction(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isWideningFloatingPoint(funct3, funct6)) {
            executeWideningFloatingPoint(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isWideningFloatingPointFusedMultiplyAdd(funct3, funct6)) {
            executeWideningFloatingPointFusedMultiplyAdd(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isFloatingPointFusedMultiplyAdd(funct6)) {
            executeFloatingPointFusedMultiplyAdd(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isFloatingPointSlide(funct3, funct6)) {
            executeFloatingPointSlide(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        if (isFloatingPointMerge(funct3, funct6)) {
            executeFloatingPointMerge(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        boolean compare = isFloatingPointCompare(funct6);
        if (!compare) {
            requireRegisterGroup(vd);
        }
        requireRegisterGroup(vs2);
        if (funct3 == OPFVV) {
            requireRegisterGroup(vs1);
        }
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                long left = readElement(vs2, element);
                long right = funct3 == OPFVV ? readElement(vs1, element) : state.decodedFloatingPointRegister(vs1);
                if (compare) {
                    writeMaskBit(vd, element, executeFloatingPointCompare(funct6, left, right));
                } else {
                    writeElement(vd, element, executeFloatingPointOperation(funct6, left, right));
                }
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes vector floating-point scalar move operations.
    private void executeFloatingPointScalarMove(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        boolean unmasked = ((raw >>> 25) & 0x1) != 0;
        int funct3 = (raw >>> 12) & 0x7;
        if (!unmasked) {
            throw unsupportedVectorIntegerFormat((raw >>> 26) & 0x3f, funct3);
        }
        if (funct3 == OPFVV && vs1 == 0) {
            requireRegisterGroup(vs2);
            state.setDecodedFloatingPointRegister(vd, readElement(vs2, 0));
        } else if (funct3 == OPFVF && vs2 == 0) {
            requireRegisterGroup(vd);
            if (vectorStart < vectorLength) {
                writeElement(vd, 0, state.decodedFloatingPointRegister(vs1));
            }
        } else {
            throw unsupportedVectorIntegerFormat((raw >>> 26) & 0x3f, funct3);
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes vector floating-point unary operations.
    private void executeFloatingPointUnary(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        int funct6 = (raw >>> 26) & 0x3f;
        if (funct6 == 0x12 && vs1 >= 8) {
            executeMixedWidthFloatingPointConvert(raw, vd, vs1, vs2, nextPc, state);
            return;
        }
        requireRegisterGroup(vd);
        requireRegisterGroup(vs2);
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                long value = readElement(vs2, element);
                if (funct6 == 0x13 && vs1 == 16) {
                    writeElement(vd, element, floatingPointClass(value));
                } else if (funct6 == 0x13) {
                    writeElement(vd, element, executeFloatingPointUnary1(vs1, value, state));
                } else {
                    writeElement(vd, element, executeFloatingPointConvert(vs1, value, state));
                }
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes widening and narrowing floating-point conversion operations.
    private void executeMixedWidthFloatingPointConvert(int raw, int vd, int selector, int vs2, long nextPc, MachineState state) {
        if (sewBits() == Short.SIZE) {
            executeHalfSingleFloatingPointConvert(raw, vd, selector, vs2, nextPc, state);
            return;
        }
        if (sewBits() != Float.SIZE) {
            throw new RiscVException("Mixed-width vector floating-point conversions require SEW 16 or 32");
        }
        int roundingMode = effectiveFloatingPointRoundingMode(state);
        long narrowGroupBytes = groupBytesForElementBits(Float.SIZE);
        long wideGroupBytes = groupBytesForElementBits(Double.SIZE);
        boolean widening = selector < 16;
        requireRegisterGroup(vd, widening ? wideGroupBytes : narrowGroupBytes);
        requireRegisterGroup(vs2, widening ? narrowGroupBytes : wideGroupBytes);
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                if (widening) {
                    int valueBits = (int) readElement(vs2, element, Float.BYTES, narrowGroupBytes);
                    float value = Float.intBitsToFloat(valueBits);
                    long result = switch (selector) {
                        case 8, 14 -> convertFloatingPointToUnsignedInteger(state, value, roundingMode, Long.SIZE);
                        case 9, 15 -> convertFloatingPointToSignedInteger(state, value, roundingMode, Long.SIZE);
                        case 10 -> Double.doubleToRawLongBits((double) (valueBits & 0xffff_ffffL));
                        case 11 -> Double.doubleToRawLongBits((double) valueBits);
                        case 12 -> Double.doubleToRawLongBits(value);
                        default -> throw new RiscVException("Unsupported vector widening conversion selector: " + selector);
                    };
                    writeElement(vd, element, Double.BYTES, wideGroupBytes, result);
                } else {
                    long valueBits = readElement(vs2, element, Double.BYTES, wideGroupBytes);
                    double value = Double.longBitsToDouble(valueBits);
                    long result = switch (selector) {
                        case 16, 22 -> convertFloatingPointToUnsignedInteger(state, value, roundingMode, Integer.SIZE) & 0xffff_ffffL;
                        case 17, 23 -> convertFloatingPointToSignedInteger(state, value, roundingMode, Integer.SIZE) & 0xffff_ffffL;
                        case 18 -> Float.floatToRawIntBits((float) (valueBits & 0xffff_ffffL)) & 0xffff_ffffL;
                        case 19 -> Float.floatToRawIntBits((float) valueBits) & 0xffff_ffffL;
                        case 20, 21 -> Float.floatToRawIntBits((float) value) & 0xffff_ffffL;
                        default -> throw new RiscVException("Unsupported vector narrowing conversion selector: " + selector);
                    };
                    writeElement(vd, element, Float.BYTES, narrowGroupBytes, result);
                }
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes the `Zvfhmin` half/single vector floating-point conversions.
    private void executeHalfSingleFloatingPointConvert(int raw, int vd, int selector, int vs2, long nextPc, MachineState state) {
        int roundingMode = effectiveFloatingPointRoundingMode(state);
        long halfGroupBytes = groupBytesForElementBits(Short.SIZE);
        long singleGroupBytes = groupBytesForElementBits(Float.SIZE);
        boolean widening = selector == 12;
        if (!widening && selector != 20) {
            throw new RiscVException("Unsupported Zvfhmin vector conversion selector: " + selector);
        }
        requireRegisterGroup(vd, widening ? singleGroupBytes : halfGroupBytes);
        requireRegisterGroup(vs2, widening ? halfGroupBytes : singleGroupBytes);
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                if (widening) {
                    int halfBits = (int) readElement(vs2, element, Short.BYTES, halfGroupBytes);
                    writeElement(
                            vd,
                            element,
                            Float.BYTES,
                            singleGroupBytes,
                            RiscVInstructionSemantics.convertHalfBitsToSingleBits(state, halfBits) & 0xffff_ffffL);
                } else {
                    int singleBits = (int) readElement(vs2, element, Float.BYTES, singleGroupBytes);
                    writeElement(
                            vd,
                            element,
                            Short.BYTES,
                            halfGroupBytes,
                            RiscVInstructionSemantics.convertSingleBitsToHalfBits(state, singleBits, roundingMode));
                }
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes vector floating-point reduction operations.
    private void executeFloatingPointReduction(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        requireRegisterGroup(vd);
        requireRegisterGroup(vs1);
        requireRegisterGroup(vs2);
        int funct6 = (raw >>> 26) & 0x3f;
        long result = readElement(vs1, 0);
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                result = switch (funct6) {
                    case 0x01, 0x03 -> executeFloatingPointOperation(0x00, result, readElement(vs2, element));
                    case 0x05 -> executeFloatingPointOperation(0x04, result, readElement(vs2, element));
                    case 0x07 -> executeFloatingPointOperation(0x06, result, readElement(vs2, element));
                    default -> throw new AssertionError("Unexpected floating-point reduction: " + funct6);
                };
            }
        }
        writeElement(vd, 0, result);
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes vector widening floating-point operations.
    private void executeWideningFloatingPoint(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        int funct3 = (raw >>> 12) & 0x7;
        int funct6 = (raw >>> 26) & 0x3f;
        if (sewBits() != Float.SIZE) {
            throw new RiscVException("Vector widening floating-point operations require SEW 32");
        }
        long narrowGroupBytes = groupBytesForElementBits(Float.SIZE);
        long wideGroupBytes = groupBytesForElementBits(Double.SIZE);
        requireRegisterGroup(vd, wideGroupBytes);
        boolean wideLeft = funct6 == 0x34 || funct6 == 0x36;
        requireRegisterGroup(vs2, wideLeft ? wideGroupBytes : narrowGroupBytes);
        if (funct3 == OPFVV) {
            requireRegisterGroup(vs1, narrowGroupBytes);
        }
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                double left = wideLeft
                        ? Double.longBitsToDouble(readElement(vs2, element, Double.BYTES, wideGroupBytes))
                        : Float.intBitsToFloat((int) readElement(vs2, element, Float.BYTES, narrowGroupBytes));
                double right = funct3 == OPFVV
                        ? Float.intBitsToFloat((int) readElement(vs1, element, Float.BYTES, narrowGroupBytes))
                        : Float.intBitsToFloat((int) state.decodedFloatingPointRegister(vs1));
                double result = switch (funct6) {
                    case 0x30, 0x34 -> left + right;
                    case 0x32, 0x36 -> left - right;
                    case 0x38 -> left * right;
                    default -> throw new AssertionError("Unexpected widening floating-point operation: " + funct6);
                };
                writeElement(vd, element, Double.BYTES, wideGroupBytes, Double.doubleToRawLongBits(result));
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes vector widening floating-point reduction operations.
    private void executeWideningFloatingPointReduction(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        int funct6 = (raw >>> 26) & 0x3f;
        if (sewBits() != Float.SIZE) {
            throw new RiscVException("Vector widening floating-point reductions require SEW 32");
        }
        long narrowGroupBytes = groupBytesForElementBits(Float.SIZE);
        long wideGroupBytes = groupBytesForElementBits(Double.SIZE);
        requireRegisterGroup(vd, wideGroupBytes);
        requireRegisterGroup(vs1, wideGroupBytes);
        requireRegisterGroup(vs2, narrowGroupBytes);
        double result = Double.longBitsToDouble(readElement(vs1, 0, Double.BYTES, wideGroupBytes));
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                double value = Float.intBitsToFloat((int) readElement(vs2, element, Float.BYTES, narrowGroupBytes));
                result = switch (funct6) {
                    case 0x31, 0x33 -> result + value;
                    default -> throw new AssertionError("Unexpected widening floating-point reduction: " + funct6);
                };
            }
        }
        writeElement(vd, 0, Double.BYTES, wideGroupBytes, Double.doubleToRawLongBits(result));
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes vector widening floating-point fused multiply-add operations.
    private void executeWideningFloatingPointFusedMultiplyAdd(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        int funct3 = (raw >>> 12) & 0x7;
        int funct6 = (raw >>> 26) & 0x3f;
        if (sewBits() != Float.SIZE) {
            throw new RiscVException("Vector widening floating-point FMA operations require SEW 32");
        }
        long narrowGroupBytes = groupBytesForElementBits(Float.SIZE);
        long wideGroupBytes = groupBytesForElementBits(Double.SIZE);
        requireRegisterGroup(vd, wideGroupBytes);
        requireRegisterGroup(vs2, narrowGroupBytes);
        if (funct3 == OPFVV) {
            requireRegisterGroup(vs1, narrowGroupBytes);
        }
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                double left = funct3 == OPFVV
                        ? Float.intBitsToFloat((int) readElement(vs1, element, Float.BYTES, narrowGroupBytes))
                        : Float.intBitsToFloat((int) state.decodedFloatingPointRegister(vs1));
                double right = Float.intBitsToFloat((int) readElement(vs2, element, Float.BYTES, narrowGroupBytes));
                double accumulator = Double.longBitsToDouble(readElement(vd, element, Double.BYTES, wideGroupBytes));
                double result = switch (funct6) {
                    case 0x3c -> left * right + accumulator;
                    case 0x3d -> -(left * right) + accumulator;
                    case 0x3e -> left * right - accumulator;
                    case 0x3f -> -(left * right) - accumulator;
                    default -> throw new AssertionError("Unexpected widening floating-point FMA: " + funct6);
                };
                writeElement(vd, element, Double.BYTES, wideGroupBytes, Double.doubleToRawLongBits(result));
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes vector floating-point fused multiply-add operations.
    private void executeFloatingPointFusedMultiplyAdd(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        int funct3 = (raw >>> 12) & 0x7;
        int funct6 = (raw >>> 26) & 0x3f;
        requireRegisterGroup(vd);
        requireRegisterGroup(vs2);
        if (funct3 == OPFVV) {
            requireRegisterGroup(vs1);
        }
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                long multiplicandBits = funct3 == OPFVV ? readElement(vs1, element) : state.decodedFloatingPointRegister(vs1);
                long vectorBits = readElement(vs2, element);
                long accumulatorBits = readElement(vd, element);
                writeElement(vd, element, executeFloatingPointFusedMultiplyAddOperation(funct6, multiplicandBits, vectorBits, accumulatorBits));
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes floating-point slide1 instructions.
    private void executeFloatingPointSlide(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        requireRegisterGroup(vd);
        requireRegisterGroup(vs2);
        int funct6 = (raw >>> 26) & 0x3f;
        boolean slideDown = funct6 == 0x0f;
        long scalar = state.decodedFloatingPointRegister(vs1);
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                if (slideDown) {
                    writeElement(vd, element, element + 1 == length ? scalar : readElement(vs2, element + 1));
                } else {
                    writeElement(vd, element, element == 0 ? scalar : readElement(vs2, element - 1));
                }
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes floating-point merge and move instructions.
    private void executeFloatingPointMerge(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        int funct3 = (raw >>> 12) & 0x7;
        requireRegisterGroup(vd);
        requireRegisterGroup(vs2);
        if (funct3 == OPFVV) {
            requireRegisterGroup(vs1);
        }
        boolean unmasked = ((raw >>> 25) & 0x1) != 0;
        if (unmasked && vs2 != 0) {
            throw unsupportedVectorIntegerFormat((raw >>> 26) & 0x3f, funct3);
        }
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (unmasked || isActive(raw, element)) {
                long value = funct3 == OPFVV ? readElement(vs1, element) : state.decodedFloatingPointRegister(vs1);
                writeElement(vd, element, unmasked || maskBit(element) ? value : readElement(vs2, element));
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes one vector floating-point operation.
    private long executeFloatingPointOperation(int funct6, long leftBits, long rightBits) {
        return sewBits() == Float.SIZE
                ? executeSingleFloatingPointOperation(funct6, (int) leftBits, (int) rightBits) & 0xffff_ffffL
                : executeDoubleFloatingPointOperation(funct6, leftBits, rightBits);
    }

    /// Executes one single-precision vector floating-point operation.
    private int executeSingleFloatingPointOperation(int funct6, int leftBits, int rightBits) {
        float left = Float.intBitsToFloat(leftBits);
        float right = Float.intBitsToFloat(rightBits);
        return switch (funct6) {
            case 0x00 -> Float.floatToRawIntBits(left + right);
            case 0x02 -> Float.floatToRawIntBits(left - right);
            case 0x04 -> Float.floatToRawIntBits(Math.min(left, right));
            case 0x06 -> Float.floatToRawIntBits(Math.max(left, right));
            case 0x08 -> signInjectSingle(leftBits, rightBits, false, false);
            case 0x09 -> signInjectSingle(leftBits, rightBits, true, false);
            case 0x0a -> signInjectSingle(leftBits, rightBits, false, true);
            case 0x20 -> Float.floatToRawIntBits(left / right);
            case 0x21 -> Float.floatToRawIntBits(right / left);
            case 0x24 -> Float.floatToRawIntBits(left * right);
            case 0x27 -> Float.floatToRawIntBits(right - left);
            default -> throw new RiscVException("Unsupported vector floating-point operation funct6: 0x"
                    + Integer.toUnsignedString(funct6, 16));
        };
    }

    /// Executes one double-precision vector floating-point operation.
    private long executeDoubleFloatingPointOperation(int funct6, long leftBits, long rightBits) {
        double left = Double.longBitsToDouble(leftBits);
        double right = Double.longBitsToDouble(rightBits);
        return switch (funct6) {
            case 0x00 -> Double.doubleToRawLongBits(left + right);
            case 0x02 -> Double.doubleToRawLongBits(left - right);
            case 0x04 -> Double.doubleToRawLongBits(Math.min(left, right));
            case 0x06 -> Double.doubleToRawLongBits(Math.max(left, right));
            case 0x08 -> signInjectDouble(leftBits, rightBits, false, false);
            case 0x09 -> signInjectDouble(leftBits, rightBits, true, false);
            case 0x0a -> signInjectDouble(leftBits, rightBits, false, true);
            case 0x20 -> Double.doubleToRawLongBits(left / right);
            case 0x21 -> Double.doubleToRawLongBits(right / left);
            case 0x24 -> Double.doubleToRawLongBits(left * right);
            case 0x27 -> Double.doubleToRawLongBits(right - left);
            default -> throw new RiscVException("Unsupported vector floating-point operation funct6: 0x"
                    + Integer.toUnsignedString(funct6, 16));
        };
    }

    /// Executes one vector floating-point comparison.
    private boolean executeFloatingPointCompare(int funct6, long leftBits, long rightBits) {
        if (sewBits() == Float.SIZE) {
            float left = Float.intBitsToFloat((int) leftBits);
            float right = Float.intBitsToFloat((int) rightBits);
            return switch (funct6) {
                case 0x18 -> left == right;
                case 0x19 -> left <= right;
                case 0x1b -> left < right;
                case 0x1c -> left != right;
                case 0x1d -> left > right;
                case 0x1f -> left >= right;
                default -> throw new RiscVException("Unsupported vector floating-point compare funct6: 0x"
                        + Integer.toUnsignedString(funct6, 16));
            };
        }
        double left = Double.longBitsToDouble(leftBits);
        double right = Double.longBitsToDouble(rightBits);
        return switch (funct6) {
            case 0x18 -> left == right;
            case 0x19 -> left <= right;
            case 0x1b -> left < right;
            case 0x1c -> left != right;
            case 0x1d -> left > right;
            case 0x1f -> left >= right;
            default -> throw new RiscVException("Unsupported vector floating-point compare funct6: 0x"
                    + Integer.toUnsignedString(funct6, 16));
        };
    }

    /// Applies single-precision sign injection.
    private static int signInjectSingle(int left, int right, boolean invert, boolean xor) {
        int sign = xor ? left ^ right : right;
        if (invert) {
            sign ^= 0x8000_0000;
        }
        return left & 0x7fff_ffff | sign & 0x8000_0000;
    }

    /// Applies double-precision sign injection.
    private static long signInjectDouble(long left, long right, boolean invert, boolean xor) {
        long sign = xor ? left ^ right : right;
        if (invert) {
            sign ^= Long.MIN_VALUE;
        }
        return left & Long.MAX_VALUE | sign & Long.MIN_VALUE;
    }

    /// Executes one floating-point unary operation from the `VFUNARY1` space.
    private long executeFloatingPointUnary1(int selector, long valueBits, MachineState state) {
        if (sewBits() == Float.SIZE) {
            float value = Float.intBitsToFloat((int) valueBits);
            return switch (selector) {
                case 0 -> Float.floatToRawIntBits((float) Math.sqrt(value)) & 0xffff_ffffL;
                case 4 -> Float.floatToRawIntBits(1.0f / (float) Math.sqrt(value)) & 0xffff_ffffL;
                case 5 -> Float.floatToRawIntBits(1.0f / value) & 0xffff_ffffL;
                default -> throw new RiscVException("Unsupported vector floating-point unary selector: " + selector);
            };
        }
        double value = Double.longBitsToDouble(valueBits);
        return switch (selector) {
            case 0 -> Double.doubleToRawLongBits(Math.sqrt(value));
            case 4 -> Double.doubleToRawLongBits(1.0d / Math.sqrt(value));
            case 5 -> Double.doubleToRawLongBits(1.0d / value);
            default -> throw new RiscVException("Unsupported vector floating-point unary selector: " + selector);
        };
    }

    /// Executes one floating-point conversion from the `VFUNARY0` space.
    private long executeFloatingPointConvert(int selector, long valueBits, MachineState state) {
        int roundingMode = effectiveFloatingPointRoundingMode(state);
        if (sewBits() == Float.SIZE) {
            float value = Float.intBitsToFloat((int) valueBits);
            return switch (selector) {
                case 0, 6 -> convertFloatingPointToUnsignedInteger(state, value, roundingMode, Integer.SIZE) & 0xffff_ffffL;
                case 1, 7 -> convertFloatingPointToSignedInteger(state, value, roundingMode, Integer.SIZE) & 0xffff_ffffL;
                case 2 -> Float.floatToRawIntBits((float) (valueBits & 0xffff_ffffL)) & 0xffff_ffffL;
                case 3 -> Float.floatToRawIntBits((float) (int) valueBits) & 0xffff_ffffL;
                default -> throw new RiscVException("Unsupported vector floating-point conversion selector: " + selector);
            };
        }
        double value = Double.longBitsToDouble(valueBits);
        return switch (selector) {
            case 0, 6 -> convertFloatingPointToUnsignedInteger(state, value, roundingMode, Long.SIZE);
            case 1, 7 -> convertFloatingPointToSignedInteger(state, value, roundingMode, Long.SIZE);
            case 2 -> Double.doubleToRawLongBits((double) valueBits);
            case 3 -> Double.doubleToRawLongBits((double) valueBits);
            default -> throw new RiscVException("Unsupported vector floating-point conversion selector: " + selector);
        };
    }

    /// Executes one vector floating-point fused multiply-add operation.
    private long executeFloatingPointFusedMultiplyAddOperation(int funct6, long multiplicandBits, long vectorBits, long accumulatorBits) {
        if (sewBits() == Float.SIZE) {
            float multiplicand = Float.intBitsToFloat((int) multiplicandBits);
            float vector = Float.intBitsToFloat((int) vectorBits);
            float accumulator = Float.intBitsToFloat((int) accumulatorBits);
            float result = switch (funct6) {
                case 0x28 -> multiplicand * accumulator + vector;
                case 0x29 -> -(multiplicand * accumulator) - vector;
                case 0x2a -> multiplicand * accumulator - vector;
                case 0x2b -> -(multiplicand * accumulator) + vector;
                case 0x2c -> multiplicand * vector + accumulator;
                case 0x2d -> -(multiplicand * vector) + accumulator;
                case 0x2e -> multiplicand * vector - accumulator;
                case 0x2f -> -(multiplicand * vector) - accumulator;
                default -> throw new AssertionError("Unexpected floating-point FMA operation: " + funct6);
            };
            return Float.floatToRawIntBits(result) & 0xffff_ffffL;
        }
        double multiplicand = Double.longBitsToDouble(multiplicandBits);
        double vector = Double.longBitsToDouble(vectorBits);
        double accumulator = Double.longBitsToDouble(accumulatorBits);
        double result = switch (funct6) {
            case 0x28 -> multiplicand * accumulator + vector;
            case 0x29 -> -(multiplicand * accumulator) - vector;
            case 0x2a -> multiplicand * accumulator - vector;
            case 0x2b -> -(multiplicand * accumulator) + vector;
            case 0x2c -> multiplicand * vector + accumulator;
            case 0x2d -> -(multiplicand * vector) + accumulator;
            case 0x2e -> multiplicand * vector - accumulator;
            case 0x2f -> -(multiplicand * vector) - accumulator;
            default -> throw new AssertionError("Unexpected floating-point FMA operation: " + funct6);
        };
        return Double.doubleToRawLongBits(result);
    }

    /// Classifies one floating-point element using the scalar `fclass` bit layout.
    private long floatingPointClass(long valueBits) {
        if (sewBits() == Float.SIZE) {
            int bits = (int) valueBits;
            int exponent = bits >>> 23 & 0xff;
            int fraction = bits & 0x7f_ffff;
            boolean negative = bits < 0;
            if (exponent == 0xff) {
                if (fraction == 0) {
                    return negative ? 1 : 1 << 7;
                }
                return (fraction & 0x40_0000) == 0 ? 1 << 8 : 1 << 9;
            }
            if (exponent == 0) {
                if (fraction == 0) {
                    return negative ? 1 << 3 : 1 << 4;
                }
                return negative ? 1 << 2 : 1 << 5;
            }
            return negative ? 1 << 1 : 1 << 6;
        }
        int exponent = (int) (valueBits >>> 52) & 0x7ff;
        long fraction = valueBits & 0x000f_ffff_ffff_ffffL;
        boolean negative = valueBits < 0;
        if (exponent == 0x7ff) {
            if (fraction == 0) {
                return negative ? 1 : 1 << 7;
            }
            return (fraction & 0x0008_0000_0000_0000L) == 0 ? 1 << 8 : 1 << 9;
        }
        if (exponent == 0) {
            if (fraction == 0) {
                return negative ? 1 << 3 : 1 << 4;
            }
            return negative ? 1 << 2 : 1 << 5;
        }
        return negative ? 1 << 1 : 1 << 6;
    }

    /// Returns the active floating-point rounding mode for vector conversions.
    private static int effectiveFloatingPointRoundingMode(MachineState state) {
        int roundingMode = state.floatingPointRoundingMode();
        if (roundingMode > ROUND_NEAREST_MAX_MAGNITUDE) {
            throw new RiscVException("Unsupported floating-point rounding mode: " + roundingMode);
        }
        return roundingMode;
    }

    /// Converts a floating-point value to an unsigned integer with saturation.
    private static long convertFloatingPointToUnsignedInteger(MachineState state, double value, int roundingMode, int bits) {
        double rounded = roundFloatingPointToInteger(value, roundingMode);
        double exclusiveUpperBound = bits == Long.SIZE ? 0x1.0p64 : (double) (1L << bits);
        if (Double.isNaN(value) || rounded < 0.0d) {
            state.addFloatingPointFlags(FLOAT_INVALID_OPERATION);
            return 0;
        }
        if (rounded >= exclusiveUpperBound) {
            state.addFloatingPointFlags(FLOAT_INVALID_OPERATION);
            return maskForBits(bits);
        }
        if (rounded != value) {
            state.addFloatingPointFlags(FLOAT_INEXACT);
        }
        return bits == Long.SIZE && rounded >= 0x1.0p63
                ? ((long) (rounded - 0x1.0p63)) | Long.MIN_VALUE
                : (long) rounded;
    }

    /// Converts a floating-point value to a signed integer with saturation.
    private static long convertFloatingPointToSignedInteger(MachineState state, double value, int roundingMode, int bits) {
        double rounded = roundFloatingPointToInteger(value, roundingMode);
        double minimum = bits == Long.SIZE ? Long.MIN_VALUE : -(double) (1L << (bits - 1));
        double exclusiveUpperBound = bits == Long.SIZE ? 0x1.0p63 : (double) (1L << (bits - 1));
        if (Double.isNaN(value) || rounded < minimum) {
            state.addFloatingPointFlags(FLOAT_INVALID_OPERATION);
            return (long) minimum;
        }
        if (rounded >= exclusiveUpperBound) {
            state.addFloatingPointFlags(FLOAT_INVALID_OPERATION);
            return bits == Long.SIZE ? Long.MAX_VALUE : (1L << (bits - 1)) - 1;
        }
        if (rounded != value) {
            state.addFloatingPointFlags(FLOAT_INEXACT);
        }
        return (long) rounded;
    }

    /// Rounds a floating-point value to an integral double.
    private static double roundFloatingPointToInteger(double value, int roundingMode) {
        return switch (roundingMode) {
            case ROUND_NEAREST_EVEN -> Math.rint(value);
            case ROUND_TOWARDS_ZERO -> value < 0.0d ? Math.ceil(value) : Math.floor(value);
            case ROUND_DOWN -> Math.floor(value);
            case ROUND_UP -> Math.ceil(value);
            case ROUND_NEAREST_MAX_MAGNITUDE -> value < 0.0d ? Math.ceil(value - 0.5d) : Math.floor(value + 0.5d);
            default -> throw new RiscVException("Unsupported floating-point rounding mode: " + roundingMode);
        };
    }

    /// Executes an integer reduction operation.
    private void executeReduction(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        requireRegisterGroup(vd);
        requireRegisterGroup(vs1);
        requireRegisterGroup(vs2);
        int funct6 = (raw >>> 26) & 0x3f;
        long result = readElement(vs1, 0);
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                result = executeReductionOperation(funct6, result, readElement(vs2, element));
            }
        }
        writeElement(vd, 0, result);
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes one reduction step.
    private long executeReductionOperation(int funct6, long accumulator, long value) {
        long mask = elementMask();
        long left = accumulator & mask;
        long right = value & mask;
        return switch (funct6) {
            case 0x00 -> (left + right) & mask;
            case 0x01 -> left & right;
            case 0x02 -> left | right;
            case 0x03 -> left ^ right;
            case 0x04 -> Long.compareUnsigned(left, right) <= 0 ? left : right;
            case 0x05 -> signedElement(left) <= signedElement(right) ? left : right;
            case 0x06 -> Long.compareUnsigned(left, right) >= 0 ? left : right;
            case 0x07 -> signedElement(left) >= signedElement(right) ? left : right;
            default -> throw new RiscVException("Unsupported vector reduction funct6: 0x"
                    + Integer.toUnsignedString(funct6, 16));
        };
    }

    /// Executes an add-with-carry or subtract-with-borrow operation.
    private void executeCarryBorrow(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        int funct3 = (raw >>> 12) & 0x7;
        int funct6 = (raw >>> 26) & 0x3f;
        boolean unmasked = ((raw >>> 25) & 0x1) != 0;
        boolean subtract = funct6 == 0x12 || funct6 == 0x13;
        boolean maskResult = funct6 == 0x11 || funct6 == 0x13;
        if ((funct3 == OPIVI && subtract) || (funct3 != OPIVV && funct3 != OPIVX && funct3 != OPIVI)) {
            throw unsupportedVectorIntegerFormat(funct6, funct3);
        }
        if (!maskResult && (unmasked || vd == 0)) {
            throw new RiscVException("Unsupported vector carry/borrow instruction encoding");
        }
        if (!maskResult) {
            requireRegisterGroup(vd);
        }
        requireRegisterGroup(vs2);
        if (funct3 == OPIVV) {
            requireRegisterGroup(vs1);
        }

        long mask = elementMask();
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            long left = readElement(vs2, element) & mask;
            long right = switch (funct3) {
                case OPIVV -> readElement(vs1, element) & mask;
                case OPIVX -> state.decodedRegister(vs1) & mask;
                case OPIVI -> signExtend(vs1, 5) & mask;
                default -> throw new AssertionError("Unexpected carry/borrow format: " + funct3);
            };
            long carry = unmasked ? 0 : maskBit(element) ? 1 : 0;
            if (maskResult) {
                boolean bit = subtract ? borrowOut(left, right, carry) : carryOut(left, right, carry);
                writeMaskBit(vd, element, bit);
            } else {
                long value = subtract ? left - right - carry : left + right + carry;
                writeElement(vd, element, value & mask);
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes a vector register gather operation.
    private void executeGather(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        requireRegisterGroup(vd);
        requireRegisterGroup(vs2);
        int funct3 = (raw >>> 12) & 0x7;
        if (funct3 == OPIVV) {
            requireRegisterGroup(vs1);
        } else if (funct3 != OPIVX && funct3 != OPIVI) {
            throw unsupportedVectorIntegerFormat((raw >>> 26) & 0x3f, funct3);
        }
        long maximumLength = maximumVectorLength();
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                long index = switch (funct3) {
                    case OPIVV -> readElement(vs1, element);
                    case OPIVX -> state.decodedRegister(vs1);
                    case OPIVI -> vs1 & 0x1fL;
                    default -> throw new AssertionError("Unexpected gather format: " + funct3);
                };
                writeElement(vd, element, Long.compareUnsigned(index, maximumLength) < 0 ? readElement(vs2, index) : 0);
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes `vrgatherei16.vv`.
    private void executeGatherEi16(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        requireRegisterGroup(vd);
        requireRegisterGroup(vs2);
        long indexGroupBytes = groupBytesForElementBits(Short.SIZE);
        requireRegisterGroup(vs1, indexGroupBytes);
        long maximumLength = maximumVectorLength();
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                long index = readElement(vs1, element, Short.BYTES, indexGroupBytes);
                writeElement(vd, element, Long.compareUnsigned(index, maximumLength) < 0 ? readElement(vs2, index) : 0);
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes a vector slide operation.
    private void executeSlide(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        requireRegisterGroup(vd);
        requireRegisterGroup(vs2);
        int funct3 = (raw >>> 12) & 0x7;
        int funct6 = (raw >>> 26) & 0x3f;
        boolean slideDown = funct6 == 0x0f;
        boolean slideOne = funct3 == OPMVX;
        if (funct3 != OPIVX && funct3 != OPIVI && funct3 != OPMVX) {
            throw unsupportedVectorIntegerFormat(funct6, funct3);
        }
        long offset = slideOne ? 1 : funct3 == OPIVI ? vs1 & 0x1fL : state.decodedRegister(vs1);
        long scalar = slideOne ? state.decodedRegister(vs1) & elementMask() : 0;
        long start = vectorStart;
        long length = vectorLength;
        long maximumLength = maximumVectorLength();
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                if (slideDown) {
                    long source = element + offset;
                    long value = slideOne && element + 1 == length
                            ? scalar
                            : source < maximumLength ? readElement(vs2, source) : 0;
                    writeElement(vd, element, value);
                } else if (slideOne && element == 0) {
                    writeElement(vd, element, scalar);
                } else if (Long.compareUnsigned(element, offset) >= 0) {
                    writeElement(vd, element, readElement(vs2, element - offset));
                }
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes `vcompress.vm`.
    private void executeCompress(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        if (((raw >>> 25) & 0x1) == 0 || vectorStart != 0) {
            throw new RiscVException("Unsupported vector compress instruction encoding");
        }
        requireRegisterGroup(vd);
        requireRegisterGroup(vs2);
        long output = 0;
        long length = vectorLength;
        for (long element = 0; element < length; element++) {
            if (readMaskBit(vs1, element)) {
                writeElement(vd, output, readElement(vs2, element));
                output++;
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes integer sign/zero extension unary instructions.
    private void executeIntegerExtensionUnary(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        int factor = switch (vs1) {
            case 2, 3 -> 8;
            case 4, 5 -> 4;
            case 6, 7 -> 2;
            default -> throw unsupportedVectorIntegerFormat((raw >>> 26) & 0x3f, (raw >>> 12) & 0x7);
        };
        int sourceBits = sewBits() / factor;
        if (sourceBits < Byte.SIZE) {
            throw new RiscVException("Vector integer extension source EEW is below 8 bits");
        }
        long sourceGroupBytes = groupBytesForElementBits(sourceBits);
        requireRegisterGroup(vd);
        requireRegisterGroup(vs2, sourceGroupBytes);
        boolean signed = (vs1 & 1) != 0;
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                long value = readElement(vs2, element, sourceBits / Byte.SIZE, sourceGroupBytes);
                writeElement(vd, element, signed ? signExtend(value, sourceBits) : value);
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes `Zvbb`/`Zvkb` vector bit-manipulation unary instructions.
    private void executeIntegerBitManipUnary(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        requireRegisterGroup(vd);
        requireRegisterGroup(vs2);
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                writeElement(vd, element, executeIntegerBitManipUnaryOperation(vs1, readElement(vs2, element)));
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes one `Zvbb`/`Zvkb` vector bit-manipulation unary operation.
    private long executeIntegerBitManipUnaryOperation(int selector, long value) {
        long normalized = value & elementMask();
        return switch (selector) {
            case 8 -> reverseBitsInBytes(normalized);
            case 9 -> reverseBytesInElement(normalized);
            case 10 -> reverseBitsInElement(normalized);
            case 12 -> leadingZerosInElement(normalized);
            case 13 -> trailingZerosInElement(normalized);
            case 14 -> DataIndependent.bitCount(normalized);
            default -> throw new AssertionError("Unexpected vector bit-manipulation selector: " + selector);
        };
    }

    /// Executes `Zvbc` vector carry-less multiplication instructions.
    private void executeCarrylessMultiply(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        int funct3 = (raw >>> 12) & 0x7;
        int funct6 = (raw >>> 26) & 0x3f;
        if (sewBits() != Long.SIZE) {
            throw new RiscVException("Zvbc vector carry-less multiplication requires SEW 64");
        }
        requireRegisterGroup(vd);
        requireRegisterGroup(vs2);
        if (funct3 == OPMVV) {
            requireRegisterGroup(vs1);
        }

        boolean highHalf = funct6 == 0x0d;
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                long left = readElement(vs2, element);
                long right = funct3 == OPMVV ? readElement(vs1, element) : state.decodedRegister(vs1);
                writeElement(
                        vd,
                        element,
                        highHalf ? carrylessMultiplyHigh(left, right) : carrylessMultiplyLow(left, right));
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Returns the low half of a 64-bit carry-less multiplication.
    private static long carrylessMultiplyLow(long left, long right) {
        long result = 0;
        for (int bit = 0; bit < Long.SIZE; bit++) {
            long mask = -((right >>> bit) & 1L);
            result ^= left << bit & mask;
        }
        return result;
    }

    /// Returns the high half of a 64-bit carry-less multiplication.
    private static long carrylessMultiplyHigh(long left, long right) {
        long result = 0;
        for (int bit = 1; bit < Long.SIZE; bit++) {
            long mask = -((right >>> bit) & 1L);
            result ^= left >>> (Long.SIZE - bit) & mask;
        }
        return result;
    }

    /// Executes `vwsll.[vv,vx,vi]`.
    private void executeWideningShiftLeftLogical(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        int funct3 = (raw >>> 12) & 0x7;
        int sourceBits = sewBits();
        int resultBits = sourceBits * 2;
        requireSupportedWideElement(resultBits);
        long sourceGroupBytes = groupBytesForElementBits(sourceBits);
        long resultGroupBytes = groupBytesForElementBits(resultBits);
        requireRegisterGroup(vd, resultGroupBytes);
        requireRegisterGroup(vs2, sourceGroupBytes);
        if (funct3 == OPIVV) {
            requireRegisterGroup(vs1, sourceGroupBytes);
        }
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                long value = readElement(vs2, element, sourceBits / Byte.SIZE, sourceGroupBytes);
                long shift = integerOperand(funct3, vs1, element, state, sourceBits, true) & (resultBits - 1);
                writeElement(
                        vd,
                        element,
                        resultBits / Byte.SIZE,
                        resultGroupBytes,
                        value << shift & maskForBits(resultBits));
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes a whole-vector-register move.
    private void executeWholeRegisterMove(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        if (((raw >>> 25) & 0x1) == 0) {
            throw new RiscVException("Whole-register vector move must be unmasked");
        }
        int registersToMove = switch (vs1) {
            case 0 -> 1;
            case 1 -> 2;
            case 3 -> 4;
            case 7 -> 8;
            default -> throw unsupportedVectorIntegerFormat((raw >>> 26) & 0x3f, (raw >>> 12) & 0x7);
        };
        requireWholeRegisterGroup(vd, registersToMove);
        requireWholeRegisterGroup(vs2, registersToMove);
        for (int register = 0; register < registersToMove; register++) {
            System.arraycopy(registers[vs2 + register], 0, registers[vd + register], 0, vlenBytes);
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes widening integer arithmetic instructions.
    private void executeWideningInteger(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        int funct3 = (raw >>> 12) & 0x7;
        int funct6 = (raw >>> 26) & 0x3f;
        int narrowBits = sewBits();
        int wideBits = narrowBits * 2;
        requireSupportedWideElement(wideBits);
        long narrowGroupBytes = groupBytesForElementBits(narrowBits);
        long wideGroupBytes = groupBytesForElementBits(wideBits);
        requireRegisterGroup(vd, wideGroupBytes);
        requireRegisterGroup(vs2, funct6 >= 0x34 && funct6 <= 0x37 ? wideGroupBytes : narrowGroupBytes);
        if (funct3 == OPIVV || funct3 == OPMVV) {
            requireRegisterGroup(vs1, narrowGroupBytes);
        }
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                long left;
                long right;
                boolean signed;
                boolean subtract = funct6 == 0x32 || funct6 == 0x33 || funct6 == 0x36 || funct6 == 0x37;
                if (funct6 >= 0x38) {
                    long rawLeft = readElement(vs2, element, narrowBits / Byte.SIZE, narrowGroupBytes);
                    long rawRight = integerOperand(funct3, vs1, element, state, narrowBits, false);
                    long product = switch (funct6) {
                        case 0x38 -> (rawLeft & maskForBits(narrowBits)) * (rawRight & maskForBits(narrowBits));
                        case 0x3a -> signedValue(rawLeft, narrowBits) * (rawRight & maskForBits(narrowBits));
                        case 0x3b -> signedValue(rawLeft, narrowBits) * signedValue(rawRight, narrowBits);
                        default -> throw new AssertionError("Unexpected widening multiply: " + funct6);
                    };
                    writeElement(vd, element, wideBits / Byte.SIZE, wideGroupBytes, product & maskForBits(wideBits));
                    continue;
                }
                if (funct6 >= 0x34) {
                    left = readElement(vs2, element, wideBits / Byte.SIZE, wideGroupBytes);
                    right = integerOperand(funct3, vs1, element, state, narrowBits, false);
                    signed = funct6 == 0x35 || funct6 == 0x37;
                } else {
                    left = readElement(vs2, element, narrowBits / Byte.SIZE, narrowGroupBytes);
                    right = integerOperand(funct3, vs1, element, state, narrowBits, false);
                    signed = funct6 == 0x31 || funct6 == 0x33;
                }
                long normalizedLeft = signed ? signedValue(left, funct6 >= 0x34 ? wideBits : narrowBits) : left & maskForBits(funct6 >= 0x34 ? wideBits : narrowBits);
                long normalizedRight = signed ? signedValue(right, narrowBits) : right & maskForBits(narrowBits);
                long result = subtract ? normalizedLeft - normalizedRight : normalizedLeft + normalizedRight;
                writeElement(vd, element, wideBits / Byte.SIZE, wideGroupBytes, result & maskForBits(wideBits));
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes widening integer reduction instructions.
    private void executeWideningReduction(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        int funct6 = (raw >>> 26) & 0x3f;
        int narrowBits = sewBits();
        int wideBits = narrowBits * 2;
        requireSupportedWideElement(wideBits);
        long narrowGroupBytes = groupBytesForElementBits(narrowBits);
        long wideGroupBytes = groupBytesForElementBits(wideBits);
        requireRegisterGroup(vd, wideGroupBytes);
        requireRegisterGroup(vs1, wideGroupBytes);
        requireRegisterGroup(vs2, narrowGroupBytes);
        boolean signed = funct6 == 0x31;
        long result = readElement(vs1, 0, wideBits / Byte.SIZE, wideGroupBytes);
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                long value = readElement(vs2, element, narrowBits / Byte.SIZE, narrowGroupBytes);
                result = (result + (signed ? signedValue(value, narrowBits) : value & maskForBits(narrowBits))) & maskForBits(wideBits);
            }
        }
        writeElement(vd, 0, wideBits / Byte.SIZE, wideGroupBytes, result);
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes single-width and widening integer multiply-add instructions.
    private void executeIntegerMultiplyAdd(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        int funct3 = (raw >>> 12) & 0x7;
        int funct6 = (raw >>> 26) & 0x3f;
        boolean widening = funct6 >= 0x3c;
        int sourceBits = sewBits();
        int resultBits = widening ? sourceBits * 2 : sourceBits;
        requireSupportedWideElement(resultBits);
        long sourceGroupBytes = groupBytesForElementBits(sourceBits);
        long resultGroupBytes = groupBytesForElementBits(resultBits);
        requireRegisterGroup(vd, resultGroupBytes);
        requireRegisterGroup(vs2, sourceGroupBytes);
        if (funct3 == OPMVV) {
            requireRegisterGroup(vs1, sourceGroupBytes);
        }
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                long left = integerOperand(funct3, vs1, element, state, sourceBits, false);
                long right = readElement(vs2, element, sourceBits / Byte.SIZE, sourceGroupBytes);
                long accumulator = readElement(vd, element, resultBits / Byte.SIZE, resultGroupBytes);
                long result;
                if (widening) {
                    long product = wideningMultiply(funct6, funct3, left, right, sourceBits);
                    result = accumulator + product;
                } else {
                    long product = left * right;
                    result = switch (funct6) {
                        case 0x29 -> left * accumulator + right;
                        case 0x2b -> right - left * accumulator;
                        case 0x2d -> product + accumulator;
                        case 0x2f -> accumulator - product;
                        default -> throw new AssertionError("Unexpected multiply-add: " + funct6);
                    };
                }
                writeElement(vd, element, resultBits / Byte.SIZE, resultGroupBytes, result & maskForBits(resultBits));
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes narrowing integer shift and clip instructions.
    private void executeNarrowingInteger(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        int funct3 = (raw >>> 12) & 0x7;
        int funct6 = (raw >>> 26) & 0x3f;
        int narrowBits = sewBits();
        int wideBits = narrowBits * 2;
        requireSupportedWideElement(wideBits);
        long narrowGroupBytes = groupBytesForElementBits(narrowBits);
        long wideGroupBytes = groupBytesForElementBits(wideBits);
        requireRegisterGroup(vd, narrowGroupBytes);
        requireRegisterGroup(vs2, wideGroupBytes);
        if (funct3 == OPIVV) {
            requireRegisterGroup(vs1, narrowGroupBytes);
        }
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                long value = readElement(vs2, element, wideBits / Byte.SIZE, wideGroupBytes);
                int shift = (int) integerOperand(funct3, vs1, element, state, narrowBits, true) & (wideBits - 1);
                long result = switch (funct6) {
                    case 0x2c -> value >>> shift;
                    case 0x2d -> signedValue(value, wideBits) >> shift;
                    case 0x2e -> saturateUnsigned(roundShiftRight(value, wideBits, shift, false), narrowBits);
                    case 0x2f -> saturateSigned(roundShiftRight(value, wideBits, shift, true), narrowBits);
                    default -> throw new AssertionError("Unexpected narrowing operation: " + funct6);
                };
                writeElement(vd, element, narrowBits / Byte.SIZE, narrowGroupBytes, result & maskForBits(narrowBits));
            }
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes fixed-point integer arithmetic instructions.
    private void executeFixedPointInteger(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        int funct3 = (raw >>> 12) & 0x7;
        int funct6 = (raw >>> 26) & 0x3f;
        requireRegisterGroup(vd);
        requireRegisterGroup(vs2);
        if (funct3 == OPIVV || funct3 == OPMVV) {
            requireRegisterGroup(vs1);
        }
        boolean saturated = false;
        long mask = elementMask();
        int bits = sewBits();
        long start = vectorStart;
        long length = vectorLength;
        for (long element = start; element < length; element++) {
            if (isActive(raw, element)) {
                long left = readElement(vs2, element);
                long right = integerOperand(funct3, vs1, element, state, bits, true);
                SaturatingResult result = switch (funct6) {
                    case 0x08 -> new SaturatingResult(roundShiftRight((left & mask) + (right & mask), bits + 1, 1, false), false);
                    case 0x09 -> new SaturatingResult(roundShiftRight(signedElement(left) + signedValue(right, bits), bits + 1, 1, true), false);
                    case 0x0a -> new SaturatingResult(roundShiftRight((left & mask) - (right & mask), bits + 1, 1, false), false);
                    case 0x0b -> new SaturatingResult(roundShiftRight(signedElement(left) - signedValue(right, bits), bits + 1, 1, true), false);
                    case 0x20 -> saturatingUnsigned(left + right, bits);
                    case 0x21 -> saturatingSigned(signedElement(left) + signedValue(right, bits), bits);
                    case 0x22 -> saturatingUnsigned((left & mask) - (right & mask), bits);
                    case 0x23 -> saturatingSigned(signedElement(left) - signedValue(right, bits), bits);
                    case 0x27 -> new SaturatingResult(roundShiftRight(signedElement(left) * signedValue(right, bits), bits * 2, bits - 1, true), false);
                    case 0x2a -> new SaturatingResult(roundShiftRight(left & mask, bits, (int) right & (bits - 1), false), false);
                    case 0x2b -> new SaturatingResult(roundShiftRight(signedElement(left), bits, (int) right & (bits - 1), true), false);
                    default -> throw new AssertionError("Unexpected fixed-point operation: " + funct6);
                };
                saturated |= result.saturated();
                writeElement(vd, element, result.value() & mask);
            }
        }
        if (saturated) {
            fixedPointSaturate = 1;
        }
        vectorStart = 0;
        state.setPc(nextPc);
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

    /// Returns the rotation amount encoded by a vector rotate instruction.
    private int rotationAmount(int funct6, int funct3, int raw, long right) {
        int amount = (int) right;
        if (funct3 == OPIVI && funct6 == 0x15) {
            amount = ((raw >>> 26) & 1) << 5 | ((raw >>> 15) & 0x1f);
        }
        return amount;
    }

    /// Rotates an element left by a masked amount.
    private long rotateLeftElement(long value, long amount) {
        int bits = sewBits();
        int shift = (int) amount & (bits - 1);
        long mask = elementMask();
        long normalized = value & mask;
        return (normalized << shift | normalized >>> ((bits - shift) & (bits - 1))) & mask;
    }

    /// Rotates an element right by a masked amount.
    private long rotateRightElement(long value, long amount) {
        int bits = sewBits();
        int shift = (int) amount & (bits - 1);
        long mask = elementMask();
        long normalized = value & mask;
        return (normalized >>> shift | normalized << ((bits - shift) & (bits - 1))) & mask;
    }

    /// Reverses all bits in the current element width.
    private long reverseBitsInElement(long value) {
        return Long.reverse(value & elementMask()) >>> (Long.SIZE - sewBits());
    }

    /// Reverses bits independently in every byte of the current element.
    private long reverseBitsInBytes(long value) {
        long result = 0;
        int bytes = sewBytes();
        for (int index = 0; index < bytes; index++) {
            long byteValue = value >>> (index * Byte.SIZE) & 0xffL;
            result |= reverseByte(byteValue) << (index * Byte.SIZE);
        }
        return result;
    }

    /// Reverses byte order in the current element.
    private long reverseBytesInElement(long value) {
        long result = 0;
        int bytes = sewBytes();
        for (int index = 0; index < bytes; index++) {
            long byteValue = value >>> (index * Byte.SIZE) & 0xffL;
            result |= byteValue << ((bytes - 1 - index) * Byte.SIZE);
        }
        return result;
    }

    /// Reverses the bits in one byte without a lookup table.
    private static long reverseByte(long value) {
        long result = value & 0xffL;
        result = (result >>> 1 & 0x55) | (result & 0x55) << 1;
        result = (result >>> 2 & 0x33) | (result & 0x33) << 2;
        return (result >>> 4 & 0x0f) | (result & 0x0f) << 4;
    }

    /// Counts leading zero bits in the current element.
    private long leadingZerosInElement(long value) {
        int bits = sewBits();
        long zeros = DataIndependent.numberOfLeadingZeros(value << (Long.SIZE - bits));
        return zeros - (zeros >>> 6) * (Long.SIZE - bits);
    }

    /// Counts trailing zero bits in the current element.
    private long trailingZerosInElement(long value) {
        return DataIndependent.numberOfTrailingZeros(value | ~elementMask());
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

    /// Returns whether an unsigned element addition carries out.
    private boolean carryOut(long left, long right, long carry) {
        int bits = sewBits();
        if (bits == Long.SIZE) {
            long partial = left + right;
            return Long.compareUnsigned(partial, left) < 0 || carry == 1 && partial == -1L;
        }
        return left + right + carry > elementMask();
    }

    /// Returns whether an unsigned element subtraction borrows out.
    private boolean borrowOut(long left, long right, long borrow) {
        int bits = sewBits();
        if (bits == Long.SIZE) {
            return borrow == 0 ? Long.compareUnsigned(left, right) < 0 : Long.compareUnsigned(left, right) <= 0;
        }
        return left < right + borrow;
    }

    /// Executes a vector mask logical operation.
    private void executeMaskLogical(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
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

    /// Executes vector mask scalar operations and scalar/vector moves.
    private void executeMaskScalarOrMove(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        int funct3 = (raw >>> 12) & 0x7;
        boolean unmasked = ((raw >>> 25) & 0x1) != 0;
        if (funct3 == OPMVV && vs1 == 0 && unmasked) {
            requireRegisterGroup(vs2);
            state.setDecodedRegister(vd, signedElement(readElement(vs2, 0)));
        } else if (funct3 == OPMVV && (vs1 == 16 || vs1 == 17)) {
            executeMaskScalar(raw, vd, vs1, vs2, state);
        } else if (funct3 == OPMVX && vs2 == 0 && unmasked) {
            requireRegisterGroup(vd);
            if (vectorStart == 0 && vectorLength > 0) {
                writeElement(vd, 0, state.decodedRegister(vs1));
            }
        } else {
            throw unsupportedVectorIntegerFormat((raw >>> 26) & 0x3f, funct3);
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Executes `vcpop.m` and `vfirst.m`.
    private void executeMaskScalar(int raw, int vd, int vs1, int vs2, MachineState state) {
        long start = vectorStart;
        long length = vectorLength;
        if (vs1 == 16) {
            long count = 0;
            for (long element = start; element < length; element++) {
                if (isActive(raw, element) && readMaskBit(vs2, element)) {
                    count++;
                }
            }
            state.setDecodedRegister(vd, count);
        } else {
            long first = -1;
            for (long element = start; element < length; element++) {
                if (isActive(raw, element) && readMaskBit(vs2, element)) {
                    first = element;
                    break;
                }
            }
            state.setDecodedRegister(vd, first);
        }
    }

    /// Executes vector mask prefix, iota, and index-generation operations.
    private void executeMaskUnary(int raw, int vd, int vs1, int vs2, long nextPc, MachineState state) {
        if (vectorStart != 0) {
            throw new RiscVException("Unsupported vector mask unary instruction with non-zero vstart");
        }
        long length = vectorLength;
        if (vs1 == 17 && vs2 == 0) {
            requireRegisterGroup(vd);
            for (long element = 0; element < length; element++) {
                if (isActive(raw, element)) {
                    writeElement(vd, element, element);
                }
            }
        } else if (vs1 == 16) {
            requireRegisterGroup(vd);
            long count = 0;
            for (long element = 0; element < length; element++) {
                boolean active = isActive(raw, element);
                if (active) {
                    writeElement(vd, element, count);
                }
                if (active && readMaskBit(vs2, element)) {
                    count++;
                }
            }
        } else if (vs1 >= 1 && vs1 <= 3) {
            boolean found = false;
            for (long element = 0; element < length; element++) {
                if (isActive(raw, element)) {
                    boolean source = readMaskBit(vs2, element);
                    boolean result = switch (vs1) {
                        case 1 -> !found && !source;
                        case 2 -> !found && source;
                        case 3 -> !found;
                        default -> throw new AssertionError("Unexpected mask prefix operation: " + vs1);
                    };
                    writeMaskBit(vd, element, result);
                    if (source) {
                        found = true;
                    }
                }
            }
        } else {
            throw unsupportedVectorIntegerFormat((raw >>> 26) & 0x3f, (raw >>> 12) & 0x7);
        }
        vectorStart = 0;
        state.setPc(nextPc);
    }

    /// Requires that a widened element fits in the implemented ELEN.
    private static void requireSupportedWideElement(int bits) {
        if (bits > ELEN_BITS) {
            throw new RiscVException("Vector widened element width is not supported: " + bits);
        }
    }

    /// Reads an integer vector operand with the requested element width.
    private long integerOperand(int funct3, int vs1, long element, MachineState state, int bits, boolean unsignedImmediate) {
        return switch (funct3) {
            case OPIVV, OPMVV -> readElementBits(vs1, element, bits);
            case OPIVX, OPMVX -> state.decodedRegister(vs1) & maskForBits(bits);
            case OPIVI -> (unsignedImmediate ? vs1 & 0x1fL : signExtend(vs1, 5)) & maskForBits(bits);
            default -> throw unsupportedVectorIntegerFormat(0, funct3);
        };
    }

    /// Returns a raw mask for an element width.
    private static long maskForBits(int bits) {
        return bits == Long.SIZE ? -1L : (1L << bits) - 1L;
    }

    /// Interprets a raw value as a signed element with the supplied width.
    private static long signedValue(long value, int bits) {
        return bits == Long.SIZE ? value : signExtend(value, bits);
    }

    /// Computes a widening integer multiply result for the encoded signedness.
    private long wideningMultiply(int funct6, int funct3, long left, long right, int bits) {
        return switch (funct6) {
            case 0x3c -> (left & maskForBits(bits)) * (right & maskForBits(bits));
            case 0x3d -> signedValue(left, bits) * signedValue(right, bits);
            case 0x3e -> {
                if (funct3 != OPMVX) {
                    throw unsupportedVectorIntegerFormat(funct6, funct3);
                }
                yield (left & maskForBits(bits)) * signedValue(right, bits);
            }
            case 0x3f -> signedValue(left, bits) * (right & maskForBits(bits));
            default -> throw new AssertionError("Unexpected widening multiply-add operation: " + funct6);
        };
    }

    /// Right-shifts a fixed-point value using the current vector rounding mode.
    private long roundShiftRight(long value, int valueBits, int shift, boolean signed) {
        if (shift == 0) {
            return value;
        }
        BigInteger input = signed ? BigInteger.valueOf(value) : unsignedBigInteger(value);
        BigInteger increment = fixedPointRoundingIncrement(input, shift);
        BigInteger rounded = input.add(increment).shiftRight(shift);
        return rounded.longValue();
    }

    /// Returns the fixed-point rounding increment for a value shifted right by `shift`.
    private BigInteger fixedPointRoundingIncrement(BigInteger value, int shift) {
        if (shift <= 0) {
            return BigInteger.ZERO;
        }
        return switch (fixedPointRoundingMode) {
            case 0 -> BigInteger.ONE.shiftLeft(shift - 1);
            case 1 -> {
                BigInteger half = BigInteger.ONE.shiftLeft(shift - 1);
                BigInteger retained = value.shiftRight(shift).and(BigInteger.ONE);
                yield half.subtract(BigInteger.ONE).add(retained);
            }
            case 2 -> BigInteger.ZERO;
            case 3 -> {
                BigInteger discardedMask = BigInteger.ONE.shiftLeft(shift).subtract(BigInteger.ONE);
                boolean discarded = !value.and(discardedMask).equals(BigInteger.ZERO);
                boolean retainedOdd = value.shiftRight(shift).testBit(0);
                yield discarded && !retainedOdd ? BigInteger.ONE : BigInteger.ZERO;
            }
            default -> throw new RiscVException("Unsupported vector fixed-point rounding mode: " + fixedPointRoundingMode);
        };
    }

    /// Saturates an unsigned value and records `vxsat` on overflow.
    private long saturateUnsigned(long value, int bits) {
        long maximum = maskForBits(bits);
        if (value < 0) {
            fixedPointSaturate = 1;
            return 0;
        }
        if (Long.compareUnsigned(value, maximum) > 0) {
            fixedPointSaturate = 1;
            return maximum;
        }
        return value;
    }

    /// Saturates a signed value and records `vxsat` on overflow.
    private long saturateSigned(long value, int bits) {
        long minimum = bits == Long.SIZE ? Long.MIN_VALUE : -(1L << (bits - 1));
        long maximum = bits == Long.SIZE ? Long.MAX_VALUE : (1L << (bits - 1)) - 1;
        if (value < minimum) {
            fixedPointSaturate = 1;
            return minimum & maskForBits(bits);
        }
        if (value > maximum) {
            fixedPointSaturate = 1;
            return maximum;
        }
        return value;
    }

    /// Returns an unsigned saturating result.
    private SaturatingResult saturatingUnsigned(long value, int bits) {
        long maximum = maskForBits(bits);
        if (value < 0) {
            return new SaturatingResult(0, true);
        }
        if (Long.compareUnsigned(value, maximum) > 0) {
            return new SaturatingResult(maximum, true);
        }
        return new SaturatingResult(value, false);
    }

    /// Returns a signed saturating result.
    private SaturatingResult saturatingSigned(long value, int bits) {
        long minimum = bits == Long.SIZE ? Long.MIN_VALUE : -(1L << (bits - 1));
        long maximum = bits == Long.SIZE ? Long.MAX_VALUE : (1L << (bits - 1)) - 1;
        if (value < minimum) {
            return new SaturatingResult(minimum & maskForBits(bits), true);
        }
        if (value > maximum) {
            return new SaturatingResult(maximum, true);
        }
        return new SaturatingResult(value, false);
    }

    /// Requires an integer vector operation to use an implemented operand format.
    private static void requireSupportedIntegerFormat(int funct6, int funct3, int raw) {
        switch (funct6) {
            case 0x00, 0x09, 0x0a, 0x0b, 0x18, 0x19, 0x1c, 0x1d -> {
                if (funct3 != OPIVV && funct3 != OPIVX && funct3 != OPIVI) {
                    throw unsupportedVectorIntegerFormat(funct6, funct3);
                }
            }
            case 0x01 -> {
                if (funct3 != OPIVV && funct3 != OPIVX) {
                    throw unsupportedVectorIntegerFormat(funct6, funct3);
                }
            }
            case 0x02, 0x04, 0x05, 0x06, 0x07, 0x1a, 0x1b -> {
                if (funct3 != OPIVV && funct3 != OPIVX) {
                    throw unsupportedVectorIntegerFormat(funct6, funct3);
                }
            }
            case 0x03, 0x1e, 0x1f -> {
                if (funct3 != OPIVX && funct3 != OPIVI) {
                    throw unsupportedVectorIntegerFormat(funct6, funct3);
                }
            }
            case 0x14 -> {
                if (funct3 != OPIVV && funct3 != OPIVX && funct3 != OPIVI) {
                    throw unsupportedVectorIntegerFormat(funct6, funct3);
                }
            }
            case 0x15 -> {
                if (funct3 != OPIVV && funct3 != OPIVX && funct3 != OPIVI) {
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

    /// Returns whether the floating-point operation writes a mask compare result.
    private static boolean isFloatingPointCompare(int funct6) {
        return funct6 == 0x18 || funct6 == 0x19 || funct6 == 0x1b || funct6 == 0x1c || funct6 == 0x1d || funct6 == 0x1f;
    }

    /// Returns whether the operation is a floating-point scalar move.
    private static boolean isFloatingPointScalarMove(int funct3, int funct6, int vs1, int vs2) {
        return funct6 == 0x10 && (funct3 == OPFVV && vs1 == 0 || funct3 == OPFVF && vs2 == 0);
    }

    /// Returns whether the operation is a floating-point unary instruction.
    private static boolean isFloatingPointUnary(int funct3, int funct6) {
        return funct3 == OPFVV && (funct6 == 0x12 || funct6 == 0x13);
    }

    /// Returns whether the operation is a floating-point reduction instruction.
    private static boolean isFloatingPointReduction(int funct3, int funct6) {
        return funct3 == OPFVV && (funct6 == 0x01 || funct6 == 0x03 || funct6 == 0x05 || funct6 == 0x07);
    }

    /// Returns whether the operation is a widening floating-point reduction instruction.
    private static boolean isWideningFloatingPointReduction(int funct3, int funct6) {
        return funct3 == OPFVV && (funct6 == 0x31 || funct6 == 0x33);
    }

    /// Returns whether the operation is a widening floating-point instruction.
    private static boolean isWideningFloatingPoint(int funct3, int funct6) {
        return (funct3 == OPFVV || funct3 == OPFVF)
                && (funct6 == 0x30 || funct6 == 0x32 || funct6 == 0x34 || funct6 == 0x36 || funct6 == 0x38);
    }

    /// Returns whether the operation is a widening floating-point FMA instruction.
    private static boolean isWideningFloatingPointFusedMultiplyAdd(int funct3, int funct6) {
        return (funct3 == OPFVV || funct3 == OPFVF) && funct6 >= 0x3c && funct6 <= 0x3f;
    }

    /// Returns whether the operation is a floating-point fused multiply-add instruction.
    private static boolean isFloatingPointFusedMultiplyAdd(int funct6) {
        return funct6 >= 0x28 && funct6 <= 0x2f;
    }

    /// Returns whether the operation is a floating-point slide1 instruction.
    private static boolean isFloatingPointSlide(int funct3, int funct6) {
        return funct3 == OPFVF && (funct6 == 0x0e || funct6 == 0x0f);
    }

    /// Returns whether the operation is a floating-point merge or vector move instruction.
    private static boolean isFloatingPointMerge(int funct3, int funct6) {
        return funct6 == 0x17 && (funct3 == OPFVV || funct3 == OPFVF);
    }

    /// Returns whether the operation is an integer reduction instruction.
    private static boolean isReduction(int funct3, int funct6) {
        return funct3 == OPMVV && funct6 >= 0x00 && funct6 <= 0x07;
    }

    /// Returns whether the operation is a widening integer reduction instruction.
    private static boolean isWideningReduction(int funct3, int funct6) {
        return funct3 == OPMVV && (funct6 == 0x30 || funct6 == 0x31);
    }

    /// Returns whether the operation is a mask logical instruction.
    private static boolean isMaskLogical(int funct3, int funct6) {
        return funct3 == OPMVV && funct6 >= 0x18 && funct6 <= 0x1f;
    }

    /// Returns whether the operation is a mask scalar operation or scalar/vector move.
    private static boolean isMaskScalarOrMove(int funct3, int funct6, int vs1, int vs2) {
        return funct6 == 0x10
                && (funct3 == OPMVV && (vs1 == 0 || vs1 == 16 || vs1 == 17)
                || funct3 == OPMVX && vs2 == 0);
    }

    /// Returns whether the operation is a mask prefix, iota, or index-generation instruction.
    private static boolean isMaskUnary(int funct3, int funct6, int vs1, int vs2) {
        return funct3 == OPMVV && funct6 == 0x14 && (vs1 >= 1 && vs1 <= 3 || vs1 == 16 || vs1 == 17 && vs2 == 0);
    }

    /// Returns whether the operation is an integer sign/zero extension unary instruction.
    private static boolean isIntegerExtensionUnary(int funct3, int funct6, int vs1) {
        return funct3 == OPMVV && funct6 == 0x12 && vs1 >= 2 && vs1 <= 7;
    }

    /// Returns whether the operation is a `Zvbb`/`Zvkb` bit-manipulation unary instruction.
    private static boolean isIntegerBitManipUnary(int funct3, int funct6, int vs1) {
        return funct3 == OPMVV
                && funct6 == 0x12
                && (vs1 == 8 || vs1 == 9 || vs1 == 10 || vs1 == 12 || vs1 == 13 || vs1 == 14);
    }

    /// Returns whether the operation is a `Zvbc` carry-less multiplication instruction.
    private static boolean isCarrylessMultiply(int funct3, int funct6) {
        return (funct6 == 0x0c || funct6 == 0x0d) && (funct3 == OPMVV || funct3 == OPMVX);
    }

    /// Returns whether the operation is a whole-register vector move.
    private static boolean isWholeRegisterMove(int funct3, int funct6) {
        return funct3 == OPIVI && funct6 == 0x27;
    }

    /// Returns whether the operation is an add-with-carry or subtract-with-borrow instruction.
    private static boolean isCarryBorrow(int funct6) {
        return funct6 >= 0x10 && funct6 <= 0x13;
    }

    /// Returns whether the operation is a gather instruction.
    private static boolean isGather(int funct3, int funct6) {
        return funct6 == 0x0c && (funct3 == OPIVV || funct3 == OPIVX || funct3 == OPIVI);
    }

    /// Returns whether the operation is `vrgatherei16.vv`.
    private static boolean isGatherEi16(int funct3, int funct6) {
        return funct3 == OPMVV && funct6 == 0x0e;
    }

    /// Returns whether the operation is a slide instruction.
    private static boolean isSlide(int funct3, int funct6) {
        return (funct6 == 0x0e || funct6 == 0x0f) && (funct3 == OPIVX || funct3 == OPIVI || funct3 == OPMVX);
    }

    /// Returns whether the operation is `vcompress.vm`.
    private static boolean isCompress(int funct3, int funct6) {
        return funct3 == OPMVV && funct6 == 0x17;
    }

    /// Returns whether the operation is a widening integer arithmetic instruction.
    private static boolean isWideningInteger(int funct3, int funct6) {
        boolean opiv = funct3 == OPIVV || funct3 == OPIVX;
        boolean opm = funct3 == OPMVV || funct3 == OPMVX;
        return opiv && funct6 >= 0x30 && funct6 <= 0x33
                || opm && funct6 >= 0x34 && funct6 <= 0x38
                || opm && (funct6 == 0x3a || funct6 == 0x3b);
    }

    /// Returns whether the operation is `vwsll.[vv,vx,vi]`.
    private static boolean isWideningShiftLeftLogical(int funct3, int funct6) {
        return funct6 == 0x35 && (funct3 == OPIVV || funct3 == OPIVX || funct3 == OPIVI);
    }

    /// Returns whether the operation is an integer multiply-add instruction.
    private static boolean isIntegerMultiplyAdd(int funct3, int funct6) {
        return (funct3 == OPMVV || funct3 == OPMVX)
                && (funct6 == 0x29 || funct6 == 0x2b || funct6 == 0x2d || funct6 == 0x2f
                || funct6 >= 0x3c && funct6 <= 0x3f);
    }

    /// Returns whether the operation is a narrowing integer instruction.
    private static boolean isNarrowingInteger(int funct3, int funct6) {
        return (funct3 == OPIVV || funct3 == OPIVX || funct3 == OPIVI)
                && funct6 >= 0x2c && funct6 <= 0x2f;
    }

    /// Returns whether the operation is a fixed-point integer instruction.
    private static boolean isFixedPointInteger(int funct3, int funct6) {
        boolean opiv = funct3 == OPIVV || funct3 == OPIVX || funct3 == OPIVI;
        boolean opm = funct3 == OPMVV || funct3 == OPMVX;
        return opiv && (funct6 >= 0x20 && funct6 <= 0x23 || funct6 == 0x27 || funct6 == 0x2a || funct6 == 0x2b)
                || opm && funct6 >= 0x08 && funct6 <= 0x0b;
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

    /// Stores a fixed-point saturation result.
    ///
    /// @param value the element result
    /// @param saturated whether the operation saturated
    private record SaturatingResult(long value, boolean saturated) {
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
    /// @param fields the number of fields in each segment or whole-register transfer
    /// @param strided whether the instruction uses scalar strided addressing
    /// @param indexed whether the instruction uses vector indexed addressing
    /// @param faultOnlyFirst whether the load is a fault-only-first unit-stride load
    /// @param wholeRegister whether the transfer moves complete vector registers
    /// @param mask whether the transfer moves a packed vector mask
    /// @param memoryOperand the scalar stride register or vector index register
    private record VectorMemoryShape(
            int elementBytes,
            int fields,
            boolean strided,
            boolean indexed,
            boolean faultOnlyFirst,
            boolean wholeRegister,
            boolean mask,
            int memoryOperand) {
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
