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
 * leader_elect_dp aims to elect a leader from a set of nodes,
 * on the basis of leader election algorithm by David Peleg.
 * The algorithm is aimed towards general networks.
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
public class leader_elect_dp 
{
	var adj_graph:Array[Int], nodes:Int, idSet:Array[Int], pulse:Int=0;
	
	/** Abstract node representation as a distributed array. */
	var nodeSet:DistArray[Node];
	
	/** Region and Distribution specification. */
	var R: Region;	var D: Dist;
	
	/** Other Distributed Array used. */
	var maxd:DistArray[Int];
	var cflag:DistArray[Boolean];
	
	/** Counter for measuring total communication (as a distributed array). */ 
	var cmess:DistArray[Int];
	
	/** Counter for measuring total atomics (as a distributed array). */ 
	var ciso:DistArray[Int];
	
	/** 
	 * Acts as the starting point for the program execution. 
	 * <code>main</code> performs the task of accepting the input from the user 
	 * specified file, electing the leader and transmitting information, 
	 * printing the output and validating the result.
	 *
	 * @param args 		array of runtime arguments.
	 * @throws Exception	if File handling operation illegal. 
	 */
	public static def main(args:Array[String](1)) throws Exception {
		var inputFile:String = "inputldp16", outputFile:String = "outputldp16";
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
				Console.OUT.println("Wrong option spcified");		
		}

		var fl:File = new File(inputFile);
		var fr:FileReader = new FileReader(fl);
		var s:String = fr.readLine();
		val ldp:leader_elect_dp = new leader_elect_dp();
		ldp.nodes = Int.parse(s);
		ldp.idSet = new Array[Int]((0..(ldp.nodes-1)));
		ldp.adj_graph = new Array[Int]((0..(ldp.nodes-1))*(0..(ldp.nodes-1)),0);

		/** Region creation. */		
		ldp.R = 0..(ldp.nodes-1);
		
		/** Creation of a Block Distribution. */
    		ldp.D = Dist.makeBlock(ldp.R);
    		//ldp.D = Dist.makeUnique();
    		//ldp.R = ldp.D.region;
    		
    		/** Some more data getting distributed. */
    		ldp.maxd = DistArray.make[Int](ldp.D);
    		ldp.nodeSet = DistArray.make[Node](ldp.D);
    		
    		/** Distribution of communication counters. */
    		ldp.cmess = DistArray.make[Int](ldp.D);
    		
    		/** Distribution of atomic counters. */
	  	ldp.ciso = DistArray.make[Int](ldp.D);
		
		try {
			j=0;
			while(!((s = fr.readLine()).equals(" "))) {
				for(i=0; i<s.length(); i++) {
					var ch:Char=s.charAt(i);
					if(ch=='0')
						ldp.adj_graph(j,i) = 0;
					else
						ldp.adj_graph(j,i) = 1;	
				}
				j++;
			}
		} catch(eof: x10.io.EOFException){}
		
		try {
			j=0;
			while((s = fr.readLine()) != null) {
				ldp.idSet(j) = Int.parse(s);
				j++;
			}
		} catch(eof: x10.io.EOFException){}

		ldp.initialize();
		
		var isComplete:Boolean;
		do {
			ldp.cflag = DistArray.make[Boolean](ldp.D); 
			ldp.leaderElect();
			isComplete = ldp.checkComplete();
			if(isComplete)
				break;
		}while(true);

		for(i=0; i<ldp.nodes; i++) {
			val pt:Point = Point.make(i);
			at(ldp.D(pt)){
				if(ldp.nodeSet(pt).c >=2) {
					var idx:Int = pt.operator()(0);
					ldp.transmitLeader(pt, idx);
				}	
			}		
		}		

		/** Call to method for computing total Communication and Atomics. */	
		ldp.messCount();		
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
					val index:Int = i.operator()(0);
					nodeSet(i) = new Node();
					nodeSet(i).x = idSet(index);
					nodeSet(i).d = 0;
					nodeSet(i).leaderId = -1;
					nodeSet(i).b = 1;
					nodeSet(i).c = 0;
					nodeSet(i).complete = false;
					for(j=0; j<nodes; j++)
						if(adj_graph(index,j) == 1)
							counter++;
					nodeSet(i).neighbors = new Array[Int]((0..(counter-1)));
					counter=0;
					for(j=0; j<nodes; j++)
						if(adj_graph(index,j) == 1) {
							nodeSet(i).neighbors(counter) = j;
							counter++;
						}
				}
			}
		}
	}
	

	/** Aims at selecting the leader from a set of nodes. */
	def leaderElect() {
		pulse++;
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
					
					if(!nodeSet(i).complete) {
						maxd(i) = nodeSet(i).d;
						var sentMessage:Message = new Message();
						for(var j:Int=nodeSet(i).messageHolder.size()-1; j>=0; j--) {
							sentMessage = nodeSet(i).messageHolder.get(j);
							if(maxd(i) < sentMessage.d)
								maxd(i) = sentMessage.d;
						}
					}
					
					Clock.advanceAll();
					
					if(!nodeSet(i).complete) {
						for(var j:Int=0; j<nodeSet(i).neighbors.size; j++) {
							val pt:Point = Point.make(nodeSet(i).neighbors(j));
							sendMessage(pt, nodeSet(i).x, maxd(i));
						}	
					}
					
					Clock.advanceAll();
					
					val npulse:Int = pulse;
					if(!nodeSet(i).complete) {
						var j:Int;
						var flag:Boolean = false;
						var sentMessage:Message = new Message();
						for(j=0; j<nodeSet(i).messageHolder.size(); j++) {
							sentMessage = nodeSet(i).messageHolder.get(j);
							if(sentMessage.d == -1) {	
								cflag(i) = true;
								flag = true;
								break;
							}
						}
						
						if(!flag) {
							var y:Int = Int.MIN_VALUE, z:Int= nodeSet(i).d;	
							for(j=nodeSet(i).messageHolder.size()-1; j>=0; j--) {
								sentMessage = nodeSet(i).messageHolder.get(j);
								if(y < sentMessage.x)
									y = sentMessage.x;
							}
							for(j=nodeSet(i).messageHolder.size()-1; j>=0; j--) {
								sentMessage = nodeSet(i).messageHolder.get(j);
								if(y > sentMessage.x)
									nodeSet(i).messageHolder.remove(sentMessage);
							}
							if(y > nodeSet(i).x) {
								nodeSet(i).x = y;
								nodeSet(i).b = 0;
								nodeSet(i).d = npulse;
							}
							if(nodeSet(i).b == 0)	
								flag = true;
							if(!flag) {	
								if(y < nodeSet(i).x) {
									nodeSet(i).c = 1;
									flag = true;
								}
								if(!flag) {
									for(j=0; j<nodeSet(i).messageHolder.size(); j++) {
										sentMessage = nodeSet(i).messageHolder.get(j);
										if(z < sentMessage.d)
											z = sentMessage.d;
									}
									if(z > nodeSet(i).d) {
										nodeSet(i).d = z;
										nodeSet(i).c = 0;
										flag = true;
									}
									if(!flag) {
										nodeSet(i).c++;
										if(nodeSet(i).c >= 2)
											cflag(i) = true;
									}
								}
							}		
						}
					}
					
					Clock.advanceAll();
					
					if(!nodeSet(i).complete) {
						if(cflag(i)) {
							nodeSet(i).complete = true;
							for(var j:Int=0; j<nodeSet(i).neighbors.size; j++) {
								val pt:Point = Point.make(nodeSet(i).neighbors(j));
								sendMessage(pt, nodeSet(i).x, -1);
							}
						}
					}
				}
			}
		}
	}
	
	/** 
	 * Transmits the message from one node to another.
	 *
	 * @param	aNode		Node which recieves message.
	 * @param	x		Message value.
	 * @param	d		Distance value.
	 */
	def sendMessage(val aNode:Point, val x:Int, val d:Int) {
		val h0 = here;
		at(D(aNode)){
			val h1 = here;
			atomic {
				/** If remote data access then increment counter for communication. */
				if( h1 != h0)
					cmess(aNode) = cmess(aNode) + 1;
					
				/** Inside existing atomic operation, increment counter for atomic. */		
				ciso(aNode) = ciso(aNode) + 1;
				
				var sentMessage:Message = new Message();
				sentMessage.x = x;		sentMessage.d = d;
				nodeSet(aNode).messageHolder.add(sentMessage);
			}
		}		
	}
	
	/** 
	 * Checks if the selection of the leader is complete or not.
	 *
	 * @return 	true if <code>complete<\code> set for all the nodes.
	 */
	def checkComplete():Boolean {
		val h0 = here;
		var count:Int=0;
		for(var i:Int=0; i<nodes; i++) {
			val pt:Point = Point.make(i);
			var iscomplete:Boolean = at(D(pt)){
						val h1 = here;
						
						/** 
				 	 	 * Checking for remote data access in isolation.
				 	 	 * If remote data access then increment counter for communication.
				 	 	 */
						atomic{
							if( h1 != h0)
								cmess(pt) = cmess(pt) + 1;
						}
					
						var flag:Boolean=false;
						if(nodeSet(pt).complete)
							flag = true;
						flag
			};
			if(iscomplete)						
				count++;
		}
		if(count == nodes)
			return true;
		else
			return false;	
	}

	/** 
	 * Transmits the leader information to all the nodes.
	 *
	 * @param	aNode		Node which transmits.
	 * @param	leader		Leader Node.
	 */
	def transmitLeader(val aNode:Point, val leader:Int) {
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
			
			if(nodeSet(aNode).leaderId != -1)
				return;
			else {
				nodeSet(aNode).leaderId = leader;
				for(var i:Int=0; i<nodeSet(aNode).neighbors.size; i++) {
					val pt:Point = Point.make(nodeSet(aNode).neighbors(i));
					transmitLeader(pt,leader);
				}	
			}
		}	
	}
}

/** Defines the structure for a message. */
class Message
{
	var x:Int;
	var d:Int;
}

/**
 * <code>Node</code> specifies the structure for each abstract node
 * part of the Leader election algorithm.
 */
class Node extends Message
{
	/** Specifies the identifier of the leader. */
	var leaderId:Int;
	var b:Int;
	var c:Int;
	
	/** States when the leader identification is over. */
	var complete:Boolean;
	
	/** Specifies the set of the neighbors for a node. */
	var neighbors:Array[Int];
	var messageHolder:ArrayList[Message] = new ArrayList[Message]();
}
