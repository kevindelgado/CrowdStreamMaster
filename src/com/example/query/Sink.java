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
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.comm.NodeManagerCommunication;
import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.operator.DistributedApi;
import uk.ac.imperial.lsds.seep.operator.Operator;
import uk.ac.imperial.lsds.seep.operator.StatelessOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;

import com.example.android_seep_master.FaceService;

public class Sink implements StatelessOperator {
	Logger LOG = LoggerFactory.getLogger(Sink.class);
	private static final long serialVersionUID = 1L;
	Handler myHandler, myHandler2, myHandler_fps;
	DistributedApi api = new DistributedApi();
	private String filename = "delay.txt";
	private String filename2 = "fps.txt";
	private File myFile;
	private File myFile2;
	private FileOutputStream fos;
	private FileOutputStream fos2;
	private static long begin;
	private static int count = 1;
	private static int fps = 1;
	public static HashMap<Integer, Integer> totalDelays;

	public void setUp() {
		myHandler = FaceService.getTextViewHandler();
		myHandler2 = FaceService.getImageViewHandler();
		myHandler_fps = FaceService.getTextViewHandler2();

		LOG.info(">>>>>>>>>>>>>>>>>>>Sink set up");
		initializeFile();
		
		totalDelays = new HashMap<Integer, Integer>();
		for (int i = 1; i <= FaceService.numOps; i++){
			totalDelays.put(i, 0);
		}
	}

	public void processData(DataTuple dt) {
		int i = dt.getInt("value0");
		byte[] bytes = (byte[])dt.getValue("value1");
		int rows = dt.getInt("value2");
		int cols = dt.getInt("value3");
		int type = dt.getInt("value4");
		String name = dt.getString("value5");
		long timeStamp = dt.getLong("value6");
		int x = dt.getInt("value7");
		int y = dt.getInt("value8");
		int width = dt.getInt("value9");
		int height = dt.getInt("value10");
		int processTime = dt.getInt("value11");
		int processorIndex = dt.getInt("value12");


		long currentTime = System.currentTimeMillis();
		long pastTimeMillis = currentTime - timeStamp;

		writeToFile(i + " " + pastTimeMillis + " "+  currentTime + " " + processorIndex + "\n");
		
		if (i <= 2 * FaceService.numOps - 1){
		totalDelays.put(processorIndex, (int)pastTimeMillis);
		}
		
		
		Message msg_name = myHandler.obtainMessage();
		if (name.contains("Un"))
			name = "";
		msg_name.obj = "Recognized: "+name;
		myHandler.sendMessage(msg_name);

		if (count == 1){
			begin = currentTime;
			count ++;
		} else {
			if (currentTime - begin < 2000){
				count ++;
			} else {
			fps = (int) Math.ceil((count - 1) * 2000 / (currentTime - begin)) / 2;				
				count = 1;
				writeToFile2(fps + "\n");
				Message msg = myHandler_fps.obtainMessage();
				msg.obj = "Processing: " + fps + " video frames/sec";
				myHandler_fps.sendMessage(msg);
			}
		}

//		LOG.info(">>>Sink receive from {}" + processorIndex);

		Message msg2 = myHandler2.obtainMessage();
		Bundle b = new Bundle(5);

		//add integer data to the bundle, everyone with a key
		b.putInt("i", i);
		b.putInt("x", x);
		b.putInt("y", y);
		b.putInt("width", width);				
		b.putInt("height", height);	        
		msg2.setData(b);
		myHandler2.sendMessage(msg2);

	}

	public void processData(List<DataTuple> arg0) {
	}

	public void setCallbackOp(Operator op){
		this.api.setCallbackObject(op);
	}

	public void initializeFile(){
		myFile = new File(Environment
				.getExternalStorageDirectory(), filename);
		myFile2 = new File(Environment
				.getExternalStorageDirectory(), filename2);
		if (!myFile.exists()){
			try {
				myFile.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if (!myFile2.exists()){
			try {
				myFile2.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		try {
			fos = new FileOutputStream(myFile);
			fos2 = new FileOutputStream(myFile2);
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

	private void writeToFile2(String string){
		byte[] data = string.getBytes();

		if(fos2!=null){
			try {              
				fos2.write(data);
				fos2.flush();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
