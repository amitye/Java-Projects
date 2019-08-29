import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import sun.security.krb5.internal.crypto.Des;
import weka.core.Instances;

public class MainHW2 {

	public static BufferedReader readDataFile(String filename) {
		BufferedReader inputReader = null;

		try {
			inputReader = new BufferedReader(new FileReader(filename));
		} catch (FileNotFoundException ex) {
			System.err.println("File not found: " + filename);
		}

		return inputReader;
	}
	
	/**
	 * Sets the class index as the last attribute.
	 * @param fileName
	 * @return Instances data
	 * @throws IOException
	 */
	public static Instances loadData(String fileName) throws IOException{
		BufferedReader datafile = readDataFile(fileName);

		Instances data = new Instances(datafile);
		data.setClassIndex(data.numAttributes() - 1);
		return data;
	}
	
	public static void main(String[] args) throws Exception {
		Instances trainingCancer = loadData("cancer_train.txt");
		Instances testingCancer = loadData("cancer_test.txt");
		Instances validationCancer = loadData("cancer_validation.txt");

		// detemine which method to use for building the tree by calculating avgError on each tree
		DecisionTree giniTree = new DecisionTree();
		giniTree.buildTree(trainingCancer, true);

		DecisionTree entropyTree = new DecisionTree();
		entropyTree.buildTree(trainingCancer, false);

		double giniError = giniTree.calcAvgError(validationCancer);
		double entropyError = entropyTree.calcAvgError(validationCancer);

		System.out.println("Validation error using Entropy: " + entropyError);
		System.out.println("Validation error using Gini: " + giniError);
		System.out.println("----------------------------------------------------");

		// inspecting each p_value for pruning and printing them all
		double[] pValuesArray = {1.0, 0.75, 0.5, 0.25, 0.05, 0.005};
		double bestError = 1;
		double currentError = 0;
		double bestP = 0;
		double currentTrainError = 0;
		for(int i = 0; i < pValuesArray.length; i++){
			giniTree.buildTree(trainingCancer, true);
			System.out.println("Decision Tree with p_value of: " + pValuesArray[i]);
			giniTree.prune(giniTree.getRootNode(), pValuesArray[i]);
			currentError = giniTree.calcAvgError(validationCancer);
			currentTrainError = giniTree.calcAvgError(trainingCancer);
			System.out.println("The train error of the decision tree is: " + currentTrainError);
			System.out.println("Max height on validation data: " + giniTree.maxTreeHeight(validationCancer));
			System.out.println("Average height on validation data: " + giniTree.avgTreeHeight(validationCancer));
			System.out.println("The validation error of the decision tree is: " + currentError);
			if(currentError < bestError){
				bestError = currentError;
				bestP = pValuesArray[i];
			}
			System.out.println("----------------------------------------------------");
		}
		System.out.println("Best Validation error at p_value: " + bestP);
		giniTree.buildTree(trainingCancer, true);
		giniTree.prune(giniTree.getRootNode(), bestP);
		double testError = giniTree.calcAvgError(testingCancer);
		System.out.println("Test error with best tree: " + testError);
		giniTree.printTree();
	}
}
