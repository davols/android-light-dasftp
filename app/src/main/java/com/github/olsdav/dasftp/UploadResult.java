package com.github.olsdav.dasftp;

/**
 * Created by morn on 30.05.14.
 */
public class UploadResult {
    private boolean hasFailed;
    private String failedReason=null;
    private String mUrl;


    public UploadResult(String failedReason,String mUrl)
    {
        this.setmUrl(mUrl);
        this.setFailedReason(failedReason);
    }
    public UploadResult()
    {
        mUrl=null;
    }

    public boolean hasFailed() {
        return hasFailed;
    }


    public String getFailedReason() {
        return failedReason;
    }

    public void setFailedReason(String failedReason) {
        if(failedReason!=null) {
            this.hasFailed=true;
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
