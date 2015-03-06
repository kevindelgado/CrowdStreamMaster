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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;

import android.graphics.Bitmap;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;

import com.example.android_seep_master.MainActivity;

import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.exception.ValuedException;
import uk.ac.imperial.lsds.seep.operator.DistributedApi;
import uk.ac.imperial.lsds.seep.operator.Operator;
import uk.ac.imperial.lsds.seep.operator.StatelessOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessorUnited implements StatelessOperator{

	private static final long serialVersionUID = 1L;
	Logger LOG = LoggerFactory.getLogger(ProcessorUnited.class);
	Bitmap mBitmap;
	Message message;

	private float                  mRelativeFaceSize   = 0.2f;
	private int                    mAbsoluteFaceSize   = 0;
	Handler myHandler, myHandler2;

	DistributedApi api = new DistributedApi();
	int mLikely=999;

	private static long begin;
	private static int count = 1;
	private static int fps = 1;
	
	private File myFile;
	private FileOutputStream fos;
	
	public static int processTime; 

	public void setUp() {
		// TODO Auto-generated method stub
		LOG.info(">>>>>>>>>>>>>>>>>>>>Processor set up");
		myHandler = MainActivity.getTextViewHandler();
		myHandler2 = MainActivity.getImageViewHandler();
		initializeFile();
	}
	
	
	
	public void processData(DataTuple data) throws ValuedException {

		int index = data.getInt("value0");
		byte[] bytes = (byte[])data.getValue("value1");

		int rows = data.getInt("value2");
		int cols = data.getInt("value3");
		int type = data.getInt("value4");
		String name = data.getString("value5");
		long timeStamp = data.getLong("value6");

		//LOG.info(">>>>>>>>>>>>>>>>>>>> Processor receive "+name+" at "+(System.currentTimeMillis()-Source.sendTime));
		
		long beginTime = System.currentTimeMillis();
		
		if(bytes!=null){
			Mat mGray = new Mat(rows,cols,type);
			mGray.put(0, 0, bytes);

			//LOG.info(">>>Processor1 receive: "+mGray.toString());

			if (mAbsoluteFaceSize == 0) {
				int height = mGray.rows();
				if (Math.round(height * mRelativeFaceSize) > 0) {
					mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
				}
			}

			MatOfRect faces = new MatOfRect();

			if (MainActivity.mJavaDetector != null)
				MainActivity.mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
						new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());


			Rect[] facesArray = faces.toArray();
			
			if ((facesArray.length>0)){
				Mat m=new Mat();
				m=mGray.submat(facesArray[0]);
				mBitmap = Bitmap.createBitmap(m.width(),m.height(), Bitmap.Config.ARGB_8888);
				Utils.matToBitmap(m, mBitmap);	
				name=MainActivity.fr.predict(m);
				mLikely=MainActivity.fr.getProb();
				if(mLikely == -1)
					name="Unknown";
				if(index < 66 && name.contains("Mandis")){
					name="Unknown";
				}
				
				long finishTime = System.currentTimeMillis();		
				long pastTime = finishTime - beginTime;
				processTime = (int) pastTime;
				
				DataTuple output = data.setValues(index,null,0,0,0,name,
						timeStamp,
						facesArray[0].x,
						facesArray[0].y,
						facesArray[0].width,
						facesArray[0].height,
						processTime);
				
				api.send(output);
				
				Message msg_name = myHandler.obtainMessage();
				if (name.contains("Un"))
					name = "";
				msg_name.obj = name;
				myHandler.sendMessage(msg_name);
						
				writeToFile(index + " " + pastTime + "\n");
				
			} else {
				//LOG.info(">>>Processor1 detects no face. :(");
				
				DataTuple output = data.setValues(index,null, 0, 0, 0, "", timeStamp, 0, 0, 0, 0, 0);
				api.send(output);

			}
			
			long currentTime = System.currentTimeMillis();
			if (count == 1){
				begin = currentTime;
				count ++;
			} else {
				if (currentTime - begin < 3000){
					count ++;
				} else {
					fps = (int) Math.ceil((count - 1) * 3000 / (currentTime - begin)) / 3;				
					count = 1;
					
				}
			}
			
		} else {
			DataTuple output = data.setValues(index,null,0,0,0,"Endli",timeStamp,0,0,0,0, 0);
			api.send(output);

		}

	}


	public void initializeFile(){
		String filename = "process.txt";

		myFile = new File(Environment
				.getExternalStorageDirectory(), filename);
		if (!myFile.exists()){
			try {
				myFile.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		try {
			fos = new FileOutputStream(myFile);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}
	
	private void writeToFile(String string){
		byte[] data = string.getBytes();

		if(fos!=null){
			try {              
				fos.write(data);
				fos.flush();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public void processData(List<DataTuple> arg0) {
		// TODO Auto-generated method stub

	}

	public void setCallbackOp(Operator op){
		this.api.setCallbackObject(op);
	}

}
