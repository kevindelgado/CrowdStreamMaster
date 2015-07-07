package com.example.android_seep_master;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.ExecutionException;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
//import org.opencv.contrib.FaceRecognizer;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.objdetect.CascadeClassifier;

import com.googlecode.javacv.cpp.opencv_contrib.FaceRecognizer;
import com.googlecode.javacv.cpp.opencv_imgproc;
import com.example.query.Base;
import com.example.query.PersonRecognizer;
import com.example.query.Source;
import com.example.query.Tutorial3View;
import com.example.query.labels;

import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import uk.ac.imperial.lsds.seep.Main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FaceService extends Service {
	private static final String    TAG                 = "Android-Seep-FR::Activity";
	Logger LOG = LoggerFactory.getLogger(Base.class);

	Service self;
	Main instance;
	static Context context;

	private NotificationManager nm;
	private String currentFps = "";
	private String currentRecognized = "";


	ArrayList<Messenger> mClients = new ArrayList<Messenger>(); // Keeps track of all current registered clients.
	int mValue = 0; // Holds last value set by a client.
	static final int MSG_REGISTER_CLIENT = 1;
	static final int MSG_UNREGISTER_CLIENT = 2;
	static final int MSG_SET_INT_VALUE = 3;
	static final int MSG_SET_STRING_VALUE = 4;
	static final int MSG_SET_PORT_NUMS = 5;
	static final int MSG_SET_FACE_RESULT = 6;
	final Messenger mMessenger = new Messenger(new IncomingHandler()); // Target we publish for clients to send messages to IncomingHandler.



	//	ToggleButton btn_local;
	//	ToggleButton btn_remote;
	//	ToggleButton btn_deploy;
	//	ToggleButton btn_start;
	//	Button btn_stop;
	/*
	ImageView mImageView;
	ImageView drawingImage;
	TextView textresult;
	TextView textresult2;
	TextView textWelcome;
	Button btn_fps;
	Button btn_port;
	 */
	Bitmap mImageView;
	Bitmap drawingImage;
	String textresult;
	String textresult2;
	String textWelcome;

	private static Handler mImageViewHandler;
	private static Handler mTextViewHandler;
	private static Handler mTextViewHandler2;
	private Handler mHandler2 = new Handler();

	public static final int        JAVA_DETECTOR       = 0;
	public static final int        NATIVE_DETECTOR     = 1;
	private File                   mCascadeFile;
	public static CascadeClassifier      mJavaDetector;
	private String[]               mDetectorName;
	String mPath="";
	public static PersonRecognizer fr;
	com.googlecode.javacv.cpp.opencv_contrib.FaceRecognizer faceRecognizer;

	static final long MAXIMG = 10;
	ArrayList<Mat> alimgs = new ArrayList<Mat>();
	int[] labels = new int[(int)MAXIMG];

	public static int level, scale;
	public static WifiManager mainWifi;
	public static boolean isSystemRunning = false;

	static int currentFrameIndex = 0;

	public static int fps = 0;
	//public static int port = 2001;
	//public String port1;
	//public String nextPort1;
	private static int port1;
	private int port2;
	private int port3;
	private boolean portsReceived = false;

	public static boolean isLocal = false;

	Bitmap tmpBitmap = null;
	Canvas canvas = null;
	Paint myPaint = null;
	public int randomNum = 0;

	public static int numOps = 1;
	//private int numOps;


	@Override
	public IBinder onBind(Intent intent) {
		return mMessenger.getBinder();
	}

	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_REGISTER_CLIENT:
				mClients.add(msg.replyTo);
				break;
			case MSG_UNREGISTER_CLIENT:
				mClients.remove(msg.replyTo);
				break;
			case MSG_SET_PORT_NUMS:
				PortData portData = (PortData) msg.getData().getParcelable("PortData");
				port1 = portData.getPort1();
				port2 = portData.getPort2();
				port3 = portData.getPort3();
				portsReceived = true;
			}
		}

	}

	private void sendMessageToUI(String fps, String recognized) {

		Log.i("FaceService", "Clients size: " + mClients.size());
		for (int i=mClients.size()-1; i>=0; i--) {
			try {
				FaceResult currentData = new FaceResult(recognized, fps);
				Message msg = Message.obtain(null, MSG_SET_FACE_RESULT);
				Bundle b = new Bundle();
				b.putParcelable("FaceResult", currentData);
				msg.setData(b);
				//Log.i("FaceService",currentData.getFPS()  +" RECD: " + currentData.getName());
				Messenger client = mClients.get(i);
				client.send(msg);
			}
			catch (RemoteException e) {
				// The client is dead
				mClients.remove(i);
			}
		}

	}

	@Override
	public void onCreate() {
		//this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		//getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		//setContentView(R.layout.activity_individual_thread);

		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);

		self = this;
		context = getApplicationContext();

		mPath=Environment.getExternalStorageDirectory()+"/facerecogOCV/";
		mDetectorName = new String[2];
		mDetectorName[JAVA_DETECTOR] = "Java";
		mDetectorName[NATIVE_DETECTOR] = "Native (tracking)";

		//configureGUIandAddListeners();
		configureHandlers();


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


		/**********************************************************
		 ***************** Starting Master ************************
		 **********************************************************/
		instance = new Main();
		instance.executeMaster();

	}

	/**********************************************************
	 ***************** Message Handlers ***********************
	 **********************************************************/
	public void configureHandlers(){
		mTextViewHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				if (msg.obj != null){
					//textresult = (msg.obj.toString());
					currentRecognized = msg.obj.toString();
					sendMessageToUI(currentFps, currentRecognized);
					Log.i("FaceService", "currr rec: " + currentRecognized);
				} 
			}
		};

		mTextViewHandler2 = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				if (msg.obj != null){	
					//textresult2 = msg.obj.toString();
					currentFps = msg.obj.toString();
					sendMessageToUI(currentFps, currentRecognized);
					Log.i("FaceService", "currr FPS: " + currentFps);
				} 
			}
		};

		mImageViewHandler = new Handler(){
			@Override
			public void handleMessage(Message msg) {
				Bundle b=msg.getData();
				int i = (int) b.getInt("i");
				float x = (float) b.getInt("x");
				float y = (float) b.getInt("y");
				float width = (float) b.getInt("width");
				float height = (float) b.getInt("height");		 


				mImageView = (Source.bitmaps[currentFrameIndex]);

				if (x+y+width+height > 0){
					RectF rect = new RectF(x, y, x+width, y+height);
					tmpBitmap = null;
					canvas = null;
					tmpBitmap = Bitmap.createBitmap(480,270, Bitmap.Config.ARGB_8888);
					canvas = new Canvas(tmpBitmap);
					drawingImage = tmpBitmap;
					//canvas.drawRect(rect, myPaint);
				} else {
					//drawingImage.setImageDrawable(null);//clear drawn rectangle
				}

				super.handleMessage(msg);
			}
		};
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)  {
		//while(!portsReceived){
		//	// Chill
		//}
		//Log.i("LocalService", "PORTS RECEIVED");
		port1 = Integer.parseInt(intent.getStringExtra("Port1"));
		port2 = Integer.parseInt(intent.getStringExtra("Port2"));
		port3 = Integer.parseInt(intent.getStringExtra("Port3"));
		try{
			Log.i("LocalService", "Received start id " + startId + ": " + intent);
			// We want this service to continue running until it is explicitly
			// stopped, so return sticky.

			// Click the remote button
			startRemote();
			System.out.println("SLEEPING");
			Thread.sleep(5000);
			System.out.println("PRE_DEPLOYING");
			preDeploy();
			System.out.println("DEPLOYING");
			startDeploy();

		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return START_STICKY;
	}

	private void startDeploy() {
		// TODO Auto-generated method stub
		final String classname = "com.example.query.Base";

		instance.deploy(classname, port2, port3);	
		mHandler2.postDelayed(new Runnable() {
			public void run() {
				instance.deploy0(port2, port3);
			}
		}, 500);
		mHandler2.postDelayed(new Runnable() {
			public void run() {
				instance.deploy1();
			}
		}, 1000);
		mHandler2.postDelayed(new Runnable() {
			public void run() {
				instance.deploy2();
			}
		}, 2500);
		mHandler2.postDelayed(new Runnable() {
			public void run() {
				instance.deploy3();

			}
		}, 6000);
		mHandler2.postDelayed(new Runnable() {
			public void run() {
				System.out.println("STARTING");
				startStart();
			}

			private void startStart() {
				isSystemRunning = true;
				instance.start();

			}
		}, 7500);

	}

	private void preDeploy() {
		// TODO Auto-generated method stub
		Main instance2 = new Main();
		String worker = "Worker";
		//String port = "2002";
		String port = "" + (port1 + 1);
		//String port = getPort(1);
		String[] args = {worker, port};
		args[1] = port;
		instance2.executeSec(args);

	}

	private void startRemote() {
		//textWelcome.setVisibility(android.view.View.INVISIBLE);

		//isLocal = false;
		//btn_remote.setChecked(true);
		//btn_local.setClickable(false);
		Main instance1 = new Main();
		String worker = "Worker";
		String port = "" + port1;
		//String port = getPort(0);
		String[] args = {worker, port};
		instance1.executeSec(args);


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






	public void onDestroy() {
		super.onDestroy();
		android.os.Process.killProcess(android.os.Process.myPid());
	}

	public static Handler getImageViewHandler(){
		return mImageViewHandler;
	}

	public static Handler getTextViewHandler(){
		return mTextViewHandler;
	}	

	public static Handler getTextViewHandler2(){
		return mTextViewHandler2;
	}

	public static Context getAppContext(){
		return context;
	}

	public static String getPort(int num) {
		return "" + port1 + num;
		//return "" + port + num;
	}

	public static String getNextPort() {
		//return "" + port++;
		return "" + port1++;
	}


}





























