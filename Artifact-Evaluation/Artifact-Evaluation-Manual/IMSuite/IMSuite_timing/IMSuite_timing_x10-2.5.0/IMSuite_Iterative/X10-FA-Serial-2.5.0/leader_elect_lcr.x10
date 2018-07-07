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

	/** Parameters to enable execution with load */
	var loadValue:long=0; 

	/** Load sum represented as a distributed array. */
	var nval:DistArray[Long];
	
	/** Region and Distribution specification. */
	var R: Region;	var D: Dist;

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
			else if(args(i).equals("-lfon")) {
				le.loadValue = Long.parse(args(i+1));
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

		le.nval = DistArray.make[long](le.D);
	 	
	 	try {
			j=0;
			while((s = fr.readLine()) != null) {
				le.IdStore(j) = Long.parse(s);
				j++;	
			}
		} catch(eof: x10.io.EOFException){}
	  
	  	le.initialize();
	  
	  	var startTime:long = System.nanoTime();
	  	for(var round:Long=0; round<le.processes; round++) {
			le.leader_elect();
		}
		le.transmitLeader();
	  	var finishTime:long = System.nanoTime();
	  	var estimatedTime:long = finishTime - startTime;
	  	Console.OUT.println("Start Time: " + startTime + "\t Finish Time: " + finishTime + "\t Estimated Time: " + estimatedTime);
	  	
	  	le.printii(outputFile);
	  	if(flag)
	  		le.outputVerifier();	

		if(!le.loadValue.equals(0)) {
			var sumval:double=0;
			for(i=0; i<le.processes; i++) {
				val pt:Point = Point.make(i);
				var arrSum:Long = le.getNval(pt); 
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
        				processSet(i) = new Process();
					var pt:Long = i(0);
          				processSet(i).id = IdStore(pt);
          				processSet(i).leaderId = processSet(i).id;
          				processSet(i).send = processSet(i).id;
          				processSet(i).status = false;
        			}
        		}
  	}

	/** Aims at selecting the leader from a set of nodes. */ 
  	def leader_elect() {
  		//finish 
  			for(i in D) {
	  			//async 
				at(D(i)) {
					var pt:Long = i.operator()(0);
					pt = (pt + 1)%processes;
					val p: Point = Point.make(pt);
	  				val sval:Long = processSet(i).send;
					sendMessage(p, sval);

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
					if(processSet(i).recievedId > processSet(i).leaderId) {
						processSet(i).send = processSet(i).recievedId;
						processSet(i).leaderId = processSet(i).recievedId;
					}
					else if(processSet(i).recievedId == processSet(i).id) {
						processSet(i).status = true;
						processSet(i).leaderId = processSet(i).id;
					}

					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
					}
	  			}
			}
  	}
  
  	/** Transmits the message from one node to another. */
  	def transmitLeader() {
  		var lead: Point = Point.make(0);
  		var tmp:Long = 0;
		//finish 
	  		for(i in D) {	
				//async  
				  	tmp = at(D(i))	{
				  				var check:Long=0;
								if(processSet(i).status)
									check=1;
								check
							};
					if(tmp == 1)
						lead = i;

					if(!loadValue.equals(0)) {
						at(D(i)) {
							val ipt:Long = i(0);
							nval(i) = loadweight(nval(i)+ipt);
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
		at(D(aNode)){
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
		at(D(receiver))
			processSet(receiver).recievedId = sval;
  	}

	/** 
	 * Writes the output to the user specified file.
	 * 
	 * @param  fileName	Name of the file in which output has to be stored.
	 * @throws 		input output exception if a failure in write occurs.
	 */
  	def printii(var fileName:String) {
  		try {
 			var str:String;
			val p: Point = Point.make(0);
			val lead:Long = at(D(p)) processSet(p).leaderId;
			str = "\n Leader: " + lead;
 			var fl:File = new File(fileName);
			var fw:FileWriter = new FileWriter(fl);
			for(var j:Long=0; j<str.length(); j++) {
				var jInt:Int = j as Int;
				var ch:Char = str.charAt(jInt);
				fw.writeChar(ch);
			}
			fw.close();
		}
		catch(ex: x10.lang.Exception){}
  	}
  	
  	/** Validates the output resulting from the execution of the algorithm. */  	
  	def outputVerifier() {
  		var max:Long = Long.MIN_VALUE;
  		for(var i:long=0; i<processes; i++)
			if(max < IdStore(i))
				max = IdStore(i);
		val p: Point = Point.make(0);
		var lead:Long = at(D(p)) processSet(p).leaderId;
		val q = Point.make(lead);
		lead = at(D(q)) processSet(q).id;
		if(max == lead)
			Console.OUT.println("Output verified");
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
