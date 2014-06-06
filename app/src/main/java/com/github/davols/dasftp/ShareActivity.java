package com.github.davols.dasftp;

import android.app.Activity;
import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.util.Calendar;


public class ShareActivity extends Activity {


    private static final int DISK_CACHE_SIZE = 1024 * 1024 * 10; // 10MB
    private static final String DISK_CACHE_SUBDIR = "thumbnails";
    private static NotificationManager mNotifyManager;
    private static Builder mBuilder;
    private static ClipboardManager clipboard;
    private static SharedPreferences prefs;

    private DiskLruImageCache mDiskLruCache;

    private ImageHandler mHandler;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Get intent, action and MIME type
        final Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        mDiskLruCache = new DiskLruImageCache(this, DISK_CACHE_SUBDIR, DISK_CACHE_SIZE, Bitmap.CompressFormat.PNG, 100);
        mHandler = new ImageHandler(this, mDiskLruCache);

        if (hasPreferences()) {
            // Gets a handle to the clipboard service.
            clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            mNotifyManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mBuilder = new Notification.Builder(this);
            mBuilder.setContentTitle("Picture Upload")
                    .setContentText("Upload in progress")
                    .setSmallIcon(R.drawable.ic_stat_av_upload);
            mBuilder.setProgress(0, 0, false);
            if (Intent.ACTION_SEND.equals(action) && type != null) {
                if (type.startsWith("image/")) {
                    Toast.makeText(this, "Starting upload", Toast.LENGTH_LONG).show();
                    handleSendImage(intent); // Handle single image being sent

                } else
                    Toast.makeText(this, "No Image", Toast.LENGTH_LONG).show();
            } else {
                // Handle other intents, such as being started from the home screen
                Toast.makeText(this, "Not the correct intent", Toast.LENGTH_LONG).show();
            }

        } else {
            Toast.makeText(this, "Need to set up server in preferences", Toast.LENGTH_LONG).show();
        }

        finish();
    }

    private boolean hasPreferences() {
        return (prefs.getString("pref_host", null) != null && prefs.getString("pref_port", null) != null && prefs.getString("pref_path", null) != null && prefs.getString("pref_url", null) != null && prefs.getString("pref_user", null) != null && prefs.getString("pref_passwd", null) != null);
    }

    void handleSendImage(Intent intent) {
        String[] filePathColumn = {MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.TITLE};
        Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (imageUri != null) {
            // Update UI to reflect image being shared
            String filePath;
            String scheme = imageUri.getScheme();
            Log.d("Main", "scheme:" + scheme);
            Log.d("Main", "ImageUri:" + imageUri.toString());
            if (scheme.equals("content")) {
                Cursor cursor = getContentResolver().query(imageUri, filePathColumn, null, null, null);
                if (cursor != null) {
                    cursor.moveToFirst(); // <--no more NPE

                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);

                    filePath = cursor.getString(columnIndex);
                    String perhapsFileName = cursor.getString(cursor.getColumnIndex(filePathColumn[1]));
                    String mimeType = cursor.getString(cursor.getColumnIndex(filePathColumn[2]));
                    String mTitle = cursor.getString(cursor.getColumnIndex(filePathColumn[3]));
                    Log.d("Main", "PerhapsFileName:" + perhapsFileName);
                    Log.d("Main", "perhaps mimeType:" + mimeType);
                    Log.d("Main", "the title??:" + mTitle);
                    cursor.close();
                    //If filepath is null assume that it's from online source. So we need to download it first.
                    if (filePath == null) {


                        new DownloadPictureTask(this, imageUri).execute(imageUri.getPath(), perhapsFileName);
                    } else {
                        Log.d("Main", "not null");
                        new UploadTask().execute(filePath);
                    }

                }

            }
        }
    }

    private void addToContentProvider(UploadResult result) {
        ContentResolver cr = getContentResolver();
        ContentValues cv = new ContentValues();
        cv.put(PictureUploadProvider.KEY_DATE_UPLOADED, Calendar.getInstance().getTimeInMillis());
        cv.put(PictureUploadProvider.KEY_NAME, result.getName());
        cv.put(PictureUploadProvider.KEY_URL, prefs.getString("pref_url", null) + result.getmUrl());
        cv.put(PictureUploadProvider.KEY_UPL_NAME, result.getUploadName());
        cv.put(PictureUploadProvider.KEY_FILEPATH, result.getFilePath());
        cr.insert(PictureUploadProvider.CONTENT_URI, cv);
    }

    private class DownloadPictureTask extends AsyncTask<String, Integer, UploadResult> {
        private Context mContext;
        private Uri mUri;

        public DownloadPictureTask(Context context, Uri imgUri) {
            mContext = context;
            mUri = imgUri;
        }

        @Override
        protected UploadResult doInBackground(String... input) {
            String filePath = input[0];
            String fileName = input[1];
            boolean failed = mHandler.downloadPicture(mUri, fileName);

            if (!failed) {
                Log.d("Main", "Cool yo");

                String newFilePath = ImageHandler.getDiskCacheDir(mContext, ImageHandler.DOWNLOAD_CACHE) + "/" + fileName;
                Log.d("Main", "not failed and filePath:" + newFilePath);
                return mHandler.uploadImage(prefs, newFilePath);

            }

            return null;

        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mNotifyManager.notify(1, mBuilder.build());
        }

        @Override
        protected void onPostExecute(UploadResult result) {
            super.onPostExecute(result);

            if (result != null) {
                if (result.hasFailed()) {
                    Toast.makeText(ShareActivity.this, R.string.upload_failed, Toast.LENGTH_LONG).show();
                    SharedPreferences.Editor edit = prefs.edit();
                    edit.putBoolean("recent_failed", true);
                    edit.putString("failed_reason", result.getFailedReason());
                    edit.commit();
                    //Update notification
                    mBuilder.setSmallIcon(R.drawable.ic_stat_alerts_and_states_error);
                    mBuilder.setContentText("Upload failed")
                            // Removes the progress bar
                            .setProgress(0, 0, false);
                    mNotifyManager.notify(1, mBuilder.build());
                } else {
                    addToContentProvider(result);

                    Toast.makeText(ShareActivity.this, R.string.upload_complete, Toast.LENGTH_LONG).show();
                    //Update notification
                    mBuilder.setSmallIcon(R.drawable.ic_stat_av_upload);
                    mBuilder.setContentText("Upload complete")
                            // Removes the progress bar
                            .setProgress(0, 0, false);
                    mNotifyManager.notify(1, mBuilder.build());
                    //Recent didnt fail.
                    SharedPreferences.Editor edit = prefs.edit();
                    edit.putBoolean("recent_failed", false);
                    edit.putString("failed_reason", null);
                    edit.commit();
                    if (prefs.getBoolean("pref_tag", false)) {
                        String url = prefs.getString("pref_url", null) + result.getmUrl();
                        String shareUrl = prefs.getString("pref_tag_txt", "[img]%url[/img]").replace("%url", url);
                        ClipData clip = ClipData.newPlainText("simple text", shareUrl);
                        clipboard.setPrimaryClip(clip);
                    } else {
                        ClipData clip = ClipData.newPlainText("simple text", prefs.getString("pref_url", null) + result.getmUrl());
                        clipboard.setPrimaryClip(clip);

                    }

                }

            }


        }

    }

    private class UploadTask extends AsyncTask<String, Integer, UploadResult> {

        public UploadTask() {
        }

        @Override
        protected UploadResult doInBackground(String... paths) {
            return mHandler.uploadImage(prefs, paths[0]);

        }

        @Override
        protected void onPostExecute(UploadResult result) {
            super.onPostExecute(result);

            if (result != null) {
                if (result.hasFailed()) {
                    Toast.makeText(ShareActivity.this, R.string.upload_failed, Toast.LENGTH_LONG).show();
                    SharedPreferences.Editor edit = prefs.edit();
                    edit.putBoolean("recent_failed", true);
                    edit.putString("failed_reason", result.getFailedReason());
                    edit.commit();
                    //Update notification
                    mBuilder.setSmallIcon(R.drawable.ic_stat_alerts_and_states_error);
                    mBuilder.setContentText("Upload failed")
                            // Removes the progress bar
                            .setProgress(0, 0, false);
                    mNotifyManager.notify(1, mBuilder.build());
                } else {

                    addToContentProvider(result);
                    Toast.makeText(ShareActivity.this, R.string.upload_complete, Toast.LENGTH_LONG).show();
                    //Update notification
                    mBuilder.setSmallIcon(R.drawable.ic_stat_av_upload);
                    mBuilder.setContentText("Upload complete")
                            // Removes the progress bar
                            .setProgress(0, 0, false);
                    mNotifyManager.notify(1, mBuilder.build());
                    //Recent didnt fail.
                    SharedPreferences.Editor edit = prefs.edit();
                    edit.putBoolean("recent_failed", false);
                    edit.putString("failed_reason", null);
                    edit.commit();
                    if (prefs.getBoolean("pref_tag", false)) {
                        String url = prefs.getString("pref_url", null) + result.getmUrl();
                        String shareUrl = prefs.getString("pref_tag_txt", "[img]%url[/img]").replace("%url", url);
                        ClipData clip = ClipData.newPlainText("simple text", shareUrl);
                        clipboard.setPrimaryClip(clip);
                    } else {
                        ClipData clip = ClipData.newPlainText("simple text", prefs.getString("pref_url", null) + result.getmUrl());
                        clipboard.setPrimaryClip(clip);

                    }

                }


            }
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mNotifyManager.notify(1, mBuilder.build());
        }


    }

}
