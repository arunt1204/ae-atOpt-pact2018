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

	/** Parameters to enable execution with load */
	var loadValue:long=0; 

	/** Load sum represented as a distributed array. */
	var nval:DistArray[long];
	
	/** Region and Distribution specification. */
	var R: Region;	var D: Dist;
	
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
			else if(args(i).equals("-lfon")) {
				djr.loadValue = Long.parse(args(i+1));
				i++;
			}	
			else
				Console.OUT.println("Wrong option spcified");		
		}

		var fl:File = new File(inputFile);
		var fr:FileReader = new FileReader(fl);
		var s:String = fr.readLine();
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

		djr.nval = DistArray.make[long](djr.D);
		
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

		var startTime:long = System.nanoTime();
		djr.route(weightMatrix);
		var finishTime:long = System.nanoTime();
		var estimatedTime:long = finishTime - startTime;
		Console.OUT.println("Start Time: " + startTime + "\t Finish Time: " + finishTime + "\t Estimated Time: " + estimatedTime);
		
		djr.printTables(outputFile);
		if(flag)
			djr.outputVerifier();

		if(!djr.loadValue.equals(0)) {
			var sumval:double=0;
			for(i=0; i<djr.nodes; i++) {
				val pt:Point = Point.make(i);
				var arrSum:Long = djr.getNval(pt); 
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
	
	/** 
	 * Generates the routing table for each node.
	 *
	 * @param weightMatrix		Weight matrix.
	 */
	def route(val weightMatrix:Array[Int]) {
		finish {
			for(i in D) {
				async at(D(i)) {
					var j:Int; var minCost:Int; var node:Int=-1; var hCount:Int=0; var nrt:Int=0; var maxHop:Int=0;
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
						var sz:Int = at(D(npt)) nodeSet(npt).neighbors.size;
						var neighborSet:Array[Int] = new Array[Int]((0..(sz-1)));
						neighborSet = at(D(npt)) nodeSet(npt).neighbors;
						for(j=0; j<sz; j++) {
							if(!nodeCovered(neighborSet(j))) {
								rtf = new routingInfo();
								rtf.setVlue(neighborSet(j), minCost + weightMatrix(node, neighborSet(j)), hCount+1, node);
								queue.add(rtf);			
							}
						}
					}    
					
					val nextNode:Array[Int] = new Array[Int]((0..(nodes-1)));
					val tempNextNode:Array[Int] = new Array[Int]((0..(nodes-1)));
					for(j=0; j<nodes; j++) {
						nextNode(j) = Infinity;
						tempNextNode(j) = Infinity;
					}	
					for(j=0; j<nodeSet(i).neighbors.size; j++)
						if(parent(nodeSet(i).neighbors(j)) == index)
							nextNode(nodeSet(i).neighbors(j)) = nodeSet(i).neighbors(j);
					
					j=0;
					while(j<maxHop) {
						for(var k:Int=0; k<nodes; k++)
							if(nextNode(k) != Infinity) {
								val pt:Point = Point.make(k);
								val neighNode:Array[Int] = getNeighbors(pt);
								for(var kk:Int=0; kk<neighNode.size; kk++)
									if(parent(neighNode(kk)) == k)
										tempNextNode(neighNode(kk)) = nextNode(k);
							}
						for(var k:Int=0; k<nodes; k++)
							if(tempNextNode(k) != Infinity)
								nextNode(k) = tempNextNode(k);
						j++;
					}
					
					for(j=0; j<nodes; j++) {
						if(parent(j) == index)
							nodeSet(i).routingTable(j).nextNode = index;
						else
							nodeSet(i).routingTable(j).nextNode = nextNode(j);	
					}
					nodeSet(i).routingTable(index).nextNode = index; 

					if(!loadValue.equals(0)) {
						val ipt:Int = i(0);
						nval(i) = loadweight(nval(i)+ipt);
					}
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
	def getNeighbors(val aNode:Point):Array[Int] {
		val neighSet:Array[Int] = at(D(aNode)) nodeSet(aNode).neighbors;
		return neighSet;
	}
	
	/** 
	 * Writes the output to the user specified file.
	 * 
	 * @param  fileName	Name of the file in which output has to be stored.
	 * @throws 		input output exception if a failure in write occurs.
	 */
	def printTables(var fileName:String) {
		try {
 			var fl:File = new File(fileName);
			fw:FileWriter = new FileWriter(fl);
			var str:String;
			for(var i:Int=0; i<nodes; i++) {
				val pt:Point = Point.make(i);
				var nodeEntry:Node = new Node();
				nodeEntry = at(D(pt)) nodeSet(pt);
				str="\n Routing Table for Node " + nodeEntry.id;
				for(var j:Int=0; j<str.length(); j++) {
					var ch:Char = str.charAt(j);
					fw.writeChar(ch);
				}
				for(var j:Int=0; j<nodes; j++) {
					str="\n Node " + nodeEntry.routingTable(j).nodeId + "\t Cost to reach: " + nodeEntry.routingTable(j).costToReach + "\t Hops to Reach: " + nodeEntry.routingTable(j).hopCount + "\t Next Node: " + nodeEntry.routingTable(j).nextNode;
					for(var k:Int=0; k<str.length(); k++) {		
						var ch:Char = str.charAt(k);
						fw.writeChar(ch);
					}
				}	
				fw.writeChar('\n');	
			}
			fw.close();
		} catch(ex: x10.lang.Exception){}	
	}
	
	/** Validates the output resulting from the execution of the algorithm. */
	def outputVerifier() {
		var i:Int, j:Int;
		var flag:Boolean=false;
		for(i=0; i<nodes; i++) {
			val pt:Point = Point.make(i);
			var nodeEntry:Node = new Node();
			nodeEntry = at(D(pt)) nodeSet(pt);
				
			for(j=0; j<nodes; j++) {
				val jpt:Point = Point.make(j);
				var anodeEntry:Node = new Node();
				anodeEntry = at(D(jpt)) nodeSet(jpt);
				if(i != j)
					if((nodeEntry.routingTable(j).costToReach != anodeEntry.routingTable(i).costToReach ) && (nodeEntry.routingTable(j).hopCount != anodeEntry.routingTable(i).hopCount)) {
						flag = true;
						break;
					}
			}		
			if(flag)
				break;
		}
		if(!flag)
			Console.OUT.println("Output verified");
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
