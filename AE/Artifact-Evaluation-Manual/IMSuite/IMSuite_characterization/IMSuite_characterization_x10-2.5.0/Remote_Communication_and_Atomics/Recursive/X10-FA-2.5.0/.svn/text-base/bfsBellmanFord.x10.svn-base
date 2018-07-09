/* This file is part of IMSuite Benchamark Suite.
 * 
 * This file is licensed to You under the Eclipse Public License (EPL);
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.opensource.org/licenses/eclipse-1.0.php
 *
 * (C) Copyright IMSuite 2013-present.
 */

import x10.io.File;
import x10.io.FileReader;
import x10.io.FileWriter;
import x10.regionarray.Array;
import x10.regionarray.Dist;
import x10.regionarray.DistArray;
import x10.regionarray.Region;
import x10.util.*;

/** 
 * bfsBellmanFord implements the breadth first algorithm using the Bellman Ford
 * approach. The aim here is to output the distance of every node from the 
 * root. 
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
public class bfsBellmanFord
{
	var nodes:Long, root:Long, adj_graph:Array[Long]; 
	val Infinity = Long.MAX_VALUE;
	
	/** Abstract node representation as a distributed array. */
	var nodeSet:DistArray[Node];

	/** Region and Distribution specification. */
	var R: Region;	var D: Dist;

	/** Counter for measuring total asyncs (as a distributed array). */ 
	var casync:DistArray[Long];
	
	/** Counter for measuring total finishes (as a distributed array). */ 
	var cfinish:DistArray[Long];
	
	/** Counter for measuring total communication (as a distributed array). */ 
	var cmess:DistArray[Long];
	
	/** Counter for measuring total atomics (as a distributed array). */ 
	var catom:DistArray[Long];
	
	/** 
	 * Acts as the starting point for the program execution. 
	 * <code>main</code> performs the task of accepting the input from the user 
	 * specified file, calling the <code>bfsForm</code>, printing the output
	 * and validating the result.
	 *
	 * @param args 		array of runtime arguments.
	 * @throws Exception	if File handling operation illegal. 
	 */
	public static def main(args:Rail[String]) throws Exception {
		var inputFile:String = "inputbfsBellman16.txt", outputFile:String = "outputbfsBellman.txt";
		var i:Long,j:Long;
		var flag:Boolean = false;
		var bfs:bfsBellmanFord = new bfsBellmanFord();

		for(i=0; i<args.size; i++)
		{
			if(args(i).equals("-ver") || args(i).equals("-verify"))
				flag = true;
			else if(args(i).equals("-in")) {
				inputFile = args(i+1);
				i++;
			}	
			else if(args(i).equals("-out")) {
				outputFile = args(i+1);
				i++;
			}
			else if(args(i).equals("-lfon")) {
				i++;
			}	
			else
				Console.OUT.println("Wrong option spcified");		
		}

		var fl:File = new File(inputFile);
		var fr:FileReader = new FileReader(fl);
		var s:String = fr.readLine();
		bfs.nodes = Long.parse(s);
		s = fr.readLine();
		bfs.root = Long.parse(s);
		bfs.adj_graph = new Array[Long](Region.make(0..(bfs.nodes-1), 0..(bfs.nodes-1)), 0);
		
		try {
			j=0;
			while((s = fr.readLine()) != null) {
				for(i=0; i<s.length(); i++) {
					var iInt:Int = i as Int;
					var ch:Char=s.charAt(iInt);
					if(ch=='0')
						bfs.adj_graph(j,i) = 0;
					else
						bfs.adj_graph(j,i) = 1;	
				}
				j++;
			}
		} catch(eof: x10.io.EOFException){}
		
		/** Region creation. */
		bfs.R = Region.make(0,(bfs.nodes-1));
		
		/** Creation of a Block Distribution. */
    		bfs.D = Dist.makeBlock(bfs.R);
    		//bfs.D = Dist.makeUnique();
    		//bfs.R = bfs.D.region;
    		
    		/** Distribution of nodes. */
	  	bfs.nodeSet = DistArray.make[Node](bfs.D);
	  
		/** Distribution of async counters. */
	  	bfs.casync = DistArray.make[Long](bfs.D);
	  	
	  	/** Distribution of finish counters. */
	  	bfs.cfinish = DistArray.make[Long](bfs.D);
	  	
	  	/** Distribution of communication counters. */
	  	bfs.cmess = DistArray.make[Long](bfs.D);
	  	
	  	/** Distribution of atomic counters. */
	  	bfs.catom = DistArray.make[Long](bfs.D);

		bfs.initialize();

		val rt: Point = Point.make(bfs.root);
		bfs.bfsForm(rt);

		/** Call to method for computing total Async, Finish, Communication and Atomics. */
		bfs.countValue();
	}

	/** Computes total Async, Finish, Communication and Atomic Operations. */ 
	def countValue() {
		var smess:Long=0, sasync:Long=0, sfinish:Long=0, satom:Long=0;
		var temp:Long=0;
		
		for(i in D) {
			temp = at(D(i)) cmess(i);
			smess = smess+temp;
			temp = at(D(i)) casync(i);
			sasync = sasync+temp;
			temp = at(D(i)) catom(i);
			satom = satom+temp;
			temp = at(D(i)) cfinish(i);
			sfinish = sfinish+temp;
		}
		
		Console.OUT.println(sfinish);
		Console.OUT.println(sasync);
		Console.OUT.println(smess);
		Console.OUT.println(satom);
	}

	/** Initializes all the fields of the abstract node. */ 	
	def initialize() {
		finish {
			for(i in D) {
				async at(D(i)) {
					var j:Long; var counter:Long=0;
					nodeSet(i) = new Node();
					nodeSet(i).visited = false;
					var idx: Long = i.operator()(0);
					for(j=0; j<nodes; j++)
						if(adj_graph(idx,j) == 1)
							counter++;
					nodeSet(i).neighbors = new Rail[Long](counter);
					counter=0;
					for(j=0; j<nodes; j++)
						if(adj_graph(idx,j) == 1) {
					 		nodeSet(i).neighbors(counter) = j;
					 		counter++;
					 	}
					if(idx != root)					
						nodeSet(i).distance=Infinity;
					else
						nodeSet(i).distance=0;
				}
			}
		}
	}

	/** 
	 * Performs the task of creating BFS. Each node sends its value of distance from
	 * the root to all its neighbors. A node updates its distance variable only if it
	 * receives a value smaller than the existing.
	 */	
	def bfsForm(val aNode:Point) {
		var flag:Boolean= false;

		val h0 = here;
		flag = at(D(aNode)) {
			val h1 = here;

			var lflag: Boolean = false;
			atomic {
				/** If remote data access then increment counter for communication. */ 
				if(h0 != h1)
					cmess(aNode) = cmess(aNode) + 1;
				
				/** Inside existing atomic operation, increment counter for atomic. */		
				catom(aNode) = catom(aNode) + 1;

				if(!nodeSet(aNode).visited)
					lflag = true;
			}
			lflag
		};
		
		if(flag) {
			at(D(aNode)) {
				val h1 = here;
				atomic{ 
					/** If remote data access then increment counter for communication. */ 
					if(h0 != h1)
						cmess(aNode) = cmess(aNode) + 1;
						
					/** Inside existing atomic operation, increment counter for atomic. */		
					catom(aNode) = catom(aNode) + 1;
					
					/** Finish statements, increment counter for finish. */
					cfinish(aNode) = cfinish(aNode) + 2;

					nodeSet(aNode).visited = true; 
				}
				
				finish {
					for(i in 0..(nodeSet(aNode).neighbors.size-1)) {
						async {
							atomic {
								/** Async statements, increment counter for async. */
								casync(aNode) = casync(aNode) + 1;
							}

							var pt: Point = Point.make(nodeSet(aNode).neighbors(i));
							setDistance(pt, nodeSet(aNode).distance);
						}
					}
				}
				
				finish {
					for(i in 0..(nodeSet(aNode).neighbors.size-1)) {
						async {
							atomic {
								/** Async statements, increment counter for async. */
								casync(aNode) = casync(aNode) + 1;
							}

							var pt: Point = Point.make(nodeSet(aNode).neighbors(i));
							bfsForm(pt);	
						}
					}
				}
			}	
		}
	}
	
	/** 
	 * Sends the distance value to a neighbor.
	 *
	 * @param neighbor	Receiving node.
	 * @param distance	Value to be sent.
	 */
	def setDistance(val neighbor:Point, val distance:Long) {
		val h0 = here;
		at(D(neighbor)) {
			val h1 = here;
			atomic {
				/** If remote data access then increment counter for communication. */ 
				if(h0 != h1)
					cmess(neighbor) = cmess(neighbor) + 1;
				
				/** Inside existing atomic operation, increment counter for atomic. */					
				catom(neighbor) = catom(neighbor) + 1;

				if(nodeSet(neighbor).distance > distance) {	
					nodeSet(neighbor).distance = distance+1;
					nodeSet(neighbor).visited = false;
				}
			}
		}
	}
}

/**
 * <code>Node</code> specifies the structure for each abstract node
 * part of the BFS Bellman Ford algorithm.
 */
class Node
{
	/** Tells distance of a node from the root. */
	var distance:Long;
	
	/** Tracks all the neighbors of a node.	*/
	var neighbors:Rail[Long];
	
	/** Specifies if a node has already been visited or not. */	
	var visited:Boolean;
}
