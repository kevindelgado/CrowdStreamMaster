package uk.ac.imperial.lsds.seep.comm.routing;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

public class ComputeEquilibrium implements Serializable {	
	private static final long serialVersionUID = 1L;

	
//	public static void main(String[] args){
//		int M = 2; // number of devices 
//		int LAMBDA = 20;
//		double D[] = {0.1, 0.2};
//		double S[] = {0.2, 0.1};
//		double[] P = getProbabilities(D, S, LAMBDA, M);
//		System.out.println("The final probabilities are: "+ Arrays.toString(P));
//	}
	
	public double[] getProbabilities(double[] D, double[] S, int LAMBDA, int M) {
		double minR = 10000;
		int[] bestX = null;
		double[] probabilities = new double[M];

		ArrayList<int[]> X = getPermutations(M);

		for (int i = 0; i < X.size(); i++){
			double[] tmpD = selectSubset(D, X.get(i));
			double[] tmpS = selectSubset(S, X.get(i));
			double[] P = computeProbabilities(tmpD, tmpS, LAMBDA, tmpD.length);
			if (P != null){
				double r = getDelay(tmpD, tmpS, P, LAMBDA);
				if (r != 0 && r < minR){
					minR = r;
					bestX = X.get(i);
				}
			}
		}

		for (int i = 0; i < M; i++){
			if (bestX[i] == 0)
				probabilities[i] = 0.0;
			else 
				probabilities[i] = (minR-D[i]-S[i]*S[i])/((S[i]*S[i])*(LAMBDA-1));
		}
		
		return probabilities;

	}

	public double[] selectSubset(double[] array, int[] x){
		ArrayList<Double> result = new ArrayList<Double>();
		for (int i = 0; i < x.length; i++){
			if (x[i] == 1){
				result.add(array[i]);
			}
		}
		double[] ret = new double[result.size()];
		for (int i=0; i < ret.length; i++)
		{
			ret[i] = result.get(i).doubleValue();
		}
		System.out.println("Subset is :::: "+Arrays.toString(ret));
		return ret;
	}

	public ArrayList<int[]> getPermutations(int m){
		ArrayList<int[]> result = new ArrayList<int[]>();
		int numPermutations = (int) Math.pow(2, m);
		String tmpString = "";
		for (int i = 0; i < numPermutations; i++){
			int[] tmpInt = new int[m];
			tmpString = Integer.toBinaryString(i);
			char[] tmpChar = tmpString.toCharArray();
			for(int j = 0; j < m; j++) {
				if (tmpChar.length-j-1 >= 0){
					tmpInt[m-j-1] = Integer.parseInt(String.valueOf(tmpString.charAt(tmpChar.length-j-1)));
				} else {
					tmpInt[m-j-1] = 0;
				}
			}
			result.add(tmpInt);
			System.out.println("Permutation :::" + Arrays.toString(tmpInt));
		}
		return result;
	}

	public double[] computeProbabilities(double[] D, double[] S, int LAMBDA, int M){
		double[] result = new double[D.length];
		double tmpResult1 = 0.0;
		double tmpResult2 = 0.0; 
		double s = 0.0;
		double d = 0.0;
		double m = 0.0;
		double p = 0.0;
		for (int i = 0; i < M; i++){
			s = S[i];
			d = D[i];
			tmpResult1 = tmpResult1 + 1/(s*s); 
			tmpResult2 = tmpResult2 + d/(s*s);
		}
		System.out.println("TmpResult1 is :::: "+tmpResult1);
		System.out.println("TmpResult2 is :::: "+tmpResult2);


		double r = (LAMBDA - 1 + M + tmpResult2)/tmpResult1;
		System.out.println("Delay is :::: "+r);

		if (M == 1){
			result[0] = 1;
		} else {
			for (int i = 0; i < M; i++){
				s = S[i];
				d = D[i];
				//m = LAMBDA * (r - d - s*s) / (LAMBDA - 1);
				//p = (m + d + s*s - r) / (s*s);
				p = (r-d-s*s)/((s*s)*(LAMBDA-1));
				if (p > 0 && p < 1)
					result[i] = p;
				else
					return null;
			}
		}
		//		System.out.println("Probabilities are :::: "+Arrays.toString(result));
		return result;
	}

	public double getDelay(double[] D, double[] S, double[] P, int LAMBDA){
		double r = 0.0;
		double s = 0.0;
		double d = 0.0;
		double p = 0.0;

		for (int i = 0; i < D.length; i++){
			p = P[i];
			d = D[i];
			s = S[i];

			if (p < 0 || p > 1){
				return 0; // if the probability is invalid, return 0;
			}
			r = d + ((LAMBDA-1) * p + 1)*s*s;
		}

		return r;
	}
}