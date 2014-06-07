package com.github.davols.dasftp;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;


/**
 * Created by davols on 05.06.14.
 */
public class ImageHandler {
    public final static String DOWNLOAD_CACHE = "dl";
    private static final String TAG = "ImageHandler";
    // class variable
    final String lexicon = "ABCDEFGHIJKLMNOPQRSTUVWXYZ12345674890";
    final java.util.Random rand = new java.util.Random();
    private Context mContext;
    private DiskLruImageCache mDiskLruCache;

    public ImageHandler(Context context, DiskLruImageCache cache) {
        mContext = context;
        mDiskLruCache = cache;
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

    public static boolean isExternalStorageRemovable() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD || Environment.isExternalStorageRemovable();
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

    public UploadResult uploadImage(SharedPreferences prefs, String path) {
        UploadResult mResult = new UploadResult();


        Log.d(TAG, "uploadImage filePath:" + path);
        mResult.setFilePath(path);
        String fileName = path.substring(path.lastIndexOf("/") + 1, path.lastIndexOf("."));
        Log.d(TAG, "uploadImage fileName: " + fileName);
        mResult.setName(fileName);
        String extension = path.substring(path.lastIndexOf("."));

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
            sftp.put(path, remotePath + fileName2 + extension);
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
                Log.d(TAG, "LRU IS NULL");
            }
            if (decodeSampledBitmapFromResource(path, 200, 200) == null) {
                Log.d(TAG, "fucking pic is null but FilePath:" + path);
            }
            mDiskLruCache.put(fileName + extension, decodeSampledBitmapFromResource(path, 200, 200));


        }
        return mResult;
    }

    public DownloadResult downloadPicture(Uri imageUri, String fileName) {
        //TODO another download file path, that needs to be created (perhaps with LRUCache?)
        //TODO Google+ just says image.jpg/png. Crashes, but some stuff works.

        Log.d(TAG, "downloadPicture:filePath:" + imageUri.getPath());
        DownloadResult mResult;

        mResult = new DownloadResult();
        String filePath = imageUri.toString();
        Log.d(TAG, "YES filePath scheme:" + filePath);
        if (filePath != null && filePath.startsWith("content:")) {
            filePath = getAbsoluteImagePathFromUri(Uri.parse(filePath));
        }
        if (filePath == null || TextUtils.isEmpty(filePath)) {
            Log.d(TAG, "Cant process a null file");
        } else if (filePath.startsWith("http")) {
            Log.d(TAG, "downloadFile");
            return downloadFile(mResult, filePath, fileName);
        } else if (filePath
                .startsWith("content://com.google.android.gallery3d")
                || filePath
                .startsWith("content://com.microsoft.skydrive.content.external")) {
            Log.d(TAG, "processPicasaMedia");
            try {
                processPicasaMedia(mResult, filePath, fileName);
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "Failed:" + e.getLocalizedMessage());
                mResult.setFailedReason(e.getLocalizedMessage());
                return mResult;
            }
        } else if (filePath
                .startsWith("content://com.google.android.apps.photos.content")
                || filePath
                .startsWith("content://com.android.providers.media.documents")
                || filePath
                .startsWith("content://com.google.android.apps.docs.storage")) {
            Log.d(TAG, "processGooglePhotosMedia");
            try {
                processGooglePhotosMedia(mResult, filePath, fileName);
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "Failed:" + e.getLocalizedMessage());
                mResult.setFailedReason(e.getLocalizedMessage());
                return mResult;
            }
        } else {
            Log.d(TAG, "nothing");

        }

        return mResult;
    }

    protected DownloadResult downloadFile(DownloadResult mResult, String url, String fileName) {

        HttpClient client = new DefaultHttpClient();
        HttpGet getRequest = new HttpGet(url);

        try {
            HttpResponse response = client.execute(getRequest);
            InputStream stream = response.getEntity().getContent();

            File dlCache = getDiskCacheDir(mContext, DOWNLOAD_CACHE);
            if (!dlCache.exists()) {
                dlCache.mkdir();
                Log.d(TAG, "creating download cache");
            } else {
                Log.d(TAG, "directory exists");
            }
            String saveFilePath = getDiskCacheDir(mContext, DOWNLOAD_CACHE) + "/" + fileName;


            FileOutputStream fileOutputStream = new FileOutputStream(saveFilePath);

            byte[] buffer = new byte[1024];
            int len;
            while ((len = stream.read(buffer)) > 0)
                fileOutputStream.write(buffer, 0, len);
            fileOutputStream.flush();
            fileOutputStream.close();
            stream.close();
            mResult.setFilePath(saveFilePath);
            mResult.setName(fileName);
            Log.i(TAG, "Image saved: " + saveFilePath.toString());

        } catch (ClientProtocolException e) {
            e.printStackTrace();
            mResult.setFailedReason(e.getLocalizedMessage());
            return mResult;
        } catch (IOException e) {
            e.printStackTrace();
            mResult.setFailedReason(e.getLocalizedMessage());
            return mResult;
        } catch (Exception e) {
            e.printStackTrace();
            mResult.setFailedReason(e.getLocalizedMessage());
            return mResult;
        }

        return mResult;
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

    private DownloadResult processPicasaMedia(DownloadResult mResult, String path, String fileName) {
        Log.d(TAG, "Download Started");


        try {
            InputStream inputStream = mContext.getContentResolver()
                    .openInputStream(Uri.parse(path));
            File dlCache = getDiskCacheDir(mContext, DOWNLOAD_CACHE);
            if (!dlCache.exists()) {
                dlCache.mkdir();
                Log.d(TAG, "creating download cache");
            } else {
                Log.d(TAG, "directory exists");
            }
            String saveFilePath = getDiskCacheDir(mContext, DOWNLOAD_CACHE) + "/" + fileName;
            BufferedOutputStream outStream = new BufferedOutputStream(
                    new FileOutputStream(saveFilePath));
            byte[] buf = new byte[2048];
            int len;
            while ((len = inputStream.read(buf)) > 0) {
                outStream.write(buf, 0, len);
            }

            inputStream.close();
            outStream.close();
            mResult.setFilePath(saveFilePath);
            mResult.setName(fileName);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            mResult.setFailedReason(e.getLocalizedMessage());
            return mResult;
        } catch (Exception e) {
            e.printStackTrace();
            mResult.setFailedReason(e.getLocalizedMessage());
            return mResult;
        }

        return mResult;
    }

    private DownloadResult processGooglePhotosMedia(DownloadResult mResult, String path, String fileName) {

        Log.i(TAG, "Google photos Started");
        Log.i(TAG, "URI: " + path);

        try {

            File dlCache = getDiskCacheDir(mContext, DOWNLOAD_CACHE);
            if (!dlCache.exists()) {
                dlCache.mkdir();
                Log.d(TAG, "creating download cache");
            } else {
                Log.d(TAG, "directory exists");
            }
            String saveFilePath = getDiskCacheDir(mContext, DOWNLOAD_CACHE) + "/" + fileName;

            ParcelFileDescriptor parcelFileDescriptor = mContext
                    .getContentResolver().openFileDescriptor(Uri.parse(path),
                            "r");

            FileDescriptor fileDescriptor = parcelFileDescriptor
                    .getFileDescriptor();

            InputStream inputStream = new FileInputStream(fileDescriptor);

            BufferedInputStream reader = new BufferedInputStream(inputStream);

            BufferedOutputStream outStream = new BufferedOutputStream(
                    new FileOutputStream(saveFilePath));
            byte[] buf = new byte[2048];
            int len;
            while ((len = reader.read(buf)) > 0) {
                outStream.write(buf, 0, len);
            }
            outStream.flush();
            outStream.close();
            inputStream.close();
            mResult.setFilePath(saveFilePath);
            mResult.setName(fileName);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            mResult.setFailedReason(e.getLocalizedMessage());
            return mResult;
        } catch (Exception e) {
            e.printStackTrace();
            mResult.setFailedReason(e.getLocalizedMessage());
            return mResult;
        }

        return mResult;

    }

    private String getAbsoluteImagePathFromUri(Uri imageUri) {
        String[] proj = {MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME};

        if (imageUri.toString().startsWith(
                "content://com.android.gallery3d.provider")) {
            imageUri = Uri.parse(imageUri.toString().replace(
                    "com.android.gallery3d", "com.google.android.gallery3d"));
        }

        String filePath;
        String imageUriString = imageUri.toString();
        if (imageUriString.startsWith("content://com.google.android.gallery3d")
                || imageUriString
                .startsWith("content://com.google.android.apps.photos.content")
                || imageUriString
                .startsWith("content://com.android.providers.media.documents")
                || imageUriString
                .startsWith("content://com.google.android.apps.docs.storage")
                || imageUriString
                .startsWith("content://com.microsoft.skydrive.content.external")) {
            filePath = imageUri.toString();
        } else {
            Cursor cursor = mContext.getContentResolver().query(imageUri, proj,
                    null, null, null);
            cursor.moveToFirst();
            filePath = cursor.getString(cursor
                    .getColumnIndexOrThrow(MediaStore.MediaColumns.DATA));
            cursor.close();
        }

        return filePath;
    }


    public String checkExtension(Uri uri) {

        String extension = "";

        // The query, since it only applies to a single document, will only
        // return
        // one row. There's no need to filter, sort, or select fields, since we
        // want
        // all fields for one document.
        Cursor cursor = mContext.getContentResolver().query(uri, null, null,
                null, null);

        try {
            // moveToFirst() returns false if the cursor has 0 rows. Very handy
            // for
            // "if there's anything to look at, look at it" conditionals.
            if (cursor != null && cursor.moveToFirst()) {

                // Note it's called "Display Name". This is
                // provider-specific, and might not necessarily be the file
                // name.
                String displayName = cursor.getString(cursor
                        .getColumnIndex(OpenableColumns.DISPLAY_NAME));
                int position = displayName.indexOf(".");
                extension = displayName.substring(position + 1);
                Log.i(TAG, "Display Name: " + displayName);

                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                // If the size is unknown, the value stored is null. But since
                // an
                // int can't be null in Java, the behavior is
                // implementation-specific,
                // which is just a fancy term for "unpredictable". So as
                // a rule, check if it's null before assigning to an int. This
                // will
                // happen often: The storage API allows for remote files, whose
                // size might not be locally known.
                String size;
                if (!cursor.isNull(sizeIndex)) {
                    // Technically the column stores an int, but
                    // cursor.getString()
                    // will do the conversion automatically.
                    size = cursor.getString(sizeIndex);
                } else {
                    size = "Unknown";
                }
                Log.i(TAG, "Size: " + size);
            }
        } finally {
            cursor.close();
        }
        return extension;
    }


}
