package org.aterd.lib.Global;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.aterd.lib.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BackgroundService extends Service {
	MyApplication app;
	Boolean isUpdate=false;
	public BackgroundService () {
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException("Not yet implemented");
	}
	
	@Override
	public void onCreate() {
		app=new MyApplication();
		final Handler handler = new Handler();
		Runnable run = new Runnable() {
			@Override
			public void run() {
				check();
				handler.postDelayed(this, 2*30*1000);
			}
		};
		handler.post(run);
		super.onCreate();
	}
	public void check() {
		if(Utils.getStringCache("token") == null) {
			return;
		}
		OkHttpClient okHttpClient = new OkHttpClient();
		RequestBody requestBody = new MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("token", Utils.getStringCache("token"))
			.addFormDataPart("userid", Utils.getStringCache("userid"))
			.addFormDataPart("version", String.valueOf(Constants.VERSION))
			.addFormDataPart("useragent", "App/" + Constants.APP_VERSION)
			.addFormDataPart("userdetails", Constants.getDetails())
			.build();
		Request.Builder builder = new Request.Builder();
		Request request = builder
			.header("App", Constants.APP_VERSION)
			.url(Constants.SERVER_URL+"notification/")
			.post(requestBody)
			.build();
		okHttpClient.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, final IOException e) {
				new Handler(Looper.getMainLooper()).post(new Runnable() {
					@Override
					public void run() {
						app.dialog_network(null);
					}
				});
			}
			
			@Override
			public void onResponse(Call call, final Response response) throws IOException {
				if (response.isSuccessful()) {
					String dataBody = response.body().string();
					new Handler(Looper.getMainLooper()).post(new Runnable() {
						@Override
						public void run() {
							try {
								Log.e("network", dataBody);
								JSONObject result = new JSONObject(dataBody);
								String status=app.supportNetworkResult(result);
								if(status.equals("success")) {
									Log.e("dataok", result.toString());
									if (status.equals("success") && result.has("items")) {
										if(result.has("update") && result.getBoolean("update") == true) {
											Log.e("wantdownload", result.toString());
											update(result);
										}
										JSONArray _items = result.getJSONArray("items");
										for (int i = 0; i < _items.length(); i++) {
											JSONObject now = _items.getJSONObject(i);
											notification(Integer.valueOf(now.getString("id")),now.getString("title"), now.getString("text"),now.getString("type"));
										}
									}
								}
							} catch (JSONException e) {
								e.printStackTrace();
							}
						}
					});
				} else {
					new Handler(Looper.getMainLooper()).post(new Runnable() {
						@Override
						public void run() {
//                            app.dialog_network(null);
						}
					});
				}
			}
		});
	}
	public void update(JSONObject result) {
		try {
			if(!result.has("updateDownload") || String.valueOf(result.getBoolean("updateDownload")).toString().isEmpty()) {
				return;
			}
			if(isUpdate == false) {
				isUpdate=true;
				String _download= result.getString("updateDownload");
				String _version=result.getString("updateVersion");
				Log.e("download", _download);
				String path=Environment.getExternalStorageDirectory() + File.separator + "iKashan-"+_version+".apk";
				File file = new File(path);
				if(file.exists()) {
					isUpdate=false;
					open(path);
				}
				else {
					OkHttpClient okHttpClient = new OkHttpClient();
					RequestBody requestBody = new MultipartBody.Builder()
						.setType(MultipartBody.FORM)
						.addFormDataPart("token", Utils.getStringCache("token"))
						.addFormDataPart("userid", Utils.getStringCache("userid"))
						.addFormDataPart("useragent", "Kashan App/"+Constants.APP_VERSION)
						.addFormDataPart("userdetails", Constants.getDetails())
						.build();
					Request.Builder builder = new Request.Builder();
					Request request = builder
						.header("Kashan-App", Constants.APP_VERSION)
						.url(_download)
						.post(requestBody)
						.build();
					okHttpClient.newCall(request).enqueue(new Callback() {
						@Override
						public void onFailure(Call call, final IOException e) {
							new Handler(Looper.getMainLooper()).post(new Runnable() {
								@Override
								public void run() {
									isUpdate=false;
								}
							});
						}
						@Override
						public void onResponse(Call call, final Response response) throws IOException {
							isUpdate=false;
							if (response.isSuccessful()) {
								Log.e("complete","sure");
								Log.e("done","sure");
								new Handler(Looper.getMainLooper()).post(new Runnable() {
									@Override
									public void run() {
										final Bitmap bitmap = BitmapFactory.decodeStream(response.body().byteStream());
										open(path);
									}
								});
							} else {
								new Handler(Looper.getMainLooper()).post(new Runnable() {
									@Override
									public void run() {
									}
								});
							}
						}
					});
				}
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void open(String path) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setDataAndType(Uri.fromFile(new File(path)), "application/vnd.android.package-archive");
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
	}
	
	public void notification(Integer id, String title, String text, String type) {
		Intent intent = new Intent(this, ActivityWelcome.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this,0,intent,0);
		NotificationCompat.Builder mBuilder =
			new NotificationCompat.Builder(this)
				.setSmallIcon(R.drawable.logo)
				.setContentTitle(title)
				.setContentText(text)
				.setContentIntent(pendingIntent );
		;
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.notify(id, mBuilder.build());
		
	}
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return super.onStartCommand(intent, flags, startId);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
	}
}
