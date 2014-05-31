package com.github.davols.dasftp;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Created by morn on 31.05.14.
 */
public class UploadCursorAdapter extends CursorAdapter {
    private static final int DISK_CACHE_SIZE = 1024 * 1024 * 10; // 10MB
    private static final String DISK_CACHE_SUBDIR = "thumbnails";
    private Typeface robotoReg;
    private Typeface robotoLight;
    private DiskLruImageCache mDiskLruCache;

    public UploadCursorAdapter(Context context, Cursor c, boolean autoRequery) {
        super(context, c, autoRequery);
        robotoReg = Typeface.createFromAsset(context.getAssets(), "fonts/robotoregular.ttf");
        robotoLight = Typeface.createFromAsset(context.getAssets(), "fonts/robotolight.ttf");
        mDiskLruCache = new DiskLruImageCache(context, DISK_CACHE_SUBDIR, DISK_CACHE_SIZE, Bitmap.CompressFormat.PNG, 100);

    }

    /**
     * get a small bitmap of the file.
     * Taken from http://developer.android.com/training/displaying-bitmaps/load-bitmap.html
     *
     * @param filePath
     * @param reqWidth
     * @param reqHeight
     * @return
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
     * @param options
     * @param reqWidth
     * @param reqHeight
     * @return
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

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        return LayoutInflater.from(context).inflate(R.layout.row_history, viewGroup, false);

    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        TextView title = (TextView) view.findViewById(R.id.row_title);
        title.setTypeface(robotoReg);
        TextView extra = (TextView) view.findViewById(R.id.row_extra);
        extra.setTypeface(robotoLight);
        ImageView thumbnail = (ImageView) view.findViewById(R.id.row_pic);

        title.setText(cursor.getString(cursor.getColumnIndex(PictureUploadProvider.KEY_UPL_NAME)));

        extra.setText(cursor.getString(cursor.getColumnIndex(PictureUploadProvider.KEY_URL)));
        Log.d("Main", "url:" + extra.getText().toString());
        new FetchImageTask(thumbnail).execute(cursor.getString(cursor.getColumnIndex(PictureUploadProvider.KEY_NAME)), cursor.getString(cursor.getColumnIndex(PictureUploadProvider.KEY_FILEPATH)));
    }

    private class FetchImageTask extends AsyncTask<String, Integer, Bitmap> {
        private ImageView mView;

        public FetchImageTask(ImageView im) {
            mView = im;
        }

        @Override
        protected Bitmap doInBackground(String... paths) {
            Bitmap bm = null;
            String fileName = paths[0];
            String filePath = paths[1];
            bm = mDiskLruCache.getBitmap(fileName);
            //If cache failed. Try to fetch it from path.
            if (bm == null && filePath != null) {
                bm = decodeSampledBitmapFromResource(filePath, 150, 150);
            }
            return bm;

        }

        @Override
        protected void onPostExecute(Bitmap result) {
            super.onPostExecute(result);

            if (result != null) {
                mView.setImageBitmap(result);


            }
        }


    }
}
