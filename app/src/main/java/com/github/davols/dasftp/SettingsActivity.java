package com.github.davols.dasftp;

import android.app.Activity;
import android.app.DialogFragment;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SettingsActivity extends Activity {
    private static final int DISK_CACHE_SIZE = 1024 * 1024 * 10; // 10MB
    private static final String DISK_CACHE_SUBDIR = "thumbnails";

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

    public static class SettingsFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.mypreferences);

            Preference myPref = findPreference("clear_hist");
            myPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (preference.getKey().equalsIgnoreCase("clear_hist")) {
                        if (getActivity() != null) {
                            getActivity().getContentResolver().delete(PictureUploadProvider.CONTENT_URI, null, null);
                            DiskLruImageCache mDiskLruCache = new DiskLruImageCache(getActivity(), DISK_CACHE_SUBDIR, DISK_CACHE_SIZE, Bitmap.CompressFormat.PNG, 100);
                            mDiskLruCache.clearCache();
                        }

                        return true;
                    } else {
                        return false;
                    }
                }
            });
            myPref = findPreference("pref_host");
            myPref.setOnPreferenceChangeListener(this);
            myPref = findPreference("pref_port");
            myPref.setOnPreferenceChangeListener(this);
            myPref = findPreference("pref_user");
            myPref.setOnPreferenceChangeListener(this);
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            //Make sure the host is written correctly (no https/http if you put ip adress. ONLY IPV4 right now.
            if (preference.getKey().equalsIgnoreCase("pref_host")) {
                String myHost = preference.getSharedPreferences().getString("pref_host", null);
                if (myHost != null) {
                    Pattern p = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
                    Matcher m = p.matcher(newValue.toString());

                    if (m.find() && (newValue.toString().contains("http"))) {
                        DialogFragment newFragment = MyAlertDialogFragment.newInstance(
                                R.string.ip4_and_http_title, R.string.ip4_and_http);
                        newFragment.show(getFragmentManager(), "dialog");
                        return false;
                    }

                }


            } else if (preference.getKey().equalsIgnoreCase("pref_user")) {
                Pattern p = Pattern.compile("^\\s*(.*?)\\s*$");
                Matcher m = p.matcher(newValue.toString());
                if (m.matches()) {
                    Toast.makeText(getActivity(), R.string.make_sure_username_is_correct, Toast.LENGTH_LONG).show();
                }

            } else if (preference.getKey().equalsIgnoreCase("pref_port")) {
                //Make sure the port only contains digits.
                Pattern pattern = Pattern.compile("^[0-9]+$");
                Matcher m = pattern.matcher(newValue.toString());
                if (!m.matches()) {
                    DialogFragment newFragment = MyAlertDialogFragment.newInstance(
                            R.string.number_port_title, R.string.number_port);
                    newFragment.show(getFragmentManager(), "dialog");
                    return false;
                }
            }
            return true;
        }

    }


}
