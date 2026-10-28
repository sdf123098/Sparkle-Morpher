package com.micaftic.morpher.core.zstd;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/** Little-endian, bounds-checked byte-array access for the embedded Zstd codec. */
final class ArrayMemory {
    // Codec addresses are byte-array indices, independent of JVM object layout.
    static final int ARRAY_BYTE_BASE_OFFSET = 0;
    static final ArrayMemory MEMORY = new ArrayMemory();
    private static final VarHandle SHORT = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle INT = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle LONG = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    private ArrayMemory() {}

    byte getByte(Object base, long address) {
        return ((byte[]) base)[Math.toIntExact(address)];
    }

    short getShort(Object base, long address) {
        return (short) SHORT.get((byte[]) base, Math.toIntExact(address));
    }

    int getInt(Object base, long address) {
        return (int) INT.get((byte[]) base, Math.toIntExact(address));
    }

    long getLong(Object base, long address) {
        return (long) LONG.get((byte[]) base, Math.toIntExact(address));
    }

    void putByte(Object base, long address, byte value) {
        ((byte[]) base)[Math.toIntExact(address)] = value;
    }

    void putShort(Object base, long address, short value) {
        SHORT.set((byte[]) base, Math.toIntExact(address), value);
    }

    void putInt(Object base, long address, int value) {
        INT.set((byte[]) base, Math.toIntExact(address), value);
    }

    void putLong(Object base, long address, long value) {
        LONG.set((byte[]) base, Math.toIntExact(address), value);
    }

    void copyMemory(Object source, long sourceAddress, Object target, long targetAddress, long length) {
        System.arraycopy((byte[]) source, Math.toIntExact(sourceAddress),
                (byte[]) target, Math.toIntExact(targetAddress), Math.toIntExact(length));
    }
}
