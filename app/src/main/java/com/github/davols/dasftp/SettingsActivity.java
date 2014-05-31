package com.github.davols.dasftp;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

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

    public static class SettingsFragment extends PreferenceFragment {
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
            //TODO trim() usernames, paths and hosts.
        }

    }
}
