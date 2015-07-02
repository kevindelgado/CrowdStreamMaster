package com.example.android_seep_master;

import java.util.ArrayList;
import java.util.List;

import uk.ac.imperial.lsds.seep.Main;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

public class MainListActivity extends Activity{
	//ListView listView;
	Button btn_new;
	Button btn_cancel_all;
	TextView textFpsValue;
	TextView textNameValue;
	Messenger mService = null;
	final Messenger mMessenger = new Messenger(new IncomingHandler());

	PopupWindow popup;
	EditText port1;
	EditText port2;
	EditText port3;
	EditText numWorkers;

	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			FaceResult faceResult = (FaceResult) msg.getData().getParcelable("FaceResult");
			textFpsValue.setText("FPS: " + faceResult.getFPS());
			textNameValue.setText("Recognized: " + faceResult.getName());
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

		//listView = (ListView) findViewById(R.id.listview);

		//final List<FaceResult> list = new ArrayList<FaceResult>();
		//final FaceResultArrayAdapter adapter = new FaceResultArrayAdapter(this, android.R.layout.face_result_list_item, list);
		// Create a new Adapter containing list of processes
		// Set the adapter on this ListActivity's built-in ListView
		//setListAdapter(new ArrayAdapter<>)
		configureGuiandAddListeners();
		checkIfServiceIsRunning();
	}

	private void checkIfServiceIsRunning() {
		if (FaceService.isSystemRunning) {
			doBindService();
		}

	}

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
	}

	public void newProcess(View v) {
		//Intent intent = new Intent(this, MainActivity.class);
		//startActivity(intent);
		LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		popup = new PopupWindow(inflater.inflate(R.layout.new_process_popup, null, false), 
				300, 
				700, 
				true);
		popup.showAtLocation(this.findViewById(R.id.button_new), Gravity.CENTER, 0, 0);
		//popup.setFocusable(true);
		//popup.update();

	}

	public void startProcess(View v) {
		// Start remote connection
		Intent intent = new Intent(FaceService.class.getName());
		startService(intent);
		popup.dismiss();
	}

	public void cancelProcess(View v) {
		popup.dismiss();
	}
}