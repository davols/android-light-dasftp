package com.github.davols.dasftp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;


/**
 * Created by morn on 05.06.14.
 */
public class ImageHandler {
    public final static String DOWNLOAD_CACHE = "dl";
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

    public UploadResult uploadImage(SharedPreferences prefs, String path) {
        UploadResult mResult = new UploadResult();

        String filePath = path;
        Log.d("Main", "uploadImage filePath:" + filePath);
        mResult.setFilePath(filePath);
        String fileName = filePath.substring(filePath.lastIndexOf("/") + 1, filePath.lastIndexOf("."));
        Log.d("Main", "uploadImage fileName: " + fileName);
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

            if (mDiskLruCache == null) {
                Log.d("Main", "LRU IS NULL");
            }
            if (filePath == null) {
                Log.d("Main", "AWDG PATH NULL");
            }
            if (decodeSampledBitmapFromResource(filePath, 200, 200) == null) {
                Log.d("Main", "fucking pic is null but FilePath:" + filePath);
            }
            mDiskLruCache.put(fileName + extension, decodeSampledBitmapFromResource(filePath, 200, 200));


        }
        return mResult;
    }

    public boolean downloadPicture(Uri imageUri, String fileName) {
        //TODO another download file path, that needs to be created (perhaps with LRUCache?)
        //TODO Google+ just says image.jpg/png. Crashes, but some stuff works.

        // Log.d("Main", "downloadPicture:filePath:"+filePath);

        InputStream inputStream = null;
        try {
            inputStream = mContext.getContentResolver().openInputStream(imageUri);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.d("Main", "file not found");
        }

        BufferedInputStream reader = new BufferedInputStream(inputStream);
        File dlCache = getDiskCacheDir(mContext, DOWNLOAD_CACHE);
        if (!dlCache.exists()) {
            dlCache.mkdir();
            Log.d("Main", "creating download cache");
        } else {
            Log.d("Main", "directory exists");
        }
        String saveFilePath = getDiskCacheDir(mContext, DOWNLOAD_CACHE) + "/" + fileName;
        File savedFile = new File(saveFilePath);
        if (savedFile.exists()) {
            Log.d("Main", "pic exists");
            savedFile.delete();
        }
        Log.d("Main", "Saving to:" + saveFilePath);
        // Create an output stream to a file that you want to save to
        BufferedOutputStream outStream = null;
        try {
            outStream = new BufferedOutputStream(
                    new FileOutputStream(saveFilePath));
        } catch (FileNotFoundException e) {
            e.printStackTrace();

            Log.d("Main", "cfailed 2" + e.getLocalizedMessage());
            return true;
        }


        byte[] buffer = new byte[1024];
        int len;
        try {
            while ((len = reader.read(buffer)) != -1) {
                outStream.write(buffer, 0, len);

            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.d("Main", "cfailed 1" + e.getLocalizedMessage());
            return true;
        }


        return false;
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

}
