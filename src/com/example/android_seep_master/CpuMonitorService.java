package com.example.android_seep_master;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class CpuMonitorService extends Service{

	private static final String TAG = "MyService";
	private String filename = "monitor_master.txt";
	private File myFile;
	private FileOutputStream fos;
	private static boolean isRunning = false;
	private Intent batteryIntent = null;
	private int pid = android.os.Process.myPid();
	private BufferedReader readStream;
	private String[] a;
	private long workT, totalT, workAMT;
	private long total, totalBefore, work, workBefore, workAM, workAMBefore;

	private Socket socket;
	private static  int SERVERPORT = 10000;
	private static  String SERVER_IP;
	PrintWriter out; 
	boolean isWebbing = false;

	@Override
	public IBinder onBind(Intent arg0) {

		return null;
	}

	@Override
	public void onCreate() {
		Toast.makeText(this, "Congrats! MyService Created", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onCreate");

		initializeFile();


	}

	public void initializeFile(){
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

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i(TAG, "StartCMService");
		if (isWebbing){
			SERVER_IP = intent.getExtras().getString("IP");
			new Thread(new ClientThread()).start();
			Toast.makeText(this, "My Service Started", Toast.LENGTH_LONG).show();
		}
		Log.d(TAG, "onStart");	
		isRunning = true;
		new Thread(new Runnable(){
			public void run() {
				double cpu = 0.0;
//				int cpufreq = 0;
				long battery = (long) 0;
				int [] cpu_all = new int[4];
				while(isRunning){
					try {
						cpu_all = getCpuUsageStatistic();
						cpu = cpu_all[0] + cpu_all[1];
					//	cpufreq = getCpuFreq();
						battery = readBatteryLife();
						writeToFile(System.currentTimeMillis()+" "+cpu+" "+battery+" \n");
						if (isWebbing){
							out.println(round(cpu, 3)+"");
						}
						Thread.sleep(1000);					
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} 
				}

			}
		}).start();
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		
		Toast.makeText(this, "MyService Stopped", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onDestroy");
		isRunning = false;
		try {
			if (fos != null)
				fos.close();
			if (isWebbing){
				out.println("quit");
				out.close();
				socket.close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static double round(double value, int places) {
		if (places < 0) throw new IllegalArgumentException();

		BigDecimal bd = new BigDecimal(value);
		bd = bd.setScale(places, RoundingMode.HALF_UP);
		return bd.doubleValue();
	}

	private int getCpuFreq() {
		java.lang.Process p = null;
		BufferedReader in = null;
		String out = "0";
		int BUFF_LEN = 20;
		//			p = Runtime.getRuntime().exec("su -c 'cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_cur_freq'");
		try {
			p = Runtime.getRuntime().exec(new String[]{"su", "-c", "system/bin/sh"});
			DataOutputStream stdin = new DataOutputStream(p.getOutputStream());
			//from here all commands are executed with su permissions
			stdin.writeBytes("cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_cur_freq\n"); // \n executes the command
			InputStream stdout = p.getInputStream();
			byte[] buffer = new byte[BUFF_LEN];
			int read;
			out = new String();
			//read method will wait forever if there is nothing in the stream
			//so we need to read it in another way than while((read=stdout.read(buffer))>0)
			while(true){
				read = stdout.read(buffer);
				out += new String(buffer, 0, read);
				if(read<BUFF_LEN){
					//we have read everything
					break;
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		out=out.replaceAll("[^0-9]+", "");
		return Integer.parseInt(out);
	}


	/**
	 * 
	 * @return integer Array with 4 elements: user, system, idle and other cpu
	 *         usage in percentage.
	 */
	private int[] getCpuUsageStatistic() {

		String tempString = executeTop();

		tempString = tempString.replaceAll(",", "");
		tempString = tempString.replaceAll("User", "");
		tempString = tempString.replaceAll("System", "");
		tempString = tempString.replaceAll("IOW", "");
		tempString = tempString.replaceAll("IRQ", "");
		tempString = tempString.replaceAll("%", "");
		for (int i = 0; i < 10; i++) {
			tempString = tempString.replaceAll("  ", " ");
		}
		tempString = tempString.trim();
		String[] myString = tempString.split(" ");
		int[] cpuUsageAsInt = new int[myString.length];
		for (int i = 0; i < myString.length; i++) {
			myString[i] = myString[i].trim();
			cpuUsageAsInt[i] = Integer.parseInt(myString[i]);
		}
		return cpuUsageAsInt;
	}

	private String executeTop() {
		java.lang.Process p = null;
		BufferedReader in = null;
		String returnString = null;
		try {
			p = Runtime.getRuntime().exec("top -n 1 -d 1");
			in = new BufferedReader(new InputStreamReader(p.getInputStream()));
			while (returnString == null || returnString.contentEquals("")) {
				returnString = in.readLine();
			}
		} catch (IOException e) {
			Log.e("executeTop", "error in getting first line of top");
			e.printStackTrace();
		} finally {
			try {
				in.close();
				p.destroy();
			} catch (IOException e) {
				Log.e("executeTop",
						"error in closing and destroying top process");
				e.printStackTrace();
			}
		}
		return returnString;
	}

	private long readBatteryLife(){	
//		batteryIntent = getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
//		int rawlevel = batteryIntent.getIntExtra("level", -1);
//		double scale = batteryIntent.getIntExtra("scale", -1);
//		double level = -1;
//		if (rawlevel >= 0 && scale > 0) {
//			level = rawlevel / scale;
//		}
//		return level;
//		BatteryManager mBatteryManager = (BatteryManager)getApplicationContext().getSystemService(Context.BATTERY_SERVICE);
//		long energy = mBatteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
//		long current = mBatteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
//		long life = current;
		return (long) 0;
	}

	private void writeToFile(String string){
		byte[] data = string.getBytes();

		if(fos!=null){
			try {              
				Log.i(TAG, "WRITING_CPU");
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
	class ClientThread implements Runnable {

		@Override
		public void run() {
			if (SERVER_IP!=""){

				try {
					InetAddress serverAddr = InetAddress.getByName(SERVER_IP);
					socket = new Socket(serverAddr, SERVERPORT);
					out = new PrintWriter(new BufferedWriter(
							new OutputStreamWriter(socket.getOutputStream())),
							true);
				} catch (UnknownHostException e1) {
					e1.printStackTrace();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			} else {
				Log.e("executeTop", "IP is null!!!!!!!!!!!!");

			}
		}
	}
}

