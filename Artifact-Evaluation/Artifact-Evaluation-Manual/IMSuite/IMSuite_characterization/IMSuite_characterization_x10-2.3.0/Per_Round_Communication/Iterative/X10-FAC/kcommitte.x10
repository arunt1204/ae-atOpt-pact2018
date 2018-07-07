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
 * kcommitte aims to create committees of size less than or equal to k.
 * If k>=n then there shall be a single committee.
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
public class kcommitte 
{
	var adj_graph:Array[Int], nodes:Int, min_value:Array[Int];
	val Null=Int.MAX_VALUE;
	
	/** Default Value for K. */
	var K:Int = 6;
	
	/** Abstract node representation as a distributed array. */
	var nodeSet:DistArray[Node];
	
	/** Region and Distribution specification. */
	var R: Region;	var D: Dist;
	
	/** Counter for measuring total communication (as a distributed array). */ 
	var cmess:DistArray[Int];
	
	/** 
	 * Acts as the starting point for the program execution. 
	 * <code>main</code> performs the task of accepting the input from the user 
	 * specified file, calling <code>polling<\code> and <code>selection<\code> 
	 * methods responsible for committee creation, printing the output and 
	 * validating the result.
	 *
	 * @param args 		array of runtime arguments.
	 * @throws Exception	if File handling operation illegal. 
	 */
	public static def main(args:Array[String](1)) throws Exception {
		var inputFile:String = "inputKCommittee.txt", outputFile:String = "outputKCommittee.txt";
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
		var kc:kcommitte = new kcommitte();
		kc.nodes = Int.parse(s);
		s = fr.readLine();
		kc.K = Int.parse(s);
		kc.adj_graph = new Array[Int]((0..(kc.nodes-1))*(0..(kc.nodes-1)), 0);
		kc.min_value = new Array[Int]((0..(kc.nodes-1)));

		/** Region creation. */		
		kc.R = 0..(kc.nodes-1);
		
		/** Creation of a Block Distribution. */
    		kc.D = Dist.makeBlock(kc.R);
    		//kc.D = Dist.makeUnique();
    		//kc.R = kc.D.region;
    		
    		/** Distribution of nodes. */
    		kc.nodeSet = DistArray.make[Node](kc.D);
    		
    		/** Distribution of communication counters. */
    		kc.cmess = DistArray.make[Int](kc.D);
		
		try {
			j=0;
			while(!((s = fr.readLine()).equals(" "))) {
				for(i=0; i<s.length(); i++) {
					var ch:Char=s.charAt(i);
					if(ch=='0')
						kc.adj_graph(j,i) = 0;
					else
						kc.adj_graph(j,i) = 1;	
				}
				j++;
			}
		} catch(eof: x10.io.EOFException){}
		
		try {
			j=0;
			while((s = fr.readLine()) != null) {
				kc.min_value(j) = Int.parse(s);
				j++;
			}
		} catch(eof: x10.io.EOFException){}
		
 		kc.initialize();
		kc.adj_graph = null;
 		
 		for(var k:Int=0; k<kc.K; k++) {
 			kc.polling();
 			kc.selection();
 			kc.clear();
 			
 			/** Every iteration marks a round. */
 			kc.messCount();
			kc.cmess = DistArray.make[Int](kc.D);
 		}
 		
		kc.selfCommittee();
		
		/** For computing Communication in last round. */
		kc.messCount();
 	}
 	
 	/** Computes total Communication. */ 
	def messCount() {
		var msum:Int=0;
		for(var i:Int=0; i<nodes; i++) {
			val pt:Point = Point.make(i);
			var nvalue:Int = at(D(pt)) cmess(pt);
			msum = msum + nvalue;
		}
		Console.OUT.println(msum);
	}
 
 	/** Initializes all the fields of the abstract node. */  
 	def initialize() {
 		finish {
 			for(i in D) {
		 		async at(D(i)) {
		 			var j:Int;	
		 			var count:Int=0;
		 			var index:Int = i.operator()(0);
			 		nodeSet(i) = new Node();
			 		nodeSet(i).leader = index;
			 		nodeSet(i).committee = -1;
			 		nodeSet(i).value = min_value(i);
			 		for(j=0; j<nodes; j++)
				 		if(adj_graph(index,j) == 1)
					 		count++;
			 		nodeSet(i).neighbors = new Array[Int]((0..(count-1)));
			 		count=0;
			 		for(j=0; j<nodes; j++)
				 		if(adj_graph(index,j) == 1) {
					 		nodeSet(i).neighbors(count) = j;
					 		count++;
				 		}
				 	nodeSet(i).invitation.from=-1;
				 	nodeSet(i).invitation.to=-1;		
		 		}
			}
 		}
 	}
 
 	/** Resets the value of all holders. */
 	def clear() {
 		val h0 = here;
 		finish {
			for(i in D) {
				async at(D(i)) {
					val h1 = here;

					/** If remote data access then increment counter for communication. */
					if( h1 != h0)
						cmess(i) = cmess(i) + 1;
						
			 		nodeSet(i).minActive.clear();
			 		nodeSet(i).invitation.clear();
			 		nodeSet(i).minActiveHolder.clear();
			 	}
			 }		
 		 } 
 	}
 
 
	/** Polls to select candidates capable of being leader. */ 
 	def polling() {
 		val h0 = here;
 		finish {
 			for(i in D) {
				async at(D(i)) {
					val h1 = here;
					
					/** 
				 	 * Checking for remote data access in isolation.
				 	 * If remote data access then increment counter for communication.
				 	 */
					atomic{
					if( h1 != h0)
						cmess(i) = cmess(i) + 1;
					}
						
					var index:Int = i.operator()(0);
					if(nodeSet(i).committee == -1)
						nodeSet(i).minActive.setData(index, nodeSet(i).value);
					else
						nodeSet(i).minActive.setData(-1, Null);	
				}
		 	}
 		}
  
 		for(var kk:Int=0; kk<K-1; kk++) {
 			clocked finish {
 				for(i in D) {
					clocked async at(D(i)) {
						val h1 = here;
						
						/** 
				 	 	 * Checking for remote data access in isolation.
				 	 	 * If remote data access then increment counter for communication.
				 	 	 */
						atomic {
							if( h1 != h0)
								cmess(i) = cmess(i) + 1;
						}	
						for(var j:Int=0; j<nodeSet(i).neighbors.size; j++) {
					 		var pt:Point = Point.make(nodeSet(i).neighbors(j));
					 		sendMinActive(pt, nodeSet(i).minActive.from, nodeSet(i).minActive.value);
					 	}
					 	
					 	Clock.advanceAll();
					 	
						var ms:Message = null;
						var minValue:Int = nodeSet(i).minActive.value;
						var minId:Int = nodeSet(i).minActive.from;
				 
						for(var j:Int=nodeSet(i).minActiveHolder.size()-1; j>=0; j--) {
							ms = nodeSet(i).minActiveHolder.get(j);
							if(ms.value < minValue) {
								minValue = ms.value;
								minId = ms.from;
							}
						}
						nodeSet(i).minActiveHolder.clear();
				 		nodeSet(i).minActive.setData(minId, minValue);
					 }
				}
			}
		}
			
		finish {
			for(i in D) {
				async at(D(i)) {
					val h1 = here;
						
					/** 
				 	 * Checking for remote data access in isolation.
				 	 * If remote data access then increment counter for communication.
				 	 */
					atomic{
						if( h1 != h0)
							cmess(i) = cmess(i) + 1;
					}
					
					var pt:Point = Point.make(nodeSet(i).leader);
					if(nodeSet(i).minActive.value < getLeaderValue(pt))
				 		nodeSet(i).leader = nodeSet(i).minActive.from;	
				}
 			}
 		}
 	} 
 
 	/** Selects nodes to be part of committee of the leader. */ 
 	def selection() {
 		val h0 = here;
 		
 		finish {
 			for(i in D) {
				async at(D(i)) {
					var index:Int = i.operator()(0);
					if(nodeSet(i).leader == index) {
						val h1 = here;
						
						/** 
				 	 	 * Checking for remote data access in isolation.
				 		 * If remote data access then increment counter for communication.
				 		 */
						atomic{
							if( h1 != h0)
								cmess(i) = cmess(i) + 1;
						}
					
						var inv:Invitation = new Invitation();
						inv.from = index;	inv.to = nodeSet(i).minActive.from;	inv.leaderVal=nodeSet(i).value;
						nodeSet(i).invitationHolder.add(inv);
 					}	
				}
 			}
 		}
	 
 		for(var kk:Int=0; kk<K-1; kk++) {
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
					
						var invite:Invitation = null;
						for(var j:Int=0; j<nodeSet(i).invitationHolder.size(); j++) {
							invite = nodeSet(i).invitationHolder.get(j);
							for(var k:Int=0; k<nodeSet(i).neighbors.size; k++) {
								var pt:Point = Point.make(nodeSet(i).neighbors(k));
								sendInvitation(pt, invite.from, invite.to, invite.leaderVal);
							}	
						}
						nodeSet(i).invitationHolder.clear();
						
						Clock.advanceAll();
						
						nodeSet(i).invitationHolder.clear();
						if(nodeSet(i).tempInvitationHolder.size() > 0)
							nodeSet(i).invitationHolder.add(nodeSet(i).tempInvitationHolder.get(0));
						var flag:boolean=false;
						var cinvite:Invitation = null;
						invite = null;
						for(var j:Int=1; j<nodeSet(i).tempInvitationHolder.size(); j++) {
							flag = false;
							invite = nodeSet(i).tempInvitationHolder.get(j);
							for(var k:Int=0; k<nodeSet(i).invitationHolder.size(); k++) {
								cinvite = nodeSet(i).invitationHolder.get(k);
								if(cinvite.from == invite.from && cinvite.to == invite.to) {
									flag = true;
									break;
								}
							}
							if(!flag)
								nodeSet(i).invitationHolder.add(invite);
						}
						nodeSet(i).tempInvitationHolder.clear();
						
						Clock.advanceAll();
						
						flag=false;
						var index:Int = i.operator()(0);
		 
						if(nodeSet(i).invitationHolder.size() > 0) {
							invite = null;
							var mn:Int = Int.MAX_VALUE, ifrom:Int=-1;
								
							for(var j:Int=nodeSet(i).invitationHolder.size()-1; j>=0; j--) {
								invite=nodeSet(i).invitationHolder.get(j);
								if(invite.to == index) {
									if(invite.leaderVal < mn) {
										mn =invite.leaderVal;
										ifrom = invite.from;
									}		
									nodeSet(i).invitationHolder.remove(invite);
									flag=true;
								}
							}					 
							if(flag) {
								nodeSet(i).invitation.from = ifrom;
								nodeSet(i).invitation.to = index;
								nodeSet(i).invitation.leaderVal = mn;
							}
							else {
								nodeSet(i).invitation.from = -1;
								nodeSet(i).invitation.to = Null;
								nodeSet(i).invitation.leaderVal = -1;
							}
 						}	
					}
 				}
	 		}
 		}
		
		finish {	 
			for(i in D) {
				async at(D(i))
				{
					val h1 = here;
						
					/** 
				 	 * Checking for remote data access in isolation.
				 	 * If remote data access then increment counter for communication.
				 	 */
					atomic{
						if( h1 != h0)
							cmess(i) = cmess(i) + 1;
					}
					
					var index:Int = i.operator()(0);
					if(nodeSet(i).invitation.to == index)
						nodeSet(i).committee = nodeSet(i).invitation.from;
				}
			}
		}				
 	}

 	
 	/** Sets committee for nodes to be self which are left uninvited. */
 	def selfCommittee() {
 		val h0 = here;
 		finish {
			for(i in D) {
				async at(D(i)) {
					val h1 = here;
						
					/** 
				 	 * Checking for remote data access in isolation.
				 	 * If remote data access then increment counter for communication.
				 	 */
					atomic{
						if( h1 != h0)
							cmess(i) = cmess(i) + 1;
					}
					
					var index:Int = i.operator()(0);	
					if(nodeSet(i).committee == -1)
						nodeSet(i).committee = index;
				}
			}
		}				
 	}
 
	/** 
	 * Provides the value of the leader node. 
	 * 
	 * @param	aNode		Node whose leader is required.
	 * @return 			value of the leader.
	 */ 	
 	def getLeaderValue(val aNode:Point) {
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
					
 		val leadVal:Int = at(D(aNode)) nodeSet(aNode).value;
 		return leadVal;
 	}
 
 	/** 
 	 * Transmits the value of <code>minActive<\code> of a node.
 	 *
	 * @param	neighbor	Node which recieves <code>minActive<\code>.
	 * @param	from		Node which provides <code>minActive<\code>.
	 * @param	value		<code>minActive<\code> value.
	 */
 	def sendMinActive(val neighbor:Point, val from:Int, val value:Int) {
 		val h0 = here;
 		at(D(neighbor)){
	 		var ms:Message = new Message();
	 		ms.setData(from, value);
	 		
			val h1 = here;
	 		atomic {
	 			/** If remote data access then increment counter for communication. */
				if( h1 != h0)
					cmess(neighbor) = cmess(neighbor) + 1;

	 			nodeSet(neighbor).minActiveHolder.add(ms);
	 		}
	 	}	
 	}
 	
 	/** 
 	 * Transmits the invitation from one node to another.
 	 *
	 * @param	aNode		Node which recieves invitation.
	 * @param	from		Node which provides invitation.
	 * @param	to		Node to which invitation is meant.
	 * @param	leval		Leader value.
	 */
 	def sendInvitation(val aNode:Point, val from:Int, val to:Int, val leval:Int) {
 		val h0 = here;
 		at(D(aNode)){
	 		var invite:Invitation = new Invitation();
 			invite.from = from; 	invite.to = to;		invite.leaderVal = leval;

			val h1 = here;
 			atomic {
 				/** If remote data access then increment counter for communication. */
				if( h1 != h0)
					cmess(aNode) = cmess(aNode) + 1;

 				nodeSet(aNode).tempInvitationHolder.add(invite);
 			}
 		}		
 	}
}

/** Defines the structure of message to be sent out by a node. */
class Message
{
	var from:Int;
	var value:Int;
	
	def setData(val from:Int, val value:Int) {
		this.from = from; 		this.value = value;
	}
	def clear(){ from = -1; value = Int.MAX_VALUE;}
}

/** Defines the structure of an invitation to be sent out by a node. */
class Invitation
{
	var from:Int;		
	var to:Int;
	var leaderVal:Int;
	def clear() { from = -1; to = -1; leaderVal=Int.MAX_VALUE;}
}

/**
 * <code>Node</code> specifies the structure for each abstract node
 * part of the K Committe algorithm.
 */
class Node
{
	/** Value (minimum active) corresponding to a node. */
	var value:Int;
	
	/** Specifies the leader for the node. */
	var leader:Int;
	
	/** Specifies the committee to which a node belongs. */
	var committee:Int;
	
	/** Specifies the set of neighbors for a node. */
	var neighbors:Array[Int];
	
	/** Specifies the message, consisting of minimum value, to be sent by a node. */
	var minActive:Message = new Message();
	
	/** Specifies the invitation sent by a node. */	
	var invitation:Invitation = new Invitation();
	var invitationHolder:ArrayList[Invitation] = new ArrayList[Invitation]();
	var tempInvitationHolder:ArrayList[Invitation] = new ArrayList[Invitation]();
	var minActiveHolder:ArrayList[Message] = new ArrayList[Message]();
}
