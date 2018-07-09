/* This file is part of IMSuite Benchamark Suite.
 * 
 * This file is licensed to You under the Eclipse Public License (EPL);
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.opensource.org/licenses/eclipse-1.0.php
 *
 * (C) Copyright IMSuite 2013-present.
 */

import java.io.*;
import java.util.Random;

/** 
 * inputGeneratorVertexColoring implements an input generator for generation
 * of input for the dominating set algorithm.
 * Following options are available with the input generator:
 * For input file name: -in
 * For giving input size: -sz
 * For Random graph (by default) = -rn
 * For Star Graph = -star
 * For Chain Graph = -chain
 * For Maximum number of children per node (In case of random graph only) = -seed
 * For Seed of Random number = -seed
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
class inputGeneratorVertexColoring 
{
	/** 
	 * Acts as the starting point for the program execution. 
	 * <code>main</code> accepts the user choice for the type 
	 * of graph and generates the specified graph.
	 * 
	 * @param args 		array of runtime arguments.
	 * @throws Exception	if File handling operation illegal. 
	 */
	public static void main (String args[]) throws Exception {
		int i=0, j=0, choice=0, max=2;
		int size = 16, seed=101;	
		boolean flag = false, rnflag=false;	
		String fileName = "inputvertexColoring.txt";
		
		for(i=0; i<args.length; i++) {
			if(args[i].equals("-in")) {
				i++;
				fileName = args[i];
			}
			else if(args[i].equals("-sz")) {
				i++;
				size = Integer.parseInt(args[i]);
			}
			else if(args[i].equals("-rn") && !flag) {
				choice = 0;
				flag = true;
			}
			else if(args[i].equals("-chain") && !flag) {
				choice = 1;
				flag = true;
			}
			else if(args[i].equals("-star") && !flag) {
				choice = 2;
				flag = true;
			}
			else if(args[i].equals("-seed")) {
				i++;
				seed = Integer.parseInt(args[i]);
			}
			else if(args[i].equals("-max")) {
				i++;
				max = Integer.parseInt(args[i]);
			}		
		}
		
		Random r = new Random(seed);
		if(choice == 1)
			RandomTree.maxChild = 1;
		else if(choice == 2)
			RandomTree.maxChild = size;
		else	
			RandomTree.maxChild = r.nextInt(size-1) + 1;
		RandomTree.TREESIZE = size;
		RandomTree node = new RandomTree(0, -1, r);
		RandomTree.print(fileName, r);
	}
}


class RandomTree 
{
	static int TREESIZE = 100, count = 0, total = 0, array[], parentArray[], maxChild=2;
	static int adjacencyMatrix[][];	
	int value, childNum, ID, parentID;
	RandomTree [] children;

	/**
	 * Generates a random number within some specified range.
	 *
	 * @param   range	the acceptable variation for random number.
	 * @param   r		instance of random number generator marked by a seed.
	 * @return		random number.
	 */
	public int PseudoRandomGen (int range, Random r) {
		int temp = (r.nextInt()) % range;
		if (temp < 0 ) 
			return -temp;
		else
			return temp;
	}

	/** 
	 * Helps in generating the input file by printing data 
	 * onto a user specified <code>fileName<\code>.
	 * 
	 * @param fileName 	name of the outputted inputfile.
	 * @param r	 	instance of random number generator marked by a seed.
	 * @throws Exception	if File handling operation illegal. 
	 */
	static void print(String fileName, Random r)	throws Exception {
		Writer output = null;
  		output = new BufferedWriter(new FileWriter(fileName));
  		
  		output.write("" + TREESIZE + "\n");				// Printing size
  		output.write("" + 0 + "\n");					// Printing root
		for (int i = 0 ; i < TREESIZE ; i ++) {				// Printing tree
			for (int j = 0 ; j < TREESIZE; j ++) 
				output.write("" + adjacencyMatrix[i][j]);
			output.write("\n");
		}
		output.write(" \n");
		
		for ( int i = 0 ; i < TREESIZE; i ++)				// Printing parent information
			output.write("" + parentArray[i] + "\n");
		output.close();	
	}

	/** 
	 * Constructs the initial tree and thus defines the parent child relationships.
	 * 
	 * @param   type 	if 1 then node to be created is a Child Node
	 * @param   parent	decides which node is parent of which
	 * @throws  r		instance of random number generator marked by a seed.
	 */
	RandomTree(int type, int parent, Random r) {
		ID = count;
		parentID = parent;
		if(type == 0) {
			array = new int[TREESIZE];
			for(int i = 0; i < TREESIZE; i++)
				array[i] = i;
			for(int i = 0; i < 2 * TREESIZE; i++) {
				int x = PseudoRandomGen(TREESIZE, r);
				int y = PseudoRandomGen(TREESIZE, r);
				int temp = array[x];
				array[x] = array[y];
				array[y] = temp;
			}
			parentArray = new int[TREESIZE];
			adjacencyMatrix = new int [TREESIZE][TREESIZE];
			total = 1;
		}
		
		if (parent != -1) {
			adjacencyMatrix[ID][parentID] = 1;
			adjacencyMatrix[parentID][ID] = 1;
			parentArray[ID] = parentID;
		}
		value = array[count++];
		//childNum = 
		//childNum = TREESIZE - 1;
		
		if(maxChild > 1 && maxChild < TREESIZE)
			childNum = r.nextInt(maxChild) + 1;
		else
			childNum = maxChild;	
		childNum = ((total + childNum) < TREESIZE)?childNum : TREESIZE - total;
		total += childNum;

		if(childNum != 0) {
			children = new RandomTree[childNum];
			for (int i = 0; i < childNum; i++)
				children[i] = new RandomTree(1, ID, r);
		}	
	}
}
