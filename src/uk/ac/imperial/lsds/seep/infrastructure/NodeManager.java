/*******************************************************************************
 * Copyright (c) 2013 Imperial College London.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Raul Castro Fernandez - initial design and implementation
 *     Martin Rouaux - Changes to support scale-in of operators
 ******************************************************************************/
package uk.ac.imperial.lsds.seep.infrastructure;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectStreamClass;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.AsyncTask;
import uk.ac.imperial.lsds.seep.GLOBALS;
import uk.ac.imperial.lsds.seep.comm.NodeManagerCommunication;
import uk.ac.imperial.lsds.seep.exception.ValuedException;
import uk.ac.imperial.lsds.seep.infrastructure.dynamiccodedeployer.ExtendedObjectInputStream;
import uk.ac.imperial.lsds.seep.infrastructure.dynamiccodedeployer.RuntimeClassLoader;
import uk.ac.imperial.lsds.seep.infrastructure.master.Infrastructure;
import uk.ac.imperial.lsds.seep.infrastructure.monitor.comm.serialization.MetricsTuple;
import uk.ac.imperial.lsds.seep.infrastructure.monitor.slave.MonitorSlave;
import uk.ac.imperial.lsds.seep.infrastructure.monitor.slave.MonitorSlaveFactory;
import uk.ac.imperial.lsds.seep.operator.EndPoint;
import uk.ac.imperial.lsds.seep.operator.Operator;
import uk.ac.imperial.lsds.seep.operator.OperatorStaticInformation;
import uk.ac.imperial.lsds.seep.runtimeengine.CoreRE;
import uk.ac.imperial.lsds.seep.state.StateWrapper;

/**
 * NodeManager. This is the entity that controls the system info associated to a given node, for instance, the monitor of the node, and the 
 * operators that are within that node.
 */

public class NodeManager{

	final private Logger LOG = LoggerFactory.getLogger(NodeManager.class);

	private WorkerNodeDescription nodeDescr;
	private RuntimeClassLoader rcl = null;

	//Endpoint of the central node
	private int bindPort;
	private InetAddress bindAddr;
	//Bind port of this NodeManager
	private int ownPort;
	private NodeManagerCommunication bcu = new NodeManagerCommunication();

	static public boolean monitorOfSink = false;
	static public long clock = 0;
	static public MonitorSlave monitorSlave;
	static public int second;
	static public double throughput;

	private Thread monitorT = null;

	public NodeManager(int bindPort, InetAddress bindAddr, int ownPort) {
		this.bindPort = bindPort;
		this.bindAddr = bindAddr;

		this.ownPort = ownPort;
		AsyncAction asyncAction = new AsyncAction();
		String localAddr;
		try {
			localAddr = asyncAction.execute().get();
			nodeDescr = new WorkerNodeDescription(localAddr, ownPort);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		rcl = new RuntimeClassLoader(new URL[0], this.getClass().getClassLoader());
	}


	class AsyncAction extends AsyncTask<String, Void, String> {
		protected String doInBackground(String... args) { 
			try {
				//InetAddress localAddr = InetAddress.getLocalHost();
				StringBuilder IFCONFIG=new StringBuilder();
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
				String ip = "";				
//				if (ips.length > 1){
//					ip = ips[1];
//				} else
					ip = ips[0];
				return ip.toString();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
		}
	}

	/// \todo{the client-server model implemented here is crap, must be refactored}
	static public void setSystemStable(){
		MetricsTuple tuple = new MetricsTuple();
		tuple.setOperatorId(Infrastructure.RESET_SYSTEM_STABLE_TIME_OP_ID);
		monitorSlave.pushMetricsTuple(tuple);
	}

	public void init() {

		Thread thread1 = new Thread(new Runnable(){
			@Override
			public void run() {
				// Get unique identifier for this node
				int nodeId = nodeDescr.getNodeId();
				//Initialize node engine ( CoreRE + ProcessingUnit )
				CoreRE core = new CoreRE(nodeDescr, rcl);

				//Local variables
				ServerSocket serverSocket = null;
				PrintWriter out = null;
				ExtendedObjectInputStream ois = null;
				//	ObjectStreamClass osc = null;
				Object o = null;
				boolean listen = true;
				try{
					serverSocket = new ServerSocket(ownPort);
					LOG.info("Waiting for incoming requests on port: {}", ownPort);
					Socket clientSocket = null;
					//Send bootstrap information
					bcu.sendBootstrapInformation(bindPort, bindAddr, ownPort);
					while(listen){
						//Accept incoming connections
						clientSocket = serverSocket.accept();
						//Establish output stream
						out = new PrintWriter(clientSocket.getOutputStream(), true);
						//Establish input stream, which receives serialized objects
						ois = new ExtendedObjectInputStream(clientSocket.getInputStream(), rcl);
						//Read the serialized object sent.
						//	osc = ois.readClassDescriptor();

						//Lazy load of the required class in case is an operator

						//	if(!(osc.getName().equals("java.lang.String")) && !(osc.getName().equals("java.lang.Integer")) && 
						//			!(osc.getName().equals("java.util.ArrayList"))){
						//		LOG.info("-> Received Unknown Class -> {} <- Using custom class loader to resolve it", osc.getName());
						//rcl.loadClass(osc.getName());
						//		Class a = NodeManager.class.getClassLoader().loadClass(osc.getName());
						o = null;
						o = ois.readObject();
						if(o instanceof Operator){
							LOG.info("-> OPERATOR resolved, OP-ID: {}", ((Operator)o).getOperatorId());
							// Initialize monitor slave, start thread, we do it at
							// this stage because we need to know the node is running an operator
							int operatorId = ((Operator)o).getOperatorId();

							if (GLOBALS.valueFor("enableMonitor").equals("true")){
								MonitorSlaveFactory factory = new MonitorSlaveFactory(operatorId);
								monitorSlave = factory.create();

								monitorT = new Thread(monitorSlave);
								monitorT.start();

								LOG.info("-> Node Monitor running for operatorId={}", operatorId);
							}
							
							core.pushOperator((Operator)o);

							out.println("ack");
							out.flush();

						}
						else if (o instanceof StateWrapper){
							LOG.info("-> STATE resolved, Class: {}", o.getClass().getName());
							out.println("ack");			
							out.flush();
						}
						else if(o instanceof ArrayList<?>){
							LOG.info("-> Start topology applied");

							core.pushStarTopology((ArrayList<EndPoint>)o);
							out.println("ack");
							out.flush();

						}
						else if(o instanceof Integer){
							LOG.info("-> SetOpReady: "+(Integer)o);

							core.setOpReady((Integer)o);

							out.println("ack");
							out.flush();

						}
						else if(o instanceof String){
							String tokens[] = ((String)o).split(" ");

							LOG.debug("Tokens received: " +tokens[0]);
							if(tokens[0].equals("STOP")){
								LOG.info("-> STOP Command");
								core.stopDataProcessing();

								// Stop the monitoring slave, this node is stopping
								if (monitorSlave != null) {
									monitorSlave.stop();
								}

								listen = false;

								LOG.info("Sending ACK message back to the master");
								out.println("ack");
								out.flush();

								//since listen=false now, finish the loop
								continue;
							}
							if(tokens[0].equals("SET-RUNTIME")) {
								LOG.info("-> SET-RUNTIME Command");
								core.setRuntime();
								out.println("ack");
							}
							if(tokens[0].equals("START")){
								LOG.info("-> START Command");
								//	core.startDataProcessingAsync();
								try {
									core.startDataProcessing();
								} catch (ValuedException e) {
									// TODO Auto-generated catch block
//									e.printStackTrace();
									LOG.error("ValuedException happened... Handling...");
									core.stopDataProcessing();
								}

								//We call the processData method on the source
								/// \todo {Is START used? is necessary to answer with ack? why is this not using startOperator?}
								out.println("ack");
							}
							if(tokens[0].equals("CLOCK")){
								LOG.info("-> CLOCK Command");
								NodeManager.clock = System.currentTimeMillis();
								out.println("ack");
							}
						}

						o = null;
						ois.close();
						out.close();
						clientSocket.close();
					}

					LOG.info("Waiting before stopping manager and terminating this process");
					try {
						Thread.sleep(5000);
					} catch (InterruptedException ex) {
						LOG.error("Unable to wait for 5 seconds");
					}

					serverSocket.close();
					LOG.info("ServerSocket closed.");
					System.exit(0);
				}
				//For now send nack, probably this is not the best option...
				catch(IOException io){
					System.out.println("IOException: "+io.getMessage());
					io.printStackTrace();
				}
				catch(IllegalThreadStateException itse){
					System.out.println("IllegalThreadStateException, no problem, monitor thing");
					itse.printStackTrace();
				} 
				catch (SecurityException e) {
					e.printStackTrace();
				} 
				catch (IllegalArgumentException e) {
					e.printStackTrace();
				} 
				catch (ClassNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		});

		thread1.start(); 
	}

}

