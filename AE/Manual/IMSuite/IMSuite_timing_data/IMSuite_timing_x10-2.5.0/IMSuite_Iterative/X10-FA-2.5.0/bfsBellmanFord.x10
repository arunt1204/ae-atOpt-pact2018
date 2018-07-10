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
	var diameter:Long;

	/** Parameters to enable execution with load */
	var loadValue:long=0; 

	/** Load sum represented as a distributed array. */
	var nval:DistArray[long];
	
	/** Abstract node representation as a distributed array. */
	var nodeSet:DistArray[Node];
	
	/** Region and Distribution specification. */
	var R: Region;	var D: Dist;
	
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
			else if(args(i).equals("-lfon")) {
				bfs.loadValue = Long.parse(args(i+1));
				i++;
			}	
			else
				Console.OUT.println("Wrong option specified");		
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
		bfs.R = Region.make(0, (bfs.nodes-1));
		
		/** Creation of a Block Distribution. */
		bfs.D = Dist.makeBlock(bfs.R);
    		//bfs.D = Dist.makeUnique();
    		//bfs.R = bfs.D.region;
    		
    		/** Distribution of nodes. */
	  	bfs.nodeSet = DistArray.make[Node](bfs.D);
	  
		bfs.nval = DistArray.make[long](bfs.D);
	
	  	bfs.diameter = bfs.getDiameter();
		bfs.initialize();

		var startTime:long = System.nanoTime();
		bfs.Start();
		for(i=0; i<bfs.diameter; i++)
			bfs.bfsForm();
		var finishTime:long = System.nanoTime();
		var estimatedTime:long = finishTime - startTime;
		//Console.OUT.println("Start Time: " + startTime + "\t Finish Time: " + finishTime + "\t Estimated Time: " + estimatedTime);
		
		bfs.printOutput(outputFile);
		if(flag)
			bfs.outputVerifier();

		if(!bfs.loadValue.equals(0)) {
			var sumval:double=0;
			for(i=0; i<bfs.nodes; i++) {
				val pt:Point = Point.make(i);
				var arrSum:Long = bfs.getNval(pt); 
				sumval = sumval + arrSum;
			}

			if(sumval > 0)
				Console.OUT.println();
		}
	}

	/**
	 * Aims to busy the threads by introducing the no-op instructions
	 * equivalent to the amount of load specified.
	 *
	 * @param  weight	Specifies the current load value for a thread.
	 * @return 		Updated load value.
	 */
	def loadweight(val weight:Long):Long {
		var j:Long=0;
		for(var i:Long=0; i<loadValue; i++)
			j++;
		return j+weight;
	}

	def getNval(val pt:Point):Long {
		var sum:Long = at(D(pt)) nval(pt);
		return sum;
	}

	/** Initializes all the fields of the abstract node. */ 
	def initialize() {
		finish {
			for(i in D) {
				async at(D(i)) {
					var j:Long; var counter:Long=0;
					nodeSet(i) = new Node();
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

	/** Marks the start of BFS creation with root setting distance for its neighbors. */	
	def Start() {
		val pt:Point = Point.make(root);
		at(D(pt)){
			for(var j:Long=0; j<nodeSet(pt).neighbors.size; j++) {
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
		finish {
			for(i in D) {
				async at(D(i)) {
					for(var j:Long=0; j<nodeSet(i).tempMessageHolder.size(); j++)
						nodeSet(i).messageHolder.add(nodeSet(i).tempMessageHolder.get(j));
					nodeSet(i).tempMessageHolder.clear();
	
					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
					}
				}
			}
		}
		
		finish {
			for(i in D) {
				async at(D(i)) {
					var mn:Long = Infinity;
					for(var j:Long=0; j<nodeSet(i).messageHolder.size(); j++) {
						var message:Long = nodeSet(i).messageHolder.get(j);
						if(mn > message)
							mn = message;
					}		
					if(mn < nodeSet(i).distance) {
						nodeSet(i).distance = mn;		
						for(var j:Long=0; j<nodeSet(i).neighbors.size; j++) {
							var pt:Point = Point.make(nodeSet(i).neighbors(j));
							setDistance(pt, nodeSet(i).distance+1);
						}
					}
					nodeSet(i).messageHolder.clear();

					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
					}
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
	def getDiameter():Long {
		var distanceMat:Rail[Long] = new Rail[Long](nodes);
		distanceMat = getDistance();
		var diameter:Long=Long.MIN_VALUE;
		for(var i:Long=0; i<nodes; i++)
			if(diameter < distanceMat(i))
				diameter = distanceMat(i);
		return diameter;
	}
	
	/**
	 * Calculates for each node its distance from the root.
	 * 
	 * @return	Set of distances from the root.
	 */
	def getDistance():Rail[Long] {
		var distanceMat:Rail[Long] = new Rail[Long](nodes); var i:Long=0;
		var flag:boolean = false;
		var queue:ArrayList[Long] = new ArrayList[Long]();
		for(i=0; i<nodes; i++)
			distanceMat(i) = Infinity;
		queue.add(root);
		distanceMat(root) = 0;
		while(queue.size() > 0) {
			var anode:Long = queue.get(0);
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
	def setDistance(val neighbor:Point, val distance:Long) {
		at(D(neighbor)){
			atomic{ nodeSet(neighbor).tempMessageHolder.add(distance); }
		}
	}
	
	/** 
	 * Writes the output to the user specified file.
	 * 
	 * @param  fileName	Name of the file in which output has to be stored.
	 * @throws 		input output exception if a failure in write occurs.
	 */
	def printOutput(var fileName:String) {
		try {
			var str:String;
	  		var fl:File = new File(fileName);
			var fw:FileWriter = new FileWriter(fl);
			for(var i:Long=0; i<nodes; i++) {
				val pt:Point = Point.make(i);
				val dist:Long = at (D(pt)) nodeSet(pt).distance;
				str = " Node " + i + " : " + "Distance from root: " + dist;
				for(var j:Long=0; j<str.length(); j++) {
					var jInt:Int = j as Int;
					var ch:Char = str.charAt(jInt);
					fw.writeChar(ch);
				}
				fw.writeChar('\n');
			}
			fw.close();
		}
		catch(ex: x10.lang.Exception){}	
	}
	
	/** Validates the output resulting from the execution of the algorithm. */
	def outputVerifier() {
		var distanceMat:Rail[Long] = new Rail[Long](nodes); 
		var flag:Boolean = false;
		for(var i:Long=0; i<nodes; i++)
			distanceMat(i) = Infinity;
		var queue:ArrayList[Long] = new ArrayList[Long]();
		queue.add(root);
		distanceMat(root) = 0;
		var anode:Long=0;
		
		while(queue.size() > 0) {
			anode = queue.get(0);
			queue.remove(anode);
			val pt:Point = Point.make(anode);
			var nset:Rail[Long] = at(D(pt)) nodeSet(pt).neighbors;
			for(var i:Long=0; i<nset.size; i++) {
				if(distanceMat(nset(i)) == Infinity) {
					distanceMat(nset(i)) = distanceMat(anode)+1;
					queue.add(nset(i));
				}
			}	
		}
		
		for(var i:Long=0; i<nodes; i++) {
			val pt:Point = Point.make(i);
			val dist:Long = at(D(pt)) nodeSet(pt).distance;
			if(distanceMat(i) != dist)
				flag = true;
		}		
		if(!flag)
			Console.OUT.println("Output verified");
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
	
	/** Holds the message sent by the neighbors. */	
	var messageHolder:ArrayList[Long] = new ArrayList[Long]();
	var tempMessageHolder:ArrayList[Long] = new ArrayList[Long]();
}
