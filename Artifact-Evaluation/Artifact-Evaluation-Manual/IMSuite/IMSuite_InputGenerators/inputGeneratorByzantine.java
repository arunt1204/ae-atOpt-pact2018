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
 * inputGeneratorByzantine implements an input generator for the byzantine algorithm.
 * Based on the input size the algorithm also provides desciption of traitor and non 
 * traitor nodes also.
 * Following options are available with the input generator:
 * For input file name: -in
 * For giving input size: -sz
 * For Random graph (by default) = -rn
 * For Complete graph = -cplt
 * For Sp-Min Graph = -spmin
 * For Sp-Max Graph = -spmax
 * For Maximum number of children per node (In case of Sparse graph only) = -seed
 * For Seed of Random number = -seed
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
public class inputGeneratorByzantine 
{
	/** 
	 * Acts as the starting point for the program execution. 
	 * <code>main</code> accepts the user choice for the type 
	 * of graph, generates a graph and provides a set of traitors.
	 * 
	 * @param args 		array of runtime arguments.
	 * @throws Exception	if File handling operation illegal. 
	 */
	public static void main(String []args) throws Exception {
		int i=0, j=0, choice=0;
		int totalNodes = 16, seed=101, max=0;		// By default
		boolean flag=false;
		String fileName = "inputbyzantine.txt";
		
		for(i=0; i<args.length; i++) {
			if(args[i].equals("-in")) {
				i++;
				fileName = args[i];
			}
			else if(args[i].equals("-sz")) {
				i++;
				totalNodes = Integer.parseInt(args[i]);
			}
			else if(args[i].equals("-rn") && !flag) {
				choice = 0;
				flag = true;
			}
			else if(args[i].equals("-cplt") && !flag) {
				choice = 1;
				flag = true;
			}
			else if(args[i].equals("-spmin") && !flag) {
				choice = 2;
				flag = true;
			}
			else if(args[i].equals("-spmax") && !flag) {
				choice = 3;
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
		
		int  id, mat[][] = new int[totalNodes][totalNodes], randomVote[] = new int[totalNodes];
		int traitorCount = totalNodes/8 - 1;
		int traitors[] = new int[traitorCount];
		boolean nodes[] = new boolean[totalNodes];
		
		Random r = new Random(seed);
		if(max == 0) {
			max = r.nextInt(totalNodes-1);
			if(max == 0)
				max = 1;
		}
		
		i=0;
		while(i<traitorCount) {
			id = r.nextInt(totalNodes);
			if(!nodes[id]) {
				nodes[id] = true;
				traitors[i] = id;
				i++;
			}
		}
		
		for(i=0,j=0; i<totalNodes; i++, j++)
			if(i == j)
				mat[i][j] =0;
		
		if(choice == 0) {
			for(i=0; i<totalNodes; i++)
				for(j=i+1; j<totalNodes; j++)
					mat[i][j] = mat[j][i] = r.nextInt(2);
		}
		else if(choice == 1) {
			for(i=0; i<totalNodes; i++)
				for(j=0; j<totalNodes; j++)
					if(i!=j)
						mat[i][j] = 1;
		}
		else {
			SparseGraph.GRSIZE = totalNodes;
			SparseGraph.maxChild = max;
			if(choice == 2)
				SparseGraph.edges = true;
			else
				SparseGraph.edges = false;
			SparseGraph node = new SparseGraph(0, -1, r);
			SparseGraph.print(fileName, nodes, traitors, traitorCount, r);
		}
		
		if(choice == 0 || choice == 1) {
			Writer output = null;
  			output = new BufferedWriter(new FileWriter(fileName));
			output.write("" + totalNodes + "\n");			// Printing total Nodes
			for(i=0; i<totalNodes; i++) {				// Printing adjacency matrix
				for(j=0; j<totalNodes; j++)
					output.write("" + mat[i][j]);
				output.write("\n");
			}
			output.write(" \n");				
			
			for(i=0; i<totalNodes; i++)				// Printing random vote
				output.write("" + r.nextInt(2));
			output.write("\n ");
			
			for(i=0; i<traitorCount; i++)				// Printing traitor id
				output.write("\n" + traitors[i]);
			output.close();
		}
	}
}

/** 
 * Helps to generate the Sp-Min (Sparse minimum) and Sp-Max (Sparse maximum) graphs. 
 * A count for maximum number of children per node is also utilized.
 */
class SparseGraph 
{
	static int GRSIZE = 16, count = 0, total = 0, array[], parentArray[], maxChild = 2;
	static int adjacencyMatrix[][];	
	static boolean edges = false;
	int value, childNum, ID, parentID;
	SparseGraph [] children;

	/**
	 * Generates a random number within some specified range.
	 *
	 * @param   range	the acceptable variation for random number.
	 * @param   r		instance of random number generator marked by a seed.
	 * @return		random number.
	 */
	public int PseudoRandomGen (int range, Random r) {
		int temp = (r.nextInt()) % range;
		if(temp < 0)
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
	static void print(String fileName, boolean nodes[], int traitors[], int traitorCount, Random r)	throws Exception {
		int max = GRSIZE*((int)(Math.log(GRSIZE)/Math.log(2)));
		int idx1, idx2, i=0, no_ofEdges;
		
		if(edges)
			no_ofEdges = GRSIZE;
		else	
			no_ofEdges = max;
		
		while(i<(no_ofEdges-GRSIZE)) {
			idx1 = r.nextInt(GRSIZE);
			idx2 = r.nextInt(GRSIZE);
			if((idx1 != idx2) && (adjacencyMatrix[idx1][idx2] != 1)) {
				adjacencyMatrix[idx1][idx2] = 1;
				adjacencyMatrix[idx2][idx1] = 1;
				i++;
			}
		}
		
		Writer output = null;
  		output = new BufferedWriter(new FileWriter(fileName));
  		output.write("" + GRSIZE + "\n");				// Printing Size
		for (i = 0 ; i < GRSIZE ; i ++) {				// Printing Adjacency matrix
			for (int j = 0 ; j < GRSIZE; j ++) 
				output.write("" + adjacencyMatrix[i][j]);
			output.write("\n");
		}
		output.write(" \n");
		
		for(i=0; i<GRSIZE; i++)						// Printing random vote
			output.write("" + r.nextInt(2));
		output.write("\n ");
			
		for(i=0; i<traitorCount; i++)					// Printing traitor id
			output.write("\n" + traitors[i]);
		output.close();
	}

	/** 
	 * Constructs the initial tree and thus defines the parent child relationships.
	 * 
	 * @param   type 	if 1 then node to be created is a Child Node
	 * @param   parent	decides which node is parent of which
	 * @throws  r		instance of random number generator marked by a seed.
	 */
	SparseGraph(int type, int parent, Random r) {
		ID = count;
		parentID = parent;
		if(type == 0) {
			array = new int[GRSIZE];
			for(int i = 0; i < GRSIZE; i++)	
				array[i] = i;
			for(int i = 0; i < 2 * GRSIZE; i++) {
				int x = PseudoRandomGen(GRSIZE, r);
				int y = PseudoRandomGen(GRSIZE, r);
				int temp = array[x];
				array[x] = array[y];
				array[y] = temp;
			}
			parentArray = new int[GRSIZE];
			adjacencyMatrix = new int [GRSIZE][GRSIZE];
			total = 1;
		}
		if (parent != -1) {
			adjacencyMatrix[ID][parentID] = 1;
			adjacencyMatrix[parentID][ID] = 1;
			parentArray[ID] = parentID;
		}
		value = array[count++];
		childNum = r.nextInt(maxChild) + 1;
		childNum = ((total + childNum) < GRSIZE)?childNum : GRSIZE - total;
		total += childNum;

		if(childNum != 0) {
			children = new SparseGraph[childNum];
			for (int i = 0; i < childNum; i++)
				children[i] = new SparseGraph(1, ID, r);		// Sending 1 as the node to be created is a Child Node
		}	
	}
}
