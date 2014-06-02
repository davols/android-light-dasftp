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
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;


public class ShareActivity extends Activity {


    private static final int DISK_CACHE_SIZE = 1024 * 1024 * 10; // 10MB
    private static final String DISK_CACHE_SUBDIR = "thumbnails";
    private static NotificationManager mNotifyManager;
    private static Builder mBuilder;
    private static ClipboardManager clipboard;
    private static SharedPreferences prefs;
    // class variable
    final String lexicon = "ABCDEFGHIJKLMNOPQRSTUVWXYZ12345674890";
    final java.util.Random rand = new java.util.Random();
    private DiskLruImageCache mDiskLruCache;

    /**
     * get a small bitmap of the file.
     * Taken from http://developer.android.com/training/displaying-bitmaps/load-bitmap.html
     *
     * @param filePath  path to image file
     * @param reqWidth  required width when scaled
     * @param reqHeight required height when scaled
     * @return scaled bitmap
     */
    private static Bitmap decodeSampledBitmapFromResource(String filePath,
                                                          int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);
        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(filePath, options);
    }

    /**
     * Taken from http://developer.android.com/training/displaying-bitmaps/load-bitmap.html
     *
     * @param options   Bitmap options
     * @param reqWidth  required width when scaled
     * @param reqHeight required height when scaled
     * @return scaleSampleSize
     */
    private static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    // Creates a unique subdirectory of the designated app cache directory. Tries to use external
// but if not mounted, falls back on internal storage.
    public static File getDiskCacheDir(Context context, String uniqueName) {
        // Check if media is mounted or storage is built-in, if so, try and use external cache dir
        // otherwise use internal cache dir
        final String cachePath =
                Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) ||
                        !isExternalStorageRemovable() ? getExternalCacheDir(context).getPath() :
                        context.getCacheDir().getPath();

        return new File(cachePath + File.separator + uniqueName);
    }

    public static boolean isExternalStorageRemovable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            return Environment.isExternalStorageRemovable();
        }
        return true;
    }

    public static File getExternalCacheDir(Context context) {
        if (hasExternalCacheDir()) {
            return context.getExternalCacheDir();
        }

        // Before Froyo we need to construct the external cache dir ourselves
        final String cacheDir = "/Android/data/" + context.getPackageName() + "/cache/";
        return new File(Environment.getExternalStorageDirectory().getPath() + cacheDir);
    }

    public static boolean hasExternalCacheDir() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Get intent, action and MIME type
        final Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        mDiskLruCache = new DiskLruImageCache(this, DISK_CACHE_SUBDIR, DISK_CACHE_SIZE, Bitmap.CompressFormat.PNG, 100);

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
                    if (filePath == null) {
                        Log.d("Main", "null filepath wtf");
                        //If filepath is null assume that it's from online source. So we need to download it first.
                        new DownloadPictureTask(this).execute(imageUri.toString(), perhapsFileName);
                    } else {
                        Log.d("Main", "not null");
                        new UploadTask().execute(filePath);
                    }

                }

            }
        }
    }

    private String createUniqueFileName(ChannelSftp sftp, String remotePath, String name, String extension) {
        String mName = name;
        int length = 1;
        while (true) {
            try {
                sftp.lstat(remotePath + mName + extension);
            } catch (SftpException e) {

                break;
            }

            mName = mName + randomIdentifier(length);
            length++;
        }
        return mName;
    }

    private String randomIdentifier(int maxLength) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < maxLength; i++)
            builder.append(lexicon.charAt(rand.nextInt(lexicon.length())));
        return builder.toString();
    }

    private class DownloadPictureTask extends AsyncTask<String, Integer, UploadResult> {
        private Context mContext;

        public DownloadPictureTask(Context context) {
            mContext = context;
        }

        @Override
        protected UploadResult doInBackground(String... input) {
            //TODO another download file path, that needs to be created (perhaps with LRUCache?)
            //TODO Google+ just says image.jpg/png. Crashes, but some stuff works.

            Log.d("Main", "cool background");
            ParcelFileDescriptor parcelFileDescriptor = null;
            try {
                parcelFileDescriptor = mContext.getContentResolver()
                        .openFileDescriptor(Uri.parse(input[0]), "r");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Log.d("Main", "cfailed 3" + e.getLocalizedMessage());
            }

            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();

            InputStream inputStream = new FileInputStream(fileDescriptor);

            BufferedInputStream reader = new BufferedInputStream(inputStream);
            boolean failed = false;
            // Create an output stream to a file that you want to save to
            BufferedOutputStream outStream = null;
            try {
                outStream = new BufferedOutputStream(
                        new FileOutputStream(getDiskCacheDir(mContext, "dl") + "/" + input[1]));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                failed = true;
                Log.d("Main", "cfailed 2" + e.getLocalizedMessage());
            }
            byte[] buf = new byte[2048];
            int len;
            try {
                while ((len = reader.read(buf)) > 0) outStream.write(buf, 0, len);
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("Main", "cfailed 1" + e.getLocalizedMessage());
                failed = true;
            }
            if (!failed) {
                Log.d("Main", "Cool yo");
                UploadResult mResult = new UploadResult();
                String filePath = getDiskCacheDir(mContext, "dl") + "/" + input[1];
                Log.d("Main", "not fialed and filePath:" + filePath);
                mResult.setFilePath(filePath);
                Log.d("Main", "WTF:" + input[1]);


                String fileName = input[1].substring(0, input[1].indexOf("."));
                Log.d("Main", "name::" + fileName);
                mResult.setName(fileName);
                String extension = input[1].substring(input[1].indexOf("."), input[1].length());
                Log.d("Main", "Ext:" + extension);
                Session session = null;
                Channel channel = null;
                try {
                    JSch ssh = new JSch();
                    java.util.Properties config = new java.util.Properties();
                    if (prefs.getBoolean("pref_cert", true)) {

                        config.put("StrictHostKeyChecking", "yes");
                        JSch.setConfig("StrictHostKeyChecking", "yes");
                    } else {

                        config.put("StrictHostKeyChecking", "no");
                        JSch.setConfig("StrictHostKeyChecking", "no");
                    }

                    session = ssh.getSession(prefs.getString("pref_user", null), prefs.getString("pref_host", null), Integer.parseInt(prefs.getString("pref_port", "0")));
                    session.setPassword(prefs.getString("pref_passwd", null));
                    session.setConfig(config);
                    session.connect();


                    channel = session.openChannel("sftp");
                    channel.connect();
                    ChannelSftp sftp = (ChannelSftp) channel;

                    String remotePath;
                    if (!prefs.getString("pref_path", null).endsWith("/")) {
                        remotePath = prefs.getString("pref_path", null) + "/";
                    } else {
                        remotePath = prefs.getString("pref_path", null);
                    }
                    String fileName2 = createUniqueFileName(sftp, remotePath, fileName, extension);
                    sftp.put(filePath, remotePath + fileName2 + extension);
                    mResult.setUploadName(fileName2);
                    mResult.setmUrl(fileName2 + extension);

                } catch (JSchException e) {
                    e.printStackTrace();
                    mResult.setFailedReason(e.getLocalizedMessage());

                } catch (SftpException e) {
                    e.printStackTrace();
                    mResult.setFailedReason(e.getLocalizedMessage());

                } finally {
                    if (channel != null) {
                        channel.disconnect();
                    }
                    if (session != null) {
                        session.disconnect();
                    }
                }
                //Only add to cache if it didnt fail.
                if (!mResult.hasFailed()) {
                    if (mDiskLruCache == null) {
                        Log.d("Main", "LRU IS NULL");
                    }
                    if (filePath == null) {
                        Log.d("Main", "AWDG PATH NULL");
                    }
                    if (decodeSampledBitmapFromResource(filePath, 200, 200) == null) {
                        Log.d("Main", "fucking pic is null");
                    }
                    mDiskLruCache.put(fileName + extension, decodeSampledBitmapFromResource(filePath, 200, 200));
                }


                return mResult;

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
                    ContentResolver cr = getContentResolver();
                    ContentValues cv = new ContentValues();
                    cv.put(PictureUploadProvider.KEY_DATE_UPLOADED, Calendar.getInstance().getTimeInMillis());
                    cv.put(PictureUploadProvider.KEY_NAME, result.getName());
                    cv.put(PictureUploadProvider.KEY_URL, prefs.getString("pref_url", null) + result.getmUrl());
                    cv.put(PictureUploadProvider.KEY_UPL_NAME, result.getUploadName());
                    cv.put(PictureUploadProvider.KEY_FILEPATH, result.getFilePath());
                    cr.insert(PictureUploadProvider.CONTENT_URI, cv);

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
            UploadResult mResult = new UploadResult();

            String filePath = paths[0];
            Log.d("Main", "filePath:" + filePath);
            mResult.setFilePath(filePath);
            String fileName = filePath.substring(filePath.lastIndexOf("/") + 1, filePath.indexOf("."));
            mResult.setName(fileName);
            String extension = filePath.substring(filePath.lastIndexOf("."));

            Session session = null;
            Channel channel = null;
            try {
                JSch ssh = new JSch();
                java.util.Properties config = new java.util.Properties();
                if (prefs.getBoolean("pref_cert", true)) {

                    config.put("StrictHostKeyChecking", "yes");
                    JSch.setConfig("StrictHostKeyChecking", "yes");
                } else {

                    config.put("StrictHostKeyChecking", "no");
                    JSch.setConfig("StrictHostKeyChecking", "no");
                }

                session = ssh.getSession(prefs.getString("pref_user", null), prefs.getString("pref_host", null), Integer.parseInt(prefs.getString("pref_port", "0")));
                session.setPassword(prefs.getString("pref_passwd", null));
                session.setConfig(config);
                session.connect();


                channel = session.openChannel("sftp");
                channel.connect();
                ChannelSftp sftp = (ChannelSftp) channel;

                String remotePath;
                if (!prefs.getString("pref_path", null).endsWith("/")) {
                    remotePath = prefs.getString("pref_path", null) + "/";
                } else {
                    remotePath = prefs.getString("pref_path", null);
                }
                String fileName2 = createUniqueFileName(sftp, remotePath, fileName, extension);
                sftp.put(filePath, remotePath + fileName2 + extension);
                mResult.setUploadName(fileName2);
                mResult.setmUrl(fileName2 + extension);

            } catch (JSchException e) {
                e.printStackTrace();
                mResult.setFailedReason(e.getLocalizedMessage());

            } catch (SftpException e) {
                e.printStackTrace();
                mResult.setFailedReason(e.getLocalizedMessage());

            } finally {
                if (channel != null) {
                    channel.disconnect();
                }
                if (session != null) {
                    session.disconnect();
                }
            }
            //Only add to cache if it didnt fail.
            if (!mResult.hasFailed()) {
                mDiskLruCache.put(fileName + extension, decodeSampledBitmapFromResource(filePath, 200, 200));
            }


            return mResult;

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
                    ContentResolver cr = getContentResolver();
                    ContentValues cv = new ContentValues();
                    cv.put(PictureUploadProvider.KEY_DATE_UPLOADED, Calendar.getInstance().getTimeInMillis());
                    cv.put(PictureUploadProvider.KEY_NAME, result.getName());
                    cv.put(PictureUploadProvider.KEY_URL, prefs.getString("pref_url", null) + result.getmUrl());
                    cv.put(PictureUploadProvider.KEY_UPL_NAME, result.getUploadName());
                    cv.put(PictureUploadProvider.KEY_FILEPATH, result.getFilePath());
                    cr.insert(PictureUploadProvider.CONTENT_URI, cv);

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
