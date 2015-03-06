package com.example.android_seep_master;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Random;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.objdetect.CascadeClassifier;

import com.example.query.Base;
import com.example.query.PersonRecognizer;
import com.example.query.Source;

import android.support.v4.app.Fragment;
import android.text.Editable;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import uk.ac.imperial.lsds.seep.Main;
import uk.ac.imperial.lsds.seep.elastic.NodePoolEmptyException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.AlertDialog;
import android.graphics.PorterDuff;

public class MainActivity extends Activity {
	private static final String    TAG                 = "Android-Seep-FR::Activity";
	Logger LOG = LoggerFactory.getLogger(Base.class);

	Activity self;
	Main instance;
	static Context context;

	ToggleButton btn_local;
	ToggleButton btn_remote;
	ToggleButton btn_deploy;
	ToggleButton btn_start;
	Button btn_stop;
	ImageView mImageView;
	ImageView drawingImage;
	TextView textresult;
	TextView textresult2;
	TextView textWelcome;
	Button btn_fps;

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

	public static boolean isLocal = false;

	Bitmap tmpBitmap = null;
	Canvas canvas = null;
	Paint myPaint = null;
	public int randomNum = 0;
	
	public static int numOps = 3;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//Remove title bar
		// this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		//Remove notification bar
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.activity_main);
		self = this;
		context = getApplicationContext();

		mPath=Environment.getExternalStorageDirectory()+"/facerecogOCV/";
		mDetectorName = new String[2];
		mDetectorName[JAVA_DETECTOR] = "Java";
		mDetectorName[NATIVE_DETECTOR] = "Native (tracking)";

		configureGUIandAddListeners();
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
					textresult.setText(msg.obj.toString());
				} 
			}
		};

		mTextViewHandler2 = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				if (msg.obj != null){	
					textresult2.setText(msg.obj.toString());
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

				//mImageView.setImageBitmap(Source.bitmaps[i]);
				mImageView.setImageBitmap(Source.bitmaps[currentFrameIndex]);
				
				if (x+y+width+height > 0){
					RectF rect = new RectF(x, y, x+width, y+height);
					tmpBitmap = null;
					canvas = null;
					tmpBitmap = Bitmap.createBitmap(480,270, Bitmap.Config.ARGB_8888);
					canvas = new Canvas(tmpBitmap);
					drawingImage.setImageBitmap(tmpBitmap);
					canvas.drawRect(rect, myPaint);
				} else {
					drawingImage.setImageDrawable(null);//clear drawn rectangle
				}

				super.handleMessage(msg);
			}
		};
	}


	/**********************************************************
	 ***************** Button Listeners ***********************
	 **********************************************************/	
	public void configureGUIandAddListeners(){
		textresult = (TextView) findViewById(R.id.textResult);
		textresult2 = (TextView) findViewById(R.id.textResult2);
		textWelcome = (TextView) findViewById(R.id.textWelcome);

		btn_local = (ToggleButton) findViewById(R.id.button1);
		btn_remote = (ToggleButton) findViewById(R.id.button2);
		btn_deploy = (ToggleButton) findViewById(R.id.button3);
		btn_start = (ToggleButton) findViewById(R.id.button4);
		btn_stop = (Button) findViewById(R.id.button5);
		

		btn_deploy.setVisibility(android.view.View.INVISIBLE);
		btn_start.setVisibility(android.view.View.INVISIBLE);


		mImageView = (ImageView) findViewById(R.id.imageView);
		drawingImage = (ImageView) this.findViewById(R.id.outside_imageview);
		myPaint = new Paint();
		myPaint.setStyle(Paint.Style.STROKE);
		myPaint.setColor(0xFF3399FF);
		myPaint.setStrokeWidth(5);

		//--------------------------------test only---------------------------------------
		btn_fps = (Button) findViewById(R.id.button_fps);
		btn_fps.getBackground().setColorFilter(0xFFFFFF00, PorterDuff.Mode.MULTIPLY);

		
		btn_fps.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0){
				AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);

				alert.setTitle("Configure video fps");
				alert.setMessage("Please set the source fps, e.g., 30. Default is unlimited.");

				// Set an EditText view to get user input 
				final EditText input = new EditText(MainActivity.this);
				alert.setView(input);

				alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						Editable value = input.getText();
						// Do something with value!
						MainActivity.fps = Integer.parseInt(value.toString());
					}
				});

				alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						// Canceled.
					}
				});

				alert.show();
			}
		});
		
		//--------------------------------test only---------------------------------------



		btn_local.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0){
				textWelcome.setVisibility(android.view.View.INVISIBLE);
				Toast toast = Toast.makeText(getApplicationContext(), "Initiating face recognition computation on this device ...", Toast.LENGTH_LONG);
				LinearLayout toastLayout = (LinearLayout) toast.getView();
				TextView toastTV = (TextView) toastLayout.getChildAt(0);
				toastTV.setTextSize(30);
				toast.show();
				isLocal = true;
				btn_local.setChecked(true);
				btn_remote.setClickable(false);
				Main instance1 = new Main();
				String worker = "Worker";
				String port = "2001";
				String[] args = {worker, port};
				instance1.executeSec(args);

				mHandler2.postDelayed(new Runnable() {
					public void run() {
						Main instance2 = new Main();
						String worker = "Worker";
						String port = "2002";
						String[] args = {worker, port};
						args[1] = port;
						instance2.executeSec(args);
					}
				}, 500);
				mHandler2.postDelayed(new Runnable() {
					public void run() {
						Main instance3 = new Main();
						String worker = "Worker";
						String port = "2003";
						String[] args = {worker, port};
						args[1] = port;
						instance3.executeSec(args);
					}
				}, 1000);
				mHandler2.postDelayed(new Runnable() {
					public void run() {
						Main instance4 = new Main();
						String worker = "Worker";
						String port = "2004";
						String[] args = {worker, port};
						args[1] = port;
						instance4.executeSec(args);
					}
				}, 1500);
				mHandler2.postDelayed(new Runnable() {
					public void run() {
						btn_deploy.performClick();
					}
				}, 2000);
			}

		});

		btn_remote.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0){
				textWelcome.setVisibility(android.view.View.INVISIBLE);

				isLocal = false;
				btn_remote.setChecked(true);
				btn_local.setClickable(false);
				Main instance1 = new Main();
				String worker = "Worker";
				String port = "2001";
				String[] args = {worker, port};instance1.executeSec(args);

				AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
				// set title
				alertDialogBuilder.setTitle("Soliciting helper devices ...")
				.setMessage("Click when workers have joined")
				.setCancelable(false)
				.setNeutralButton("Done",
						new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton){
						Main instance2 = new Main();
						String worker = "Worker";
						String port = "2002";
						String[] args = {worker, port};
						args[1] = port;
						instance2.executeSec(args);

						Toast toast = Toast.makeText(getApplicationContext(), "Initiating video distribution and face recognition computation on helpers ...", Toast.LENGTH_LONG);
						LinearLayout toastLayout = (LinearLayout) toast.getView();
						TextView toastTV = (TextView) toastLayout.getChildAt(0);
						toastTV.setTextSize(30);
						toast.show();
						mHandler2.postDelayed(new Runnable() {
							public void run() {
								btn_deploy.performClick();
							}
						}, 1000);
					}
				})
				.show();
			}
		});

		btn_deploy.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0){
				final String classname = "com.example.query.Base";
				btn_deploy.setChecked(true);
				instance.deploy(classname, 40000, 50000);	
				mHandler2.postDelayed(new Runnable() {
					public void run() {
						instance.deploy0(40000, 50000);
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
						btn_start.performClick();
						Toast toast = Toast.makeText(getApplicationContext(), "Starting ...", Toast.LENGTH_LONG);
						LinearLayout toastLayout = (LinearLayout) toast.getView();
						TextView toastTV = (TextView) toastLayout.getChildAt(0);
						toastTV.setTextSize(30);
						toast.show();
					}
				}, 7500);
				
//				mHandler2.postDelayed(new Runnable() {
//					public void run(){				
//						try {
//							instance.firstScale();
//						} catch (NodePoolEmptyException e) {
//							// TODO Auto-generated catch block
//							e.printStackTrace();
//						}
//					}
//				}, 8500);
			}
		});

		btn_start.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0){
				btn_start.setChecked(true);
				isSystemRunning = true;
				instance.start();
			}
		});

		btn_stop.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0){
				Toast toast = Toast.makeText(getApplicationContext(), "Terminating all ...", Toast.LENGTH_LONG);
				LinearLayout toastLayout = (LinearLayout) toast.getView();
				TextView toastTV = (TextView) toastLayout.getChildAt(0);
				toastTV.setTextSize(30);
				toast.show();
				isSystemRunning = false;
				instance.stop();
				btn_remote.setChecked(false);
				btn_deploy.setChecked(false);
				btn_start.setChecked(false);
				
			}
		});
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



	/**********************************************************
	 **********************************************************
	 ********** Overrides and unimportant functions ***********
	 **********************************************************
	 **********************************************************/


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class PlaceholderFragment extends Fragment {

		public PlaceholderFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main, container, false);
			return rootView;
		}
	}

	@Override
	public void onPause()
	{
		super.onPause();      
	}

	@Override
	public void onResume()
	{
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
	}

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



}
