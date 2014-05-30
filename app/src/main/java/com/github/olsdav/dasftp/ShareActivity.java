package com.github.olsdav.dasftp;

import android.app.Activity;
import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
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

//TODO Error handling the upload bit. 
public class ShareActivity extends Activity{

	private static final String TAG = "ShareActivity";
	private static NotificationManager mNotifyManager;
	private static  Builder mBuilder;
	private static ClipboardManager clipboard;
    private static SharedPreferences prefs;
	@Override
	protected void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Get intent, action and MIME type
		final Intent intent = getIntent();
		String action = intent.getAction();
		String type = intent.getType();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if(hasPreferences())
        {
            // Gets a handle to the clipboard service.
            clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            mNotifyManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mBuilder = new Notification.Builder(this);
            mBuilder.setContentTitle("Picture Download")
                    .setContentText("Download in progress")
                    .setSmallIcon(R.drawable.ic_stat_av_upload);
            mBuilder.setProgress(0, 0, false);
            if (Intent.ACTION_SEND.equals(action) && type != null) {
                Log.d(TAG,"YES");
                if (type.startsWith("image/")) {
                    Log.d(TAG,"Image");
                    Toast.makeText(this, "Starting upload",Toast.LENGTH_LONG).show();
                    handleSendImage(intent); // Handle single image being sent


                }
                else
                    Toast.makeText(this, "No Image",Toast.LENGTH_LONG).show();
                Log.d(TAG,"No Image");
            } else {
                // Handle other intents, such as being started from the home screen
                Toast.makeText(this, "Not the correct intent",Toast.LENGTH_LONG).show();
                Log.d(TAG,"No correct intent");
            }

        }
        else
        {
            Toast.makeText(this, "Need to set up server in preferences",Toast.LENGTH_LONG).show();
        }

		finish();
	}

    private boolean hasPreferences()
    {
        return (prefs.getString("pref_host",null)!=null &&  prefs.getString("pref_port",null)!=null && prefs.getString("pref_path",null)!=null && prefs.getString("pref_url",null)!=null && prefs.getString("pref_user",null)!=null && prefs.getString("pref_passwd",null)!=null);
    }
	void handleSendImage(Intent intent) {
		Log.d(TAG,"handleSendImage");
		String[] filePathColumn = {MediaStore.MediaColumns.DATA};
		Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
		if (imageUri != null) {
			// Update UI to reflect image being shared
			Log.d(TAG, "imageUri is not null");
			String filePath;
			String scheme = imageUri.getScheme();
			Log.d(TAG,"imageUri: "+imageUri);

			if(scheme.equals("content")){
				Cursor cursor = getContentResolver().query(imageUri, filePathColumn, null, null, null);
				cursor.moveToFirst(); // <--no more NPE

				int columnIndex = cursor.getColumnIndex(filePathColumn[0]);

				filePath = cursor.getString(columnIndex);

				cursor.close();
				new UploadTask().execute(filePath);
			} else if(scheme.equals("file")){
				filePath = imageUri.getPath();
				Log.d(TAG, "Loading file " + filePath);
			} else {
				Log.d(TAG,"Failed to load URI " + imageUri.toString());
				return;
			}
		}
		else 
		{
			Log.d(TAG, "imageUri is null");
		}
	}
	private class UploadTask extends AsyncTask<String, Integer, String> {
		@Override
		protected String doInBackground(String... paths) {
			String remoteUrl=null;
			String filePath = paths[0];
			Log.d(TAG,"test filepath: "+filePath);
			Log.d(TAG,"WTF");

			String fileName = filePath.substring(filePath.lastIndexOf("/")+1,filePath.indexOf("."));
			Log.d(TAG,"name: "+fileName);
			String extension = filePath.substring(filePath.lastIndexOf("."));
			Log.d(TAG,"extension :"+extension);

			Session session = null;
			Channel channel = null;
			try {
				JSch ssh = new JSch();
                java.util.Properties config = new java.util.Properties();
                if(prefs.getBoolean("pref_cert",true))
                {

                    config.put("StrictHostKeyChecking", "yes");
                    JSch.setConfig("StrictHostKeyChecking", "yes");
                }
                else
                {

                    config.put("StrictHostKeyChecking", "no");
                    JSch.setConfig("StrictHostKeyChecking", "no");
                }

				session = ssh.getSession(prefs.getString("pref_user",null), prefs.getString("pref_host",null), Integer.parseInt(prefs.getString("pref_port","0")));
				session.setPassword(prefs.getString("pref_passwd",null));
                session.setConfig(config);
				session.connect();


				channel = session.openChannel("sftp");
				channel.connect();
				ChannelSftp sftp = (ChannelSftp) channel;
				//TODO SftpATTRS attrs = channelSftp.lstat(path);
                String remotePath=null;
                if(!prefs.getString("pref_path",null).endsWith("/"))
                {
                    remotePath = prefs.getString("pref_path",null)+"/";
                }
                else
                {
                    remotePath = prefs.getString("pref_path",null);
                }
				String fileName2 = createUniqueFileName(sftp,remotePath,fileName,extension);
				sftp.put(filePath,remotePath+fileName2+extension);
				remoteUrl=fileName2+extension;
                Log.d(TAG,"pick remoteUrl :"+remoteUrl);
			} catch (JSchException e) {
				e.printStackTrace();
				Log.d(TAG, "Exception 1" + e.getLocalizedMessage());
				remoteUrl=null;
			} catch (SftpException e) {
				e.printStackTrace();
				Log.d(TAG, "Exception 2" + e.getLocalizedMessage());
				remoteUrl=null;
			} finally {
				if (channel != null) {
					channel.disconnect();
				}
				if (session != null) {
					session.disconnect();
				}
			}
			return remoteUrl;

		}

		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
            // When the loop is finished, updates the notification
            mBuilder.setContentText("Upload complete")
            // Removes the progress bar
                    .setProgress(0,0,false);
            mNotifyManager.notify(1, mBuilder.build());
            if(result!=null) {
                if(prefs.getBoolean("pref_tag",false)) {
                    String url = prefs.getString("pref_url",null)+result;
                    String shareUrl = prefs.getString("pref_tag_txt","[img]%url[/img]").replace("%url",url);
                    ClipData clip = ClipData.newPlainText("simple text",shareUrl);
                    clipboard.setPrimaryClip(clip);
                }
                else {
                    ClipData clip = ClipData.newPlainText("simple text",prefs.getString("pref_url",null)+result);
                    clipboard.setPrimaryClip(clip);

                }

            }
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			mNotifyManager.notify(1, mBuilder.build());
		}


	}

	private String createUniqueFileName(ChannelSftp sftp, String remotePath, String name,String extension) 
	{
		String mName = name;
		boolean hasUniqueName=false;
		int length=1;
		while(!hasUniqueName)
		{
			try{
				sftp.lstat(remotePath+mName+extension);
			} catch(SftpException e)
			{

				hasUniqueName=true;
				break;
			} 

			mName=mName+randomIdentifier(length);
			length++;
		}

		return mName;
	}
	// class variable
	final String lexicon = "ABCDEFGHIJKLMNOPQRSTUVWXYZ12345674890";

	final java.util.Random rand = new java.util.Random();

	private String randomIdentifier(int maxLength) {
		StringBuilder builder = new StringBuilder();

		for(int i = 0; i < maxLength; i++)
			builder.append(lexicon.charAt(rand.nextInt(lexicon.length())));


		return builder.toString();
	}
}
