package com.github.olsdav.dasftp;


import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


    }

    @Override
    protected void onResume() {
        super.onResume();
        TextView tw = (TextView) findViewById(R.id.tw);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(hasPreferences(prefs) && tw !=null)
        {
            tw.setVisibility(View.INVISIBLE);
        }
        else
        {
            Typeface tf = Typeface.createFromAsset(this.getAssets(),"fonts/robotolight.ttf");
            tw.setVisibility(View.VISIBLE);
            tw.setTypeface(tf);
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
                startActivityForResult(new Intent(this,SettingsActivity.class),1);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private boolean hasPreferences(SharedPreferences prefs)
    {
        return (prefs.getString("pref_host",null)!=null &&  prefs.getString("pref_port",null)!=null && prefs.getString("pref_path",null)!=null && prefs.getString("pref_url",null)!=null && prefs.getString("pref_user",null)!=null && prefs.getString("pref_passwd",null)!=null);
    }
}
