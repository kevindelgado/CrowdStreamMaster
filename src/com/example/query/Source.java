/*******************************************************************************
 * Copyright (c) 2014 Imperial College London
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Raul Castro Fernandez - initial API and implementation
 ******************************************************************************/
package com.example.query;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import uk.ac.imperial.lsds.seep.comm.NodeManagerCommunication;
import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.comm.serialization.messages.TuplePayload;
import uk.ac.imperial.lsds.seep.exception.ValuedException;
import uk.ac.imperial.lsds.seep.operator.DistributedApi;
import uk.ac.imperial.lsds.seep.operator.Operator;
import uk.ac.imperial.lsds.seep.operator.StatelessOperator;

import org.opencv.android.Utils;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.android_seep_master.MainActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

public class Source implements StatelessOperator  {
	Logger LOG = LoggerFactory.getLogger(Source.class);
	private static final long serialVersionUID = 1L;

	sendOutTuples sendOutputTupleThread;

	DistributedApi api = new DistributedApi();

	public static Bitmap bitmaps[] = new Bitmap[92];
	public static Mat mGrays[] = new Mat[92];
	public byte[] bytes[] = new byte[92][];
	int sleep_interval = 0;

	public void setUp() {
		LOG.info(">>>>>>>>>>>>>>>>>>>>Source set up");
		sendOutputTupleThread = new sendOutTuples();
		for (int i = 0; i <= 91; i++){
			bitmaps[i] = getFrame(i+9);
			mGrays[i] = new Mat();
			Utils.bitmapToMat(bitmaps[i], mGrays[i]);
			Imgproc.cvtColor(mGrays[i], mGrays[i], Imgproc.COLOR_BGR2GRAY);
			bytes[i] = new byte[(safeLongToInt(mGrays[i].total())) * mGrays[i].channels()];	
			mGrays[i].get(0, 0, bytes[i]);
		}
//		if (MainActivity.fps==0){
//			sleep_interval = 5;
//		} else
//			sleep_interval = (int)(1000/MainActivity.fps);
	}

	class sendOutTuples implements Runnable {

		Map<String, Integer> mapper = api.getDataMapper();
		DataTuple data = new DataTuple(mapper, new TuplePayload());
		int i = 0;
		Bitmap bitmap = null; //getFrame(i);;


		@Override
		public void run(){

			while(MainActivity.isSystemRunning){	
				bitmap = bitmaps[0]; //getFrameV2(i);	


				DataTuple output = data.newTuple(i, 
						bytes[0], 
						mGrays[0].rows(), 
						mGrays[0].cols(), 
						mGrays[0].type(), 
						"", 
						System.currentTimeMillis(),
						0,
						0,
						0,
						0, 
						0);
				try {
					api.send(output);
				} catch (ValuedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

				i++;

//				if (i>91)
//					i = 0;

//				if (sleep_interval!=0){
//					try {
//						Thread.sleep(sleep_interval);
//					} catch (InterruptedException e) {
//						e.printStackTrace();
//					}
//				}
//				else {
//					if (MainActivity.isLocal){
//						try {
//							Thread.sleep(200);
//						} catch (InterruptedException e) {
//							e.printStackTrace();
//						}
//					} else {
//				if (i < 30){
//						try {
//							Thread.sleep(1000);
//						} catch (InterruptedException e) {
//							e.printStackTrace();
//						}
////					}
//				} else {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
//				}
			}

		}
	}

	public void processData(DataTuple dt) {	
		sendOutputTupleThread.run();
	}

	public void setCallbackOp(Operator op){
		this.api.setCallbackObject(op);
	}

	public int safeLongToInt(long l) {
		if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
			throw new IllegalArgumentException
			(l + " cannot be cast to int without changing its value.");
		}
		return (int) l;
	}

	private Bitmap decodeFile(File f){
		Bitmap b = null;
		int IMAGE_MAX_SIZE = 1;                                                                                                                                                                                                                                                                                                                                                                                             ;
		//Decode image size
		BitmapFactory.Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;

		FileInputStream fis;	    

		int scale = 1;
		if (o.outHeight > IMAGE_MAX_SIZE || o.outWidth > IMAGE_MAX_SIZE) {
			scale = (int)Math.pow(2, (int) Math.ceil(Math.log(IMAGE_MAX_SIZE / 
					(double) Math.max(o.outHeight, o.outWidth)) / Math.log(0.5)));
		}

		//Decode with inSampleSize
		BitmapFactory.Options o2 = new BitmapFactory.Options();
		o2.inSampleSize = scale;
		try {
			fis = new FileInputStream(f);
			b = BitmapFactory.decodeStream(fis, null, o2);
			fis.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


		return b;
	}

	private Bitmap getFrame(int i) {
		try {

			String filename = "/sdcard/frames/scene00";
			if (i<10){
				filename = filename + "00" + i + ".jpg";
			} else if (i<100){
				filename = filename + "0" + i + ".jpg";
			} else
				filename = filename + i + ".jpg";

			File f = new File(filename);

			return decodeFile(f);

		} catch (Exception e) { 
			return null; 
		}
	}




	public void processData(List<DataTuple> arg0) {
		// TODO Auto-generated method stub

	}

}
