package uk.ac.imperial.lsds.seep.comm.routing;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Random;

import com.example.query.Sink;

import android.util.Log;

public class RoutingSchemes implements Serializable{
	private static final long serialVersionUID = 1L;

	public int numDownstreams;
	public int currentIndex;
	private HashMap<Integer, Integer> scoreMap = new HashMap<Integer, Integer>();
	private double decisionTree[];
	private double candidatesDecisionTree[];
	private int scoreSum, candidatesSum;
	private ArrayList<Integer> candidates = new ArrayList<Integer>();
	private double[] totalDelays;
	private double[] processingDelays;
	private ComputeEquilibrium ce = new ComputeEquilibrium();

	int LAMBDA = 10;
	int routingScheme;

	public RoutingSchemes(int numDownstreams, int routingScheme){
		this.numDownstreams = numDownstreams;
		currentIndex = 0;
		this.routingScheme = routingScheme;
		scoreSum = 0;
		candidatesSum = 0;
		totalDelays = new double[numDownstreams];
		processingDelays = new double[numDownstreams];
		decisionTree = new double[numDownstreams];

		for (int i  = 0; i < numDownstreams; i++){
			scoreMap.put(i, 0);//initial score is zero for each downstream 
		}
	}

	public int getIndex(){
		if (numDownstreams > 0){
			switch (routingScheme) {
			case 0:
				return getRoundRobinIndex();
			case 1: 
				return getLoadBalancedIndex();
			case 2: 
				return getMinimalIndex();
			case 4:
				return getMinimalDelayWithQueueIndex();
			}
		} 
		return 0;
	}

	public int getRoundRobinIndex(){
		int result = currentIndex % numDownstreams;
		currentIndex ++ ;
		return result;
	}

	public int getLoadBalancedIndex(){
		int result = 0;
		if(scoreSum == 0){//No score is received... round robin
			result = getRoundRobinIndex();
		} else{
			result = toss();	
		}
		return result;
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
	}

	public int toss(){
		Random rand = new Random();
		double randomNum = rand.nextDouble();
		int tmpIndex = 0;
		for (int i = 0; i < numDownstreams; i++){
			double test = randomNum - decisionTree[i];
			if (test <= 0){
				break;
			} else {
				tmpIndex++;
			}
		}
		return tmpIndex;	
	}

	public int getMinimalIndex(){
		int result = 0;
		if(candidates.size()>0){//if candidate list is initialized
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

	public void updateCandidatesDecisionTree(){
		candidatesSum = 0;
		for (int i = 0; i < candidates.size(); i++){
			candidatesSum = candidatesSum + scoreMap.get(candidates.get(i));
		}
		if (candidatesSum > 0){
			candidatesDecisionTree = new double[candidates.size()];
			for (int i = 0; i < candidates.size(); i++){
				candidatesDecisionTree[i] = (double)(scoreMap.get(candidates.get(i)))/(double)candidatesSum;
			}
			for (int i = 1; i < candidatesDecisionTree.length; i++){
				candidatesDecisionTree[i] = candidatesDecisionTree[i] + candidatesDecisionTree[i-1];
			}
		}
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
		result = candidates.get(tmpIndex);
		return result;
	}

	public void selectCandidates(){
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

			int valid_index = 0;//index to the idx array	
			while ((valid_index < numDownstreams)){
				sum = sum - scoreMap.get(idx[valid_index]);
				if (sum > LAMBDA){
					valid_index ++;//skip everything that is small and doesn't really affect the final sum 
				} else {
					break;
				}
			}

			candidates.clear();

			for (int i = valid_index; i < numDownstreams; i++){
				candidates.add(idx[i]);
			}//add the rest to the candidates
		}

		//		String toPrint = "Candidates are: ";
		//		if (candidates.size()>0){
		//			for (int i = 0; i < candidates.size(); i++){
		//				toPrint = toPrint + candidates.get(i) + "(" + scoreMap.get(candidates.get(i))+")" + " ";
		//			}
		//			toPrint = toPrint + "\n";
		//			LOG.info(toPrint);		
		//		}
	}

	public int getMinimalDelayIndex(){
		int result = 0;
		double decisionTree[] = ce.getProbabilities(totalDelays, processingDelays, LAMBDA, numDownstreams);
		for (int i = 1; i < decisionTree.length; i++){
			decisionTree[i] = decisionTree[i] + decisionTree[i-1];
		}
		Random rand = new Random();
		double randomNum = rand.nextDouble();
		int tmpIndex = 0;
		for (int i = 0; i < numDownstreams; i++){
			double test = randomNum - decisionTree[i];
			if (test <= 0){
				break;
			} else {
				tmpIndex++;
			}
		}
		result = tmpIndex;
		return result;
	}

	public int getMinimalDelayWithQueueIndex(){
		int result = 0;
		if(scoreSum == 0){//No score is received... round robin
			result = getRoundRobinIndex();
		} else{
			result = toss();	
		}

		return result;
	}

	public void updateScoreMap(int index, int score){
		scoreMap.put(index, score);
		updateDecisionTree();
		//			Log.e("Decision Tree is", Arrays.toString(decisionTree));
		if (this.routingScheme == 2){
			selectCandidates();
			updateCandidatesDecisionTree();
		} 
	}

	public void updateDelayMaps(int index, double processingDelay){
		if (totalDelays[index] <= 0) { 
			if (Sink.totalDelays!=null){
				int totalDelay = Sink.totalDelays.get(index+1);
				if (totalDelay != 0){
					totalDelays[index] = totalDelay;
					Log.i("","get delay map with index = "+(index+1));

				}
			}
		}
		processingDelays[index] = processingDelay;
		int score = 0;
		if (totalDelays[index] != 0)
			score = (int) (10000/(totalDelays[index]));
		updateScoreMap(index, score);
		//====== for print ======
		//		for (int i = 0; i < transmissionDelays.length; i++){
		//			Log.e("1", ""+transmissionDelays[i]);
		//			Log.e("2", ""+processingDelays[i]);
		//			Log.e("3", ""+scoreMap.get(i));
		//			Log.e(" ", "");
		//		}
	}
}