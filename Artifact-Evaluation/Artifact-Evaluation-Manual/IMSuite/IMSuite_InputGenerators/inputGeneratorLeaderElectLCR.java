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
 * inputGeneratorLeaderElectLCR implements an input generator for generation
 * of input for the dominating set algorithm.
 * Following options are available with the input generator:
 * For input file name: -in
 * For giving input size: -sz
 * For Maximum number of children per node (In case of Sparse graph only) = -seed
 * For Seed of Random number = -seed
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
public class inputGeneratorLeaderElectLCR 
{
	/** 
	 * Acts as the starting point for the program execution. 
	 * <code>main</code> accepts the user choice for the type 
	 * of graph and generates the specified graph.
	 * 
	 * @param args 		array of runtime arguments.
	 * @throws Exception	if File handling operation illegal. 
	 */
	public static void main(String []args) throws IOException {
		int i=0;
		int size = 16, seed=101;		// By default
		String fileName = "inputleader_elect_lcr.txt";
		
		for(i=0; i<args.length; i++) {
			if(args[i].equals("-in")) {
				i++;
				fileName = args[i];
			}
			else if(args[i].equals("-sz")) {
				i++;
				size = Integer.parseInt(args[i]);
			}
			else if(args[i].equals("-seed"))
			{
				i++;
				seed = Integer.parseInt(args[i]);
			}		
		}
		
		int a[] = new int[size], x=0, y=0, temp=0;
		Random r = new Random(seed);
		
                for(i = 0; i <size; i++) 
                	a[i] = (i+1);
                for(i=0; i<size*4; i++) {
                        x = randomGen(size, r);
                        y = randomGen(size, r);
                        temp = a[x];
                        a[x] = a[y];
                        a[y] = temp;
                }
                
		Writer output = null;
  		output = new BufferedWriter(new FileWriter(fileName));		

		output.write("" + size);
		for(i=0; i<size; i++)
			output.write("\n" + a[i]);
		output.close();	
	}

	/**
	 * Generates a random number within some specified range.
	 *
	 * @param   range	the acceptable variation for random number.
	 * @param   r		instance of random number generator marked by a seed.
	 * @return		random number.
	 */	
	public static int randomGen(int range, Random r) {
                int val = (r.nextInt()) % range;
                if(val < 0)
			return -val;
		else
			return val;
        }
}
