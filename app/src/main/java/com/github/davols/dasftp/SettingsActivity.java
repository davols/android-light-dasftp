package com.github.davols.dasftp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;

public class SettingsActivity extends Activity {

    private static final int DISK_CACHE_SIZE = 1024 * 1024 * 10; // 10MB
    private static final String DISK_CACHE_SUBDIR = "thumbnails";
    private static final int REQUEST_ADD_SITE = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();

    }


    public void doPositiveClick() {
        Toast.makeText(this, R.string.nothing_saved, Toast.LENGTH_LONG).show();
    }

    public void doNegativeClick() {
        Toast.makeText(this, R.string.nothing_saved, Toast.LENGTH_LONG).show();
    }

    public static class SettingsFragment extends PreferenceFragment {
        private String secretKey;

        SavedSite decryptedSite=new SavedSite();
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.mypreferences);
            getSecretKey();

            Preference myPref = findPreference("clear_hist");
            myPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (preference.getKey().equalsIgnoreCase("clear_hist")) {
                        if (getActivity() != null) {
                            getActivity().getContentResolver().delete(daSftpProvider.CONTENT_URI_UPLOADS, null, null);
                            DiskLruImageCache mDiskLruCache = new DiskLruImageCache(getActivity(),
                                    DISK_CACHE_SUBDIR, DISK_CACHE_SIZE,
                                    Bitmap.CompressFormat.PNG, 100);
                            mDiskLruCache.clearCache();
                        }

                        return true;
                    } else {
                        return false;
                    }
                }
            });

            Preference sites = (Preference) findPreference("add_site");
            sites.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivityForResult(new Intent(getActivity(),AddSiteActivity.class),REQUEST_ADD_SITE);
                    return true;
                }
            });
            ListPreference myList = (ListPreference) findPreference("saved_sites_key");
            if(myList != null || myList.getEntries().length==0)
            {

                myList.setEnabled(false);
                myList.setSummary("No saved sites. Please add one");
            }
            //Populate list.
            populateServerList((ListPreference) findPreference("saved_sites_key"));
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            Log.d("prefs","requestCode:"+requestCode+" result code:"+resultCode);
            if(requestCode==REQUEST_ADD_SITE && resultCode==Activity.RESULT_OK)
            {
                populateServerList((ListPreference) findPreference("saved_sites_key"));
            }
        }
        private void getSecretKey()
        {
            SharedPreferences sp = getActivity().getSharedPreferences("DASFTP", MODE_PRIVATE);
            if (sp.getString("ENCODE",null) != null) {
                secretKey = sp.getString("ENCODE", null);
            }
            else
            {
                secretKey = null;
            }


        }
        //Only populate with host.
        private void populateServerList(ListPreference myList)
        {
            ArrayList<SavedSite> mList = new ArrayList<SavedSite>();
            SavedSite mSite;
            Cursor c = getActivity().getContentResolver().query(daSftpProvider.CONTENT_URI_SITES, new String[]{daSftpProvider.KEY_ID,daSftpProvider.KEY_URL, daSftpProvider.KEY_PATH, daSftpProvider.KEY_USERNAME, daSftpProvider.KEY_PASSWORD, daSftpProvider.KEY_PORT, daSftpProvider.KEY_SIGNED, daSftpProvider.KEY_HOST}, null, null, daSftpProvider.KEY_ID + " DESC");
            if(c.moveToFirst())
            {
                mSite = new SavedSite();
                do{
                    mSite.setHost(c.getString(c.getColumnIndex(daSftpProvider.KEY_HOST)));
                    mSite.setUrl(c.getString(c.getColumnIndex(daSftpProvider.KEY_URL)));
                    mSite.setPath(c.getString(c.getColumnIndex(daSftpProvider.KEY_PATH)));
                    mSite.setUsername(c.getString(c.getColumnIndex(daSftpProvider.KEY_USERNAME)));
                    mSite.setPassword(c.getString(c.getColumnIndex(daSftpProvider.KEY_PASSWORD)));
                    mSite.setPort(c.getInt(c.getColumnIndex(daSftpProvider.KEY_PORT)));
                    mSite.setId(c.getInt(c.getColumnIndex(daSftpProvider.KEY_ID)));
                    mList.add(mSite);
                }while(c.moveToNext());
            }

            //Loop through and get the obfuscuted names.
            CharSequence[] namedList = new CharSequence[mList.size()];
            CharSequence[] ids = new CharSequence[mList.size()];
            for(int i=0;i<mList.size();i++)
            {
                SavedSite p = mList.get(i);
                try {

                    namedList[i]= SimpleCrypto.decrypt(p.getUrl(), secretKey);
                    ids[i]=""+p.getId();
                    if(namedList[i]==null)
                    {
                        Log.d("prefs","fucking null");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.d("prefs","fucking error. " + e.getLocalizedMessage());
                }
            }

            if(mList.size() > 0) {
                Log.d("prefs","enabling mylist");
                myList.setEntryValues(ids);
                myList.setEntries(namedList);
                myList.setEnabled(true);
                myList.setSummary("Chose default uploading site");
            }

        }
        private boolean hasAllSettings(SharedPreferences sp){
            return sp.getInt("pref_port",-1)!=-1 && sp.getString("pref_path", null)!=null && sp.getString("pref_url",null)!=null && sp.getString("pref_host",null)!=null && sp.getString("pref_user",null)!=null && sp.getString("pref_passwd",null)!=null;
        }
    }


}
