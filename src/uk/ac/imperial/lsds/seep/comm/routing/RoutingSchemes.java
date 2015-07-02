package uk.ac.imperial.lsds.seep.comm.routing;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.query.Sink;

import android.util.Log;

public class RoutingSchemes implements Serializable{
	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(RoutingSchemes.class);

	public int numDownstreams;
	public int currentIndex;
	private HashMap<Integer, Integer> scoreMap = new HashMap<Integer, Integer>();
	private double decisionTree[];
	private double candidatesDecisionTree[];
	private int scoreSum, candidatesSum;
	private ArrayList<Integer> candidates = new ArrayList<Integer>();
	private double[] processingDelays;

	int LAMBDA = 20;
	int routingScheme;

	public RoutingSchemes(int numDownstreams, int routingScheme){
		this.numDownstreams = numDownstreams;
		currentIndex = 0;
		this.routingScheme = routingScheme;
		scoreSum = 0;
		candidatesSum = 0;
		processingDelays = new double[numDownstreams];
		decisionTree = new double[numDownstreams];

		for (int i  = 0; i < numDownstreams; i++){
			scoreMap.put(i, 0);//initial score is zero for each downstream 
		}
	}

	public int getIndex(){
		if (numDownstreams > 1){
			switch (routingScheme) {
			case 0:
				return getRoundRobinIndex();
			case 1: 
				return getRoundRobinThroughputIndex();
			case 2:
				return getRoundRobinDelayIndex();
			case 3: 
				return getMinimalThroughputIndex();
			case 4:
				return getMinimalDelayIndex();

			}
		} 
		return 0;
	}

	public int getRoundRobinIndex(){
		int result = currentIndex % numDownstreams;
		currentIndex ++ ;
		return result;
	}

	public int getRoundRobinThroughputIndex(){
		int result = 0;
		if(scoreSum == 0){//No score is received... round robin
			result = getRoundRobinIndex();
		} else{
			result = toss();	
		}
		return result;
	}
	
	public int getRoundRobinDelayIndex(){
		return getRoundRobinThroughputIndex(); // the only difference is that the score map is updated differently
	}
	
	public int getMinimalThroughputIndex(){
		int result = 0;
		if(currentIndex >= 2*numDownstreams &&  candidates.size()>0){//run rr for the first two rounds to let the candidate list to be initialized
			if (candidatesSum == 0){
				result = getRoundRobinIndex();
			} else {
				result = tossCandidates();
			}
		} else {//default is round robin in all workers
			result = getRoundRobinIndex();
		}
		return result;
	}
	
	public int getMinimalDelayIndex(){
		return getMinimalThroughputIndex(); // the only difference is that the score map is updated differently
	}
	
	
	public int toss(){
		Random rand = new Random();
		double randomNum = rand.nextDouble();
		int tmpIndex = 0;
		for (int i = 0; i < numDownstreams; i++){
			double test = randomNum - decisionTree[i];//assuming that decisionTree is updated elsewhere
			if (test <= 0){
				break;
			} else {
				tmpIndex++;
			}
		}
		return tmpIndex;	
	}
	
	public int tossCandidates(){
		int result = 0;
		Random rand = new Random();
		double randomNum = rand.nextDouble();
		int tmpIndex = 0;
		for (int i = 0; i < candidates.size(); i++){
			double test = randomNum - candidatesDecisionTree[i];
			if (test <= 0){
				break;
			} else {
				tmpIndex++;
			}
		}
		if (tmpIndex == candidates.size())
			tmpIndex = tmpIndex - 1;
		result = candidates.get(tmpIndex);
		return result;
	}

	// =============== updating processes defined below ========================
	
	public Integer[] getRankByScore(){// rank the devices by scores from low to high
		final Integer[] idx = new Integer[numDownstreams];
		final int[] data = new int[numDownstreams];
		for (int i = 0; i < numDownstreams; i++){
			idx[i] = i;
			data[i] = scoreMap.get(i);
		}
		//sort the worker indices according to their scores
		Arrays.sort(idx, new Comparator<Integer>() {
			@Override
			public int compare(final Integer entry1, final Integer entry2) {
				if(data[entry1.intValue()] < data[entry2.intValue()]) {
					return -1;
				} else if(data[entry1] > data[entry2]) {
					return 1;
				} else {
					return 0;
				}
			}
		});
		
		return idx;
	}
	
	public void selectCandidatesByThroughput(){ 
		int sum = 0;
		for (int i = 0; i < numDownstreams; i++){
			sum = sum + scoreMap.get(i);
		}

		if (sum < LAMBDA){//summing up all workers still doesn't satisfy ideal arrival rate
			//round robin -- all workers are candidates
			candidates.clear();
			for (int i = 0; i < numDownstreams; i++){
				candidates.add(i);
			}
		} else {
			Integer[] idx = getRankByScore();// get devices ranks
			int valid_index = 0;//index to the idx array	
			while ((valid_index < numDownstreams)){
				sum = (int) (sum - 1000/processingDelays[idx[valid_index]]);
				if (sum > LAMBDA){
					valid_index ++;//skip devices whose throughput are small  
				} else {
					break;
				}
			}

			candidates.clear();
			for (int i = valid_index; i < numDownstreams; i++){
				candidates.add(idx[i]);
			}//add the rest devices (whose throughput are large) to the candidates
		}

//				String toPrint = "Candidates are: ";
//				if (candidates.size()>0){
//					for (int i = 0; i < candidates.size(); i++){
//						toPrint = toPrint + candidates.get(i) + "(" + scoreMap.get(candidates.get(i))+")" + " ";
//					}
//					toPrint = toPrint + "\n";
//					LOG.info(toPrint);		
//				}
	}
	
	
	public void updateDecisionTree(){
		scoreSum = 0;
		for (int i = 0; i < numDownstreams; i++){
			scoreSum = scoreSum + scoreMap.get(i);
		}
		for (int i = 0; i < numDownstreams; i++){
			decisionTree[i] = (double)(scoreMap.get(i))/(double)scoreSum;
		}
		for (int i = 1; i < decisionTree.length; i++){
			decisionTree[i] = decisionTree[i] + decisionTree[i-1];
		}
		String toPrint = "";
		for (int i = 0; i < decisionTree.length; i++){
			toPrint = toPrint + decisionTree[i] + " ";
		}
		toPrint = toPrint + "\n";
		LOG.info(toPrint);				
	}

	public void updateCandidatesDecisionTree(){
		candidatesSum = 0;
		try{
		for (int i = 0; i < candidates.size(); i++){
			if (scoreMap.get(candidates.get(i)) != null)
				candidatesSum = candidatesSum + scoreMap.get(candidates.get(i));
		}
		if (candidatesSum > 0){
			candidatesDecisionTree = new double[candidates.size()];
			for (int i = 0; i < candidatesDecisionTree.length; i++){
				candidatesDecisionTree[i] = (double)(scoreMap.get(candidates.get(i)))/(double)candidatesSum;
			}
			for (int i = 1; i < candidatesDecisionTree.length; i++){
				candidatesDecisionTree[i] = candidatesDecisionTree[i] + candidatesDecisionTree[i-1];
			}
		}
		}catch (IndexOutOfBoundsException ex){
			LOG.error("Index out of bound!");
			candidatesSum = 0;
		}
		catch (NullPointerException ex){
			LOG.error("Null pointer!");
			candidatesSum = 0;
		}

	}
	
    // receiving processingDelay information and update everything defined above
	public void updateProcessingDelays(int index, int processingDelay){
		//			Log.e("Decision Tree is", Arrays.toString(decisionTree));
		int score = 0;
		processingDelays[index] = processingDelay;
		if (this.routingScheme == 1 || this.routingScheme == 3 ){// rr-t || pr-t
			if (processingDelay != 0)
				score = 1000/processingDelay;
		}
		if (this.routingScheme == 2 || this.routingScheme == 4 ){// rr-l // pr-l
			score = getScoreFromTotalDelay(index); // use total-delay to compute score, 	
		}
		scoreMap.put(index, score);
		updateDecisionTree();
		if (this.routingScheme == 3 || this.routingScheme == 4 ){// update candidates for partial round robin methods
			selectCandidatesByThroughput();
			updateCandidatesDecisionTree();
		} 
	}

	
	public int getScoreFromTotalDelay(int index){
		int score = 0;
		if (Sink.totalDelays!=null){
			int totalDelay = Sink.totalDelays.get(index+1); // obtain total delay from sink node
			if (totalDelay > 0)
				score = (int) (2000/totalDelay) - 1; // used 2000 here because total delay might be big. This parameter is tunnable
			if (score < 0)
				score = 0;
		}
		LOG.info("Get Score From Total Delay:" + score);
		return score;
	}
}