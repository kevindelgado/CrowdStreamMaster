/*******************************************************************************
 * Copyright (c) 2013 Imperial College London.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Raul Castro Fernandez - initial design and implementation
 ******************************************************************************/
package uk.ac.imperial.lsds.seep.comm;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.comm.serialization.ControlTuple;
import uk.ac.imperial.lsds.seep.infrastructure.NodeManager;
import uk.ac.imperial.lsds.seep.infrastructure.master.Infrastructure;
import uk.ac.imperial.lsds.seep.operator.OperatorStaticInformation;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.FieldSerializer;

public class RuntimeCommunicationTools {

	final private Logger LOG = LoggerFactory.getLogger(RuntimeCommunicationTools.class);

	private Map<Integer, Socket> uniqueSocket = new HashMap<Integer, Socket>();
	private Kryo k = null;


	public RuntimeCommunicationTools(){
		this.k = initializeKryo();
	}

	private Kryo initializeKryo(){
		k = new Kryo();
		FieldSerializer<?> serializer = new FieldSerializer<ControlTuple>(k, ControlTuple.class);
		k.register(ControlTuple.class, serializer);
		//	k.setAsmEnabled(true);
		return k;
	}


	public void sendControlMsg(OperatorStaticInformation loc, ControlTuple ct, int socketId) throws KryoException{
		SendControlMsgThread sendControlMsgThread = new SendControlMsgThread(loc,ct,socketId);
		Thread thread1 = new Thread(sendControlMsgThread);	
		thread1.start();
	}

	class SendControlMsgThread implements Runnable {
		OperatorStaticInformation loc;
		ControlTuple ct;
		int socketId;

		public SendControlMsgThread(OperatorStaticInformation loc, ControlTuple ct, int socketId){
			this.loc = loc;
			this.ct = ct;
			this.socketId = socketId;
		}

		@Override
		public void run() {
			// TODO Auto-generated method stub
			Socket connection = uniqueSocket.get(socketId);
			try{
				if(connection == null){
					connection = new Socket(loc.getMyNode().getIp(), loc.getInC());
					LOG.debug("-> BCU. New socket in sendControlMsg");
					uniqueSocket.put(socketId, connection);
				}
				Output output = new Output(connection.getOutputStream());
				Input input = new Input(connection.getInputStream());
				System.out.println("sendTo: "+connection.toString());
				k.writeObject(output, ct);
				/**Critical line in KRYO**/
				output.flush();	
				if (!input.eof()){
					//wait for application level ack
					ControlTuple ack = k.readObject(input, ControlTuple.class);
					//waiting for ack
				} else {//if no ack is received
					k.writeObject(output,ct);//try again
					output.flush();
				}
				//LOG.debug("-> controlMsg ACK");
//				output.close();
//				input.close();
			}
			catch(IOException io){
				LOG.error("-> Infrastructure. While sending Msg "+io.getMessage());
				io.printStackTrace();
				LOG.error("CONN: "+connection.toString());
			}
			
		}

	}

	public void sendControlMsgWithoutACK(OperatorStaticInformation loc, ControlTuple ct, int socketId){
		Socket connection = uniqueSocket.get(socketId);
		try{

			//Output output = new Output(connection.getOutputStream());

			connection = new Socket(loc.getMyNode().getIp(), loc.getInC());
			LOG.debug("-> BCU. New socket in sendControlMsg");
			uniqueSocket.put(socketId, connection);
			Output output = new Output(connection.getOutputStream());

			//			if(connection == null){
			//				connection = new Socket(loc.getMyNode().getIp(), loc.getInC());
			//				Infrastructure.nLogger.info("-> BCU. New socket in sendControlMsg");
			//				uniqueSocket.put(socketId, connection);
			//			}

			k.writeObject(output, ct);
			output.flush();
		}
		catch(IOException io){
			LOG.error("-> Infrastructure. While sending Msg "+io.getMessage());
			io.printStackTrace();
			LOG.error("CONN: "+connection.toString());
		}
	}
}
