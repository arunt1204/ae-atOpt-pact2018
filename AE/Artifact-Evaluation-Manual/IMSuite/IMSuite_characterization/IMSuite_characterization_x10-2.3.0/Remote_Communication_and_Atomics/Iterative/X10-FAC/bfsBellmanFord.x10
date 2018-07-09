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
	var nodes:Int, root:Int, adj_graph:Array[Int]; 
	val Infinity = Int.MAX_VALUE;
	var diameter:Int;
	
	/** Abstract node representation as a distributed array. */
	var nodeSet:DistArray[Node];
	
	/** Region and Distribution specification. */
	var R: Region;	var D: Dist;

	/** Counter for measuring total communication (as a distributed array). */ 
	var cmess:DistArray[Int];
	
	/** Counter for measuring total atomics (as a distributed array). */ 
	var ciso:DistArray[Int];
	
	/** 
	 * Acts as the starting point for the program execution. 
	 * <code>main</code> performs the task of accepting the input from the user 
	 * specified file, calling the <code>bfsForm</code>, printing the output
	 * and validating the result.
	 *
	 * @param args 		array of runtime arguments.
	 * @throws Exception	if File handling operation illegal. 
	 */
	public static def main(args:Array[String](1)) throws Exception {
		var inputFile:String = "inputbfsBellman16.txt", outputFile:String = "outputbfsBellman.txt";
		var i:Int,j:Int;
		var flag:Boolean = false;
		
		for(i=0; i<args.size; i++) {
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
			else
				Console.OUT.println("Wrong option specified");		
		}

		var fl:File = new File(inputFile);
		var fr:FileReader = new FileReader(fl);
		var s:String = fr.readLine();
		var bfs:bfsBellmanFord = new bfsBellmanFord();
		bfs.nodes = Int.parse(s);
		s = fr.readLine();
		bfs.root = Int.parse(s);
		bfs.adj_graph = new Array[Int]((0..(bfs.nodes-1))*(0..(bfs.nodes-1)), 0);
		
		try {
			j=0;
			while((s = fr.readLine()) != null) {
				for(i=0; i<s.length(); i++) {
					var ch:Char=s.charAt(i);
					if(ch=='0')
						bfs.adj_graph(j,i) = 0;
					else
						bfs.adj_graph(j,i) = 1;	
				}
				j++;
			}
		} catch(eof: x10.io.EOFException){}
		
		/** Region creation. */
		bfs.R = 0..(bfs.nodes-1);
		
		/** Creation of a Block Distribution. */
		bfs.D = Dist.makeBlock(bfs.R);
    		//bfs.D = Dist.makeUnique();
    		//bfs.R = bfs.D.region;
    		
    		/** Distribution of nodes. */
	  	bfs.nodeSet = DistArray.make[Node](bfs.D);

		/** Distribution of communication counters. */
	  	bfs.cmess = DistArray.make[Int](bfs.D);
	  	
	  	/** Distribution of atomic counters. */
	  	bfs.ciso = DistArray.make[Int](bfs.D);
	  	
	  	bfs.diameter = bfs.getDiameter();
		bfs.initialize();

		bfs.Start();
		for(i=0; i<bfs.diameter; i++) {
			bfs.bfsForm();
		}
		
		/** Call to method for computing total Communication and Atomics. */
		bfs.messCount();	
	}
	
	/** Computes total Communication and Atmoic Operations. */ 
	def messCount() {
		var msum:Int=0, isum:Int=0;
		for(var i:Int=0; i<nodes; i++) {
			val pt:Point = Point.make(i);
			var nvalue:Int = at(D(pt)) cmess(pt);
			msum = msum + nvalue;
			
			var isovalue:Int = at(D(pt)) ciso(pt);
			isum = isum + isovalue;
		}
		Console.OUT.println(msum);
		Console.OUT.println(isum);
	}
	
	/** Initializes all the fields of the abstract node. */ 
	def initialize() {
		finish {
			for(i in D) {
				async at(D(i)) {
					var j:Int; var counter:Int=0;
					nodeSet(i) = new Node();
					var idx: Int = i.operator()(0);
					for(j=0; j<nodes; j++)
						if(adj_graph(idx,j) == 1)
							counter++;
					nodeSet(i).neighbors = new Array[Int]((0..(counter-1)));
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
	
	/** Marks the start of BFS creation with root setting distance for its neighbors. */
	def Start() {
		val h0 = here;
		val pt:Point = Point.make(root);
		at(D(pt)){
			val h1 = here;
			
			/** If remote data access then increment counter for communication. */
			if(h1 != h0)
				cmess(pt) = cmess(pt) + 1;
			
			for(var j:Int=0; j<nodeSet(pt).neighbors.size; j++) {
				var ipt:Point = Point.make(nodeSet(pt).neighbors(j));
				setDistance(ipt, nodeSet(pt).distance+1);
			}
		}		
	}
	
	/** 
	 * Performs the task of creating BFS. Each node sends its value of distance from
	 * the root to all its neighbors. A node updates its distance variable only if it
	 * receives a value smaller than the existing.
	 */
	def bfsForm() {
		val h0 = here;
		
		clocked finish {
			for(i in D) {
				clocked async at(D(i)) {
					val h1 = here;
					
					/** 
					 * Checking for remote data access in isolation.
					 * If remote data access then increment counter for communication.
					 */
					atomic{
						if( h1 != h0)
							cmess(i) = cmess(i) + 1;
					}
					
					for(var j:Int=0; j<nodeSet(i).tempMessageHolder.size(); j++)
						nodeSet(i).messageHolder.add(nodeSet(i).tempMessageHolder.get(j));
					nodeSet(i).tempMessageHolder.clear();
					
					Clock.advanceAll();
					
					var mn:Int = Infinity;
					for(var j:Int=0; j<nodeSet(i).messageHolder.size(); j++) {
						var message:Int = nodeSet(i).messageHolder.get(j);
						if(mn > message)
							mn = message;
					}		
					if(mn < nodeSet(i).distance) {
						nodeSet(i).distance = mn;		
						for(var j:Int=0; j<nodeSet(i).neighbors.size; j++) {
							var pt:Point = Point.make(nodeSet(i).neighbors(j));
							setDistance(pt, nodeSet(i).distance+1);
						}
					}
					nodeSet(i).messageHolder.clear();
				}
			}
		}
	}
	
	/**
	 * Calculates the diameter for the graph from the root by selecting the 
	 * maximum distance of a node from the root.
	 *
	 * @return	Value of diameter form the root.
	 */
	def getDiameter():Int {
		var distanceMat:Array[Int] = new Array[Int]((0..(nodes-1)));
		distanceMat = getDistance();
		var diameter:Int=Int.MIN_VALUE;
		for(var i:Int=0; i<nodes; i++)
			if(diameter < distanceMat(i))
				diameter = distanceMat(i);
		return diameter;
	}
	
	/**
	 * Calculates for each node its distance from the root.
	 * 
	 * @return	Set of distances from the root.
	 */
	def getDistance():Array[Int] {
		var distanceMat:Array[Int] = new Array[Int]((0..(nodes-1))); var i:Int=0;
		var flag:boolean = false;
		var queue:ArrayList[Int] = new ArrayList[Int]();
		for(i=0; i<nodes; i++)
			distanceMat(i) = Infinity;
		queue.add(root);
		distanceMat(root) = 0;
		while(queue.size() > 0) {
			var anode:Int = queue.get(0);
			queue.remove(anode);
			for(i=0; i<nodes; i++)
				if(adj_graph(anode, i) == 1 && distanceMat(i) == Infinity) {
					distanceMat(i) = distanceMat(anode)+1;
					queue.add(i);
				}
		}
		return distanceMat;
	}
	
	/** 
	 * Sends the distance value to a neighbor.
	 *
	 * @param neighbor	Receiving node.
	 * @param distance	Value to be sent.
	 */
	def setDistance(val neighbor:Point, val distance:Int) {
		val h0 = here;
		at(D(neighbor)){
			val h1 = here;
			atomic {
				/** If remote data access then increment counter for communication. */ 
				if( h1 != h0)
					cmess(neighbor) = cmess(neighbor) + 1;
				
				/** Inside existing atomic operation, increment counter for atomic. */	
				ciso(neighbor) = ciso(neighbor) + 1;
				
				nodeSet(neighbor).tempMessageHolder.add(distance); 
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
	var distance:Int;
	
	/** Tracks all the neighbors of a node.	*/
	var neighbors:Array[Int];
	
	/** Holds the message sent by the neighbors. */	
	var messageHolder:ArrayList[Int] = new ArrayList[Int]();
	var tempMessageHolder:ArrayList[Int] = new ArrayList[Int]();
}	
