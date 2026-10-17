package com.micaftic.morpher.peer;

/** Pure marker implemented by the client context; the evaluator keeps no Minecraft client references. */
public interface PeerEvaluationDomain {
    boolean peerInput();
    long evaluationFrame();
}
