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
 * kcommitte aims to create committees of size less than or equal to k.
 * If k>=n then there shall be a single committee.
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
public class kcommitte 
{
	var adj_graph:Array[Long], nodes:Long, min_value:Rail[Long];
	val Null=Long.MAX_VALUE;
	
	/** Default Value for K. */
	var K:Long = 6;
	
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
	 * specified file, calling <code>polling<\code> and <code>selection<\code> 
	 * methods responsible for committee creation, printing the output and 
	 * validating the result.
	 *
	 * @param args 		array of runtime arguments.
	 * @throws Exception	if File handling operation illegal. 
	 */
	public static def main(args:Rail[String]) throws Exception {
		var inputFile:String = "inputKCommittee.txt", outputFile:String = "outputKCommittee.txt";
		var i:Long,j:Long;
		var flag:Boolean = false;
		var kc:kcommitte = new kcommitte();
	
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
				kc.loadValue = Long.parse(args(i+1));
				i++;
			}
			else
				Console.OUT.println("Wrong option spcified");		
		}

		var fl:File = new File(inputFile);
		var fr:FileReader = new FileReader(fl);
		var s:String = fr.readLine();
		kc.nodes = Long.parse(s);
		s = fr.readLine();
		kc.K = Long.parse(s);
		kc.adj_graph = new Array[Long](Region.make(0..(kc.nodes-1), 0..(kc.nodes-1)), 0);
		kc.min_value = new Rail[Long](kc.nodes);

		/** Region creation. */		
		kc.R = Region.make(0, (kc.nodes-1));
		
		/** Creation of a Block Distribution. */
    		kc.D = Dist.makeBlock(kc.R);
    		//kc.D = Dist.makeUnique();
    		//kc.R = kc.D.region;
    		
    		/** Distribution of nodes. */
    		kc.nodeSet = DistArray.make[Node](kc.D);

		kc.nval = DistArray.make[long](kc.D);
		
		try {
			j=0;
			while(!((s = fr.readLine()).equals(" "))) {
				for(i=0; i<s.length(); i++) {
					var iInt:Int = i as Int;
					var ch:Char=s.charAt(iInt);
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
				kc.min_value(j) = Long.parse(s);
				j++;
			}
		} catch(eof: x10.io.EOFException){}
		
 		kc.initialize();
		kc.adj_graph = null;
 		
		var startTime:long = System.nanoTime();
 		for(var k:Long=0; k<kc.K; k++) {
 			kc.polling();
 			kc.selection();
 			kc.clear();
 		}
		kc.selfCommittee();
		var finishTime:long = System.nanoTime();
		var estimatedTime:long = finishTime - startTime;
		Console.OUT.println("Start Time: " + startTime + "\t Finish Time: " + finishTime + "\t Estimated Time: " + estimatedTime);
		  
 		kc.printCommittee(outputFile);
 		if(flag)
 			kc.outputVerifier();

		if(!kc.loadValue.equals(0)) {
			var sumval:double=0;
			for(i=0; i<kc.nodes; i++) {
				val pt:Point = Point.make(i);
				var arrSum:Long = kc.getNval(pt); 
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
 		//finish 
 			for(i in D) {
		 		//async 
				at(D(i)) {
		 			var j:Long;	
		 			var count:Long=0;
		 			var index:Long = i.operator()(0);
			 		nodeSet(i) = new Node();
			 		nodeSet(i).leader = index;
			 		nodeSet(i).committee = -1;
			 		nodeSet(i).value = min_value(index);
			 		for(j=0; j<nodes; j++)
				 		if(adj_graph(index,j) == 1)
					 		count++;
			 		nodeSet(i).neighbors = new Rail[Long](count);
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
 
 	/** Resets the value of all holders. */
 	def clear() {
 		//finish 
			for(i in D) {
				//async 
				at(D(i)) {
			 		nodeSet(i).minActive.clear();
			 		nodeSet(i).invitation.clear();
			 		nodeSet(i).minActiveHolder.clear();

					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
					}
			 	}
			 }		
 	}
 
 	/** Polls to select candidates capable of being leader. */ 
 	def polling() {
 		//finish 
 			for(i in D) {
				//async 
				at(D(i)) {
					var index:Long = i.operator()(0);
					if(nodeSet(i).committee == -1)
						nodeSet(i).minActive.setData(index, nodeSet(i).value);
					else
						nodeSet(i).minActive.setData(-1, Null);	

					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
					}
				}
		 	}
  
 		for(var kk:Long=0; kk<K-1; kk++) {
 			//finish 
 				for(i in D) {
					//async 
					at(D(i)) {
						for(var j:Long=0; j<nodeSet(i).neighbors.size; j++) {
					 		var pt:Point = Point.make(nodeSet(i).neighbors(j));
					 		sendMinActive(pt, nodeSet(i).minActive.from, nodeSet(i).minActive.value);
					 	}

						if(!loadValue.equals(0)) {
							val ipt:Long = i(0);
							nval(i) = loadweight(nval(i)+ipt);
						}
					}
				}	
		 
		 	//finish 
		 		for(i in D) {
					//async 
					at(D(i)) {
						var ms:Message = null;
						var minValue:Long = nodeSet(i).minActive.value;
						var minId:Long = nodeSet(i).minActive.from;
				 
						for(var j:Long=nodeSet(i).minActiveHolder.size()-1; j>=0; j--) {
							ms = nodeSet(i).minActiveHolder.get(j);
							if(ms.value < minValue) {
								minValue = ms.value;
								minId = ms.from;
							}
						}
						nodeSet(i).minActiveHolder.clear();
				 		nodeSet(i).minActive.setData(minId, minValue);

						if(!loadValue.equals(0)) {
							val ipt:Long = i(0);
							nval(i) = loadweight(nval(i)+ipt);
						}
					 }
				}
		}
			
		//finish 
			for(i in D) {
				//async 
				at(D(i)) {
					var pt:Point = Point.make(nodeSet(i).leader);
					if(nodeSet(i).minActive.value < getLeaderValue(pt))
				 		nodeSet(i).leader = nodeSet(i).minActive.from;	

					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
					}
				}
 			}
 	} 

	/** Selects nodes to be part of committee of the leader. */ 
 	def selection() {
 		//finish 
 			for(i in D) {
				//async 
				at(D(i)) {
					var index:Long = i.operator()(0);
					if(nodeSet(i).leader == index) {
						var inv:Invitation = new Invitation();
						inv.from = index;	inv.to = nodeSet(i).minActive.from;	inv.leaderVal=nodeSet(i).value;
						nodeSet(i).invitationHolder.add(inv);
 					}

					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
					}	
				}
 			}
	 
 		for(var kk:Long=0; kk<K-1; kk++) {
 			//finish 
 				for(i in D) {
					//async 
					at(D(i)) {
						var invite:Invitation = null;
						for(var j:Long=0; j<nodeSet(i).invitationHolder.size(); j++) {
							invite = nodeSet(i).invitationHolder.get(j);
							for(var k:Long=0; k<nodeSet(i).neighbors.size; k++) {
								var pt:Point = Point.make(nodeSet(i).neighbors(k));
								sendInvitation(pt, invite.from, invite.to, invite.leaderVal);
							}	
						}
						nodeSet(i).invitationHolder.clear();	

						if(!loadValue.equals(0)) {
							val ipt:Long = i(0);
							nval(i) = loadweight(nval(i)+ipt);
						}
					}
 				}
 			
 			//finish 
 				for(i in D) {
					//async 
					at(D(i)) {
						nodeSet(i).invitationHolder.clear();
						if(nodeSet(i).tempInvitationHolder.size() > 0)
							nodeSet(i).invitationHolder.add(nodeSet(i).tempInvitationHolder.get(0));
						var flag:boolean=false;
						var cinvite:Invitation = null;
						var invite:Invitation = null;
						for(var j:Long=1; j<nodeSet(i).tempInvitationHolder.size(); j++) {
							flag = false;
							invite = nodeSet(i).tempInvitationHolder.get(j);
							for(var k:Long=0; k<nodeSet(i).invitationHolder.size(); k++) {
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
					}
				}
					
			
	 		//finish 
	 			for(i in D) {
					//async 
					at(D(i)) {
						var flag:Boolean=false;
						var index:Long = i.operator()(0);
		 
						if(nodeSet(i).invitationHolder.size() > 0) {
							var invite:Invitation = null;
							var mn:Long = Long.MAX_VALUE, ifrom:Long=-1;
								
							for(var j:Long=nodeSet(i).invitationHolder.size()-1; j>=0; j--) {
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

						if(!loadValue.equals(0)) {
							val ipt:Long = i(0);
							nval(i) = loadweight(nval(i)+ipt);
						}	
					}
 				}
 		}
		
		//finish 
			for(i in D) {
				//async 
				at(D(i)) {
					var index:Long = i.operator()(0);
					if(nodeSet(i).invitation.to == index)
						nodeSet(i).committee = nodeSet(i).invitation.from;

					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
					}
				}
			}
 	}

 	/** Sets committee for nodes to be self which are left uninvited. */
 	def selfCommittee() {
 		finish {
			for(i in D) {
				async at(D(i)) {
					var index:Long = i.operator()(0);	
					if(nodeSet(i).committee == -1)
						nodeSet(i).committee = index;
	
					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
					}
				}
			}
		}				
 	}
 
 	/** 
	 * Writes the output to the user specified file.
	 * 
	 * @param  fileName	Name of the file in which output has to be stored.
	 * @throws 		input output exception if a failure in write occurs.
	 */
 	def printCommittee(var fileName:String) {
 		try {
 			var j:Long;
 			var fl:File = new File(fileName);
			var fw:FileWriter = new FileWriter(fl);
	  		for(var i:Long=0; i<nodes; i++) {
	  			val ipt:Point = Point.make(i);
	  			val comm:Long = at(D(ipt)) nodeSet(ipt).committee;
	  			var str:String = "Node " + i + "\t: Committee " + comm;
	  			
	  			for(j=0; j<str.length(); j++)
				{
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
 	
 	/** 
	 * Provides the value of the leader node. 
	 * 
	 * @param	aNode		Node whose leader is required.
	 * @return 			value of the leader.
	 */
 	def getLeaderValue(val aNode:Point) {
 		val leadVal:Long = at(D(aNode)) nodeSet(aNode).value;
 		return leadVal;
 	}
 
 	/** 
 	 * Transmits the value of <code>minActive<\code> of a node.
 	 *
	 * @param	neighbor	Node which recieves <code>minActive<\code>.
	 * @param	from		Node which provides <code>minActive<\code>.
	 * @param	value		<code>minActive<\code> value.
	 */
 	def sendMinActive(val neighbor:Point, val from:Long, val value:Long) {
 		at(D(neighbor)){
	 		var ms:Message = new Message();
	 		ms.setData(from, value);
	 		nodeSet(neighbor).minActiveHolder.add(ms); 
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
 	def sendInvitation(val aNode:Point, val from:Long, val to:Long, val leval:Long) {
 		at(D(aNode)){
	 		var invite:Invitation = new Invitation();
 			invite.from = from; 	invite.to = to;		invite.leaderVal = leval;
 			nodeSet(aNode).tempInvitationHolder.add(invite); 
 		}		
 	}
 	
 	/** Validates the output resulting from the execution of the algorithm. */
 	def outputVerifier() {
 		var i:Long; 	var id:Long=-1;	var min:Long = Long.MAX_VALUE;	var alead:Long=-1;
 		var flag:Boolean = false;
 		var nodeComm:Rail[Long] = new Rail[Long](nodes);
 		for(i=0; i<nodes; i++) {
 			val pt:Point = Point.make(i);
 			var comm:Long = at(D(pt)) nodeSet(pt).committee;
 			nodeComm(comm) = nodeComm(comm) + 1;
 		}
 		for(i=0; i<nodes; i++)
 			if(nodeComm(i) > K) {
 				flag = true;
 				break;
 			}	
 		if(!flag)
			Console.OUT.println("Output verified");	
 	}	
}

/** Defines the structure of message to be sent out by a node. */
class Message
{
	var from:Long;
	var value:Long;
	
	def setData(val from:Long, val value:Long) {
		this.from = from; 		this.value = value;
	}
	def clear(){ from = -1; value = Long.MAX_VALUE;}
}

/** Defines the structure of an invitation to be sent out by a node. */
class Invitation
{
	var from:Long;		
	var to:Long;
	var leaderVal:Long;
	def clear() { from = -1; to = -1; leaderVal=Long.MAX_VALUE;}
}

/**
 * <code>Node</code> specifies the structure for each abstract node
 * part of the K Committe algorithm.
 */
class Node
{
	/** Value (minimum active) corresponding to a node. */
	var value:Long;
	
	/** Specifies the leader for the node. */
	var leader:Long;
	
	/** Specifies the committee to which a node belongs. */
	var committee:Long;
	
	/** Specifies the set of neighbors for a node. */
	var neighbors:Rail[Long];
	
	/** Specifies the message, consisting of minimum value, to be sent by a node. */
	var minActive:Message = new Message();
	
	/** Specifies the invitation sent by a node. */	
	var invitation:Invitation = new Invitation();
	var invitationHolder:ArrayList[Invitation] = new ArrayList[Invitation]();
	var tempInvitationHolder:ArrayList[Invitation] = new ArrayList[Invitation]();
	var minActiveHolder:ArrayList[Message] = new ArrayList[Message]();
}
