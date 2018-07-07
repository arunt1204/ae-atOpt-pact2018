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
import x10.regionarray.Dist;
import x10.regionarray.DistArray;
import x10.regionarray.Region;
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
	var processes:Long, IdStore:Rail[Long];

	/** Abstract node representation as a distributed array. */
	var processSet:DistArray[Process];

	/** Region and Distribution specification. */
	var R: Region;	var D: Dist;

	/** Counter for measuring total communication (as a distributed array). */ 
	var cmess:DistArray[Long];

	/** 
	 * Acts as the starting point for the program execution. 
	 * <code>main</code> performs the task of accepting the input from the user 
	 * specified file, electing the leader and transmitting information, 
	 * printing the output and validating the result.
	 *
	 * @param args 		array of runtime arguments.
	 * @throws Exception	if File handling operation illegal. 
	 */	
	public static def main(args:Rail[String]) {
		var inputFile:String = "inputlcr.txt", outputFile:String = "outputlcr.txt";
		var i:Long,j:Long;
		var flag:Boolean = false;
		var le:leader_elect_lcr = new leader_elect_lcr();
	
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
	   	le.processes = Long.parse(s);
	  	le.IdStore = new Rail[Long](le.processes);

		/** Region creation. */	  	
	  	le.R = Region.make(0, (le.processes-1));
	  	
	  	/** Creation of a Block Distribution. */
    		le.D = Dist.makeBlock(le.R);
    		//le.D = Dist.makeUnique();
    		//le.R = le.D.region;
    		
    		/** Distribution of nodes. */
	  	le.processSet = DistArray.make[Process](le.D);

		/** Distribution of communication counters. */
		le.cmess = DistArray.make[Long](le.D);
	 	
	 	try {
			j=0;
			while((s = fr.readLine()) != null) {
				le.IdStore(j) = Long.parse(s);
				j++;	
			}
		} catch(eof: x10.io.EOFException){}
	  
	  	le.initialize();
	  
	  	for(var round:Long=0; round<le.processes; round++) {
			le.leader_elect();
		}
		le.transmitLeader();

		/** For computing Communication after transmit. */
		le.messCount();
	} 

	/** Computes total Communication. */ 
	def messCount() {
		var msum:Long=0;
		for(var i:Long=0; i<processes; i++) {
			val pt:Point = Point.make(i);
			var nvalue:Long = at(D(pt)) cmess(pt);
			msum = msum + nvalue;
		}
		Console.OUT.println(msum);
	}

 	/** Initializes all the fields of the abstract node. */
	def initialize() {
  		finish {
  			for(i in D) {
        			async at(D(i)) {
        				processSet(i) = new Process();
					var pt:Long = i(0);
          				processSet(i).id = IdStore(pt);
          				processSet(i).leaderId = processSet(i).id;
          				processSet(i).send = processSet(i).id;
          				processSet(i).status = false;
        			}
        		}
  		}
  	}

	/** Aims at selecting the leader from a set of nodes. */ 
  	def leader_elect() {
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

					var pt:Long = i.operator()(0);
					pt = (pt + 1)%processes;
					val p: Point = Point.make(pt);
	  				val sval:Long = processSet(i).send;
					sendMessage(p, sval);
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
  		var tmp:Long = 0;
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
				  				var check:Long=0;
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
		for(var j:Long=0; j<processes; j++) {
			val pt_loc:Long = lead.operator()(0);
			if(pt.equals(R.maxPoint()))
				pt = R.minPoint();
			else
				pt = pt+1;
			setLeader(pt_loc, pt);				
		}
		
		val lindex:Point = lead;
		at(D(lindex)){
			val pt_loc:Long = lindex.operator()(0);
			processSet(lindex).leaderId = pt_loc;
		}
  	}
  
  	/** 
  	 * Sets the leader for a node.
  	 * 
  	 * @param	uNode		Leader node.
  	 * @param	aNode		Node whose leader has to be set.
  	 */
  	def setLeader(val uNode:Long, val aNode:Point) {
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
	def sendMessage(val receiver: Point, val sval:Long) {
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
  	var id:Long;
  	
  	/** Represents the Identifier that the process will send to its neighbor. */
  	var send:Long;

	/** Represents the Identifier of the leader. */  
  	var leaderId:Long;
  	
	/** Represents the Identifier that the process receives from its neighbor. */
  	var recievedId:Long;
  
  	/** Represents the status of the process and will be set to true for leader. */
  	var status:Boolean;
}
