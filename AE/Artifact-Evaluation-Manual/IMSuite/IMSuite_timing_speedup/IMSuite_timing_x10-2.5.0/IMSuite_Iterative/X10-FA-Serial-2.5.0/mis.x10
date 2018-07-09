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
 * mis aims to find a maximal independent set from a set of nodes,
 * The algorithm utilizes random identifiers to select a candidate
 * node for MIS.
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
public class mis 
{
	var adj_graph:Array[Long];
	var nodes:Long;
	val Infinity = Long.MAX_VALUE;
	
	/** Abstract node representation as a distributed array. */
	var nodeSet:DistArray[node];

	/** Parameters to enable execution with load */
	var loadValue:long=0; 

	/** Load sum represented as a distributed array. */
	var nval:DistArray[long];
	
	/** Region and Distribution specification. */
	var R: Region;	var D: Dist;
	
	/** Other Distributed Array used. */
	var rs: DistArray[Random];
	var misSet:DistArray[Boolean];
	var mark:DistArray[Boolean];

	/** 
	 * Acts as the starting point for the program execution. 
	 * <code>main</code> performs the task of accepting the input from the user 
	 * specified file, creaton of MIS, printing the output and 
	 * validating the result.
	 *
	 * @param args 		array of runtime arguments.
	 * @throws Exception	if File handling operation illegal. 
	 */	
	public static def main(args:Rail[String]) throws Exception {
		var inputFile:String = "inputmis16.txt", outputFile:String = "outputmis16.txt";
		var i:Long,j:Long;
		var flag:Boolean = false;
		var ms:mis = new mis();

		for(i=0; i<args.size; i++)
		{
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
				ms.loadValue = Long.parse(args(i+1));
				i++;
			}	
			else
				Console.OUT.println("Wrong option specified");		
		}

		var fl:File = new File(inputFile);
		var fr:FileReader = new FileReader(fl);
		var s:String = fr.readLine();
		ms.nodes = Long.parse(s);
		ms.adj_graph = new Array[Long](Region.make(0..(ms.nodes-1), 0..(ms.nodes-1)));

		/** Region creation. */		
		ms.R = Region.make(0, (ms.nodes-1));
		
		/** Creation of a Block Distribution. */
     		ms.D = Dist.makeBlock(ms.R);
    		//ms.D = Dist.makeUnique();
    		//ms.R = ms.D.region;
    		
    		/** Distribution of nodes. */
    		ms.nodeSet = DistArray.make[node](ms.D);
    		
    		/** Some more data getting distributed. */
    		ms.misSet = DistArray.make[Boolean](ms.D);
    		ms.mark = DistArray.make[Boolean](ms.D);
    		ms.rs = DistArray.make[Random](ms.D);  
		ms.nval = DistArray.make[long](ms.D);
		
		try {
			j=0;
			while((s = fr.readLine()) != null) {
				for(i=0; i<s.length(); i++) {
					var iInt:Int = i as Int;
					var ch:Char=s.charAt(iInt);
					if(ch=='0')
						ms.adj_graph(j,i) = 0;
					else
						ms.adj_graph(j,i) = 1;	
				}
				j++;
			}
		} catch(eof: x10.io.EOFException){}
		
		ms.initialize();
		
		var startTime:long = System.nanoTime();
		var someval:Double = (Math.log(ms.nodes)/Math.log(2));
		var phases:Long = (someval as Long);
		var again:boolean = false;
		do {
			ms.misForm();
			again = ms.countNeighbor();
		}while(again);
		ms.checkMark();	
		var finishTime:long = System.nanoTime();
		var estimatedTime:long = finishTime - startTime;
		Console.OUT.println("Start Time: " + startTime + "\t Finish Time: " + finishTime + "\t Estimated Time: " + estimatedTime);
		
		ms.printMis(outputFile);
		if(flag)
			ms.outputVerifier();

		if(!ms.loadValue.equals(0)) {
			var sumval:double=0;
			for(i=0; i<ms.nodes; i++) {
				val pt:Point = Point.make(i);
				var arrSum:Long = ms.getNval(pt); 
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
					nodeSet(i) = new node();
					val idex:Long = i.operator()(0);
					
					for(var j:Long=0; j<nodes; j++)
						if(adj_graph(idex,j) == 1)
							nodeSet(i).neighbors.add(j);
					rs(i) = new Random(i(0));
				}
			}
	}
	
	/** 
	 * Aims to count the nodes which are still competing to be part of MIS.
	 *
	 * @return	true if there are still nodes competing for MIS.
	 */
	def countNeighbor():boolean {
		var flag:boolean = false;
		for(var i:Long=0; i<nodes; i++) {
			val pt:Point = Point.make(i);
			var neighSet:ArrayList[Long] = at(D(pt)) nodeSet(pt).neighbors;
			if(neighSet.size() > 0) {
				flag = true;
				break;
			}
		}
		return flag;
	}

	/** Aims to create an MIS from a given set of nodes. */	
	def misForm() {
		//finish 
			for(i in D) {
				//async 
				at(D(i)) {
					val idex:Long = i.operator()(0);
					nodeSet(i).randomValue = rs(i).nextDouble();
					for(j in 0..(nodeSet(i).neighbors.size()-1)) {
						val pt:Point = Point.make(nodeSet(i).neighbors.get(j));
						sendVal(nodeSet(i).randomValue, pt);
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
					var j:Long;
					var flag:Boolean=false;	
					var minId:double = Long.MAX_VALUE;
								
					for(j=0; j<nodeSet(i).neighborValue.size(); j++) {
						var nvalue:Double=nodeSet(i).neighborValue.get(j);
						if(minId.operator>(nvalue))
							minId = nvalue;
					}		
					if(minId.operator>(nodeSet(i).randomValue)) {
						if(nodeSet(i).neighbors.size() > 0) {
							misSet(i) = true;
							mark(i) = true;
						}	
						for(k in 0..(nodeSet(i).neighbors.size()-1)) {
							val neighbor:Long = nodeSet(i).neighbors.get(k);
							val npt:Point = Point.make(neighbor);
							val idex:Long = i.operator()(0);
										
							at(D(npt)) {	mark(npt) = true; 	} 
							deleteEdge(npt, idex);
							deleteNeighbor(npt);
							deleteEdge(i, neighbor); 
						}
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
					if(nodeSet(i).neighbors.size() > 0) {
						for(var j:Long=0; j<nodeSet(i).deleteQueue.size(); j++) {
							var nodeDelete:Long = nodeSet(i).deleteQueue.get(j);
							for(var k:Long=nodeSet(i).neighbors.size()-1; k>=0; k--) {
								var ndel:Long = nodeSet(i).neighbors.get(k);
								if(nodeDelete == ndel) {	
									nodeSet(i).neighbors.remove(ndel);
									break;
								}
							}
						}
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
					nodeSet(i).deleteQueue.clear();
					nodeSet(i).neighborValue.clear();		

					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
					}
				}
			}
	}

	/** Checks for unmarked nodes and adds them to MIS. */	
	def checkMark() {
		//finish 
			for(i in D) {
				//async 
				at(D(i)) {
					if(!mark(i))
						misSet(i)=true;

					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
					}
				}		
			}		
	}
	
	/** 
	 * Adding a node to the <code>deleteQueue</code> of target node.
	 * 
	 * @param	neighbor	Whose <code>deleteQueue</code> has to be modified.
	 * @param	sender		Node to be added to the <code>deleteQueue</code>.
	 */	
	def deleteEdge(val neighbor:Point, val sender:Long) {
		at(D(neighbor)){
			nodeSet(neighbor).deleteQueue.add(sender); 
		}	
	}
	
	/** 
	 * Delete the neighbor of a node.
	 *
	 * @param	aNode		Whose neighbors are to be marked for deletion.
	 */	
	def deleteNeighbor(val anode:Point) {
		at(D(anode)){
				for(var j:Long=0; j<nodeSet(anode).neighbors.size(); j++) {
					var neighbor:Long = nodeSet(anode).neighbors.get(j);
					val npt:Point = Point.make(neighbor);
					val idex:Long = anode.operator()(0);
					deleteEdge(npt, idex);
					deleteEdge(anode, neighbor); 
				}
		}	
	}				 
	
	/** 
	 * Sends the random value to the neighbor.
	 *
	 * @param	value		Random value.
	 * @param	neighbor	Value receiver.
	 */
	def sendVal(val value:Double, val neighbor:Point) {
		at(D(neighbor)){
			nodeSet(neighbor).neighborValue.add(value); 
		}	
	}
	
	/** 
	 * Writes the output to the user specified file.
	 * 
	 * @param  fileName	Name of the file in which output has to be stored.
	 * @throws 		input output exception if a failure in write occurs.
	 */
	def printMis(var fileName:String) {
  		try {
 			var fl:File = new File(fileName);
			fw:FileWriter = new FileWriter(fl);
			var str:String;
			for(var i:Long=0; i<nodes; i++) {
				val pt: Point = Point.make(i);
				var ismis: Boolean = at(D(pt)){
							var lflag:Boolean=false;
							if(misSet(pt))
								lflag = true;
							lflag
				};			
				if(ismis) {
					str="\n" + i;
					for(var j:Long=0; j<str.length(); j++) {
						var jInt:Int = j as Int;
						var ch:Char = str.charAt(jInt);
						fw.writeChar(ch);
					}
				}
			}
			fw.close();
		} catch(ex: x10.lang.Exception){}
  	}
  	
  	/** Validates the output resulting from the execution of the algorithm. */  	
  	def outputVerifier() {
		var i:Long;
		var flag:Boolean = false, setNodes:Rail[Boolean] = new Rail[Boolean](nodes);
		var misNodes:Rail[Boolean] = new Rail[Boolean](nodes);
		
		for(i=0; i<nodes; i++) {
			val pt: Point = Point.make(i);
			var ismis: Boolean = at(D(pt)){
						var lflag:Boolean=false;
						if(misSet(pt))
							lflag = true;
						lflag
			};			
			if(ismis) {
				misNodes(i) = true;
				for(var j:Long=0; j<nodes; j++)
					if(adj_graph(i,j) == 1)
						setNodes(j) = true;
			}			
		}				
		for(i=0; i<nodes; i++) {
			if(misNodes(i) && setNodes(i)) {
				flag = true;
				break;
			}
			else if(!setNodes(i) && !misNodes(i)) {
				flag = true;
				break;
			}	
		}
		if(!flag)
			Console.OUT.println("Output verified");
	}
}

/**
 * <code>node</code> specifies the structure for each abstract node,
 * part of the MIS algorithm.
 */
class node
{
	/** Specifies the random value selected for a node. */
	var randomValue:Double;
	
	/** Specifies the set of neighbors of a node. */
	var neighbors:ArrayList[Long] = new ArrayList[Long]();

	/** Stores the information about neighors to be deleted. */	
	var deleteQueue:ArrayList[Long] = new ArrayList[Long]();
	
	/** Mailbox to store the received random values of neighbors. */
	var neighborValue:ArrayList[Double] = new ArrayList[Double]();
}
