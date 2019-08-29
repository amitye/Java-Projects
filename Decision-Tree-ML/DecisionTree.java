import weka.classifiers.Classifier;
import weka.core.*;

import java.util.LinkedList;
import java.util.Queue;

class Node {
	Node[] children;
	Node parent;
	int attributeIndex;
	double returnValue;
	Instances currentData;
}

public class DecisionTree implements Classifier {
	private Node rootNode;

	/**
	 * A getter for the current tree's root node
	 * @return current root node
	 */
	public Node getRootNode(){
		return this.rootNode;
	}

	/**
	 * Builds a Decision Tree on a given data set
	 * @param data the data set
	 * @param isGini build with gini or entropy
	 */
	public void buildTree(Instances data, boolean isGini){
		double currentGain;
		// initialize the queue to hold the nodes by order
		Node current;
		Queue<Node> q = new LinkedList<>();
		// initialize the root node to hold the entire given data
		this.rootNode = new Node();
		this.rootNode.currentData = data;
		q.add(rootNode);
		while(!q.isEmpty()){
			current = q.remove();
			current.returnValue = calcReturnValue(current);
			if(current.currentData.numInstances() > 0) {
				if (!perfectlyClassified(current)) {
					// setting the attributeIndex of the current node to be the best one
					int attIndex = findBestAttribute(current, isGini);
					if(attIndex != -1) {
						current.attributeIndex = attIndex;
						Instances[] arrayOfChildrenData = distributeData(current.currentData, current.attributeIndex);
						Node[] children = new Node[arrayOfChildrenData.length];
						current.children = children;
						for (int i = 0; i < arrayOfChildrenData.length; i++) {
							// constructing a node from each data segment
							Node currentChild = new Node();
							children[i] = currentChild;
							currentChild.parent = current;
							currentChild.currentData = arrayOfChildrenData[i];
							// add the node to the queue
							if (arrayOfChildrenData[i] != null) {
								q.add(currentChild);
							}
						}
					}
				}
			}
		}
	}

	@Override
	public void buildClassifier(Instances arg0) throws Exception {
		buildTree(arg0, true);
	}
    @Override
	public double classifyInstance(Instance instance) {
		Node currentNode = this.rootNode;
		Node nextNode;
		// traversing the tree
		while (currentNode.children != null){
			// determine which is the next node according to the current instance's attribute value
			nextNode = currentNode.children[(int)instance.value(currentNode.attributeIndex)];
			// if we have a way to continue traversing the tree
			if(currentNode.children != null) {
				currentNode = nextNode;
			}
			// if we stopped at a node with no option to continue traversing we want to stop and return its value
			else{
				break;
			}
		}
		// if the current node has no instances, classify as the parent node
		if(currentNode.currentData.numInstances() == 0){
			return currentNode.parent.returnValue;
		}
		return currentNode.returnValue;
    }


	/**
	 * Calculate the average error on a given instances set
	 * @param data the data set
	 * @return the average calculation error
	 */
	public double calcAvgError(Instances data){
		// initialize a variable that will count the number of prediction mistakes in order to calculate the error
		int numberOfPredictionMistakes = 0;
		double currentClassification;
		for(int i = 0; i < data.numInstances(); i++){
			currentClassification = classifyInstance(data.instance(i));
			// in case a prediction mistake was made, add 1 to the number of mistakes
			if(currentClassification != data.instance(i).classValue()){
				numberOfPredictionMistakes++;
			}
		}
		return (double)numberOfPredictionMistakes/data.numInstances();
	}

	/**
	 * Finds the best attribute for the current split
	 * @param current the current node for which we want to find the best attribute for the split
	 * @param isGini notifies the method if the method we are using is Gini or Entropy
	 * @return
	 */
	private int findBestAttribute(Node current, boolean isGini) {
		double maxGain = 0;
		double currentGain;
		int bestAttributeIndex = 0;
		// iterate over all possible attributes for the current node (excluding class atribute)
		for (int i = 0; i < current.currentData.numAttributes() - 1; i++) {
			currentGain = calcGain(current, current.currentData, i, isGini);
			// in case we got a better Gain for the current attribute, change the maxGain and the best attribute index
			if (currentGain > maxGain) {
				maxGain = currentGain;
				bestAttributeIndex = i;
			}
		}
		if (maxGain == 0) {
			bestAttributeIndex = -1;
		}
		return bestAttributeIndex;
	}

	/**
	 * Calculates Entropy value for given set
	 * @param probs the set of probabilities built on the data set
	 * @return the Entropy calculation according to the formula
	 */
	private double calcEntropy(double[] probs){
		double answer = 0;
		for(int i = 0; i < probs.length; i ++){
			// making sure not to calc log(0)
			if(probs[i] == 0){
				return 0;
			}
			// calculates according to the given formula, dividing according to log law for changing bases.
			answer -= (double)(probs[i]*Math.log(probs[i])/Math.log(2));
		}
		return answer;
	}

	/**
	 * Calculates Gini value for given set
	 * @param probs the set of probabilities built on the data set
	 * @return the Gini calculation according to the formula
	 */
    private double calcGini(double[] probs){
		double answer = 0;
		// calculates the sum (only of the sigma part) according to the the formula
		for(int i = 0; i < probs.length; i++){
			answer += (double)Math.pow(probs[i], 2);
		}
		return (1.0 - answer);
	}

	/**
	 * Distributes the given data into the children by the current attribute's possible values
	 * @param data the data set which we want to distribute
 	 * @param attributeIndex current attribute index
	 * @return an array with distributed data, as each cell in the array represents the data that belongs to the
	 * corresponding value of the current attribute
	 */
	private Instances[] distributeData(Instances data, int attributeIndex) {
		Instances[] dataArray = new Instances[data.attribute(attributeIndex).numValues()];
    	// initializing the data array
		for(int i = 0; i < dataArray.length; i++){
			dataArray[i] = new Instances(data, 0);
		}
		// iterating over all values for given attributeIndex
		for(int i = 0; i < dataArray.length; i++) {
			// adding the correct instances to the correct data set in the data array
			for(int j = 0; j < data.numInstances(); j++) {
				if(data.instance(j).value(attributeIndex) == i) {
					dataArray[i].add(data.instance(j));
				}
			}
		}
		return dataArray;
	}

	/**
	 * Calculates Gain of the current split
	 * @param current the current node
 	 * @param data the current data set
	 * @param attributeIndex the current attribute
	 * @param isGini notifies if the calculation is made with Gini or Entropy
	 * @return the Gain
	 */
	private double calcGain(Node current, Instances data, int attributeIndex, boolean isGini){
		double gain = 0;
		// will hold the value of the sigma calculation
		double sigma = 0;
		double[] probs = probabilities(data);
		double[] splitProbs;
		Instances[] dataArray = distributeData(data, attributeIndex);
		for(int i = 0; i < data.attribute(attributeIndex).numValues(); i++){
			// if there is no data distributed to the current slot in the array, continue to the next one.
			if(dataArray[i].numInstances() == 0){
				continue;
			}
			splitProbs = probabilities(dataArray[i]);
			// preforms the calculation of temp according to the method Gini/Entropy
			if(isGini) {
				sigma += ((double)(dataArray[i].numInstances()) / (double)(data.numInstances())) * calcGini(splitProbs);
			}
			else{
				sigma += ((double)(dataArray[i].numInstances()) / (double)(data.numInstances())) * calcEntropy(splitProbs);
			}
		}
		// calculates the final return value according to the method Gini/Entropy
		if(isGini){
			return calcGini(probs) - sigma;
		}
		// in case we use Entropy
		return calcEntropy(probs) - sigma;
	}

	/**
	 * Creates a set of probabilities for a given data of instances
	 * @param data the data set
	 * @return an array with probabilities for the current data set
	 */
	private double[] probabilities(Instances data){
		// initialization of the returned array
    	double[] probs = new double[2];
		int index;
		for(int i = 0; i < probs.length; i++){
			probs[i] = 0;
		}
    	// iterate over the instances in the given data
		for(int i = 0; i < data.numInstances(); i++){
			// gives the current index the value of the current class attribute (0 or 1)
			index = (int)data.instance(i).classValue();
			// increment the amount of 0/1 in the array
			probs[index]++;
		}
		// divide each index in the array by the total number of instances in order to get probability
		for(int i = 0; i < probs.length; i++){
			probs[i] /= (double)data.numInstances();
		}
		return probs;
	}

	/**
	 * Determines whether a given node is perfectly classified, returns true if so
	 * @param current current node
	 * @return true if perfectly classified, else false
	 */
	private boolean perfectlyClassified(Node current){
		// set the parameter clas to hold the value of the first instance's classValue
		int clas = (int) current.currentData.firstInstance().classValue();
		// iterate over all instances in the current data in order to determine whether the current node is perfectly
		// classified or not
		for (int i = 1; i < current.currentData.numInstances(); i++) {
			if ((int) current.currentData.instance(i).classValue() != clas) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Calculates the correct return value for a given node
	 * @param current the current node
	 * @return the correct return value for this node
	 */
	private double calcReturnValue(Node current){
		double[] probs = probabilities(current.currentData);
		// in case the majority if 0 returns 0 as the return value
		if(probs[0] > probs[1]){
			return 0.0;
		}
		else{
			return 1.0;
		}
	}

	/**
	 * Calculates the chiSquare value
	 * @param data current data set
	 * @param attributeIndex current attribute
	 * @return chiSquare value
	 */
	private double calcChiSquare(Instances data, int attributeIndex){
		double chiValue = 0;
		int[] countArray = countClassification(data);
		// initialize an array to hold the calculation for P(Y=0) and P(Y=1)
		double[] P = new double[2];
		P[0] = (double)(countArray[0])/(double)data.numInstances();
		P[1] = (double)(countArray[1])/(double)data.numInstances();
		// initialize an array which counts the number of instances that distribute to each of the current attribute's
		// values
		Instances[] D = distributeData(data, attributeIndex);
		// initializing 2 arrays which will contain the amount of instances with class values 0 or 1 respectively for
		// each value of the current attribute
		int[] p_f = new int[D.length];
		int[] n_f = new int[D.length];
		// calculating the amount of 0 and 1 classes within the current attribute's value (over all values)
		for(int i = 0; i < D.length; i++){
			int[] temp = countClassification(D[i]);
			p_f[i] = temp[0];
			n_f[i] = temp[1];
		}
		double E0;
		double E1;
		// calculating the chiValue according to the given formula
		for(int i = 0; i < D.length; i++){
			E0 = (double)D[i].numInstances()*P[0];
			E1 = (double)D[i].numInstances()*P[1];
			if(E0 != 0 && E1 != 0) {
				chiValue += ((Math.pow((p_f[i] - E0), 2) / E0) + (Math.pow((n_f[i] - E1), 2) / E1));
			}
		}
		return chiValue;
	}

	/**
	 * Building the table of probabilities for chi square distribution
	 * @return the table of probabilities
	 */
	private double[][] chiTable(){
		double[][] table ={
				{0, 0.102, 0.455, 1.323, 3.841, 7.879},
				{0, 0.575, 1.386, 2.773, 5.991, 10.597},
				{0, 1.213, 2.366, 4.108, 7.815, 12.838},
				{0, 1.923, 3.357, 5.385, 9.488, 14.860},
				{0, 2.675, 4.351, 6.626, 11.070, 16.750},
				{0, 3.455, 5.348, 7.841, 12.592, 18.548},
				{0, 4.255, 6.346, 9.037, 14.067, 20.278},
				{0, 5.071, 7.344, 10.219, 15.507, 21.955},
				{0, 5.899, 8.343, 11.389, 16.919, 23.589},
				{0, 6.737, 9.342, 12.549, 18.307, 25.188},
				{0, 7.584, 10.341, 13.701, 19.675, 26.757}
		};
		return table;
	}

	/**
	 * this method gets a root node as argument (which represents a tree), and prunes the tree according to a given pvalue
	 * @param root the current tree's root node
	 * @param pValue the value to be pruned according to
	 */
	public void prune(Node root, double pValue){
		// initializing a queue in order to inspect all nodes in the given tree
		Queue<Node> q = new LinkedList<>();
		int degreeOfFreedom = 0;
		double[][] chiSquareTable = this.chiTable();
		q.add(root);
		while(!q.isEmpty()){
			Node current = q.remove();
			if(current.children != null){
				double chiSquareValue = calcChiSquare(current.currentData, current.attributeIndex);
				int location = findLocationInTable(pValue);
				degreeOfFreedom = calcDegreeOfFreedom(current);
				if(chiSquareValue >= chiSquareTable[degreeOfFreedom][location]) {
					// in case we didn't prune the current node, add the current node's non-leaf children to the queue
					for (int i = 0; i < current.children.length; i++) {
						if(current.children[i].children != null) {
							q.add(current.children[i]);
						}
					}
				}
				// pruning
				else{
					current.children = null;
				}
			}
		}
	}

	/**
	 * Calculates degree of freedom
	 * @param current the current node
	 * @return the correct degree of freedom
	 */
	private int calcDegreeOfFreedom(Node current){
		int df = 0;
		for(int i = 0; i < current.children.length; i++){
			if(current.children[i].currentData.numInstances() != 0){
				df++;
			}
		}
		return (df-1);
	}

	/**
	 * Finds the correct column index in the chiSquare table according to the given p_value
	 * @param pValue the given p_value
	 * @return column index
	 */
	private int findLocationInTable(double pValue){
		if(pValue == 1) return 0;
		if(pValue == 0.75) return 1;
		if(pValue == 0.5) return 2;
		if(pValue == 0.25) return 3;
		if(pValue == 0.05) return 4;
		return 5;
	}

	/**
	 * Returns an array which holds the number of instance of the given set which hold the class value 0 or 1
	 * @param data the given data set
	 * @return
	 */
	private int[] countClassification(Instances data){
		int[] array = new int[2];
		// filling the correct values in the array
		for(int i = 0; i < data.numInstances(); i++){
			// counts the number of instances which hold the class value 0 or 1 respectively
			if(data.instance(i).classValue() == 0){
				array[0]++;
			}
			else{
				array[1]++;
			}
		}
		return array;
	}

	/**
	 * Calculates the maximum tree height
	 * @param data current data set
	 * @return the max tree height
	 */
	public int maxTreeHeight(Instances data){
		int maxHeight = 0;
		int currentHeight = 0;
		Node current = this.getRootNode();
		Node next = new Node();
		for(int i = 0; i < data.numInstances(); i++){
			currentHeight = calcHeight(data.instance(i));
			if(currentHeight > maxHeight){
				maxHeight = currentHeight;
			}
			current = this.getRootNode();
		}
		return maxHeight;
	}

	/**
	 * Calculates average tree height
	 * @param data curren data set
	 * @return the average tree height
	 */
	public double avgTreeHeight(Instances data){
		int sumOfHeights = 0;
		double avgHeight = 0;
		for(int i = 0; i < data.numInstances(); i++){
			sumOfHeights += calcHeight(data.instance(i));
		}
		avgHeight = (double)sumOfHeights/(double)data.numInstances();
		return avgHeight;
	}

	/**
	 * Calculates the length of the path a certain instance goes through in order to be classified
	 * @param instance current instance
	 * @return the length of its path to classification
	 */
	private int calcHeight(Instance instance){
		int currentHeight = 0;
		Node current = this.rootNode;
		Node next = new Node();
		// traversing the tree
		while (current.children != null){
			// determine which is the next node according to the current instance's attribute value
			next = current.children[(int)instance.value(current.attributeIndex)];
			// if we have a way to continue traversing the tree
			if(current.children != null) {
				current = next;
				currentHeight++;
			}
			// if we stopped at a node with no option to continue traversing we want to stop
			else{
				break;
			}
		}
		return currentHeight;
	}

	/**
	 * Prints the tree
	 */
	public void printTree(){
		// initializing a stringBuilder variable to hold the printed form of the tree
		StringBuilder toBePrinted = new StringBuilder();
		System.out.println((printTreeAux(toBePrinted, this.rootNode, 1)).toString());
	}

	/**
	 * An auxiliary method for printing the tree
	 * @param str a stringBuilder which represents the printed tree
	 * @param current current node
	 * @param space number of spaces in the current indentation phase
	 * @return
	 */
	private StringBuilder printTreeAux(StringBuilder str, Node current, int space){
		// printing the root of the tree
		if(current.parent == null) {
			str.append("Root\n");
			str.append("Returning value: " + this.rootNode.returnValue + "\n");
		}
		// in case the current node is a leaf
		if(current.children == null){

			// excluding the leaf nodes which hold no data from the final tree representation
			if(current.currentData.numInstances() == 0){
				str.delete(str.lastIndexOf("\n"), str.length());
				str.delete(str.lastIndexOf("\n")+1, str.length());
				return str;
			}
			// appending the correct amount of spaces to the beginning of the line
			makeSpace(str, space);
			str.append("Leaf. Returning value: " + current.returnValue + "\n");
			return str;
		}
		// in case the current node has children
		if(current.children != null) {
			for (int i = 0; i < current.children.length; i++) {
				makeSpace(str, space);
				str.append("If attribute " + current.attributeIndex + " = " + i + "\n");
				// if the current child is not a leaf
				makeSpace(str, space);
				if (current.children[i].children != null) {
					str.append("Returning value: " + current.children[i].returnValue + "\n");
				}
				printTreeAux(str, current.children[i], space + 1);
			}
		}
		return str;
	}

	// an auxiliary method for adding the correct number of spaces to the stringBuilder

	/**
	 * adding the correct number of spaces to the stringBuilder
	 * @param str the current stringBuilder
 	 * @param count number of spaces to be added
	 */
	private void makeSpace(StringBuilder str, int count){
		for(int i = 0; i < count; i++){
			str.append(" ");
		}
	}
    @Override
	public double[] distributionForInstance(Instance arg0) throws Exception {
		// Don't change
		return null;
	}

	@Override
	public Capabilities getCapabilities() {
		// Don't change
		return null;
	}

}
