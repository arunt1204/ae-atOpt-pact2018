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
 * dijkstraRouting aims to create a rounting table specific for each
 * node, consistingNodes can be classified as traitors and non-traitors. 
 * Traitors aim to disrupt the consensus.
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
public class dijkstraRouting {
	var adj_graph:Array[Long]; var nodes:Long;
	val Infinity = Long.MAX_VALUE;

	/** Abstract node representation as a distributed array. */
	var nodeSet:DistArray[Node];

	/** Region and Distribution specification. */
	var R: Region;	var D: Dist;
	
	/** Counter for measuring total communication (as a distributed array). */ 
	var cmess:DistArray[Long];

	var sumMess:DistArray[Long];
	
	/** 
	 * Acts as the starting point for the program execution. 
	 * <code>main</code> performs the task of accepting the input from the user 
	 * specified file, calling the methods responsible for BFS tree creation, 
	 * printing the output and validating the result.
	 *
	 * @param args 		array of runtime arguments.
	 * @throws Exception	if File handling operation illegal. 
	 */
	public static def main(args:Rail[String]) throws Exception {
		var inputFile:String = "inputdijkstraRouting.txt", outputFile:String = "outputdijkstraRouting.txt";
		var i:Long,j:Long;
		var flag:Boolean = false;
		var djr:dijkstraRouting = new dijkstraRouting();
		
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
				Console.OUT.println("Wrong option spcified");		
		}

		var fl:File = new File(inputFile);
		var fr:FileReader = new FileReader(fl);
		var s:String = fr.readLine();
		djr.nodes = Long.parse(s);
		djr.adj_graph = new Array[Long](Region.make(0..(djr.nodes-1), 0..(djr.nodes-1)));
		var weightMatrix:Array[Long] = new Array[Long](Region.make(0..(djr.nodes-1), 0..(djr.nodes-1)));

		/** Region creation. */		
		djr.R = Region.make(0, (djr.nodes-1));
		
		/** Creation of a Block Distribution. */
		djr.D = Dist.makeBlock(djr.R);
    		//djr.D = Dist.makeUnique();
		//djr.R = djr.D.region;
		
		/** Distribution of nodes. */
	  	djr.nodeSet = DistArray.make[Node](djr.D);

		/** Distribution of communication counters. */
		djr.cmess = DistArray.make[Long](djr.D);

		djr.sumMess = DistArray.make[Long](djr.D);

		try {
			j=0;
			while(!((s = fr.readLine()).equals(" "))) {
				for(i=0; i<s.length(); i++) {
					var iInt:Int = i as Int;
					var ch:Char=s.charAt(iInt);
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
                	                weightMatrix(j,i) = Long.parse(s);
                	                i++;
                	        }
                	        j++;
                	}
		} catch(eof: x10.io.EOFException){}
		
		djr.initialize();

		djr.route(weightMatrix);
	}

	/** 
	 * Totals up the communication.
	 *
	 * @param sumVal	The value to be added.
	 * @param tnode		Node which sums up the values..
	 */
	def sumfunction(val sumval:Long, val tnode:Point) {
		at(D(tnode)){
			atomic{
				sumMess(tnode) = sumMess(tnode) + sumval;
			}
		}
	}


	/** Initializes all the fields of the abstract node. */ 	
	def initialize() {
		finish {
			for(i in D) {
				async at(D(i)) {
					var j:Long;	var count:Long=0;
					var index:Long = i.operator()(0);
					nodeSet(i) = new Node();
					nodeSet(i).id = index;
					nodeSet(i).routingTable = new Rail[routingInfo](nodes);
					for(j=0; j<nodes; j++) {
						nodeSet(i).routingTable(j) = new routingInfo();
							if(adj_graph(index,j) == 1)
								count++;
					}
					nodeSet(i).neighbors = new Rail[Long](count);
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
	
	/** 
	 * Generates the routing table for each node.
	 *
	 * @param weightMatrix		Weight matrix.
	 */
	def route(val weightMatrix:Array[Long]) {
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

					var j:Long; var minCost:Long; var node:Long=-1; var hCount:Long=0; var nrt:Long=0; var maxHop:Long=0;
					val parent:Rail[Long] = new Rail[Long](nodes);
					val notLeaf:Rail[Boolean] = new Rail[Boolean](nodes);
					var nodeCovered:Rail[Boolean] = new Rail[Boolean](nodes);
					var queue:ArrayList[routingInfo] = new ArrayList[routingInfo]();
					var index:Long = i.operator()(0);
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
						if(maxHop < hCount)
							maxHop = hCount;
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

						at(D(npt)){
							atomic{
								val h2 = here;
								if(h2 != h1)
									cmess(npt) = cmess(npt) + 2;
							}
						}

						var sz:Long = at(D(npt)) nodeSet(npt).neighbors.size;
						var neighborSet:Rail[Long] = new Rail[Long](sz);
						neighborSet = at(D(npt)) nodeSet(npt).neighbors;
						for(j=0; j<sz; j++) {
							if(!nodeCovered(neighborSet(j))) {
								rtf = new routingInfo();
								rtf.setVlue(neighborSet(j), minCost + weightMatrix(node, neighborSet(j)), hCount+1, node);
								queue.add(rtf);			
							}
						}
						
						val spt:Point = Point.make(0);
						sumfunction(cmess(i), spt);
						
						Clock.advanceAll();
						
						if(i.equals(spt))
							Console.OUT.println(sumMess(i));
						cmess(i) = 0;
						sumMess(i) = 0;
						
						Clock.advanceAll();
					}    
					
					val nextNode:Rail[Long] = new Rail[Long](nodes);
					val tempNextNode:Rail[Long] = new Rail[Long](nodes);
					for(j=0; j<nodes; j++) {
						nextNode(j) = Infinity;
						tempNextNode(j) = Infinity;
					}	
					for(j=0; j<nodeSet(i).neighbors.size; j++)
						if(parent(nodeSet(i).neighbors(j)) == index)
							nextNode(nodeSet(i).neighbors(j)) = nodeSet(i).neighbors(j);
					
					j=0;
					while(j<maxHop) {
						if(j<maxHop)
						{
							for(var k:Long=0; k<nodes; k++)
								if(nextNode(k) != Infinity) {
									val pt:Point = Point.make(k);
									val neighNode:Rail[Long] = getNeighbors(pt);
									for(var kk:Long=0; kk<neighNode.size; kk++)
										if(parent(neighNode(kk)) == k)
											tempNextNode(neighNode(kk)) = nextNode(k);
								}
							for(var k:Long=0; k<nodes; k++)
								if(tempNextNode(k) != Infinity)
									nextNode(k) = tempNextNode(k);
						}
						j++;
							
						val spt:Point = Point.make(0);
						sumfunction(cmess(i), spt);		// Calling for summation
						
						Clock.advanceAll();
						
						if(i.equals(spt))
							Console.OUT.println(sumMess(i));
						cmess(i) = 0;
						sumMess(i) = 0;
						
						Clock.advanceAll();
					}
					
					for(j=0; j<nodes; j++) {
						if(parent(j) == index)
							nodeSet(i).routingTable(j).nextNode = index;
						else
							nodeSet(i).routingTable(j).nextNode = nextNode(j);	
					}
					nodeSet(i).routingTable(index).nextNode = index; 
				}
			}
		}						
	}

	/** 
	 * Gets the set of neighbors of a node.	
	 *
	 * @param  anode	node whose neighbor set is required.	
	 * @return 		neighbors of the <code>anode<\code>
	 */	
	def getNeighbors(val aNode:Point):Rail[Long] {
		val h0 = here;
		at(D(aNode)){
			val h1 = here;	
			/** 
			 * Checking for remote data access in isolation.
			 * If remote data access then increment counter for communication.
			 */
			atomic{
				if( h1 != h0)
					cmess(aNode) = cmess(aNode) + 1;
			}
		}
		val neighSet:Rail[Long] = at(D(aNode)) nodeSet(aNode).neighbors;
		return neighSet;
	}
}

/** States the structure for the routing table. */
class routingInfo
{
	/** Specifies identifier of the destination node. */
	var nodeId:Long;
	
	/** Specifies the hops needed to reach the destination node. */ 
	var hopCount:Long;
	
	/** Specifies the cost of the path to reach source. */
	var costToReach:Long;
	
	/** Identifies the first node on path to destination node from source. */
	var nextNode:Long;
	
	def setVlue(var nodeId:Long, var costToReach:Long, var hopCount:Long, var nextNode:Long) {
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
	var id:Long;
	
	/** Specifies neighbors of the router. */
	var neighbors:Rail[Long];
	
	/** Routing table structure. */							
	var routingTable:Rail[routingInfo];
	
	/** Holder for receiving the messages. */
	var messageHolder:ArrayList[routingInfo] = new ArrayList[routingInfo]();
}
