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
package uk.ac.imperial.lsds.seep.infrastructure.master;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.minlog.Log;

import dalvik.system.DexClassLoader;

import android.os.Environment;

import uk.ac.imperial.lsds.seep.GLOBALS;
import uk.ac.imperial.lsds.seep.api.QueryPlan;
import uk.ac.imperial.lsds.seep.elastic.ElasticInfrastructureUtils;
import uk.ac.imperial.lsds.seep.elastic.NodePoolEmptyException;
import uk.ac.imperial.lsds.seep.infrastructure.OperatorDeploymentException;


public class MasterController {
	
	final private Logger LOG = LoggerFactory.getLogger(MasterController.class.getName());

	//MasterController must be a singleton
	private static final MasterController instance = new MasterController();
	
	private ClassLoader ucl = null;
	
    private MasterController() {}
 
    public static MasterController getInstance() {
        return instance;
    }
	
    private Infrastructure inf;
    ElasticInfrastructureUtils eiu;
	
	public void init(){
		LOG.debug("-> Initializing Master Controller...");
		inf = new Infrastructure(3500);
		eiu = new ElasticInfrastructureUtils(inf);
		inf.setEiu(eiu);
		inf.startInfrastructure();
		LOG.debug("-> Initializing Master Controller...DONE");
	}
	
	public void submitQuery(QueryPlan qp, int BaseInC, int BaseInD){
		LOG.info("-> Submitting query to the system...");
		inf.loadQuery(qp, BaseInC, BaseInD);
		LOG.info("-> Submitting query to the system...DONE");
	}
		
	public void startSystem(){
		try {
			inf.start();
		} catch (ESFTRuntimeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void stopOperators(){
		inf.stopAllOperators();
	}
	
	public QueryPlan executeComposeFromQuery(String definitionClass){
		Class<?> baseI = null;
		Object baseInstance = null;
		Method compose = null;
		QueryPlan qp = null;

		ucl = MasterController.class.getClassLoader();
//		eiu.setClassLoader(ucl);
		try {
			baseI = ucl.loadClass(definitionClass);
			baseInstance = (Object)baseI.newInstance();
			compose = baseI.getMethod("compose");
			qp = (QueryPlan) compose.invoke(baseInstance, (Object[])null);
		}
		catch (SecurityException e) {
			e.printStackTrace();
		} 
		catch (NoSuchMethodException e) {
			e.printStackTrace();
		} 
		catch (IllegalArgumentException e) {
			e.printStackTrace();
		} 
		catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		catch (InvocationTargetException e) {
			e.printStackTrace();
		} 
		catch (InstantiationException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		//Finally we return the queryPlan
		return qp;
	}
	
	
	
	public void deployQueryToNodes(int BaseInC, int BaseInD){
		LOG.info("-> Configuring and deploying query...");
		//First configure statically (local) the connections between operators
		inf.localMapPhysicalOperatorsToNodes( BaseInC,  BaseInD);
		inf.createInitialStarTopology();
		inf.deployQuery0();	
		LOG.info("-> Configuring and deploying query...DONE");
	}
	
	public void deployQueryToNodes1(){
		try {
			inf.deployQuery1();
		}
		catch (OperatorDeploymentException e) {
			// TODO Auto-generated catch block
			Log.error("-> Deploy Query failed!!");
			e.printStackTrace();
		}
	}
	
	public void deployQueryToNodes2(){
		try {
			inf.deployQuery2();
		}
		catch (OperatorDeploymentException e) {
			// TODO Auto-generated catch block
			Log.error("-> Deploy Query failed!!");
			e.printStackTrace();
		}
	}
	
	public void deployQueryToNodes3(){
		inf.deployQuery3();
	}
	
	public void plotRoutingMap(){
		inf.plotRoutingMapFull();
	}
	
	public void scaleIn(int opId){
		inf.stopOneOperator(opId);
	}
	
	public void firstScale() throws NodePoolEmptyException{
		inf.firstScale();
	}
	
	private String getUserInput(String msg) throws IOException{
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		System.out.println(msg);
		String option = br.readLine();
		return option;
	}
	
	public void startSystemOption(Infrastructure inf) throws IOException, ESFTRuntimeException{
		getUserInput("Press a button to start the source");
		
        //Start the source, and thus the stream processing system
		inf.start();
	}
	
	public void configureSourceRateOption(Infrastructure inf) throws IOException{
//		String option = getUserInput("Introduce number of events: ");
//		int numberEvents = Integer.parseInt(option);
//		option = getUserInput("Introduce time (ms): ");
//		int time = Integer.parseInt(option);
//		inf.configureSourceRate(numberEvents, time);
	}
	
	public boolean parallelizeOpManualOption(int opId, int newOpId) throws IOException{
//		String option = getUserInput("Enter operator ID (old): ");
//		int opId = Integer.parseInt(option);
//		option = getUserInput("Enter operator ID (new): ");
//		int newOpId = Integer.parseInt(option);
//		System.out.println("1= get node automatically");
//		System.out.println("2= get node manually, put new data");
//		option = getUserInput("");
//		int opt = Integer.parseInt(option);
		Node newNode = null;
//		switch (opt){
//			case 1:
				try {
					newNode = inf.getNodeFromPool();
				} 
				catch (NodePoolEmptyException e) {
//					 TODO Auto-generated catch block
					e.printStackTrace();
				}
//				break;
//			case 2:
//				option = getUserInput("Introduce IP: ");
//				InetAddress ip = InetAddress.getByName(option);
//				option = getUserInput("Introduce port: ");
//				int newPort = Integer.parseInt(option);
//				newNode = new Node(ip, newPort);
//				inf.addNode(newNode);
//				break;
//			default:
//		}
		if(newNode == null){
			System.out.println("NO NODES AVAILABLE. IMPOSSIBLE TO PARALLELIZE");
			return false;
		}
		boolean result = eiu.scaleOutOperator(opId, newOpId, newNode);
		return result;
	}
	
	public void parallelizeOpManualOption1() {
		eiu.scaleOutOperator1();
	}
	
	public void parallelizeOpManualOption2(int opId, int newOpId) {
		eiu.scaleOutOperator2(opId, newOpId);
	}
	
	public void consoleOutputMessage(){
		System.out.println("#############");
		System.out.println("USER Console, choose an option");
		System.out.println();
		System.out.println("0- Submit query to the System");
		System.out.println("1- Deploy query to Nodes");
		System.out.println("2- Start system");
//		System.out.println("3- Configure source rate");
		System.out.println("4- Parallelize Operator Manually");
		System.out.println("5- Stop system console (EXP)");
		System.out.println("6- Exit");
		System.out.println("10- Parse txt file to binary kryo");
	}
}
