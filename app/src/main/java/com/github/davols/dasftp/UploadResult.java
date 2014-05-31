package com.github.davols.dasftp;

/**
 * Created by davols on 30.05.14.
 */
public class UploadResult {
    private boolean hasFailed;
    private String failedReason = null;
    private String mUrl;
    private String mName;
    private String uploadName;
    private String filePath;

    public UploadResult(String failedReason, String mUrl) {
        this.setmUrl(mUrl);
        this.setFailedReason(failedReason);
    }

    public UploadResult() {
        mUrl = null;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getName() {
        return mName;
    }

    public void setName(String mName) {
        this.mName = mName;
    }

    public String getUploadName() {
        return uploadName;
    }

    public void setUploadName(String uploadName) {
        this.uploadName = uploadName;
    }

    public boolean hasFailed() {
        return hasFailed;
    }


    public String getFailedReason() {
        return failedReason;
    }

    public void setFailedReason(String failedReason) {
        if (failedReason != null) {
            this.hasFailed = true;
        }
        this.failedReason = failedReason;
    }

    public String getmUrl() {
        return mUrl;
    }

    public void setmUrl(String mUrl) {
        this.mUrl = mUrl;
    }
}
