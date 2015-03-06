package uk.ac.imperial.lsds.seep.infrastructure.master;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;

import android.os.AsyncTask;

public class LocalIPAddressTask extends AsyncTask<Void, Void, String> {

    @Override
    protected String doInBackground(Void... passing) {
			String ip = "";
            StringBuilder IFCONFIG=new StringBuilder();
            try {
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
			        NetworkInterface intf = en.nextElement();
			        for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
			                InetAddress inetAddress = enumIpAddr.nextElement();
			                if (!inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress() && inetAddress.isSiteLocalAddress()) {
			                        IFCONFIG.append(inetAddress.getHostAddress().toString()+"\n");
			                }

			        }
			}

			String[] ips = IFCONFIG.toString().split("\n");

			ip = ips[0];
            } catch (SocketException ex){
            	ex.printStackTrace();
            }
          
			return ip;
    }
}
           