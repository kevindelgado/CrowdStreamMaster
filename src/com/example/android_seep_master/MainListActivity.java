package com.example.android_seep_master;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.objdetect.CascadeClassifier;

import com.example.android_seep_master.NewProcessPopup.OnSubmitListener;
import com.example.query.PersonRecognizer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;


public class MainListActivity extends Activity implements OnSubmitListener{
	private static final String    TAG                 = "Android-Seep-FR::Activity";

	//ListView listView;
	int index;
	Button btn_new;
	Button btn_cancel_all;
	TextView textFpsValue;
	TextView textNameValue;
	Messenger mService = null;
	final Messenger mMessenger = new Messenger(new IncomingHandler());

	NewProcessPopup popup;
	Context context;

	EditText port1;
	EditText port2;
	EditText port3;
	EditText numWorkers;

	public static final int        JAVA_DETECTOR       = 0;
	public static final int        NATIVE_DETECTOR     = 1;
	private File                   mCascadeFile;
	public static CascadeClassifier      mJavaDetector;
	private String[]               mDetectorName;
	String mPath="";
	public static PersonRecognizer fr;

	public static int level, scale;
	public static WifiManager mainWifi;

	ExecutorService executor;

	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			if(msg.what == FaceTask.MSG_SET_FACE_RESULT){
				Log.i("MainListActivity", "new face");
				FaceResult faceResult = (FaceResult) msg.getData().getParcelable("FaceResult");
				textFpsValue.setText("FPS: " + faceResult.getFPS());
				//Log.i("MainListActivity", "SHOW FPS :" + faceResult.getFPS());
				textNameValue.setText("Recognized: " + faceResult.getName());
				//Log.i("MainListActivity", "SHOW REC :" + faceResult.getName());

			}
		}
	}

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mService = new Messenger(service);
			//textStatus.setText("Attached.");
			try {
				Message msg = Message.obtain(null, FaceTask.MSG_REGISTER_CLIENT);
				msg.replyTo = mMessenger;
				mService.send(msg);
			}
			catch (RemoteException e) {
				Log.i("MainListActivity", "Service crash");
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been unexpectedly disconnected - process crashed.
			mService = null;
			//textStatus.setText("Disconnected.");
		}
	};




	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main_list);
		configureGuiandAddListeners();
		executor = Executors.newCachedThreadPool();

		mPath=Environment.getExternalStorageDirectory()+"/facerecogOCV/";
		mDetectorName = new String[2];
		mDetectorName[JAVA_DETECTOR] = "Java";
		mDetectorName[NATIVE_DETECTOR] = "Native (tracking)";


		mainWifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
				scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
			}
		};
		IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		registerReceiver(batteryReceiver, filter);




	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		try {
			//doUnbindService();
		}
		catch (Throwable t) {
			Log.e("MainListActivity", "Failed to unbind from the service", t);
		}
	}

	private void configureGuiandAddListeners() {
		btn_new = (Button) findViewById(R.id.button_new);
		registerForContextMenu(btn_new);
		//textFpsValue = (TextView) findViewById(R.id.textFpsValue);
		//textNameValue = (TextView) findViewById(R.id.textNameValue);
		context = this;
		popup = new NewProcessPopup(context, this);

	}

	public void newProcess(View v) {

		popup.show(this.findViewById(R.id.button_new));
	}


	public void valueChanged(String one, String two, String three) {

		String[] ports = {one, two, three};
		//TextView[] textViews = {textFpsValue, textNameValue};
		TextView[] textViews = getTextViews(index++);
		FaceTask currentTask = new FaceTask(ports, textViews);
		executor.execute(currentTask);
		popup.dismiss();
	}

	private TextView[] getTextViews(int i) {
		TextView[] views = new TextView[2];
		switch(i){
		case 7:
			views[0] = (TextView) findViewById(R.id.textFps0);
			views[1] = (TextView) findViewById(R.id.textName0);

		case 6:
			views[0] = (TextView) findViewById(R.id.textFps1);
			views[1] = (TextView) findViewById(R.id.textName1);

		case 5:
			views[0] = (TextView) findViewById(R.id.textFps2);
			views[1] = (TextView) findViewById(R.id.textName2);

		case 4:
			views[0] = (TextView) findViewById(R.id.textFps3);
			views[1] = (TextView) findViewById(R.id.textName3);

		case 3:
			views[0] = (TextView) findViewById(R.id.textFps4);
			views[1] = (TextView) findViewById(R.id.textName4);

		case 2:
			views[0] = (TextView) findViewById(R.id.textFps5);
			views[1] = (TextView) findViewById(R.id.textName5);

		case 1:
			views[0] = (TextView) findViewById(R.id.textFps6);
			views[1] = (TextView) findViewById(R.id.textName6);

		case 0:
			views[0] = (TextView) findViewById(R.id.textFps7);
			views[1] = (TextView) findViewById(R.id.textName7);
		}
		return views;
	}

	@Override
	public void onResume() {
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
	}


	private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
			switch (status) {
			case LoaderCallbackInterface.SUCCESS: {
				Log.i(TAG, "OpenCV loaded successfully");

				fr=new PersonRecognizer(mPath);
				fr.load();

				try {
					// load cascade file from application resources
					InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
					File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
					mCascadeFile = new File(cascadeDir, "lbpcascade.xml");
					FileOutputStream os = new FileOutputStream(mCascadeFile);

					byte[] buffer = new byte[4096];
					int bytesRead;
					while ((bytesRead = is.read(buffer)) != -1) {
						os.write(buffer, 0, bytesRead);
					}
					is.close();
					os.close();

					mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
					if (mJavaDetector.empty()) {
						Log.e(TAG, "Failed to load cascade classifier");
						mJavaDetector = null;
					} else
						Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());

					cascadeDir.delete();

				} catch (IOException e) {
					e.printStackTrace();
					Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
				}

			} break;
			default:
			{
				super.onManagerConnected(status);
			} break;

			}
		}
	};


}