package com.example.android_seep_master;
import android.content.Context;  
import android.view.Gravity;  
import android.view.LayoutInflater;  
import android.view.MotionEvent;  
import android.view.View;  
import android.view.View.OnClickListener;  
import android.view.View.OnTouchListener;  
import android.view.WindowManager;  
import android.widget.Button;  
import android.widget.EditText;  
import android.widget.PopupWindow;  


public class NewProcessPopup extends PopupWindow{
	Context context;  
	EditText editPort1, editPort2, editPort3, editPort4;  
	String port1, port2, port3, port4;  

	private int dx;
	private int dy;
	private OnSubmitListener mListener;  

	public NewProcessPopup(Context ctx, OnSubmitListener listener) {  
		super(ctx);  

		context = ctx;  
		mListener = listener;  

		setContentView(LayoutInflater.from(context).inflate(R.layout.new_process_popup, null));  
		setHeight(WindowManager.LayoutParams.WRAP_CONTENT);  
		setWidth(WindowManager.LayoutParams.WRAP_CONTENT);  
		View popupView = getContentView();  
		setFocusable(true);  

		Button btn_close = (Button) popupView.findViewById(R.id.cancel_process);  
		Button btn_submit = (Button) popupView.findViewById(R.id.start_process);  
		editPort1 = (EditText) popupView.findViewById(R.id.port1);  
		editPort2 = (EditText) popupView.findViewById(R.id.port2);
		editPort3 = (EditText) popupView.findViewById(R.id.port3);
		editPort4 = (EditText) popupView.findViewById(R.id.port4);

		btn_submit.setOnClickListener(new OnClickListener() {  

			public void onClick(View v) {  
				String port1 = editPort1.getText().toString();  
				String port2 = editPort2.getText().toString();
				String port3 = editPort3.getText().toString();
				String port4 = editPort4.getText().toString();

				mListener.valueChanged(port1, port2, port3, port4);//To change the value of the textview of activity.  
				dismiss();  
			}  
		});
		
		btn_close.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				dismiss();
			}
			
		});


		popupView.setOnTouchListener(new OnTouchListener() {  

			public boolean onTouch(View arg0, MotionEvent motionEvent) {  
				switch (motionEvent.getAction()) {  

				case MotionEvent.ACTION_DOWN:  
					dx = (int) motionEvent.getRawX();  
					dy = (int) motionEvent.getRawY();  
					break;  

				case MotionEvent.ACTION_MOVE:  
					int x = (int) motionEvent.getRawX();  
					int y = (int) motionEvent.getRawY();  
					int left = (x - dx);  
					int top = (y - dy);  
					update(left, top, -1, -1);  
					break;  
				}  
				return true;  
			}  
		});  
	} 

	public void show(View v){
		showAtLocation(v, Gravity.CENTER, 0, 0);
	}

	public interface OnSubmitListener {
		void valueChanged(String port1, String port2, String port3, String port4);
	}

}
