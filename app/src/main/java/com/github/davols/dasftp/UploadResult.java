package com.github.davols.dasftp;

/**
 * Created by davols on 30.05.14.
 */
public class UploadResult extends TaskResult {

    private String mUrl;
    private String mName;
    private String uploadName;
    private String filePath;

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


    public String getmUrl() {
        return mUrl;
    }

    public void setmUrl(String mUrl) {
        this.mUrl = mUrl;
    }
}
