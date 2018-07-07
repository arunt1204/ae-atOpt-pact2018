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
 * on the basis of leader election algorithm by Hirschberg and Sinclair.
 * The algorithm is aimed towards bidirectional ring networks.
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
public class leader_elect_hs 
{
	var nodes:Int;
	var idSet:Array[Int];
	
	/** Abstract node representation as a distributed array. */
	var nodeSet:DistArray[PROCESS];
	
	/** Region and Distribution specification. */
	var R: Region;	var D: Dist; 
	
	/** Other Distributed Array used. */
	var send_pl:DistArray[Boolean];
	var send_min:DistArray[Boolean];
	
	/** Counter for measuring total communication (as a distributed array). */ 
	var cmess:DistArray[Int];
	
	/** Counter for measuring total atomics (as a distributed array). */ 
	var ciso:DistArray[Int];
	
	public static def main(args:Array[String](1)) {
		var inputFile:String = "inputlhs.txt", outputFile:String = "outputlhs.txt";
		var i:Int,j:Int;
		var flag:Boolean = false;
		
		for(i=0; i<args.size; i++) {
			if(args(i).equals("-ver") || args(i).equals("-verify")) {
				flag = true;
				i++;
			}
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
		var lhs:leader_elect_hs = new leader_elect_hs();
		lhs.nodes = Int.parse(s);
		lhs.idSet = new Array[Int]((0..(lhs.nodes-1)));
		
		/** Region creation. */
		lhs.R = 0..(lhs.nodes-1);
		
		/** Creation of a Block Distribution. */
    		lhs.D = Dist.makeBlock(lhs.R);
    		//lhs.D = Dist.makeUnique();
    		//lhs.R = lhs.D.region;
    		
    		/** Distribution of nodes. */
    		lhs.nodeSet = DistArray.make[PROCESS](lhs.D);
    		
    		/** Distribution of communication counters. */
    		lhs.cmess = DistArray.make[Int](lhs.D);
    		
    		/** Distribution of atomic counters. */
	  	lhs.ciso = DistArray.make[Int](lhs.D);
    		
		try {
			j=0;
			while((s = fr.readLine()) != null) {
				lhs.idSet(j) = Int.parse(s);
				j++;	
			}
		} catch(eof: x10.io.EOFException){}			

		lhs.initialize();
		
		var someval:Double = (Math.log(lhs.nodes)/Math.log(2));
		var phases:Int = (someval as Int);
	  	for(i=0; i<phases; i++) {
			lhs.send_pl = DistArray.make[Boolean](lhs.D);
	    		lhs.send_min = DistArray.make[Boolean](lhs.D);
			lhs.elect(i);
		}	
		lhs.transmit(phases);	
		
		/** Call to method for computing total Communication and Atomics. */
		lhs.messCount();
	}
	
	/** Initializes all the fields of the abstract node. */	
	def initialize() {
		finish {
			for(i in D) {
				async at(D(i)) {
					nodeSet(i) = new PROCESS();
					var idx:Int = i.operator()(0);
					nodeSet(i).id = idSet(idx);
					nodeSet(i).phase = 0;
					nodeSet(i).status = true;
					nodeSet(i).sendToPlus.setMessage(idSet(idx), 1, false, 0);
					nodeSet(i).sendToMinus.setMessage(idSet(idx), 1, false, 0);
				}
			}
		}
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
	
	
	
	/** 
	 * Aims at selecting the leader from a set of nodes.
	 *
	 * @param	phases		Current phase value.
	 */
	def elect(var phases:Int) {
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

					if(nodeSet(i).status) {
						nodeSet(i).sendToMinus.mtime=0;
						nodeSet(i).sendToPlus.mtime=0;
						var ptplus:Point = i;
						ptplus = ptplus.equals(R.maxPoint())? R.minPoint() : ptplus+1;
						var ptminus:Point = i;
						ptminus = ptminus.equals(R.minPoint())? R.maxPoint() : ptminus-1;
						sendMessage(ptplus, nodeSet(i).sendToPlus.uid, nodeSet(i).sendToPlus.hop, nodeSet(i).sendToPlus.mtime, nodeSet(i).sendToPlus.boundType, false);
						sendMessage(ptminus, nodeSet(i).sendToMinus.uid, nodeSet(i).sendToMinus.hop, nodeSet(i).sendToMinus.mtime, nodeSet(i).sendToMinus.boundType, true);
					}	
				}
			}
		}
		
		for(var k:Int=0; k<2*x10.lang.Math.pow2(phases); k++) {
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
					
						send_pl(i) = false; send_min(i) = false; 
						var mflag:Boolean=false; var pflag:Boolean=false; var sflag:Boolean=false;
						var msp:messageSet = new messageSet(); 
						var msl:messageSet = new messageSet();
						if(nodeSet(i).messageFromPlus != null) {
							msp = nodeSet(i).messageFromPlus;
							if(msp.boundType == false) {
								if(msp.uid > nodeSet(i).id && msp.hop > 1) {
									send_min(i) = true;
									nodeSet(i).sendToMinus = new messageSet();
									nodeSet(i).sendToMinus.setMessage(msp.uid, msp.hop-1, false, 1);	
								}
								else if(msp.uid > nodeSet(i).id && msp.hop == 1) {
									send_pl(i) = true;
									nodeSet(i).sendToPlus = new messageSet();
									nodeSet(i).sendToPlus.setMessage(msp.uid, msp.hop, true, 1);
								}
								else if(msp.uid == nodeSet(i).id)
									nodeSet(i).status = true;
							}	
								
							if(msp.boundType == true && msp.uid > nodeSet(i).id) {
								send_min(i) = true;
								nodeSet(i).sendToMinus = new messageSet();
								nodeSet(i).sendToMinus.setMessage(msp.uid, msp.hop, msp.boundType, 1);
							}
							pflag = true;	
						}
						
						if(nodeSet(i).messageFromMinus != null) {
							msl = nodeSet(i).messageFromMinus;
							if(msl.boundType == false) {
								if(msl.uid > nodeSet(i).id && msl.hop > 1) {
									send_pl(i) = true;
									nodeSet(i).sendToPlus = new messageSet();
									nodeSet(i).sendToPlus.setMessage(msl.uid, msl.hop-1, false, 1);
								}
								else if(msl.uid > nodeSet(i).id && msl.hop == 1) {
									send_min(i) = true;
									nodeSet(i).sendToMinus = new messageSet();
									nodeSet(i).sendToMinus.setMessage(msl.uid, msl.hop, true, 1);
								}
								else if(msl.uid == nodeSet(i).id)
									nodeSet(i).status = true;
							}

							if(msl.boundType == true && msl.uid > nodeSet(i).id) {
								send_pl(i) = true;
								nodeSet(i).sendToPlus = new messageSet();
								nodeSet(i).sendToPlus.setMessage(msl.uid, msl.hop, msl.boundType, 1);
							}
							mflag=true;
						}
						
						if(mflag == true && pflag == true) {
							if(msp.uid == msl.uid && msp.uid == nodeSet(i).id && msp.boundType == msl.boundType && msp.boundType == true && msp.hop == msl.hop && msp.hop == 1) {
								nodeSet(i).phase = nodeSet(i).phase + 1;
								nodeSet(i).status = true;
								nodeSet(i).sendToPlus = new messageSet();
								nodeSet(i).sendToPlus.setMessage(nodeSet(i).id, x10.lang.Math.pow2(nodeSet(i).phase), false, 1);
								nodeSet(i).sendToMinus = new messageSet();
								nodeSet(i).sendToMinus.setMessage(nodeSet(i).id, x10.lang.Math.pow2(nodeSet(i).phase), false, 1);
								sflag=true;
							}				
						}

						if(!sflag)
							nodeSet(i).status = false;
							
						Clock.advanceAll();
							
						if(send_pl(i) == true) {
							var ptplus:Point = i;
							ptplus = ptplus.equals(R.maxPoint())? R.minPoint() : ptplus+1;
							sendMessage(ptplus, nodeSet(i).sendToPlus.uid, nodeSet(i).sendToPlus.hop, nodeSet(i).sendToPlus.mtime, nodeSet(i).sendToPlus.boundType, false);
						}	
						if(send_min(i) == true) {
							var ptminus:Point = i;
							ptminus = ptminus.equals(R.minPoint())? R.maxPoint() : ptminus-1;
							sendMessage(ptminus, nodeSet(i).sendToMinus.uid, nodeSet(i).sendToMinus.hop, nodeSet(i).sendToMinus.mtime, nodeSet(i).sendToMinus.boundType, true);
						}
						
						Clock.advanceAll();
						
						if(nodeSet(i).messageFromPlus != null) {
							if(nodeSet(i).messageFromPlus.mtime != 1)
								nodeSet(i).messageFromPlus = null;
							else
								nodeSet(i).messageFromPlus.mtime = 0;
						}
						if(nodeSet(i).messageFromMinus != null) {
							if(nodeSet(i).messageFromMinus.mtime != 1)
								nodeSet(i).messageFromMinus = null;
							else
								nodeSet(i).messageFromMinus.mtime = 0;
						}
					}
				}
			}
		}	
	}

	/** 
	 * Transmits the message from one node to another.
	 *
	 * @param	aNode		Node whose messages are to be set.
	 * @param	uid		Leader Id.
	 * @param	hop		Hop count value.
	 * @param	mtime		A timestamp.
	 * @param	boundType	Inbound or Outbound message.
	 * @param	from		Message Sender.
	 */
	def sendMessage(val aNode:Point, val uid:Int, val hop:Int, val mtime:Int, val boundType:Boolean, val from:Boolean) {
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
			
			if(from) {
				nodeSet(aNode).messageFromPlus = new messageSet();
				nodeSet(aNode).messageFromPlus.setMessage(uid, hop, boundType, mtime);
			}
			else {
				nodeSet(aNode).messageFromMinus = new messageSet();
				nodeSet(aNode).messageFromMinus.setMessage(uid, hop, boundType, mtime);
			}
		}	
	}
	
	/** 
	 * Transmits the message from one node to another. 
	 *
	 * @param	phases		Total phases.
	 */
	def transmit(var phases:Int) {
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
					
					if(nodeSet(i).status) {
						val index:Int = i.operator()(0);
						var ptplus:Point = i;
						ptplus = ptplus.equals(R.maxPoint())? R.minPoint() : ptplus+1;
						var ptminus:Point = i;
						ptminus = ptminus.equals(R.minPoint())? R.maxPoint() : ptminus-1;
						sendMessage(ptplus, index, 1, 0, true, false);
						sendMessage(ptminus, index, 1, 0, true, true);
				
						for(var k:Int=0; k<nodes/2; k++) {
							val ntplus:Point = ptplus;
							val ntminus:Point = ptminus;
							at(D(ntminus)){
								if(nodeSet(ntminus).messageFromPlus != null) {
									nodeSet(ntminus).leaderId = nodeSet(ntminus).messageFromPlus.uid;
									nodeSet(ntminus).status = false;
									var pt:Point = ntminus.equals(R.minPoint())? R.maxPoint() : ntminus-1;
									sendMessage(pt, nodeSet(ntminus).messageFromPlus.uid, 1, 0, true, true);
								}
							}
							ptminus = ptminus.equals(R.minPoint())? R.maxPoint() : ptminus-1;
							
							at(D(ntplus)){		
								if(nodeSet(ntplus).messageFromMinus != null) {
									nodeSet(ntplus).leaderId = nodeSet(ntplus).messageFromMinus.uid;
									nodeSet(ntplus).status = false;
									var pt:Point = ntplus.equals(R.maxPoint())? R.minPoint() : ntplus+1;
									sendMessage(pt, nodeSet(ntplus).messageFromMinus.uid, 1, 0, true, false);
								}
							}
							ptplus = ptplus.equals(R.maxPoint())? R.minPoint() : ptplus+1;
						}
						nodeSet(i).leaderId = index;
					}			
				}
			}			
		}
	}
}

/** Defines the structure for a message. */
class messageSet
{
	/** Specifies the identifier of the sender. */
	var uid:Int;
	
	/** Specifies hops for a message to travel. */
	var hop:Int;
	
	/** mtime = 0, message to be used in this round ; mtime = 1, message for next round. */
	var mtime:Int;
	
	/** boundType = false, outbound message ; boundType = true, inbound message. */
	var boundType:Boolean;
	
	def setMessage(var uid:Int, var hop:Int, var boundType:Boolean, var mtime:Int) {
		this.uid = uid;
		this.hop =  hop;
		this.mtime = mtime;
		this.boundType = boundType;
	}
}
 
/**
 * <code>PROCESS</code> specifies the structure for each abstract node
 * part of the Leader election algorithm.
 */
class PROCESS
{
	/** Specifies identifier for each node. */
	var id:Int;
	
	/** Specifies information about the phase. */
	var phase:Int;
	
	/** Specifies the identifier of the leader. */
	var leaderId:Int;
	
	/** If status = true, then leader capable. */	
	var status:Boolean;

	/** Message to be sent to plus node. */
	var sendToPlus:messageSet = new messageSet();
	
	/** message to be sent to minus. */ 				
	var sendToMinus:messageSet = new messageSet();

	/** Mailbox for message recieved from plus. */
	var messageFromPlus:messageSet = new messageSet();
	
	/** Mailbox for message recieved from minus. */	
	var messageFromMinus:messageSet = new messageSet();
}
