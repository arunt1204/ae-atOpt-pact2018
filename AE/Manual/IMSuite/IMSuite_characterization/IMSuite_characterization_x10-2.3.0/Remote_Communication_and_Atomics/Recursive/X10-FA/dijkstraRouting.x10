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
 * dijkstraRouting aims to create a rounting table specific for each
 * node, consistingNodes can be classified as traitors and non-traitors. 
 * Traitors aim to disrupt the consensus.
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
public class dijkstraRouting {
	var adj_graph:Array[Int]; var nodes:Int;
	val Infinity = Int.MAX_VALUE;
	
	/** Abstract node representation as a distributed array. */
	var nodeSet:DistArray[Node];
	
	/** Region and Distribution specification. */
	var R: Region;	var D: Dist;
	
	/** Counter for measuring total asyncs (as a distributed array). */ 
	var casync:DistArray[Int];
	
	/** Counter for measuring total finishes (as a distributed array). */ 
	var cfinish:DistArray[Int];
	
	/** Counter for measuring total communication (as a distributed array). */ 
	var cmess:DistArray[Int];
	
	/** Counter for measuring total atomics (as a distributed array). */ 
	var catom:DistArray[Int];
	
	/** 
	 * Acts as the starting point for the program execution. 
	 * <code>main</code> performs the task of accepting the input from the user 
	 * specified file, calling the methods responsible for BFS tree creation, 
	 * printing the output and validating the result.
	 *
	 * @param args 		array of runtime arguments.
	 * @throws Exception	if File handling operation illegal. 
	 */
	public static def main(args:Array[String](1)) throws Exception {
		var inputFile:String = "inputdijkstraRouting.txt", outputFile:String = "outputdijkstraRouting.txt";
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
		var djr:dijkstraRouting = new dijkstraRouting();
		djr.nodes = Int.parse(s);
		djr.adj_graph = new Array[Int]((0..(djr.nodes-1))*(0..(djr.nodes-1)));
		var weightMatrix:Array[Int] = new Array[Int]((0..(djr.nodes-1))*(0..(djr.nodes-1)));

		/** Region creation. */				
		djr.R = 0..(djr.nodes-1);
		
		/** Creation of a Block Distribution. */
		djr.D = Dist.makeBlock(djr.R);
    		//djr.D = Dist.makeUnique();
		//djr.R = djr.D.region;
		
		/** Distribution of nodes. */
	  	djr.nodeSet = DistArray.make[Node](djr.D);
	  	
	  	/** Distribution of async counters. */
	  	djr.casync = DistArray.make[Int](djr.D);
	  	
	  	/** Distribution of finish counters. */
	  	djr.cfinish = DistArray.make[Int](djr.D);
	  	
	  	/** Distribution of communication counters. */
	  	djr.cmess = DistArray.make[Int](djr.D);
	  	
	  	/** Distribution of atomic counters. */
	  	djr.catom = DistArray.make[Int](djr.D);
	  	
		try {
			j=0;
			while(!((s = fr.readLine()).equals(" "))) {
				for(i=0; i<s.length(); i++) {
					var ch:Char=s.charAt(i);
					if(ch=='0')
						djr.adj_graph(j,i) = 0;
					else
						djr.adj_graph(j,i) = 1;	
				}
				j++;
			}
		} catch(eof: x10.io.EOFException){}
		
		try {
			j=0;
                	while(j<djr.nodes) {
                	        i=0;
                	        while(i<djr.nodes) {
                	                s = fr.readLine();
                	                weightMatrix(j,i) = Int.parse(s);
                	                i++;
                	        }
                	        j++;
                	}
		} catch(eof: x10.io.EOFException){}
						
		djr.initialize();

		djr.route(weightMatrix);
		
		/** Call to method for computing total Async, Finish, Communication and Atomics. */
		djr.countValue();
	}
	
	/** Computes total Async, Finish, Communication and Atomic Operations. */ 
	def countValue() {
		var smess:Int=0, sasync:Int=0, sfinish:Int=0, satom:Int=0;
		var temp:Int=0;
		
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
					var j:Int;	var count:Int=0;
					var index:Int = i.operator()(0);
					nodeSet(i) = new Node();
					nodeSet(i).id = index;
					nodeSet(i).routingTable = new Array[routingInfo]((0..(nodes-1)));
					for(j=0; j<nodes; j++) {
						nodeSet(i).routingTable(j) = new routingInfo();
							if(adj_graph(index,j) == 1)
								count++;
					}
					nodeSet(i).neighbors = new Array[Int]((0..(count-1)));
					count=0;
					for(j=0; j<nodes; j++)
						if(adj_graph(index,j) == 1) {
							nodeSet(i).neighbors(count) = j;
							count++;
						}	
				}
			}
		}
	}
	
	/** Generates the routing table for each node. */
	def route(val weightMatrix:Array[Int]) {
		val fpt:Point = Point.make(0);
		at(D(fpt)){
			/** Finish statements, increment counter for finish. */
			cfinish(fpt) = cfinish(fpt) + 1;
		}
		
		val h0 = here;
		
		finish {
			for(i in D) {
				async at(D(i)) {
					val h1 = here;
					atomic{
						/** If remote data access then increment counter for communication. */ 
						if(h0 != h1)
							cmess(i) = cmess(i) + 1;
						
						/** Async statements, increment counter for async. */	
						casync(i) = casync(i) + 1;
					}
					
					var j:Int; var minCost:Int; var node:Int=-1; var hCount:Int=0; var nrt:Int=0;
					val parent:Array[Int] = new Array[Int]((0..(nodes-1)));
					val notLeaf:Array[Boolean] = new Array[Boolean]((0..(nodes-1)));
					var nodeCovered:Array[Boolean] = new Array[Boolean]((0..(nodes-1)));
					var queue:ArrayList[routingInfo] = new ArrayList[routingInfo]();
					var index:Int = i.operator()(0);
			
					var rtf:routingInfo = new routingInfo();
					parent(index) = Infinity;
					rtf.setVlue(index, 0, 0, index);
					queue.add(rtf);
					
					while(queue.size() > 0) {
						minCost=Infinity;
						for(j=queue.size()-1; j>=0; j--) {
							rtf = queue.get(j);
							if(minCost > rtf.costToReach) {
								minCost = rtf.costToReach;
								node = rtf.nodeId;
								hCount = rtf.hopCount;
								nrt = rtf.nextNode;
							}
						}
						
						rtf = new routingInfo();
						rtf.setVlue(node, minCost, hCount, nrt);
						if(node != index) {
							parent(node) = nrt;
							notLeaf(parent(node)) = true;
						}
						nodeSet(i).routingTable(node) = rtf;
						nodeCovered(node) = true;
						for(j=queue.size()-1; j>=0; j--) {
							rtf = queue.get(j);
							if(rtf.nodeId == node)
								queue.remove(rtf);
						}
						val npt:Point = Point.make(node);
						var sz:Int = at(D(npt)) nodeSet(npt).neighbors.size;
						var neighborSet:Array[Int] = new Array[Int]((0..(sz-1)));
						neighborSet = at(D(npt)) nodeSet(npt).neighbors;
						
						at(D(npt)){
							val h2 = here;
							atomic{
								/** If remote data access then increment counter for communication. */ 
								if(h2 != h1)
									cmess(npt) = cmess(npt) + 2;
							}
						}	
						
						for(j=0; j<sz; j++) {
							if(!nodeCovered(neighborSet(j))) {
								rtf = new routingInfo();
								rtf.setVlue(neighborSet(j), minCost + weightMatrix(node, neighborSet(j)), hCount+1, node);
								queue.add(rtf);			
							}
						}
					}    
					
					val idx:Int = i.operator()(0);
					atomic{
						/** Finish statements, increment counter for finish. */
						cfinish(i) = cfinish(i) + 1;
					}	
					finish {
						for(k in 0..(nodeSet(i).neighbors.size-1)) {
							async {
								atomic {
									/** Async statements, increment counter for async. */
									casync(i) = casync(i) + 1;
								}
					
								if(parent(nodeSet(i).neighbors(k)) == idx) {
									val pt:Point = Point.make(nodeSet(i).neighbors(k));
									neighborTask(i, pt, parent, notLeaf);
									nodeSet(i).routingTable(nodeSet(i).neighbors(k)).nextNode = idx;
								}
							}
						}
					} 
				}
			}
		}						
	}
	
	/**
	 * Associates each neighbor of a node to provide the next node information.
	 *
	 * @param  sender	node whose routing table has to be updated
	 * @param  aNode	neighbor of <code>sender<\code> which needs to update information
	 * @param  parent	information about parent of a node according to <code>sender<\code>
	 * @param  notLeaf	tells which node is leaf or not.	
	 */
	def neighborTask(val sender:Point, val aNode:Point, val parent:Array[Int], val notLeaf:Array[Boolean]) {
		val h0 = here;
		
		at(D(aNode)){
			val h1 = here;
			atomic{
				/** If remote data access then increment counter for communication. */ 
				if(h0 != h1)
					cmess(aNode) = cmess(aNode) + 1;
			}
					
			var ipt:Int = aNode.operator()(0);
			for(var k:Int=0; k<nodeSet(aNode).neighbors.size; k++)
				if(parent(nodeSet(aNode).neighbors(k)) == ipt) {
					val pt:Point = Point.make(nodeSet(aNode).neighbors(k));
					findNextNode(sender, aNode, pt, parent, notLeaf);
				}	
		}			
	}
	
	/**
	 * Provides the next node information.
	 * @param  pNode	node whose routing table has to be updated
	 * @param  uNode	node which acts as the next node information
	 * @param  aNode	node whose entry in <code>pNode<\code> routing table has to be updated 
	 * @param  parent	information about parent of a node according to <code>sender<\code>
	 * @param  notLeaf	tells which node is leaf or not.	
	 */
	def findNextNode(val pNode:Point, val uNode:Point, val aNode:Point, val parent:Array[Int], val notLeaf:Array[Boolean]) {
		val h0 = here;
		
		at(D(pNode)){
			val h1 = here;
			atomic{
				/** If remote data access then increment counter for communication. */ 
				if(h0 != h1)
					cmess(pNode) = cmess(pNode) + 1;
			}
		
			val uindex:Int = uNode.operator()(0);
			val aindex:Int = aNode.operator()(0);
			val pindex:Int = pNode.operator()(0);
			if(!notLeaf(aindex)) {
				nodeSet(pNode).routingTable(aindex).nextNode = uindex;
				return;
			}
			else {
				nodeSet(pNode).routingTable(aindex).nextNode = uindex;
				
				at(D(aNode)){
					val h2 = here;
					atomic{
						/** If remote data access then increment counter for communication. */ 
						if(h2 != h1)
							cmess(aNode) = cmess(aNode) + 1;
						
						/** Finish statements, increment counter for finish. */	
						cfinish(aNode) = cfinish(aNode) + 1;	
					}
			  
					finish {
						for(j in 0..(nodeSet(aNode).neighbors.size-1)) {
							async {
								atomic{
									/** Async statements, increment counter for async. */
									casync(aNode) = casync(aNode) + 1;
								}
								
								if(parent(nodeSet(aNode).neighbors(j)) == aindex) {
									val pt:Point = Point.make(nodeSet(aNode).neighbors(j));
									findNextNode(pNode, uNode, pt, parent, notLeaf);
								}	
							}
						}	
					}
				}	
			}
		}
	}	
}

/** States the structure for the routing table. */
class routingInfo
{
	/** Specifies identifier of the destination node. */
	var nodeId:Int;
	
	/** Specifies the hops needed to reach the destination node. */ 
	var hopCount:Int;
	
	/** Specifies the cost of the path to reach source. */
	var costToReach:Int;
	
	/** Identifies the first node on path to destination node from source. */
	var nextNode:Int;
	
	def setVlue(var nodeId:Int, var costToReach:Int, var hopCount:Int, var nextNode:Int) {
		this.nodeId = nodeId;
		this.costToReach = costToReach;
		this.hopCount = hopCount;
		this.nextNode = nextNode;
	}
}

/**
 * <code>Node</code> specifies the structure for each abstract node
 * part of the Dijkstra Routing algorithm.
 */
class Node
{
	/** Specifies identifier of the source router. */ 
	var id:Int;
	
	/** Specifies neighbors of the router. */
	var neighbors:Array[Int];
	
	/** Routing table structure. */							
	var routingTable:Array[routingInfo];
	
	/** Holder for receiving the messages. */
	var messageHolder:ArrayList[routingInfo] = new ArrayList[routingInfo]();
}
