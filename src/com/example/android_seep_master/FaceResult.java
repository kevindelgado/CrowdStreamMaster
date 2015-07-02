package com.example.android_seep_master;

import android.os.Parcel;
import android.os.Parcelable;

public final class FaceResult implements Parcelable {
	
	public static final Creator<FaceResult> CREATOR = new Creator<FaceResult>() {
		public FaceResult createFromParcel(Parcel source) {
			return new FaceResult(source);
		}

		@Override
		public FaceResult[] newArray(int size) {
			return new FaceResult[size];
		}
		
	};
	
	private String nameRecognized;
	private String outputFPS;
	
	public FaceResult(String name, String fps) {
		this.nameRecognized = name;
		this.outputFPS = fps;
	}
	
	private FaceResult(Parcel source) {
		nameRecognized = source.readString();
		outputFPS = source.readString();
	}
	
	public String getName() {
		return nameRecognized;
	}
	
	public String getFPS() {
		return outputFPS;
	}

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(nameRecognized);
		dest.writeString(outputFPS);
	}

}
