package com.github.davols.dasftp;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddSiteActivity extends Activity implements View.OnClickListener {

    SavedSite decryptedSite=new SavedSite();

    private EditText etHost;
    private EditText etPort;
    private EditText etPath;
    private EditText etUrl;
    private EditText etUsername;
    private EditText etPassword;
    private Switch swSigned;
    private String secretKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_site);
        getSecretKey();
        initViews();

    }


    private void getSecretKey()
    {
        SharedPreferences sp = getSharedPreferences("DASFTP", MODE_PRIVATE);
        if (sp.getString("ENCODE",null) != null) {
            secretKey = sp.getString("ENCODE", null);
        }
        else
        {
            secretKey = null;
        }


    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            setResult(RESULT_CANCELED);
            finish();
        }
        return super.onMenuItemSelected(featureId, item);
    }

    private void initViews()
    {
        etHost = (EditText) findViewById(R.id.et_host);
        etPort = (EditText) findViewById(R.id.et_port);
        etPath = (EditText) findViewById(R.id.et_path);
        etUrl = (EditText) findViewById(R.id.et_url);
        etUsername = (EditText) findViewById(R.id.et_username);
        etPassword = (EditText) findViewById(R.id.et_password);
        swSigned = (Switch) findViewById(R.id.switch1);
        Button btnCancel = (Button) findViewById(R.id.btn_cancel);
        Button btnSave = (Button) findViewById(R.id.btn_save);
        btnCancel.setOnClickListener(this);
        btnSave.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch(view.getId()){
            case R.id.btn_cancel:
                setResult(RESULT_CANCELED);
                finish();
                break;
            case R.id.btn_save:
                startSave();
                break;
        }
    }
    private void startSave()
    {
        boolean somethingWrong=false;
        //Check if all entries are ok first.
        String myHost = etHost.getText().toString();
        String myPath = etPath.getText().toString();
        String myUrl = etUrl.getText().toString();
        String myUsername = etUsername.getText().toString();
        String myPassword = etPassword.getText().toString();
        String myPort = etPort.getText().toString();
        if (myHost != null) {
            Pattern p = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
            Matcher m = p.matcher(myHost.toString());

            if (m.find() && (myHost.toString().contains("http"))) {
                DialogFragment newFragment = MyAlertDialogFragment.newInstance(
                        R.string.ip4_and_http_title, R.string.ip4_and_http);
                newFragment.show(getFragmentManager(), "dialog");
                somethingWrong=true;
            } else {
                decryptedSite.setHost(myHost);
            }
        }
        if (myUsername!=null && myUsername.length()!=myUsername.trim().length()) {
            Toast.makeText(this, R.string.make_sure_username_is_correct, Toast.LENGTH_LONG).show();
            somethingWrong=true;
        }
        else
        {
            decryptedSite.setUsername(myUsername);
        }
        if (myPath != null && myPath.length()!=myPath.trim().length()) {
            Toast.makeText(this, R.string.make_sure_path_is_correct, Toast.LENGTH_LONG).show();
            somethingWrong=true;
        }
        else
        {
            decryptedSite.setPath(myPath);
        }

        //Make sure the port only contains digits.
        Pattern pattern = Pattern.compile("^[0-9]+$");
        Matcher m = pattern.matcher(myPort);
        if (!m.matches()) {
            DialogFragment newFragment = MyAlertDialogFragment.newInstance(
                    R.string.number_port_title, R.string.number_port);
            newFragment.show(getFragmentManager(), "dialog");
            somethingWrong=true;
        }
        else
        {
            decryptedSite.setPort(Integer.parseInt(myPort));
        }

        if(myPassword!=null)
        {
            decryptedSite.setPassword(myPassword);
        }
        else
        {
            somethingWrong=true;
            Toast.makeText(this, R.string.make_sure_password_is_correct, Toast.LENGTH_LONG).show();
        }
        if(myUrl!=null)
        {
         decryptedSite.setUrl(myUrl);
        }
        else
        {
            somethingWrong=true;
            Toast.makeText(this, R.string.make_sure_url_is_correct, Toast.LENGTH_LONG).show();
        }
        //If every input for a server is correct then add it to ContentProvider but encrypt it first. and remember to set everything to after.
        if(!somethingWrong)
        {
            //Encrypt and save.

            ContentResolver cr = getContentResolver();
            ContentValues cv = new ContentValues();
            try {
                decryptedSite.enCrypt(secretKey);
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("prefs","cant encrypt: "+e.getLocalizedMessage());
            }
            cv.put(daSftpProvider.KEY_HOST,decryptedSite.getHost());
            cv.put(daSftpProvider.KEY_PORT,decryptedSite.getPort());
            cv.put(daSftpProvider.KEY_PATH,decryptedSite.getPath());
            cv.put(daSftpProvider.KEY_USERNAME,decryptedSite.getUsername());
            cv.put(daSftpProvider.KEY_PASSWORD,decryptedSite.getPassword());
            cv.put(daSftpProvider.KEY_URL,decryptedSite.getUrl());
            Log.d("prefs","sizeHost:"+decryptedSite.getHost());
            if(swSigned.isChecked())
            {
                cv.put(daSftpProvider.KEY_SIGNED,1);
            }
            else
            {
                cv.put(daSftpProvider.KEY_SIGNED,0);
            }
           Uri muri = cr.insert(daSftpProvider.CONTENT_URI_SITES, cv);
           Log.d("prefs", "uri:" + muri);
           finish();
            Toast.makeText(this, "Saved site", Toast.LENGTH_LONG).show();
        }
        else
        {
            Toast.makeText(this,"Something went wrong. Did not save",Toast.LENGTH_LONG).show();
        }

    }

}

