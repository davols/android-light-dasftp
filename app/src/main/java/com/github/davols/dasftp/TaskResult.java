package com.github.davols.dasftp;

/**
 * Created by morn on 07.06.14.
 */
public abstract class TaskResult {
    private boolean failed = false;
    private String failedReason = null;

    public String getFailedReason() {
        return failedReason;
    }

    public void setFailedReason(String failedReason) {
        if (failedReason != null) {
            this.failed = true;
        }
        this.failedReason = failedReason;

    }

    public boolean hasFailed() {
        return failed;
    }


}
