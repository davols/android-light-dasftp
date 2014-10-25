package com.github.davols.dasftp;

/**
 * Created by davols on 29.06.14.
 */
public class SavedSite {


    private int id;
    private String mUrl;
    private String mHost;
    private String mPath;
    private String mUsername;
    private String mPassword;
    private int mPort;

    public String getUrl() {
        return mUrl;
    }

    public void setUrl(String mUrl) {
        this.mUrl = mUrl;
    }

    public String getHost() {
        return mHost;
    }

    public void setHost(String mHost) {
        this.mHost = mHost;
    }

    public String getPath() {
        return mPath;
    }

    public void setPath(String mPath) {
        this.mPath = mPath;
    }

    public String getUsername() {
        return mUsername;
    }

    public void setUsername(String mUsername) {
        this.mUsername = mUsername;
    }

    public String getPassword() {
        return mPassword;
    }

    public void setPassword(String mPassword) {
        this.mPassword = mPassword;
    }

    public int getPort() {
        return mPort;
    }

    public void setPort(int mPort) {
        this.mPort = mPort;
    }

    public void enCrypt(String secretKey) throws Exception
    {

        setUrl(SimpleCrypto.encrypt(this.mUrl, secretKey));
        setHost(SimpleCrypto.encrypt(this.mHost, secretKey));
        setPath(SimpleCrypto.encrypt(this.mPath, secretKey));
        setUsername(SimpleCrypto.encrypt(this.mUsername, secretKey));
        setPassword(SimpleCrypto.encrypt(this.mPassword, secretKey));

    }
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
