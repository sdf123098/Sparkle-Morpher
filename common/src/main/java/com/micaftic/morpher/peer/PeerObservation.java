package com.micaftic.morpher.peer;

import java.util.Objects;

/** An observer's current load/submission report, never proof of a visible GPU frame. */
public final class PeerObservation {
    public enum State { DOWNLOADING, VERIFIED, IMPORTING, APPLIED, RENDER_SUBMITTED, HIDDEN, NOT_TRACKED, FAILED, WAITING_RENDER }
    public record Report(PeerSession owner, long revision, State state, PeerFrame.ErrorCode failure) {
        public Report {
            Objects.requireNonNull(owner); Objects.requireNonNull(state);
            if(revision < 0 || (state == State.FAILED) != (failure != null)) throw new IllegalArgumentException("Invalid Peer observation");
        }
    }
    public record Receipt(PeerSession observer, Report report) {
        public Receipt { Objects.requireNonNull(observer); Objects.requireNonNull(report); }
    }
    private PeerObservation() {}
}
