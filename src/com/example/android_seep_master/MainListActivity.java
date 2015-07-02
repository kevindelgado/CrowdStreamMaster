package com.example.android_seep_master;

import java.util.ArrayList;
import java.util.List;

import uk.ac.imperial.lsds.seep.Main;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.PopupMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

public class MainListActivity extends Activity{
	ListView listView;
	Button btn_new;
	PopupWindow popup;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main_list);
		
		 listView = (ListView) findViewById(R.id.listview);
		
		//final List<FaceResult> list = new ArrayList<FaceResult>();
		//final FaceResultArrayAdapter adapter = new FaceResultArrayAdapter(this, android.R.layout.face_result_list_item, list);
		// Create a new Adapter containing list of processes
		// Set the adapter on this ListActivity's built-in ListView
		//setListAdapter(new ArrayAdapter<>)
		configureGuiandAddListeners();
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