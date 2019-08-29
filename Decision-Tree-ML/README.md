Implementation of the Decision Tree machine learning algorithm using Weka3. 
The goal of this project is to predict, based on a set of training data, whether a breast cancer tumor has a recurrence based on parameters of the patient and the tumor.
First, I chose an impurity measure by constructing two trees - one constructed with Entropy as an impurity measure and one constructed with Gini. After building each tree using the training set, I calculated the validation error on each tree and chose the impurity measure that gave the lowest error.
Furthermore, I have implemented Chi Square pruning in order to verify that the attribute that was chosen to split a certain node has predictive power or are close to random.
The program outputs statistics on the test set - how accurate are the results deriving from the decision tree built.
