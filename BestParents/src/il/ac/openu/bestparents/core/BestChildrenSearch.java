/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package il.ac.openu.bestparents.core;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.TreeMap;

import il.ac.openu.bestparents.util.NewBNUtils;
import weka.classifiers.bayes.BayesNet;
import weka.classifiers.bayes.net.search.SearchAlgorithm;
import weka.core.ContingencyTables;
import weka.core.Instances;

/**
 * @author Andrew Kreimer - algonell.com
 */
public class BestChildrenSearch extends SearchAlgorithm {

	private static final long serialVersionUID = 1032285588625105530L;
	
	private int maxNrOfChildren;
	private double[][][][] attributeMatrix;
	
	/**
	 * @param bayesNet
	 *            the network
	 * @param instances
	 *            the data to work with
	 * @throws Exception
	 *             if something goes wrong
	 */
	@Override
	public void search(BayesNet bayesNet, Instances instances) throws Exception {
		// contingency table for each attribute X attribute matrix
		attributeMatrix = new double[instances.numAttributes()][instances
				.numAttributes()][][];

		// allocate
		allocate(instances);

		// count instantiations
		count(instances);

		// for each attribute with index i: map<entropy, child index>, keeping the map sorted
		ArrayList<TreeMap<Double, Integer>> attributeBestChildrenList = new ArrayList<>();
		
		//allocate
		for (int i = 0; i < instances.numAttributes(); i++) {
			TreeMap<Double, Integer> tmpTreeMap = new TreeMap<>();
			attributeBestChildrenList.add(i, tmpTreeMap);
		}
		
		//map<entropy, rule(string)>
		TreeMap<Double, String> entropyRuleMap = new TreeMap<>();

		//map<entropy, rule(attributeParentIndex -> attributeChildIndex)>
		TreeMap<Double, Entry<Integer,Integer>> entropyParentToChildMap = new TreeMap<>();
		
		// calculate conditional entropy for contingency tables
		calculateContingencyTables(instances, attributeBestChildrenList, entropyRuleMap, entropyParentToChildMap);

		//build network
		assembleNetwork(bayesNet, instances, attributeBestChildrenList);
	}

	/**
	 * Assembles network 
	 * 
	 * @param bayesNet
	 * @param instances
	 * @param attributeBestChildrenList
	 */
	private void assembleNetwork(BayesNet bayesNet, Instances instances,
			ArrayList<TreeMap<Double, Integer>> attributeBestChildrenList) {
		for (int i = 0; i < instances.numAttributes(); i++) {
			TreeMap<Double, Integer> tmpTreeMap = attributeBestChildrenList.get(i);
			int numOfAddedRules = 0;
			
			for (Entry<Double, Integer> entry : tmpTreeMap.entrySet()) {
				int value = entry.getValue();
				
				int numOfParentsForCurrentChild = bayesNet.getParentSet(value).getNrOfParents();
				if (numOfAddedRules < getMaxNrOfChildren() &&
						numOfParentsForCurrentChild < getMaxNrOfChildren() &&
						numOfAddedRules < tmpTreeMap.size() &&
						NewBNUtils.countNumOfChildren(bayesNet, instances, i) < getMaxNrOfChildren() &&
						!bayesNet.getParentSet(value).contains(i)){
					bayesNet.getParentSet(value).addParent(i, instances);
					numOfAddedRules++;
				}
			}
		}
	}

	/**
	 * Calculate conditional entropies
	 * 
	 * @param instances
	 * @param attributeBestChildrenList
	 * @param entropyRuleMap
	 * @param entropyParentToChildMap
	 */
	private void calculateContingencyTables(Instances instances,
			ArrayList<TreeMap<Double, Integer>> attributeBestChildrenList, TreeMap<Double, String> entropyRuleMap,
			TreeMap<Double, Entry<Integer, Integer>> entropyParentToChildMap) {
		for (int i = 0; i < instances.numAttributes(); i++) {
			for (int j = 0; j < i; j++) {
				double entropyConditionedOnRows = ContingencyTables.entropyConditionedOnRows(attributeMatrix[i][j]);
				double entropyConditionedOnColumns = ContingencyTables.entropyConditionedOnColumns(attributeMatrix[i][j]);

				double lowestEntropy = (entropyConditionedOnRows < entropyConditionedOnColumns) ? 
						entropyConditionedOnRows : 
						entropyConditionedOnColumns;
				
				//save current rule
				String arc = (entropyConditionedOnRows < entropyConditionedOnColumns) ? 
						instances.attribute(i).name() + " -> " + instances.attribute(j).name() : 
						instances.attribute(j).name() + " -> " + instances.attribute(i).name();
				entropyRuleMap.put(lowestEntropy, arc);
				
				if (entropyConditionedOnRows < entropyConditionedOnColumns) {
					attributeBestChildrenList.get(i).put(lowestEntropy, j);
					entropyParentToChildMap.put(lowestEntropy, new AbstractMap.SimpleEntry<>(i,j));
				} else {
					attributeBestChildrenList.get(j).put(lowestEntropy, i);
					entropyParentToChildMap.put(lowestEntropy, new AbstractMap.SimpleEntry<>(j,i));
				}
			}
		}
	}

	/**
	 * Count occurances
	 * 
	 * @param instances
	 */
	private void count(Instances instances) {
		for (int n = 0; n < instances.numInstances(); n++) {
			for (int i = 0; i < instances.numAttributes(); i++) {
				for (int j = 0; j < i; j++) {
					int iAttrIndex = (int) instances.instance(n).value(i);
					int jAttrIndex = (int) instances.instance(n).value(j);
					attributeMatrix[i][j][iAttrIndex][jAttrIndex]++;
				}
			}
		}
	}

	/**
	 * Allocate memory
	 * 
	 * @param instances
	 */
	private void allocate(Instances instances) {
		for (int j = 0; j < instances.numAttributes(); j++) {
			for (int k = 0; k < j; k++) {
				attributeMatrix[j][k] = new double[instances.attribute(j)
						.numValues()][instances.attribute(k).numValues()];
			}
		}
	}
	
	/**
	 * Sets the max number of children
	 * 
	 * @param nMaxNrOfChildren
	 *            the max number of children
	 */
	public void setMaxNrOfChildren(int nMaxNrOfChildren) {
		maxNrOfChildren = nMaxNrOfChildren;
	}

	/**
	 * Gets the max number of children.
	 * 
	 * @return the max number of children
	 */
	public int getMaxNrOfChildren() {
		return maxNrOfChildren;
	}
}
