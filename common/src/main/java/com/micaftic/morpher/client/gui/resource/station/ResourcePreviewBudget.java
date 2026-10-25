package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.client.gui.resource.ModelRepoClient;
import java.util.concurrent.atomic.AtomicLong;

/** A page's transfer allowance includes every attempted mirror and every card. */
public final class ResourcePreviewBudget {
    private final long limit;
    private final AtomicLong transferred = new AtomicLong();

    public ResourcePreviewBudget(long limit) {
        if (limit <= 0) throw new IllegalArgumentException("limit");
        this.limit = limit;
    }

    public long transferredBytes() { return transferred.get(); }
    public boolean exhausted() { return transferred.get() >= limit; }

    public ModelRepoClient.ProgressListener listener(DownloadCancellation cancellation) {
        return new ModelRepoClient.ProgressListener() {
            private int previous;
            @Override public void onCandidate(String url, int index, int total) {
                previous = 0;
                if (exhausted()) cancellation.cancel();
                cancellation.checkCancelled();
            }
            @Override public void onProgress(int downloaded, int total) {
                cancellation.checkCancelled();
                if (downloaded < previous) previous = 0;
                long totalTransferred = transferred.addAndGet(Math.max(0, downloaded - previous));
                previous = downloaded;
                if (totalTransferred > limit) {
                    cancellation.cancel();
                    cancellation.checkCancelled();
                }
            }
            @Override public boolean isCancelled() { return cancellation.isCancelled(); }
            @Override public Runnable onCancel(Runnable action) { return cancellation.onCancel(action); }
        };
    }
}
