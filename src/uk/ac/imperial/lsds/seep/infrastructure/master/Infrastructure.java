/*******************************************************************************
 * Copyright (c) 2013 Imperial College London.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Raul Castro Fernandez - initial design and implementation
 *     Martin Rouaux - Changes to support operator scale-in of operators
 ******************************************************************************/
package uk.ac.imperial.lsds.seep.infrastructure.master;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.GLOBALS;
import uk.ac.imperial.lsds.seep.api.QueryPlan;
import uk.ac.imperial.lsds.seep.api.ScaleOutIntentBean;
import uk.ac.imperial.lsds.seep.comm.ConnHandler;
import uk.ac.imperial.lsds.seep.comm.NodeManagerCommunication;
import uk.ac.imperial.lsds.seep.comm.RuntimeCommunicationTools;
import uk.ac.imperial.lsds.seep.comm.routing.Router;
import uk.ac.imperial.lsds.seep.comm.serialization.ControlTuple;
import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.comm.serialization.messages.BatchTuplePayload;
import uk.ac.imperial.lsds.seep.comm.serialization.messages.Payload;
import uk.ac.imperial.lsds.seep.comm.serialization.messages.TuplePayload;
import uk.ac.imperial.lsds.seep.comm.serialization.serializers.ArrayListSerializer;
import uk.ac.imperial.lsds.seep.elastic.ElasticInfrastructureUtils;
import uk.ac.imperial.lsds.seep.elastic.NodePoolEmptyException;
import uk.ac.imperial.lsds.seep.elastic.ParallelRecoveryException;
import uk.ac.imperial.lsds.seep.infrastructure.OperatorDeploymentException;
import uk.ac.imperial.lsds.seep.infrastructure.monitor.master.MonitorMaster;
import uk.ac.imperial.lsds.seep.infrastructure.monitor.master.MonitorMasterFactory;
import uk.ac.imperial.lsds.seep.operator.Connectable;
import uk.ac.imperial.lsds.seep.operator.EndPoint;
import uk.ac.imperial.lsds.seep.operator.InputDataIngestionMode;
import uk.ac.imperial.lsds.seep.operator.Operator;
import uk.ac.imperial.lsds.seep.operator.OperatorContext;
import uk.ac.imperial.lsds.seep.operator.OperatorStaticInformation;
import uk.ac.imperial.lsds.seep.operator.StatefulOperator;
import uk.ac.imperial.lsds.seep.operator.OperatorContext.PlacedOperator;
import uk.ac.imperial.lsds.seep.runtimeengine.DisposableCommunicationChannel;
import uk.ac.imperial.lsds.seep.state.StateWrapper;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import uk.ac.imperial.lsds.seep.infrastructure.monitor.comm.serialization.MetricsTuple;
import uk.ac.imperial.lsds.seep.infrastructure.monitor.master.MonitorMasterListener;
import uk.ac.imperial.lsds.seep.infrastructure.monitor.policy.PolicyRules;
import uk.ac.imperial.lsds.seep.runtimeengine.CoreRE;
import uk.ac.imperial.lsds.seep.runtimeengine.CoreRE.ControlTupleType;

/**
 * Infrastructure. This class is in charge of dealing with nodes, deployment and profiling of the system.
 */


public class Infrastructure {

	final private Logger LOG = LoggerFactory.getLogger(Infrastructure.class);

	int value = 250;
	static public MasterStatisticsHandler msh = new MasterStatisticsHandler();

	private int baseId = 50;

	private Deque<Node> nodeStack = new ArrayDeque<Node>();
	private Deque<Node> nodeStackLocal = new ArrayDeque<Node>();

	private int numberRunningMachines = 0;

	private boolean systemIsRunning = false;
	private String pathToQueryDefinition = null;

	///\todo{Put this in a map{query->structure} and refer back to it properly}
	private ArrayList<Operator> ops = new ArrayList<Operator>();
	// States of the query
	private ArrayList<StateWrapper> states = new ArrayList<StateWrapper>();
	//public Map<Integer,QuerySpecificationI> elements = new HashMap<Integer, QuerySpecificationI>();
	public Map<Integer,Connectable> elements = new HashMap<Integer, Connectable>();
	//More than one source is supported
	//	private ArrayList<Operator> src = new ArrayList<Operator>();
	private Operator src;
	private Operator snk;
	//Mapping of nodeId-operator
	private Map<Integer, Operator> queryToNodesMapping = new HashMap<Integer, Operator>();
	//map with star topology information
	private ArrayList<EndPoint> starTopology = new ArrayList<EndPoint>();

	private RuntimeCommunicationTools rct = new RuntimeCommunicationTools();
	private NodeManagerCommunication bcu = new NodeManagerCommunication();
	private ElasticInfrastructureUtils eiu;

	private ManagerWorker manager = null;
	private MonitorMaster monitorMaster = null;
	private int port;

	// Scaling policy rules. These are needed by the MonitorMaster instance.
	private PolicyRules policyRules;

	public static int RESET_SYSTEM_STABLE_TIME_OP_ID = -666;

	public Infrastructure(int listeningPort) {
		this.port = listeningPort;
	}

	public boolean isSystemRunning(){
		return systemIsRunning;
	}

	public ArrayList<EndPoint> getStarTopology(){
		return starTopology;
	}

	public void addSource(Operator op){
		//this.src.add(op);
		this.src = op;
	}

	/** 
	 * For now, the query plan is directly submitted to the infrastructure. to support multi-query, first step is to have a map with the queries, 
	 * and then, for the below methods, indicate the query id that needs to be accessed.
	 **/
	public void loadQuery(QueryPlan qp, int BaseInC, int BaseInD) {
		// We can only start the monitor master process at this point because
		// we need to know the scaling rules in advance. These are only accessible
		// through the QueryPlan.
		if (GLOBALS.valueFor("enableMonitor").equals("true")){
			LOG.debug("-> MonitorMaster running");
			MonitorMasterFactory factory = new MonitorMasterFactory(this, qp.getPolicyRules());
			monitorMaster = factory.create();

			// We need a listener to reset the system stable time
			monitorMaster.addListener(new MonitorMasterListener() {

				@Override
				public int getOperatorId() {
					return RESET_SYSTEM_STABLE_TIME_OP_ID;
				}

				@Override
				public void onTupleReceived(MetricsTuple tuple) {
					Infrastructure.msh.setSystemStableTime(System.currentTimeMillis());
				}
			});

			Thread monitorManagerT = new Thread(monitorMaster);
			monitorManagerT.start();
		}

		ops = qp.getOps();
		states = qp.getStates();
		elements = qp.getElements();
		src = qp.getSrc();
		snk = qp.getSnk();

		///\todo{log what is going on here}
		queryToNodesMapping = qp.getMapOperatorToNode();
		configureRouterStatically();

		for(Operator op : ops){
			// Never will be empty, as there are no sources here (so all operators will have at least one upstream
			makeDataIngestionModeLocalToOp(op);
		}
		// Then we do the inversion with sink, since this also has upstream operators.
		makeDataIngestionModeLocalToOp(snk);

		ArrayList<ScaleOutIntentBean> soib = new ArrayList<ScaleOutIntentBean>();
		if(!qp.getScaleOutIntents().isEmpty()){
			LOG.debug("-> Manual static scale out");
			soib = eiu.staticInstantiateNewReplicaOperator(qp.getScaleOutIntents(), qp, BaseInC, BaseInD);
		}
		// The default and preferred option, used
		else if (!qp.getPartitionRequirements().isEmpty()){
			LOG.debug("-> Automatic static scale out");
			soib = eiu.staticInstantiationNewReplicaOperators(qp, BaseInC, BaseInD);
		}
		// After everything is set up, then we scale out ops
		eiu.executeStaticScaleOutFromIntent(soib);
	}

	private void makeDataIngestionModeLocalToOp(Operator op){
		// Never will be empty, as there are no sources here (so all operators will have at least one upstream
		for(Entry<Integer, InputDataIngestionMode> entry : op.getInputDataIngestionModeMap().entrySet()){
			for(Operator upstream : ops){
				if(upstream.getOperatorId() == entry.getKey()){
					LOG.info("-> Op: {} consume from Op: {} with {}",upstream.getOperatorId(), op.getOperatorId(), entry.getValue());
					// Use opContext to make an operator understand how it consumes data from its upstream
					upstream.getOpContext().setInputDataIngestionModePerUpstream(op.getOperatorId(), entry.getValue());
				}
			}
		}
	}

	private boolean checkReplicaOperator(Operator op, int opId){
		if(op.getOpContext().getOriginalUpstreamFromOpId(opId) != opId){
			return false;
		}
		return true;
	}

	public void configureRouterStatically(){
		for(Operator op: ops){
			LOG.info("-> Configuring Routing for OP {} ...", op.getOperatorId());
			boolean requiresLogicalRouting = op.getOpContext().doesRequireLogicalRouting();
			HashMap<Integer, ArrayList<Integer>> routeInfo = op.getOpContext().getRouteInfo();
			Router r = new Router(requiresLogicalRouting, routeInfo);
			// Configure routing implementations of the operator
			ArrayList<Operator> downstream = new ArrayList<Operator>();
			for(Integer i : op.getOpContext().getOriginalDownstream()){
				downstream.add(this.getOperatorById(i));
			}
			r.configureRoutingImpl(op.getOpContext(), downstream);
			op.setRouter(r);
			LOG.info("Configuring Routing for OP {} ...DONE", op.getOperatorId());
		}
	}

	public void setEiu(ElasticInfrastructureUtils eiu){
		this.eiu = eiu;
	}

	public void setPathToQueryDefinition(String pathToQueryDefinition){
		this.pathToQueryDefinition = pathToQueryDefinition;
	}

	public String getPathToQueryDefinition(){
		return pathToQueryDefinition;
	}
	//
	//	public MonitorMaster getMonitorMaster(){
	//		return monitorMaster;
	//	}

	public ArrayList<Operator> getOps() {
		return ops;
	}

	//	public Map<Integer, QuerySpecificationI> getElements() {
	//		return elements;
	//	}

	public Map<Integer, Connectable> getElements() {
		return elements;
	}

	public int getNodePoolSize(){
		return nodeStack.size();
	}

	public int getNodePoolLocalSize(){
		return nodeStackLocal.size();
	}

	public int getNumberRunningMachines(){
		return numberRunningMachines;
	}

	public RuntimeCommunicationTools getRCT() {
		return rct;
	}

	public NodeManagerCommunication getBCU(){
		return bcu;
	}

	public ElasticInfrastructureUtils getEiu() {
		return eiu;
	}

	public synchronized int getBaseId() {
		return baseId;
	}

	public void addNode(Node n) throws NodePoolEmptyException {
		nodeStack.push(n);
		LOG.debug("-> New Node: {}", n);
		LOG.debug("-> Num nodes: {}", getNodePoolSize());
		/* spica@110514 */
		if (systemIsRunning){
			int opId = 1;
			n = getNodeFromPool();
			Random rand = new Random();
			int randomNum = rand.nextInt((100 - 20) + 1) + 20;
			int newOpId = randomNum;
			try {
				eiu.scaleOutOperator(opId, newOpId, n);
				Thread.sleep(5000);
				eiu.scaleOutOperator1();
				Thread.sleep(5000);
				eiu.scaleOutOperator2(opId, newOpId);
			} catch (SocketException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public void addNodeLocal(Node n) {
		nodeStackLocal.push(n);
		LOG.debug("-> New Node: {}", n);
		LOG.debug("-> Num nodes: {}", getNodePoolSize());
	}

	public void updateContextLocations(Operator o) {
		for (Connectable op: elements.values()) {
			if (op!=o){
				setDownstreamLocationFromPotentialDownstream(o, op);
				setUpstreamLocationFromPotentialUpstream(o, op);
			}
		}
	}

	private void setDownstreamLocationFromPotentialDownstream(Connectable target, Connectable downstream) {
		for (PlacedOperator op: downstream.getOpContext().upstreams) {
			if (op.opID() == target.getOperatorId()) {
				target.getOpContext().setDownstreamOperatorStaticInformation(downstream.getOperatorId(), downstream.getOpContext().getOperatorStaticInformation());
			}
		}
	}

	private void setUpstreamLocationFromPotentialUpstream(Connectable target, Connectable upstream) {
		for (PlacedOperator op: upstream.getOpContext().downstreams) {
			if (op.opID() == target.getOperatorId()) {
				target.getOpContext().setUpstreamOperatorStaticInformation(upstream.getOperatorId(), upstream.getOpContext().getOperatorStaticInformation());
			}
		}
	}

	/// \todo {Any thread that it is started should be stopped somehow}
	public void startInfrastructure(){
		LOG.debug("-> ManagerWorker running");
		manager = new ManagerWorker(this, port);
		Thread centralManagerT = new Thread(manager, "managerWorkerT");
		centralManagerT.start();
	}

	public void localMapPhysicalOperatorsToNodes(int BaseInC, int BaseInD) {
		//	Finally get the mapping for this query and assign real nodes
		for(Entry<Integer, Operator> e : queryToNodesMapping.entrySet()){
			Node a = null;
			try {
				if(e.getValue().getOperatorId()==0 || e.getValue().getOperatorId()==100){//If source or sink
					a = getNodeFromLocalPool();
				} else {//for processing operators, prefer remote workers
					if (!nodeStack.isEmpty())
						a = getNodeFromPool();
					else if (!nodeStackLocal.isEmpty()) 
						a = getNodeFromLocalPool();
				}
			} catch (NodePoolEmptyException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			LOG.info("-> Mapping OP: {} to Node: {}", e.getValue().getOperatorId(), a);
			placeNew(e.getValue(), a, BaseInC, BaseInD);
		}
		LOG.info("-> All operators have been mapped");
		for(Operator o : queryToNodesMapping.values()){
			LOG.debug("OP: {}, CONF: {}", o.getOperatorId(), o);
		}
	}

	public void createInitialStarTopology(){
		// We build the initialStarTopology
		for(Operator op : ops){
			// sources and sinks are not part of the starTopology
			if(!(op.getOpContext().isSink()) && !(op.getOpContext().isSource())){
				int opId = op.getOperatorId();
				InetAddress ip = op.getOpContext().getOperatorStaticInformation().getMyNode().getIp();
				DisposableCommunicationChannel oscc = new DisposableCommunicationChannel(opId, ip);
				starTopology.add(oscc);
			}
		}
		LOG.debug("Initial StarTopology Size: {}",starTopology.size());
		for(EndPoint ep : starTopology){
			LOG.debug("Op: {} IP: {}", ep.getOperatorId(), ((DisposableCommunicationChannel)ep).getIp().toString());
		}
	}

	public void addNodeToStarTopology(int opId, InetAddress ip){
		DisposableCommunicationChannel dcc = new DisposableCommunicationChannel(opId, ip);
		starTopology.add(dcc);
	}

	public void removeNodeFromStarTopology(int opId){
		for(int i = 0; i<starTopology.size(); i++){
			EndPoint ep = starTopology.get(i);
			if(ep.getOperatorId() == opId){
				starTopology.remove(i);
			}
		}
	}

	public void broadcastState(StateWrapper s){
		for(Operator op: ops){
			Node node = op.getOpContext().getOperatorStaticInformation().getMyNode();
			bcu.sendObject(node, s);
		}
	}

	public void broadcastState(Operator op){
		for(StateWrapper s : states){
			Node node = op.getOpContext().getOperatorStaticInformation().getMyNode();
			bcu.sendObject(node, s);
		}
	}

	public void deployQuery0() {
		LOG.info("-> Deploying query...");
		//First broadcast the information regarding the initialStarTopology
		broadcastStarTopology();

	}

	public void deployQuery1() throws OperatorDeploymentException{

		Thread thread = new Thread()
		{
			@Override
			public void run() {
				//Deploy operators (push operators to nodes)
				for(Operator op: ops){
					//Establish the connection with the specified address
					LOG.info("-> Deploying OP: {}", op.getOperatorId());
					remoteOperatorInstantiation(op);
				}

			}
		};

		thread.start();

	}

	public void deployQuery2() throws OperatorDeploymentException{
		//Once all operators have been pushed to the nodes, we say that those are ready to run
		Thread thread = new Thread()
		{
			@Override
			public void run() {
				for(Operator op: ops){
					LOG.info("-> Configuring OP: {}", op.getOperatorId());
					init(op);
				}
			}
		};

		thread.start();

		//Broadcast the registered states to all the worker nodes, so that these can register the classes in the custom class loader
		for(StateWrapper s : states){
			//Send every state to all the worker nodes
			broadcastState(s);
			LOG.info("-> Broadcasting state {} to nodes", s);
		}
	}


	public void deployQuery3(){
		for (Operator op:ops){
			initRuntime(op);
		}
	}

	public void firstScale() throws NodePoolEmptyException{
		while(!nodeStack.isEmpty()){//after deployment, there is still cahance to scale
			Node n = getNodeFromPool();
			int opId = 1;
			Random rand = new Random();
			int randomNum = rand.nextInt((99 - 20) + 1) + 20;
			int newOpId = randomNum;
			try {
				eiu.scaleOutOperator(opId, newOpId, n);
				Thread.sleep(3000);
				eiu.scaleOutOperator1();
				Thread.sleep(3000);
				eiu.scaleOutOperator2(opId, newOpId);
			} catch (SocketException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	public void plotRoutingMapFull(){
		for (Operator op : ops){
			LOG.info("-> DownstreamImpl of OP: {}", op.getOperatorId()+"is null:"+
					op.getRouter().isDownstreamRoutingImplNull());
		}
	}

	public void reDeploy(Node n){

		System.out.println("REDEPLOY-operators with ip: "+n.toString());

		//Redeploy operators
		for(Connectable op: ops){
			//Loop through the operators, if someone has the same ip, redeploy
			if(op.getOpContext().getOperatorStaticInformation().getMyNode().equals(n)){
				LOG.debug("-> Redeploy OP: {}", op.getOperatorId());
				bcu.sendObject(n, op);
			}
		}
		for(Connectable op: ops){
			//Loop through the operators, if someone has the same ip, reconfigure
			if(op.getOpContext().getOperatorStaticInformation().getMyNode().equals(n)){
				LOG.debug("-> Reconfigure OP: ", op.getOperatorId());
				bcu.sendObject(n, new Integer ((op).getOperatorId()));
			}
		}
	}

	public void failure(int opId){
		// create a controltuple with a streamstate, target opid
		ControlTuple streamState = new ControlTuple().makeStreamState(opId);
		// Get access to starTopology and send the controltuple to all of them
		for(Operator op : ops){
			System.out.println("OP: "+op.getOperatorId());
			if(op.getOperatorId() != opId){
				if(!(op.getOpContext().isSink()) && !(op.getOpContext().isSource())){
					OperatorStaticInformation osi = op.getOpContext().getOperatorStaticInformation();
					System.out.println("sending stream state to : "+op.getOperatorId());
					rct.sendControlMsgWithoutACK(osi, streamState, op.getOperatorId());
				}
			}
		}
	}

	//	public void sendCode(Node n, byte[] data){
	//		bcu.sendFile(n, data);
	//	}
	//
	//	public void sendCode(Operator op, byte[] data){
	//		///\fixme{once there are more than one op per node this code will need to be fixed}
	//		Node node = op.getOpContext().getOperatorStaticInformation().getMyNode();
	//		LOG.debug("-> Sending CODE to Op: {} , Node: {}",op.getOperatorId(), node.toString());
	//		bcu.sendFile(node, data);
	//	}

	public boolean remoteOperatorInstantiation(Operator op) {
		Node node = op.getOpContext().getOperatorStaticInformation().getMyNode();
		LOG.debug("-> Remotely instantiating OP: ", op.getOperatorId());
		boolean result = bcu.sendObject(node, op);
		return result;
	}

	public void broadcastStarTopology(){
		for(Operator op : ops){
			if(!(op.getOpContext().isSink()) && !(op.getOpContext().isSource())){
				Node node = op.getOpContext().getOperatorStaticInformation().getMyNode();
				LOG.debug("-> Sending updated starTopology to OP: {}",op.getOperatorId());
				bcu.sendObject(node, starTopology);
			}
		}
	}

	public void init(Operator op) {
		Node node = op.getOpContext().getOperatorStaticInformation().getMyNode();
		LOG.info("-> Initializing OP: {}", op.getOperatorId());
		bcu.sendObject(node, op.getOperatorId());
	}

	public void initRuntime(Operator op){

		Node node = op.getOpContext().getOperatorStaticInformation().getMyNode();
		LOG.info("-> Starting RUNTIME of OP: {}", op.getOperatorId());
		bcu.sendObject(node, "SET-RUNTIME");
	}

	/// \test {some variables were bad, check if now is working}
	public void reMap(InetAddress oldIp, InetAddress newIp){
		OperatorContext opCtx = null;
		for(Connectable op: ops){
			opCtx = op.getOpContext();
			OperatorStaticInformation loc = opCtx.getOperatorStaticInformation();
			Node node = loc.getMyNode();
			if(node.getIp().equals(oldIp)){
				Node newNode = node.setIp(newIp);
				OperatorStaticInformation newLoc = loc.setNode(newNode);
				opCtx.setOperatorStaticInformation(newLoc);
			}
		}
	}

	/// \todo{remove boolean paralell recovery}
	/// parallel recovery was added to force the scale out of the failed operator before recovering it. it is necessary to change this and make it properly
	public void updateU_D(InetAddress oldIp, InetAddress newIp, boolean parallelRecovery){
		LOG.warn("-> Using sendControlMsg WITHOUT ACK");
		//Update operator information
		for(Connectable me : ops){
			//If there is an operator that was placed in the oldIP...
			if(me.getOpContext().getOperatorStaticInformation().getMyNode().getIp().equals(oldIp)){
				//We get its downstreams
				for(PlacedOperator downD : me.getOpContext().downstreams){
					//Now we change each downstream info (about me) and update its conn with me
					for(Connectable downstream: ops){
						if(downstream.getOperatorId() == downD.opID()){
							//To change info of this operator, locally first
							downstream.getOpContext().changeLocation(oldIp, newIp);

							ControlTuple ctb = new ControlTuple().makeReconfigure(me.getOperatorId(), "reconfigure_U", newIp.getHostAddress());

							LOG.debug("-> Updating Upstream OP: {}", downstream.getOperatorId());
							//bcu.sendControlMsg(downstream.getOpContext().getOperatorStaticInformation(), ctb.build(), downstream.getOperatorId());
							rct.sendControlMsgWithoutACK(downstream.getOpContext().getOperatorStaticInformation(), ctb, downstream.getOperatorId());
						}
					}
				}
				for(PlacedOperator upU: me.getOpContext().upstreams){
					for(Connectable upstream: ops){
						if(upstream.getOperatorId() == upU.opID()){
							//To change info of this operator, locally and remotely
							upstream.getOpContext().changeLocation(oldIp, newIp);
							ControlTuple ctb = null;
							//It needs to change its upstream conn
							if(!parallelRecovery){
								System.out.println("");
								ctb = new ControlTuple().makeReconfigure(me.getOperatorId(), "reconfigure_D", newIp.getHostAddress());
							}
							else{
								ctb = new ControlTuple().makeReconfigure(me.getOperatorId(), "just_reconfigure_D", newIp.getHostAddress());
							}
							LOG.debug("-> Updating Downstream OP: {}", upstream.getOperatorId());
							//bcu.sendControlMsg(upstream.getOpContext().getOperatorStaticInformation(), ctb.build(), upstream.getOperatorId());
							rct.sendControlMsgWithoutACK(upstream.getOpContext().getOperatorStaticInformation(), ctb, upstream.getOperatorId());
							//It needs to replay buffer
							String target = "";
							ControlTuple ctb2 = new ControlTuple().makeReconfigure(0, "replay", target);
						}	
					}
				}
			}
		}
	}	

	public void start() throws ESFTRuntimeException{
		//Send the messages to start the sources
		//for(Operator source : src){
		Operator source = src;
		String msg = "START "+source.getOperatorId();
		LOG.info("-> Starting source at source (OpId {}:{}), msg = {}", 
				source.getOpContext().getOperatorStaticInformation().getMyNode().getIp(),
				source.getOpContext().getOperatorStaticInformation().getMyNode().getPort(),
				msg);
		bcu.sendObject(source.getOpContext().getOperatorStaticInformation().getMyNode(), msg);
		//}
		//Start clock in sink.
		//bcu.sendObject(snk.getOpContext().getOperatorStaticInformation().getMyNode(), "CLOCK");
		LOG.info("SOURCES have been notified. System started.");
		systemIsRunning = true;
	}

	public void stopAllOperators(){

		Thread thread = new Thread()
		{
			@Override
			public void run() {
				for(Operator op: ops){
					stopOneOperator(op.getOperatorId());
				}
			}
		};

		thread.start();
		systemIsRunning = false;

	}
	public void stopOneOperator(int opId) {
		LOG.info("Stopping operator [{}]", opId);
		for(Operator op : ops) {
			if (op.getOperatorId() == opId) {
				String msg = "STOP " + op.getOperatorId();
				LOG.info("-> Stopping operator, msg = {}", msg);

				bcu.sendObject(op.getOpContext().getOperatorStaticInformation().getMyNode(), opId, msg);

				// Monitoring: scale-in feature

				// We first find the upstream operator for the chosen victim

				// TODO: support multiple upstreams. I only intend to support one 
				// upstream at the moment. Not difficult to support many.
				int size = op.getOpContext().getUpstreamSize();
				if (size > 1) {
					LOG.warn("More than one upstream operator detected for [{}]", opId);
				}

				if (size > 0){
					// Send SCALE_IN control tuple to the upstream of the victim
					PlacedOperator upstreamVictimOp = op.getOpContext().upstreams.iterator().next();
					if (upstreamVictimOp != null) {
						LOG.info("-> Stopping upstream operator {}, msg = {}", 
								upstreamVictimOp.opID(), ControlTupleType.SCALE_IN.toString());

						ControlTuple ct = new ControlTuple()
						.makeScaleIn(upstreamVictimOp.opID(), opId, upstreamVictimOp.isStateful());

						rct.sendControlMsg(upstreamVictimOp.location(), ct, upstreamVictimOp.opID());
					}
				}
			}
		}
	}

	public synchronized Node getNodeFromPool() throws NodePoolEmptyException{
		if(nodeStack.size() < 10){
			//nLogger.info("Instantiating EC2 images");
			//new Thread(new EC2Worker(this)).start();
		}
		numberRunningMachines++;
		if(nodeStack.isEmpty()){
			throw new NodePoolEmptyException("Node pool is empty, impossible to get more nodes");
		}
		Node i = nodeStack.getLast();
		nodeStack.removeLast();
		//return nodeStack.pop();
		return i;
	}

	public synchronized Node getNodeFromLocalPool() throws NodePoolEmptyException{
		numberRunningMachines++;
		if(nodeStackLocal.isEmpty()){
			throw new NodePoolEmptyException("Node pool local is empty, impossible to get more nodes");
		}
		Node i = nodeStackLocal.getLast();
		nodeStackLocal.removeLast();
		//return nodeStack.pop();
		return i;
	}

	public synchronized void incrementBaseId(){
		baseId++;
	}

	public void placeNew(Operator o, Node n, int BaseInC, int BaseInD) {

		int opId = o.getOperatorId();
		int originalOpId = o.getOriginalOpId();

		boolean isStatefull = (o.getOperatorCode() instanceof StatefulOperator) ? true : false;
		// Note that opId and originalOpId are the same value here, since placeNew places only original operators in the query

		OperatorStaticInformation l = new OperatorStaticInformation(opId, originalOpId, n, 
				BaseInC + opId, 
				BaseInD + opId, isStatefull);
		o.getOpContext().setOperatorStaticInformation(l);

		for (OperatorContext.PlacedOperator downDescr: o.getOpContext().downstreams) {
			int downID = downDescr.opID();
			Connectable downOp = elements.get(downID);
			downOp.getOpContext().setUpstreamOperatorStaticInformation(opId, l);
		}

		for (OperatorContext.PlacedOperator upDescr: o.getOpContext().upstreams) {
			int upID = upDescr.opID();
			Connectable upOp = elements.get(upID);
			upOp.getOpContext().setDownstreamOperatorStaticInformation(opId, l);
		}
	}

	public void placeNewParallelReplica(Operator originalOp, Operator o, Node n){
		int opId = o.getOperatorId();
		int originalOpId = originalOp.getOpContext().getOperatorStaticInformation().getOpId();
		boolean isStatefull = (o.getOperatorCode() instanceof StatefulOperator) ? true : false;

		OperatorStaticInformation l = new OperatorStaticInformation(opId, originalOpId, n, 
				Integer.parseInt(GLOBALS.valueFor("controlSocket")) + opId, 
				Integer.parseInt(GLOBALS.valueFor("dataSocket")) + opId, isStatefull);
		o.getOpContext().setOperatorStaticInformation(l);

		for (OperatorContext.PlacedOperator downDescr: o.getOpContext().downstreams) {
			int downID = downDescr.opID();
			Connectable downOp = elements.get(downID);
			downOp.getOpContext().setUpstreamOperatorStaticInformation(opId, l);
		}

		for (OperatorContext.PlacedOperator upDescr: o.getOpContext().upstreams) {
			int upID = upDescr.opID();
			Connectable upOp = elements.get(upID);
			upOp.getOpContext().setDownstreamOperatorStaticInformation(opId, l);
		}
	}

	public void deployConnection(String command, Connectable opToContact, Connectable opToAdd, String operatorType) {
		System.out.println("OPERATOR TYPE: "+operatorType);
		ControlTuple ct = null;
		String ip = null;
		//Some commands do not require opToAdd
		if(opToAdd != null){
			int opId = opToAdd.getOperatorId();
			ip = opToAdd.getOpContext().getOperatorStaticInformation().getMyNode().getIp().getHostAddress();
			int originalOpId = opToAdd.getOpContext().getOperatorStaticInformation().getOriginalOpId();
			int node_port = opToAdd.getOpContext().getOperatorStaticInformation().getMyNode().getPort();
			int in_c = opToAdd.getOpContext().getOperatorStaticInformation().getInC();
			int in_d = opToAdd.getOpContext().getOperatorStaticInformation().getInD();
			boolean operatorNature = opToAdd.getOpContext().getOperatorStaticInformation().isStatefull();
			ct = new ControlTuple().makeReconfigure(opId, originalOpId, command, ip, node_port, in_c, in_d, operatorNature, operatorType);
		}
		else{
			ct = new ControlTuple().makeReconfigure(0, command, ip);
		}
		rct.sendControlMsg(opToContact.getOpContext().getOperatorStaticInformation(), ct, opToContact.getOperatorId());
	}

	@Deprecated
	public void configureSourceRate(int numberEvents, int time){

		ControlTuple tuple = new ControlTuple().makeReconfigureSourceRate(numberEvents, "configureSourceRate", time);

		//		Main.eventR = numberEvents;
		//		Main.period = time;
		//for(Operator source : src){
		rct.sendControlMsg(src.getOpContext().getOperatorStaticInformation(), tuple, src.getOperatorId());
		//}
		rct.sendControlMsg(snk.getOpContext().getOperatorStaticInformation(), tuple, snk.getOperatorId());
	}

	public int getOpIdFromIp(InetAddress ip){
		int opId = -1;
		for(Operator op : ops){
			if(op.getOpContext().getOperatorStaticInformation().getMyNode().getIp().equals(ip)){
				opId = op.getOperatorId();
				return opId;
			}
		}
		return opId;
	}

	public int getNumDownstreams(int opId){
		for(Operator op : ops){
			if(op.getOperatorId() == opId){
				return op.getOpContext().downstreams.size();
			}
		}
		return -1;
	}

	public int getNumUpstreams(int opId){
		for(Operator op : ops){
			if(op.getOperatorId() == opId){
				return op.getOpContext().upstreams.size();
			}
		}
		return -1;
	}

	public void printCurrentInfrastructure(){
		System.out.println("##########################");
		System.out.println("INIT: printCurrentInfrastructure");
		System.out.println("Nodes registered in system:");
		System.out.println("  ");
		System.out.println();
		for(Node n : nodeStack){
			System.out.println(n);
		}
		System.out.println("  ");

		System.out.println("OPERATORS: ");
		for (Connectable op: ops) {
			System.out.println(op);
			System.out.println();
		}
		System.out.println("END: printCurrentInfrastructure");
		System.out.println("##########################");
	}

	public void saveResults() {
		ControlTuple tuple = new ControlTuple().makeReconfigureSingleCommand("saveResults");
		rct.sendControlMsg(snk.getOpContext().getOperatorStaticInformation(), tuple, snk.getOperatorId());
	}

	public void switchMechanisms(){
		ControlTuple tuple = new ControlTuple().makeReconfigureSingleCommand("deactivateMechanisms");
		for(Operator o : ops){
			rct.sendControlMsg(o.getOpContext().getOperatorStaticInformation(), tuple, o.getOperatorId());
		}
		//Send msg to src and snk
		//for(Operator source : src){
		rct.sendControlMsg(src.getOpContext().getOperatorStaticInformation(), tuple, src.getOperatorId());
		//}
		rct.sendControlMsg(snk.getOpContext().getOperatorStaticInformation(), tuple, snk.getOperatorId());
	}

	public String getOpType(int opId) {
		for(Operator op : ops){
			if(op.getOperatorId() == opId){
				return op.getClass().getName(); 
			}
		}
		return null;
	}

	public void parallelRecovery(String oldIp_txt) throws UnknownHostException{
		try {
			eiu.executeParallelRecovery(oldIp_txt);
		} 
		catch (NodePoolEmptyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		catch (ParallelRecoveryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void saveResultsSWC() {
		ControlTuple tuple = new ControlTuple().makeReconfigureSingleCommand("saveResults");
		Operator aux = null;
		for(Operator op : ops){
			if(op.getClass().getName().equals("seep.operator.collection.SmartWordCounter")){
				aux = op;
			}
		}
		rct.sendControlMsg(aux.getOpContext().getOperatorStaticInformation(), tuple, aux.getOperatorId());
	}

	public Operator getOperatorById(int opIdToParallelize) {
		for(Operator op : ops){
			if(op.getOperatorId() == opIdToParallelize){
				return op;
			}
		}
		return null;
	}

	public void parseFileForNetflix() {
		System.out.println("Parse file for Netflix...");
		File f = new File("data.txt");
		File o = new File("data.bin");

		Kryo k = new Kryo();
		k.register(ArrayList.class, new ArrayListSerializer());
		k.register(Payload.class);
		k.register(TuplePayload.class);
		k.register(BatchTuplePayload.class);
		k.setAsmEnabled(true);
		try {
			//OUT
			FileOutputStream fos = new FileOutputStream(o);
			Output output = new Output(fos);

			//IN
			FileReader fr = new FileReader(f);
			BufferedReader br = new BufferedReader(fr);
			String currentLine = null;

			//PARSE
			Map<String, Integer> mapper = new HashMap<String, Integer>();
			ArrayList<String> artList = new ArrayList<String>();
			artList.add("userId");
			artList.add("itemId");
			artList.add("rating");
			for(int i = 0; i<artList.size(); i++){
				System.out.println("MAP: "+artList.get(i));
				mapper.put(artList.get(i), i);
			}

			DataTuple dts = new DataTuple(mapper, new TuplePayload());
			int counter = 0;
			int total = 0;
			while((currentLine = br.readLine()) != null){

				counter++;
				if(counter == 10000){
					total += 10000;
					System.out.println("total: "+total);
					counter = 0;
				}

				String[] tokens = currentLine.split(",");
				//				dt.setUserId(Integer.parseInt(tokens[1]));
				//				dt.setItemId(Integer.parseInt(tokens[0]));
				//				dt.setRating(Integer.parseInt(tokens[2]));
				TuplePayload tp = new TuplePayload();
				//				tp.attrValues = new Payload(Integer.parseInt(tokens[1]), Integer.parseInt(tokens[0]), Integer.parseInt(tokens[2]));
				DataTuple dt = dts.newTuple(Integer.parseInt(tokens[1]), Integer.parseInt(tokens[0]), Integer.parseInt(tokens[2]));
				//				dt.setValues(Integer.parseInt(tokens[1]), Integer.parseInt(tokens[0]), Integer.parseInt(tokens[2]));

				k.writeObject(output, dt);
				//Flush the buffer to the stream
				output.flush();
			}
			fos.close();
			br.close();
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void addOperator(Operator o) {
		ops.add(o);
		elements.put(o.getOperatorId(), o);
		LOG.debug("Added new Operator to Infrastructure: {}", o.toString());
	}
}
