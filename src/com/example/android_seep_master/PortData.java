package com.example.android_seep_master;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class PortData implements Parcelable{

	public static final Creator<PortData> CREATOR = new Creator<PortData>() {
		public PortData createFromParcel(Parcel source) {
			return new PortData(source);
		}

		@Override
		public PortData[] newArray(int size) {
			return new PortData[size];
		}
		
	};
	
	private int port1;
	private int port2;
	private int port3;
	
	public PortData(int port1, int port2, int port3) {
		this.port1 = port1;
		this.port2 = port2;
		this.port3 = port3;
	}
	
	private PortData(Parcel source) {
		port1 = source.readInt();
		port2 = source.readInt();
		port3 = source.readInt();
	}
	
	public int getPort1() {
		return port1;
	}
	
	public int getPort2() {
		return port2;
	}
	
	public int getPort3() {
		return port3;
	}

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(port1);
		dest.writeInt(port2);
		dest.writeInt(port3);
	}

}
