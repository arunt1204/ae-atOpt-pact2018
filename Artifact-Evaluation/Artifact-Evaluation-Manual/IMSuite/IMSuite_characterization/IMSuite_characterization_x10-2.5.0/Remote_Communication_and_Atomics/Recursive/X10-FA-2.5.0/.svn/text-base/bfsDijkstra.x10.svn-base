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
 * bfsDijkstra implements the breadth first algorithm using the Dijkstra
 * approach. The aim here is to construct a tree that marks the parent child
 * relationship between two nodes.
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
public class bfsDijkstra
{
	/** Signals used in transmisiion. */
	val START = 0;
	val JOIN = 1;
	val ACK = 2;
	val NACK = 3;
	
	val Infinity = Long.MAX_VALUE;
	var nodes:Long, root:Long, adj_graph:Array[Long], cPhase:Long=0;
	
	/** Abstract node representation as a distributed array. */
	var nodeSet:DistArray[Node];
	
	/** Region and Distribution specification. */
	var R: Region;	var D: Dist;
	
	/** Other Distributed Array used. */
	var found:DistArray[Boolean];

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
	 * specified file, calling the methods responsible for BFS tree creation, 
	 * printing the output and validating the result.
	 *
	 * @param args 		array of runtime arguments.
	 * @throws Exception	if File handling operation illegal. 
	 */
	public static def main(args:Rail[String]) throws Exception {
		var inputFile:String = "inputbfsDijkstra.txt", outputFile:String = "outputbfsDijkstra.txt";
		var i:Long,j:Long;
		var flag:Boolean = false;
		var bfs:bfsDijkstra = new bfsDijkstra();

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
		
		var newNode:Boolean = false;
		while(true) {
			bfs.found = DistArray.make[Boolean](bfs.D);
			val rt:Point = Point.make(bfs.root);	
			bfs.sendSignal(rt, bfs.START, 0);
			bfs.broadCast(rt, bfs.cPhase);
			bfs.joinMessage();
			bfs.joinTree();
			newNode = bfs.foundCheck();	
			if(!newNode)
				break;
			bfs.echoReply(rt);	
			bfs.reset();
			bfs.cPhase++;
		}

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
					nodeSet(i) = new Node();
					nodeSet(i).parent = Infinity;
					nodeSet(i).phaseDiscovered = -1;
					nodeSet(i).currentPhase = 0;
					var rt:Point = Point.make(root);
					if(i.equals(rt)) {
						nodeSet(i).parent = 0;
						nodeSet(i).phaseDiscovered = 0;
					}
				}
			}
		}
	}
	
	/** Transmits the <code>START<\code> signal to all the children of a node.  */
	def broadCast(val aNode:Point, val phase:Long) {
		val h0=here;
		at(D(aNode)){
			val h1=here;
			atomic{
				/** If remote data access then increment counter for communication. */ 
				if(h0 != h1)
					cmess(aNode) = cmess(aNode) + 1;
					
				/** Finish statements, increment counter for finish. */	
				cfinish(aNode) = cfinish(aNode) + 1;	
			}
			finish {
				for(i in 0..(nodeSet(aNode).children.size()-1)) {
					async {
						atomic {
							/** Async statements, increment counter for async. */
							casync(aNode) = casync(aNode) + 1;	
						}

						val child:Point = Point.make(nodeSet(aNode).children.get(i));
						sendSignal(child, START, phase);
						broadCast(child, phase);
					}	
				}
			}
		}		
	}
	
	/** 
	 * Transmits the join message to all the undiscovered neighbors of the node discovered
	 * in previous phase.
	 */
	def joinMessage() {
		val pt:Point = Point.make(0);
		at(D(pt)){
			/** Finish statements, increment counter for finish. */	
			cfinish(pt) = cfinish(pt) + 1;
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

					val index:Long = i.operator()(0);
					if(nodeSet(i).phaseDiscovered == nodeSet(i).currentPhase) {
						for(var j:Long=0; j<nodes; j++) {
							var flag:Boolean = false;
							if(adj_graph(index,j) == 1) {	
								for(var k:Long=0; k<nodeSet(i).neighborTalked.size(); k++)
									if(nodeSet(i).neighborTalked.get(k) == j) {
										flag = true;
										break;
									}
								if(!flag) {	
									nodeSet(i).neighborTalked.add(j);
									val jnode:Point = Point.make(j);
									sendJoinMessage(jnode, index, JOIN, nodeSet(i).currentPhase+1);
								}	
							}
						}
					}
				}
			}
		}		
	}
	
	/** Merges new nodes to existing BFS Tree. */
	def joinTree() {
		val pt:Point = Point.make(0);
		at(D(pt)){
			/** Finish statements, increment counter for finish. */	
			cfinish(pt) = cfinish(pt) + 2;
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

					var index:Long = i.operator()(0);
					if(nodeSet(i).parent == Infinity) {
						var msp:messagePair = new messagePair();
						var flag:Boolean = false;
						for(var j:Long=0; j<nodeSet(i).sendMessage.size(); j++) {
							msp = nodeSet(i).sendMessage.get(j);
							val acknode:Point = Point.make(msp.from);
							if(!flag) {
								sendAck(acknode, index, ACK);
								nodeSet(i).parent = msp.from;
								nodeSet(i).neighborTalked.add(msp.from);
								setChild(acknode, index);
								found(i)=true;	flag=true;
							}	
							else {	
								nodeSet(i).neighborTalked.add(msp.from);
								sendAck(acknode, index, NACK);
							}
						}
					}
					else {
						var msp:messagePair = new messagePair();
						for(var j:Long=0; j<nodeSet(i).sendMessage.size(); j++) {
							msp = nodeSet(i).sendMessage.get(j);
							val acknode:Point = Point.make(msp.from);
							sendAck(acknode, index, NACK);
							nodeSet(i).neighborTalked.add(msp.from);
						}					
					}
				}
			}
		}	
				
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

					nodeSet(i).sendMessage.clear();
					nodeSet(i).sendMessage.addAll(nodeSet(i).tempHolder);
					nodeSet(i).tempHolder.clear();
				}
			}
		}	
	}
	
	/** Tells whether all the nodes are part of the BFS tree or some nodes are left.
	 * 
	 * @return	true if all nodes are part of the BFS tree.
	 */
	def foundCheck():Boolean {
		var counter:Long=0;
		var flag:Boolean=false;

		val h0 = here;
		for(var i:Long=0; i<nodes; i++) {
			val pt:Point = Point.make(i);
			flag = at(D(pt)) found(pt);
			if(flag) {
				counter++;
				break;
			}
			at(D(pt)){
				/** If remote data access then increment counter for communication. */ 
				val h1 = here;
				if(h0 != h1)
					cmess(pt) = cmess(pt) + 1;
			}
		}
		if(counter > 0)
			return true;
		else
			return false;	
	}	

	/** 
	 * Replies are echoed back to root from all the children. 
	 *
	 * @param aNode		Node which echoes reply back to its parent.
	 */
	def echoReply(val aNode:Point) {
		val h0 = here;
		at(D(aNode)){
			val h1 = here;
			
			/** If remote data access then increment counter for communication. */ 
			if(h0 != h1)
				cmess(aNode) = cmess(aNode) + 1;
			
			if(nodeSet(aNode).phaseDiscovered != nodeSet(aNode).currentPhase) {
				/** Finish statements, increment counter for finish. */
				cfinish(aNode) = cfinish(aNode) + 1;
				finish {
					for(i in 0..(nodeSet(aNode).children.size()-1)) {
						async {
							atomic {
								/** Async statements, increment counter for async. */
								casync(aNode) = casync(aNode) + 1;
							}
							val child:Point = Point.make(nodeSet(aNode).children.get(i));
							echoReply(child);
						}
					}
				}
				var index:Long = aNode.operator()(0);
				var pt:Point = Point.make(nodeSet(aNode).parent);
				sendAck(pt, index, ACK);
			}
		}	
	}	

	/** Resets the variables. */			
	def reset() {
		val pt:Point = Point.make(0);
		at(D(pt)){
			/** Finish statements, increment counter for finish. */
			cfinish(pt) = cfinish(pt) + 2;
		}
		
		val h0 = here;
		finish {
			for(i in D) {
				async at(D(i)) {
					val h1 = here;
					
					/** If remote data access then increment counter for communication. */ 
					if(h0 != h1)
						cmess(i) = cmess(i) + 1;
					
					/** Async statements, increment counter for async. */	
					casync(i) = casync(i) + 1;

					nodeSet(i).sendMessage = new ArrayList[messagePair]();
					nodeSet(i).signal = -1;
				}
			}
		}
	}
	
	/** 
	 * Sets a node as the child node of another node. 
	 *
	 * @param aNode		Parent Node.
	 * @param uNode		Child Node.
	 */
	def setChild(val aNode:Point, val uNode:Long) {
		val h0 = here;
		at(D(aNode)){
			val h1 = here;
			atomic {
				/** If remote data access then increment counter for communication. */ 
				if(h0 != h1)
					cmess(aNode) = cmess(aNode) + 1;
					
				/** Inside existing atomic operation, increment counter for atomic. */	
				catom(aNode) = catom(aNode) + 1;
				
				nodeSet(aNode).children.add(uNode);
			}
		}	
	}
	
	/** 
	 * Sends <code>ACK<\code> or <code>NACK<\code> signal to the sender of 
	 * <code>JOIN<\code> signal.
	 *
	 * @param aNode		Signal receiver.
	 * @param uNode		Signal sender.
	 * @param uSignal	<code>ACK<\code> or <code>NACK<\code> signal.
	 */
	def sendAck(val aNode:Point, val uNode:Long, val uSignal:Long) {
		val h0 = here;
		at(D(aNode)){
			var msp:messagePair = new messagePair();
			msp.from = uNode; 	msp.signal = uSignal;
			
			atomic {
				val h1 = here;
				
				/** If remote data access then increment counter for communication. */ 
				if(h0 != h1)
					cmess(aNode) = cmess(aNode) + 1;
					
				/** Inside existing atomic operation, increment counter for atomic. */	
				catom(aNode) = catom(aNode) + 1;
				
				nodeSet(aNode).tempHolder.add(msp);
			}
		}		
	}
	
	/** 
	 * Sends <code>JOIN<\code> signal to all the prospective children.
	 *
	 * @param aNode		Signal receiver.
	 * @param from		Signal sender.
	 * @param signal	<code>JOIN<\code> signal.
	 * @param phase		Current phase value.
	 */
	def sendJoinMessage(val aNode:Point, val from:Long, val signal:Long, val phase:Long) {
		val h0 = here;
		at(D(aNode)){
			atomic {
				val h1 = here;
				
				/** If remote data access then increment counter for communication. */ 
				if(h0 != h1)
					cmess(aNode) = cmess(aNode) + 1;
					
				/** Inside existing atomic operation, increment counter for atomic. */	
				catom(aNode) = catom(aNode) + 1;
				var msp:messagePair = new messagePair();
				msp.from = from;	msp.signal = JOIN;
				nodeSet(aNode).sendMessage.add(msp);
				if(nodeSet(aNode).parent == Infinity)
					nodeSet(aNode).phaseDiscovered = phase;
			}
		}		
	}

	/** 
	 * Sends <code>START<\code> signal to a node.
	 *
	 * @param aNode		Signal receiver.
	 * @param uSignal	<code>START<\code> signal.
	 * @param phase		Current phase value.
	 */
	def sendSignal(val aNode:Point, val uSignal:Long, val phase:Long)
	{
		val h0 = here;
		at(D(aNode)){
			val h1 = here;
			atomic {
				/** If remote data access then increment counter for communication. */ 
				if(h0 != h1)
					cmess(aNode) = cmess(aNode) + 1;
			}

			nodeSet(aNode).signal = uSignal;
			nodeSet(aNode).currentPhase = phase;
		}
	}
}

/** States the structure of message to be transmitted. */
class messagePair
{
	var from:Long;
	var signal:Long;
}

/**
 * <code>Node</code> specifies the structure for each abstract node
 * part of the BFS Dijkstra algorithm.
 */
class Node
{
	/** Specifies parent of a node. */
	var parent:Long;
	
	/** Specifies the phase in which the node is discovered. */
	var phaseDiscovered:Long;
	
	/** Specifies the current ongoing phase. */
	var currentPhase:Long;
	
	/** Enumerates the set of children of the node. */
	var children:ArrayList[Long] = new ArrayList[Long]();
	
	/** Enumerates the neighbor communicated by a node. */
	var neighborTalked:ArrayList[Long] = new ArrayList[Long]();
	
	/** Holds all the messages received by a node. */
	var sendMessage:ArrayList[messagePair] = new ArrayList[messagePair]();
	var tempHolder:ArrayList[messagePair] = new ArrayList[messagePair]();
	
	var signal:Long;
}
