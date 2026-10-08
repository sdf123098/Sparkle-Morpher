package com.micaftic.morpher.core.storage;

import java.util.function.BooleanSupplier;

/**
 * Small, testable boundary between committing an imported source and publishing its runtime candidate.
 * A publication failure cannot undo a committed source, so the outcome retains that source for recovery.
 */
public final class ImportCommitFlow {

    private ImportCommitFlow() { }

    public enum State {
        SUPERSEDED_BEFORE_COMMIT,
        FAILED_BEFORE_COMMIT,
        SOURCE_COMMITTED_PENDING_PUBLICATION,
        PUBLISHED
    }

    public record Outcome<T>(State state, T committedSource, Exception failure) {
        public boolean sourceCommitted() {
            return state == State.SOURCE_COMMITTED_PENDING_PUBLICATION || state == State.PUBLISHED;
        }
    }

    @FunctionalInterface
    public interface Committer<T> {
        T commit() throws Exception;
    }

    @FunctionalInterface
    public interface Publisher<T> {
        void publish(T committedSource) throws Exception;
    }

    public static <T> Outcome<T> commitThenPublish(Committer<T> committer, Publisher<T> publisher) {
        return commitThenPublish(() -> true, committer, publisher);
    }

    public static <T> Outcome<T> commitThenPublish(BooleanSupplier isCurrent,
                                                   Committer<T> committer,
                                                   Publisher<T> publisher) {
        if (!isCurrent.getAsBoolean()) {
            return new Outcome<>(State.SUPERSEDED_BEFORE_COMMIT, null, null);
        }
        final T committedSource;
        try {
            committedSource = committer.commit();
        } catch (Exception failure) {
            return new Outcome<>(State.FAILED_BEFORE_COMMIT, null, failure);
        }
        try {
            publisher.publish(committedSource);
            return new Outcome<>(State.PUBLISHED, committedSource, null);
        } catch (Exception failure) {
            return new Outcome<>(State.SOURCE_COMMITTED_PENDING_PUBLICATION, committedSource, failure);
        }
    }
}
