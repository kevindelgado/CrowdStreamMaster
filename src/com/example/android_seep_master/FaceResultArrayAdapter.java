package com.example.android_seep_master;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class FaceResultArrayAdapter extends ArrayAdapter<String> {

	private final Context context;
	private final String[] values;

	public FaceResultArrayAdapter(Context context, String[] values) {
		super(context, -1, values);
		this.context = context;
		this.values = values;
	}
	/*
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		
		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(R.layout.rowlayout, parent, false);
		TextView nameView = (TextView) rowView.findViewById(R.id.name);
		TextView fpsView = (TextView) rowView.findViewById(R.id.fps);
		ImageView imageView = (ImageView) rowView.findViewById(R.id.image);
		//nameView.setText(values[position]);
		return rowView;
		
	}
	*/
}
