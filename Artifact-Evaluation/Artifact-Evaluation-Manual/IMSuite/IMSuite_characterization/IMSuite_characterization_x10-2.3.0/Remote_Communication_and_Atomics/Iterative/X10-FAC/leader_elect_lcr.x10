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
 * leader_elect_lcr aims to elect a leader from a set of nodes,
 * on the basis of leader election algorithm by Le Lang et al..
 * The algorithm is aimed towards unidirectional ring networks.
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
public class leader_elect_lcr
{
	var processes:Int, IdStore:Array[Int];

	/** Abstract node representation as a distributed array. */
	var processSet:DistArray[Process];
	
	/** Region and Distribution specification. */
	var R: Region;	var D: Dist;
	
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
	public static def main(args:Array[String](1)) {
		var inputFile:String = "inputlcr.txt", outputFile:String = "outputlcr.txt";
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
	  	var le:leader_elect_lcr = new leader_elect_lcr();
	  	le.processes = Int.parse(s);
	  	le.IdStore = new Array[Int]((0..(le.processes-1)));

		/** Region creation. */	  	
	  	le.R = 0..(le.processes-1);
	  	
	  	/** Creation of a Block Distribution. */
    		le.D = Dist.makeBlock(le.R);
    		//le.D = Dist.makeUnique();
    		//le.R = le.D.region;
    		
    		/** Distribution of nodes. */
	  	le.processSet = DistArray.make[Process](le.D);
	  	
	  	/** Distribution of communication counters. */
    		le.cmess = DistArray.make[Int](le.D);
    		
    		/** Distribution of atomic counters. */
	  	le.ciso = DistArray.make[Int](le.D);
	 	
	 	try {
			j=0;
			while((s = fr.readLine()) != null) {
				le.IdStore(j) = Int.parse(s);
				j++;	
			}
		} catch(eof: x10.io.EOFException){}
	  
	  	le.initialize();
	  
	  	for(var round:Int=0; round<le.processes; round++) {
			le.leader_elect();
		}
		le.transmitLeader();
		
		/** Call to method for computing total Communication and Atomics. */
		le.messCount();	  	
  	} 
  	
  	/** Computes total Communication and Atmoic Operations. */ 
	def messCount() {
		var msum:Int=0, isum:Int=0;
		for(var i:Int=0; i<processes; i++) {
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
        				processSet(i) = new Process();
          				processSet(i).id = IdStore(i);
          				processSet(i).leaderId = processSet(i).id;
          				processSet(i).send = processSet(i).id;
          				processSet(i).status = false;
        			}
        		}
  		}
  	}
 
 	/** Initializes all the fields of the abstract node. */
  	def leader_elect() {
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
					
					var pt: Int = i.operator()(0);
					pt = (pt + 1)%processes;
					val p: Point = Point.make(pt);
	  				val sval:Int = processSet(i).send;
					sendMessage(p, sval);
					
					Clock.advanceAll();
					
					if(processSet(i).recievedId > processSet(i).leaderId) {
						processSet(i).send = processSet(i).recievedId;
						processSet(i).leaderId = processSet(i).recievedId;
					}
      					else if(processSet(i).recievedId == processSet(i).id) {
						processSet(i).status = true;
						processSet(i).leaderId = processSet(i).id;
					}
	  			}
			}
  		}
  	}

	/** Transmits the message from one node to another. */  
  	def transmitLeader() {
  		val h0 = here;
  		var lead: Point = Point.make(0);
  		var tmp: Int = 0;
		finish {
	  		for(i in D) {	
				async {
					val h1 = here;
					
					/** 
				 	 * Checking for remote data access in isolation.
				 	 * If remote data access then increment counter for communication.
				 	 */
					atomic{
						if( h1 != h0)
							cmess(i) = cmess(i) + 1;
					}
					 
				  	tmp = at(D(i))	{
				  				var check:Int=0;
								if(processSet(i).status)
									check=1;
								check
							};
					if(tmp == 1)
						lead = i;
				}
			}		
		}
		
		var pt: Point = lead;
		for(var j:Int=0; j<processes; j++) {
			val pt_loc: Int = lead.operator()(0);
			if(pt.equals(R.maxPoint()))
				pt = R.minPoint();
			else
				pt = pt+1;
			setLeader(pt_loc, pt);				
		}
		val lindex:Point = lead;
		at(D(lindex)){
			val h1 = here;
			
			/** 
			 * Checking for remote data access in isolation.
			 * If remote data access then increment counter for communication.
			 */
			if( h1 != h0)
				cmess(lindex) = cmess(lindex) + 1;

			val pt_loc: Int = lindex.operator()(0);
			processSet(lindex).leaderId = pt_loc;
		}
  	}

	/** 
  	 * Sets the leader for a node.
  	 * 
  	 * @param	uNode		Leader node.
  	 * @param	aNode		Node whose leader has to be set.
  	 */  
  	def setLeader(val uNode:Int, val aNode:Point) {
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
			
			processSet(aNode).leaderId = uNode;
		}	
  	}
  	 
  	/** 
	 * Sends message to the neighbor.
	 * 
	 * @param	receiver	Node which receives the message.
	 * @param	sval		Message value.
	 */  
	def sendMessage(val receiver: Point, val sval: Int) {
  		val h0 = here;
		at(D(receiver)) {
			val h1 = here;
			atomic{
				if( h1 != h0)
					cmess(receiver) = cmess(receiver) + 1;
			}
					
			processSet(receiver).recievedId = sval;
		}	
  	}
}

/**
 * <code>PROCESS</code> specifies the structure for each abstract node
 * part of the Leader election algorithm.
 */
class Process
{
	/** Specifies identifier for each node. */
  	var id:Int;
  	
  	/** Represents the Identifier that the process will send to its neighbor. */
  	var send:Int;

	/** Represents the Identifier of the leader. */  
  	var leaderId:Int;
  	
	/** Represents the Identifier that the process receives from its neighbor. */
  	var recievedId:Int;
  
  	/** Represents the status of the process and will be set to true for leader. */
  	var status:Boolean;
}
