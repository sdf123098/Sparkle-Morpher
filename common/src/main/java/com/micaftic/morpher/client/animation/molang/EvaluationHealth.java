package com.micaftic.morpher.client.animation.molang;

/** Side channel for values whose Molang conversion legally returns zero. */
public final class EvaluationHealth {
    private static final ThreadLocal<long[]> FAILURES = ThreadLocal.withInitial(()->new long[1]);
    private EvaluationHealth() { }
    public static long sequence() { return FAILURES.get()[0]; }
    public static void invalid() { FAILURES.get()[0]++; }
}
