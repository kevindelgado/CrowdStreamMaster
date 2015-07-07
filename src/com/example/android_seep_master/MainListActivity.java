package com.example.android_seep_master;

import com.example.android_seep_master.NewProcessPopup.OnSubmitListener;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
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
	//ListView listView;
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

	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			if(msg.what == FaceService.MSG_SET_FACE_RESULT){
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
				Message msg = Message.obtain(null, FaceService.MSG_REGISTER_CLIENT);
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
		
		//listView = (ListView) findViewById(R.id.listview);

		//final List<FaceResult> list = new ArrayList<FaceResult>();
		//final FaceResultArrayAdapter adapter = new FaceResultArrayAdapter(this, android.R.layout.face_result_list_item, list);
		// Create a new Adapter containing list of processes
		// Set the adapter on this ListActivity's built-in ListView
		//setListAdapter(new ArrayAdapter<>)
		
		//checkIfServiceIsRunning();
	}
	/*
	private void checkIfServiceIsRunning() {
		if (FaceService.isSystemRunning) {
			doBindService();
		}

	}
	 */
	void doBindService() {
		bindService(new Intent(this, FaceService.class), mConnection, Context.BIND_AUTO_CREATE);

	}

	void doUnbindService() {
		//if (mIsBound) {
		// If we have received the service, and hence registered with it, then now is the time to unregister.
		if (mService != null) {
			try {
				Message msg = Message.obtain(null, FaceService.MSG_UNREGISTER_CLIENT);
				msg.replyTo = mMessenger;
				mService.send(msg);
			}
			catch (RemoteException e) {
				// There is nothing special we need to do if the service has crashed.
			}
		}
		// Detach our existing connection.
		unbindService(mConnection);
		//mIsBound = false;
		//textStatus.setText("Unbinding.");
		//}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		try {
			doUnbindService();
		}
		catch (Throwable t) {
			Log.e("MainListActivity", "Failed to unbind from the service", t);
		}
	}

	private void configureGuiandAddListeners() {
		btn_new = (Button) findViewById(R.id.button_new);
		registerForContextMenu(btn_new);
		textFpsValue = (TextView) findViewById(R.id.textFpsValue);
		textNameValue = (TextView) findViewById(R.id.textNameValue);
		context = this;
		popup = new NewProcessPopup(context, this);
		
	}

	public void newProcess(View v) {
		//Intent intent = new Intent(this, MainActivity.class);
		//startActivity(intent);
		//LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		//popup = new PopupWindow(inflater.inflate(R.layout.new_process_popup, null, false), 
		//		300, 
		//		700, 
		//		true);
		//popup.showAtLocation(this.findViewById(R.id.button_new), Gravity.CENTER, 0, 0);
		
		//initializePorts();
		//popup.setFocusable(true);
		//popup.update();
		popup.show(this.findViewById(R.id.button_new));
	}


	/*
	public void startProcess(View v) {
		// Start remote connection
		Intent intent = new Intent(FaceService.class.getName());
		String one = port1.getText().toString();
		String two = port2.getText().toString();
		String three = port3.getText().toString();
		sendPortNums(one, two, three);
		startService(intent);
		doBindService();
		popup.dismiss();
	}
	*/
	
	public void valueChanged(String one, String two, String three) {
		Intent intent = new Intent(FaceService.class.getName());
		//PortData data = new PortData(Integer.parseInt(one), Integer.parseInt(two), Integer.parseInt(three));
		//Bundle b = new Bundle();
		//b.putParcelable("PortData", data);
		intent.putExtra("Port1", one);
		intent.putExtra("Port2", two);
		intent.putExtra("Port3", three);
		//sendPortNums(one, two, three);
		startService(intent);
		doBindService();
		popup.dismiss();
	}

	private void sendPortNums(String one, String two, String three) {
		if (mService != null) {
			try {

				Message msg = Message.obtain(null, FaceService.MSG_SET_PORT_NUMS);
				PortData data = new PortData(Integer.parseInt(one), Integer.parseInt(two), Integer.parseInt(three));
				Bundle b = new Bundle();
				b.putParcelable("PortData", data);
				msg.setData(b);
				msg.replyTo = mMessenger;
				mService.send(msg);
			}
			catch (RemoteException e) {
				Log.i("MainListActivity", "Port nums exception");
			}
		}
	}


}