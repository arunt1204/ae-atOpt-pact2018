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
	
	val Infinity = Int.MAX_VALUE;
	var nodes:Int, root:Int, adj_graph:Array[Int], cPhase:Int=0;
	
	/** Abstract node representation as a distributed array. */
	var nodeSet:DistArray[Node];

	/** Parameters to enable execution with load */
	var loadValue:long=0; 

	/** Load sum represented as a distributed array. */
	var nval:DistArray[long];
	
	/** Region and Distribution specification. */
	var R: Region;	var D: Dist;
	
	/** Other Distributed Array used. */
	var found:DistArray[Boolean];
	
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
		var inputFile:String = "inputbfsDijkstra.txt", outputFile:String = "outputbfsDijkstra.txt";
		var i:Int,j:Int;
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
				bfs.loadValue = Long.parse(args(i+1));
				i++;
			}	
			else
				Console.OUT.println("Wrong option spcified");		
		}

		var fl:File = new File(inputFile);
		var fr:FileReader = new FileReader(fl);
		var s:String = fr.readLine();
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
    	
		bfs.nval = DistArray.make[Long](bfs.D);
	
		bfs.initialize();
		
		var startTime:long = System.nanoTime();
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
		var finishTime:long = System.nanoTime();
		var estimatedTime:long = finishTime - startTime;
		Console.OUT.println("Start Time: " + startTime + "\t Finish Time: " + finishTime + "\t Estimated Time: " + estimatedTime);
		
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
					nodeSet(i) = new Node();
					nodeSet(i).parent = Infinity;
					nodeSet(i).phaseDiscovered = -1;
					nodeSet(i).currentPhase = 0;
					var rt:Point = Point.make(root);
					if(i.equals(rt)) {
						nodeSet(i).parent = 0;
						nodeSet(i).phaseDiscovered = 0;
					} } } }
	}
	
	/** Transmits the <code>START<\code> signal to all the children of a node.  */
	def broadCast(val aNode:Point, val phase:Int) {
		at(D(aNode)){
			finish {
				for(i in 0..(nodeSet(aNode).children.size()-1)) {
					async {
						val child:Point = Point.make(nodeSet(aNode).children.get(i));
						sendSignal(child, START, phase);
						broadCast(child, phase);

						if(!loadValue.equals(0)) {
							at(D(child)) {
								nval(child) = loadweight(nval(child)+nodeSet(aNode).children.get(i));
							}
						} } } } }
	}
	
	/** 
	 * Transmits the join message to all the undiscovered neighbors of the node discovered
	 * in previous phase.
	 */
	def joinMessage() {
		finish {
			for(i in D) {
				async at(D(i)) {
					val index:Int = i.operator()(0);
					if(nodeSet(i).phaseDiscovered == nodeSet(i).currentPhase) {
						for(var j:Int=0; j<nodes; j++) {
							var flag:Boolean = false;
							if(adj_graph(index,j) == 1) {	
								for(var k:Int=0; k<nodeSet(i).neighborTalked.size(); k++)
									if(nodeSet(i).neighborTalked.get(k) == j) {
										flag = true;
										break;
									}
								if(!flag) {	
									nodeSet(i).neighborTalked.add(j);
									val jnode:Point = Point.make(j);
									sendJoinMessage(jnode, index, JOIN, nodeSet(i).currentPhase+1);
								} } } }
					if(!loadValue.equals(0)) {
						nval(i) = loadweight(nval(i)+index);
					} } } }	
	}
	
	/** Merges new nodes to existing BFS Tree. */
	def joinTree() {
		clocked finish {
			for(i in D) {
				clocked async at(D(i)) {
					var index:Int = i.operator()(0);
					var msp:messagePair;
					if(nodeSet(i).parent == Infinity) {
						var flag:Boolean = false;
						for(var j:Int=0; j<nodeSet(i).sendMessage.size(); j++) {
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
							} } }
					else {
						for(var j:Int=0; j<nodeSet(i).sendMessage.size(); j++) {
							msp = nodeSet(i).sendMessage.get(j);
							val acknode:Point = Point.make(msp.from);
							sendAck(acknode, index, NACK);
							nodeSet(i).neighborTalked.add(msp.from);
						} }			
					
					Clock.advanceAll();
					
					nodeSet(i).sendMessage.clear();
					nodeSet(i).sendMessage.addAll(nodeSet(i).tempHolder);
					nodeSet(i).tempHolder.clear();

					if(!loadValue.equals(0)) {
						nval(i) = loadweight(nval(i)+index);
					} } } }
	}
	
	/** Tells whether all the nodes are part of the BFS tree or some nodes are left.
	 * 
	 * @return	true if all nodes are part of the BFS tree.
	 */
	def foundCheck():Boolean {
		var counter:Int=0;
		var flag:Boolean=false;
		for(var i:Int=0; i<nodes; i++) {
			val pt:Point = Point.make(i);
			flag = at(D(pt)) found(pt);
			if(flag) {
				counter++;
				break;
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
		at(D(aNode)){
			if(nodeSet(aNode).phaseDiscovered != nodeSet(aNode).currentPhase) {
				finish {
					for(i in 0..(nodeSet(aNode).children.size()-1)) {
						async {
							val child:Point = Point.make(nodeSet(aNode).children.get(i));
							echoReply(child);

							if(!loadValue.equals(0)) {
								at(D(child)) {
									nval(child) = loadweight(nval(child)+nodeSet(aNode).children.get(i));
								} } } } }
				var index:Int = aNode.operator()(0);
				var pt:Point = Point.make(nodeSet(aNode).parent);
				sendAck(pt, index, ACK);
			} }
	}	

	/** Resets the variables. */			
	def reset() {
		finish {
			for(i in D) {
				async at(D(i)) {
					nodeSet(i).sendMessage = new ArrayList[messagePair]();
					nodeSet(i).signal = -1;

					if(!loadValue.equals(0)) {
						val ipt:Int = i(0);
						nval(i) = loadweight(nval(i)+ipt);
					} } } }
	}
	
	/** 
	 * Sets a node as the child node of another node. 
	 *
	 * @param aNode		Parent Node.
	 * @param uNode		Child Node.
	 */
	def setChild(val aNode:Point, val uNode:Int) {
		at(D(aNode)){
			atomic { nodeSet(aNode).children.add(uNode); }
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
	def sendAck(val aNode:Point, val uNode:Int, val uSignal:Int) {
		at(D(aNode)){ 
			var msp:messagePair = new messagePair();
			msp.from = uNode; 	msp.signal = uSignal;
			atomic { nodeSet(aNode).tempHolder.add(msp); }
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
	def sendJoinMessage(val aNode:Point, val from:Int, val signal:Int, val phase:Int) {
		at(D(aNode)){
			atomic {
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
	def sendSignal(val aNode:Point, val uSignal:Int, val phase:Int)
	{
		at(D(aNode)){
			nodeSet(aNode).signal = uSignal;
			nodeSet(aNode).currentPhase = phase;
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
			var flag:boolean = false;
			var str:String;		var j:Int;
	  		var fl:File = new File(fileName);
			var fw:FileWriter = new FileWriter(fl);
  			str = "Root: " + root;
			for(j=0; j<str.length(); j++) {
				var ch:Char = str.charAt(j);
				fw.writeChar(ch);
			}
			fw.writeChar('\n');
  			str = "All (U,V) Pairs where U is parent and V is child";
  			for(j=0; j<str.length(); j++) {
				var ch:Char = str.charAt(j);
				fw.writeChar(ch);
			}
			fw.writeChar('\n');
			
  			for(var i:Int=0; i<nodes; i++) {
  				flag = false;
  				val pnode:Point = Point.make(i);
  				var childArray:Array[Int];
  				
  				childArray = at(D(pnode)){
  						var retArray:Array[Int] = new Array[Int]((0..(nodeSet(pnode).children.size()-1)));
  						for(var kk:Int=0; kk<nodeSet(pnode).children.size(); kk++)
  							retArray(kk) = nodeSet(pnode).children(kk);
  						retArray
  				};		
		  		
		  		for(j=0; j<childArray.size; j++) {
					str = "(" + i + "," + childArray(j) + ") ";
  					for(var k:Int=0; k<str.length(); k++) {		
						var ch:Char = str.charAt(k);
						fw.writeChar(ch);
					}
  					flag = true;
  				}
  				if(flag)	
  					fw.writeChar('\n');
  			}
  			fw.close();
  		}
  		catch(ex: x10.lang.Exception){}		
	}
	
	/** Validates the output resulting from the execution of the algorithm. */
	def outputVerifier() {
		var i:Int; var j:Int; 	var child:Int;
		var nodeCheck:Array[Int] = new Array[Int]((0..(nodes-1)));
		var flag:Boolean = false;
		
		for(i=0; i<nodes; i++) {
			val pnode:Point = Point.make(i);
  			val childArray:ArrayList[Int] = at(D(pnode)) nodeSet(pnode).children;
			if(childArray.size() > 0) {
				for(j=0; j<childArray.size(); j++) {
					child = childArray.get(j);
					nodeCheck(child)=nodeCheck(child)+1;	
				}
			}
		}	
		nodeCheck(root)=nodeCheck(root)+1;
		for(i=0; i<nodes; i++)
			if(nodeCheck(i) != 1)
				flag = true;
		if(!flag)
			Console.OUT.println("Output verified");
	}
}

/** States the structure of message to be transmitted. */
class messagePair
{
	var from:Int;
	var signal:Int;
}

/**
 * <code>Node</code> specifies the structure for each abstract node
 * part of the BFS Dijkstra algorithm.
 */
class Node
{
	/** Specifies parent of a node. */
	var parent:Int;
	
	/** Specifies the phase in which the node is discovered. */
	var phaseDiscovered:Int;
	
	/** Specifies the current ongoing phase. */
	var currentPhase:Int;
	
	/** Enumerates the set of children of the node. */
	var children:ArrayList[Int] = new ArrayList[Int]();
	
	/** Enumerates the neighbor communicated by a node. */
	var neighborTalked:ArrayList[Int] = new ArrayList[Int]();
	
	/** Holds all the messages received by a node. */
	var sendMessage:ArrayList[messagePair] = new ArrayList[messagePair]();
	var tempHolder:ArrayList[messagePair] = new ArrayList[messagePair]();
	
	var signal:Int;
}
