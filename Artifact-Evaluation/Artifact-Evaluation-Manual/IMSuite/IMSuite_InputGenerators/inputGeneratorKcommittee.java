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
 * inputGeneratorKcommittee implements an input generator for generation
 * of input for the dominating set algorithm.
 * Following options are available with the input generator:
 * For input file name: -in
 * For giving input size: -sz
 * For Random graph (by default) = -rn
 * For Complete graph = -cplt
 * For Sp-Min Graph = -spmin
 * For Sp-Max Graph = -spmax
 * For Maximum number of children per node (In case of Sparse graph only) = -seed
 * For maximum Committee Size (K) = -kval
 * For Seed of Random number = -seed
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
public class inputGeneratorKcommittee 
{
	/** 
	 * Acts as the starting point for the program execution. 
	 * <code>main</code> accepts the user choice for the type 
	 * of graph, generates a graph and provide a root node.
	 * 
	 * @param args 		array of runtime arguments.
	 * @throws Exception	if File handling operation illegal. 
	 */
	public static void main(String []args) throws Exception {
		int i=0, j=0, choice=0;
		int size = 16, max=0, seed=101;		// By default
		int K = 0;
		boolean flag=false;
		String fileName = "inputkCommitte.txt";
		
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
			else if(args[i].equals("-kval")) {
				i++;
				K = Integer.parseInt(args[i]);		//K=3
			}		
		}		
		
		int mat[][] = new int[size][size];
		Random r = new Random(seed);
		
		if(max == 0) {
			max = r.nextInt(size-1);
			if(max == 0)
				max = 1;
		}

		if(K == 0) {
			K = r.nextInt(size);
			if(K == 0)
				K = 1;
		}	
		
		Writer output = null;
  		output = new BufferedWriter(new FileWriter(fileName));
		
		for(i=0,j=0; i<size; i++, j++)
			if(i == j)
				mat[i][j] = 0;
						
		if(choice == 0) {
			for(i=0; i<size; i++)
				for(j=i+1; j<size; j++)
					mat[i][j] = mat[j][i] = r.nextInt(2);
		}
		else if(choice == 1) {
			for(i=0; i<size; i++)
				for(j=0; j<size; j++)
					if(i!=j)
						mat[i][j] = 1;
		}
		else {
			SparseGraph.GRSIZE = size;
			SparseGraph.KVAL = K;
			SparseGraph.maxChild = max;
			if(choice == 2)
				SparseGraph.edges = true;
			else
				SparseGraph.edges = false;
			SparseGraph node = new SparseGraph(0, -1, r);
			SparseGraph.print(fileName, r);
		}
		
		if(choice == 0 || choice == 1) {
			output.write("" + size + "\n");				// Printing Size
			K = 3;	// CHECKING.......
			output.write("" + K + "\n");				// Printing K value
			for(i=0; i<size; i++) {					// Printing Graph
				for(j=0; j<size; j++)
					output.write("" + mat[i][j]);
				output.write("\n");
			}
			output.write(" ");
			
			int a[] = new int[size], x=0, y=0, temp=0;
                	for(i = 0; i <size; i++) 
                		a[i] = (i+1);
                	for(i=0; i<size*4; i++) {
				x = randomGen(r, size);
                	        y = randomGen(r, size);
                	        temp = a[x];
                	        a[x] = a[y];
                	        a[y] = temp;
                	}
			for(i=0; i<size; i++)					// Printing Random identifiers
				output.write("\n" + a[i]);
			output.close();
		}	
	}
	
	/**
	 * Generates a random number within some specified range.
	 *
	 * @param   r		instance of random number generator marked by a seed.
	 * @param   range	the acceptable variation for random number.
	 * @return		random number.
	 */
	public static int randomGen(Random r, int range) {
                int val = (r.nextInt()) % range;
                if(val < 0)
			return -val;
		else
			return val;
        }
}

/** 
 * Helps to generate the Sp-Min (Sparse minimum) and Sp-Max (Sparse maximum) graphs. 
 * A count for maximum number of children per node is also utilized.
 */
class SparseGraph 
{
	static int GRSIZE = 16, KVAL, count = 0, total = 0, array[], parentArray[], maxChild = 2;
	static int adjacencyMatrix[][];	
	static boolean edges = false, Kflag=false;
	int value, childNum, ID, parentID;
	SparseGraph [] children;

	/** 
	 * Helps in generating the input file by printing data 
	 * onto a user specified <code>fileName<\code>.
	 * 
	 * @param fileName 	name of the outputted inputfile.
	 * @param r	 	instance of random number generator marked by a seed.
	 * @throws Exception	if File handling operation illegal. 
	 */ 
	static void print(String fileName, Random r)	throws Exception {
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
  		output.write("" + GRSIZE + "\n");				// Printing size
  		output.write("" + KVAL + "\n");					// Printing K value
		for (i = 0 ; i < GRSIZE ; i ++) {				// Printing Graph
			for (int j = 0 ; j < GRSIZE; j ++) 
				output.write("" + adjacencyMatrix[i][j]);
			output.write("\n");
		}
		output.write(" ");
			
		int a[] = new int[GRSIZE], x=0, y=0, temp=0;
                	for(i = 0; i <GRSIZE; i++) 
                		a[i] = (i+1);
		inputGeneratorKcommittee ikc = new inputGeneratorKcommittee();                
               	for(i=0; i<GRSIZE*4; i++) {
			x = ikc.randomGen(r, GRSIZE);
               	        y = ikc.randomGen(r, GRSIZE);
               	        temp = a[x];
               	        a[x] = a[y];
               	        a[y] = temp;
               	}
			
		for(i=0; i<GRSIZE; i++)						// Printing Random identifiers
			output.write("\n" + a[i]);
					
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
			inputGeneratorKcommittee ikc = new inputGeneratorKcommittee();
			for(int i = 0; i < 2 * GRSIZE; i++) {
				int x = ikc.randomGen(r, GRSIZE);
				int y = ikc.randomGen(r, GRSIZE);
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
