package com.github.davols.dasftp;

/**
 * Created by morn on 07.06.14.
 */
public class DownloadResult extends TaskResult {

    private String mName;
    private String uploadName;
    private String filePath;

    public DownloadResult(String failedReason) {

        this.setFailedReason(failedReason);
    }

    public DownloadResult() {

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


}
