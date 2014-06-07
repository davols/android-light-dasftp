package com.github.davols.dasftp;


import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.crittercism.app.Crittercism;

public class MainActivity extends Activity implements AdapterView.OnItemClickListener {

    private TextView mTextView;
    private ListView mListView;
    private ImageView mImg;
    private UploadCursorAdapter mAdapter;
    private ClipboardManager clipboard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Crittercism.initialize(getApplicationContext(), "CRITTERCISM_APP_ID");
        setContentView(R.layout.activity_main);
        clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mTextView = (TextView) findViewById(R.id.tw);
        mTextView.setTypeface(Typeface.createFromAsset(this.getAssets(), "fonts/robotolight.ttf"));
        mImg = (ImageView) findViewById(R.id.warning);
        mListView = (ListView) findViewById(R.id.list);
//        query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
        Cursor c = getContentResolver().query(PictureUploadProvider.CONTENT_URI, new String[]{PictureUploadProvider.KEY_ID, PictureUploadProvider.KEY_NAME, PictureUploadProvider.KEY_FILEPATH, PictureUploadProvider.KEY_URL, PictureUploadProvider.KEY_UPL_NAME, PictureUploadProvider.KEY_DATE_UPLOADED}, null, null, PictureUploadProvider.KEY_ID + " DESC");
        mAdapter = new UploadCursorAdapter(this, c, true);
        registerForContextMenu(mListView);

        mListView.setAdapter(mAdapter);
        mImg.setVisibility(View.GONE);

        assert c != null;
        Log.d("Main", "count:" + c.getCount());
        if (c.getCount() == 0) {
            mListView.setVisibility(View.GONE);
            mTextView.setVisibility(View.VISIBLE);
            mTextView.setText(R.string.no_uploads);
        } else {
            mListView.setAdapter(mAdapter);
            mTextView.setVisibility(View.GONE);
            mListView.setVisibility(View.VISIBLE);
        }
        mListView.setOnItemClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (hasPreferences(prefs) && mTextView != null) {

            if (prefs.getBoolean("recent_failed", false)) {
                mListView.setVisibility(View.GONE);
                mTextView.setText(String.format(getString(R.string.last_upload_failed), prefs.getString("failed_reason", "Unknown")));
                mTextView.setVisibility(View.VISIBLE);
                mImg.setVisibility(View.VISIBLE);

            } else {
                mListView.setVisibility(View.VISIBLE);
                mAdapter.notifyDataSetInvalidated();
                mTextView.setVisibility(View.INVISIBLE);
                mImg.setVisibility(View.GONE);
            }
        } else {
            mListView.setVisibility(View.GONE);
            mTextView.setText(R.string.add_server);
            mTextView.setVisibility(View.VISIBLE);
            mImg.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.settings:
                startActivityForResult(new Intent(this, SettingsActivity.class), 1);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.context_menu, menu);
    }

    private boolean hasPreferences(SharedPreferences prefs) {
        return (prefs.getString("pref_host", null) != null && prefs.getString("pref_port", null) != null && prefs.getString("pref_path", null) != null && prefs.getString("pref_url", null) != null && prefs.getString("pref_user", null) != null && prefs.getString("pref_passwd", null) != null);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {

        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        Cursor c = mAdapter.getCursor();
        c.moveToPosition(info.position);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String url = c.getString(c.getColumnIndex(PictureUploadProvider.KEY_URL));
        ClipData clip;
        if (url != null) {
            if (!url.startsWith("http://") && !url.startsWith("https://"))
                url = "http://" + url;
        }
        switch (item.getItemId()) {
            case R.id.browser:
                //Open in browser
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
                return true;
            case R.id.copy:
                //Copy
                clip = ClipData.newPlainText("simple text", url);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, R.string.added_clipboard, Toast.LENGTH_LONG).show();
                return true;
            case R.id.copy_tags:
                //Copy with tags.
                String shareUrl = prefs.getString("pref_tag_txt", "[img]%url[/img]").replace("%url", url);
                clip = ClipData.newPlainText("simple text", shareUrl);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, R.string.added_tag_clipboard, Toast.LENGTH_LONG).show();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        Cursor c = mAdapter.getCursor();
        c.moveToPosition(i);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int pref = Integer.parseInt(prefs.getString("pref_list", "0"));
        String url = c.getString(c.getColumnIndex(PictureUploadProvider.KEY_URL));
        if (url != null) {
            if (!url.startsWith("http://") && !url.startsWith("https://"))
                url = "http://" + url;
        }
        if (pref == 0) {
            //Open in browser
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
        } else if (pref == 1) {
            //Copy
            ClipData clip = ClipData.newPlainText("simple text", url);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.added_clipboard, Toast.LENGTH_LONG).show();
        } else if (pref == 2) {
            //Copy with tags.
            String shareUrl = prefs.getString("pref_tag_txt", "[img]%url[/img]").replace("%url", url);
            ClipData clip = ClipData.newPlainText("simple text", shareUrl);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.added_tag_clipboard, Toast.LENGTH_LONG).show();
        }

    }
}
