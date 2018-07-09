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
 * byzantine aims to reach a consensus among a set of nodes.
 * Nodes can be classified as traitors and non-traitors. 
 * Traitors aim to disrupt the consensus.
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
public class byzantine
{
	var adj_graph:Array[Long], traitorId:Rail[Long], randomVote:Rail[Long], totalNodes:Long, nodes:Long, traitorCount:Long;
        var ifTraitor:DistArray[Boolean];
        
        /** Abstract node representation as a distributed array. */
        var allNodes:DistArray[Nd];

	/** Parameters to enable execution with load */
	var loadValue:long=0; 

	/** Load sum represented as a distributed array. */
	var nval:DistArray[long];
        
        /** Region and Distribution specification. */
	var R: Region;		var D: Dist;

        /** Global coin toss value. */
        val gToss = coinToss();

	/** 
	 * Sets the value of global coin toss.
	 * The value can be either 0 or 1.
	 *
	 * @return 	value of coin toss.
	 */
        final def coinToss():Long {
                var r:Random = new Random(100);
                var ret:Long = r.nextLong(2);
                return ret;
        }	
	
	/** 
	 * Acts as the starting point for the program execution. 
	 * <code>main</code> performs the task of accepting the input from the user 
	 * specified file, consensus decision fromulation, printing the output 
	 * and validating the result.
	 *
	 * @param args 		array of runtime arguments.
	 * @throws Exception	if File handling operation illegal. 
	 */
        public static def main(args:Rail[String]) throws Exception {
                var inputFile:String = "inputbyzantine.txt", outputFile:String = "outputbyzantine.txt";
		var i:Long,j:Long;
		var flag:Boolean = false;
		val bz:byzantine = new byzantine();

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
				bz.loadValue = Long.parse(args(i+1));
				i++;
			}
			else
				Console.OUT.println("Wrong option spcified");		
		}
		
		var fl:File = new File(inputFile);
		var fr:FileReader = new FileReader(fl);
		var s:String = fr.readLine();
                bz.totalNodes = Long.parse(s);
                bz.traitorCount = bz.totalNodes/8 - 1;
                bz.nodes = bz.totalNodes - bz.traitorCount;
                bz.adj_graph = new Array[Long](Region.make(0..(bz.totalNodes-1), 0..(bz.totalNodes-1)));
                bz.randomVote = new Rail[Long](bz.totalNodes);
                bz.traitorId = new Rail[Long](bz.traitorCount);

		/** Region creation. */
		bz.R = Region.make(0,(bz.totalNodes-1));
		
		/** Creation of a Block Distribution. */
		bz.D = Dist.makeBlock(bz.R);
		
		/** Distribution of nodes. */
		bz.allNodes = DistArray.make[Nd](bz.D);
		
		/** Some more data getting distributed. */
                bz.ifTraitor = DistArray.make[Boolean](bz.D);

		bz.nval = DistArray.make[Long](bz.D);

		/** Global parameters used for consensus. */
                val L = (5*bz.totalNodes/8)+1;
	        val H = (3*bz.totalNodes/4)+1;
        	val G = (7*bz.totalNodes/8);
        	
                j=0;
		while(!((s = fr.readLine()).equals(" "))) {
			for(i=0; i<s.length(); i++) {
				var iInt:Int = i as Int;
				var ch:Char=s.charAt(iInt);
				if(ch=='0')
					bz.adj_graph(j,i) = 0;
				else
					bz.adj_graph(j,i) = 1;	
			}
			j++;
		}
		
		while(!((s = fr.readLine()).equals(" "))) {
			for(i=0; i<s.length(); i++) {
				var iInt:Int = i as Int;
				var ch:Char=s.charAt(iInt);
				if(ch=='0')
					bz.randomVote(i) = 0;
				else
					bz.randomVote(i) = 1;	
			}
		}
		
		try {	
			j=0;
			while((s = fr.readLine()) != null) {
				bz.traitorId(j) = Long.parse(s);
                	        j++;
			}
		} catch(eof: x10.io.EOFException){}	
		
		for(pt in bz.D) {
			val statTrait:Rail[Long] = bz.traitorId;
			at(bz.D(pt)){
				var ipt:Long = pt(0);
				for(var ii:Long=0; ii<statTrait.size; ii++)
					if(statTrait(ii) == ipt) 
						bz.ifTraitor(pt) = true; 
			}
		}

                bz.initialize();
                
                var startTime:long = System.nanoTime();
                for(i=0; i<bz.totalNodes/8+1; i++)
                {
                        bz.voteAll();
                        bz.voteDecision(L, H, G);
                }
                var finishTime:long = System.nanoTime();
                var estimatedTime:long = finishTime - startTime;
                Console.OUT.println("Start Time: " + startTime + "\t Finish Time: " + finishTime + "\t Estimated Time: " + estimatedTime);       
                
                bz.printDecision(outputFile);
                if(flag)
                        bz.outputVerifier();

		if(!bz.loadValue.equals(0)) {
			var sumval:double=0;
			for(i=0; i<bz.nodes; i++) {
				val pt:Point = Point.make(i);
				var arrSum:Long = bz.getNval(pt); 
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
					allNodes(i) = new Nd();
					var Nde:Nd = allNodes(i);
					var index:Long = i(0);
                                        Nde.id = index;
                                        Nde.permanent=0;
                                        var counter:Long=0, j:Long=0; 
                                        for(j=0; j<totalNodes; j++)
                                                if(adj_graph(index,j) == 1)
                                                        counter++;
					Nde.neighbors = new Rail[Long](counter);
					counter=0;
					for(j=0; j<totalNodes; j++)
                                                if(adj_graph(index,j) == 1) {
                                                	Nde.neighbors(counter) = j;
                                                        counter++;
                                                }        
					Nde.voteSent = new Rail[Boolean](totalNodes);
					Nde.voteSent(index) = true;
					Nde.vote = new Rail[Long]((Nde.neighbors.size+1));
					if(ifTraitor(i)) {
						var r:Random = new Random(index);
						for(j=0; j<Nde.neighbors.size; j++)
							Nde.vote(j) = r.nextLong(2);
						Nde.vote(Nde.neighbors.size) = randomVote(index);	
					}
					else
						Nde.vote(Nde.neighbors.size) = randomVote(index);		
                                }
                        }
	}

	/** Transmits the vote of each node to its neighbors.*/
        def voteAll() {
        	//finish 
                        for(i in D) {
                                //async 
				at(D(i)) {
                                	var index:Long = i(0);
                                	if(ifTraitor(i)) {
					        for(var j:Long=0; j<allNodes(i).neighbors.size; j++) {
	                                        	val pt:Point = Point.make(allNodes(i).neighbors(j));
                                                	broadcast(pt, index, allNodes(i).vote(j));
						}	                                        	        
					}
					else {
						for(var j:Long=0; j<allNodes(i).neighbors.size; j++) {
	                                        	val pt:Point = Point.make(allNodes(i).neighbors(j));
                                                	broadcast(pt, index, allNodes(i).vote(allNodes(i).neighbors.size));
						}
                               		}

					if(!loadValue.equals(0)) {
						var retVal:Long = loadweight(nval(i)+index);
					}
                                }
                        }
        }

	/** 
         * Broadcasts vote received by a node to all its neighbors.
         * 
         * @param aNode		Vote receiver.
	 * @param source	Source of Vote.
	 * @param Vote		Vote value.
	 */
	def broadcast(val aNode:Point, val source:Long, val Vote:Long) {
        	at(D(aNode)){
        		var nd:Nd = new Nd();
        		nd = allNodes(aNode);
                	var sflag:Boolean=false;
	                
        		if(!nd.voteSent(source)) {
        			sflag = true;
        			nd.voteSent(source) = true;
        		}
                	
        	        if(sflag) {
				//finish 
                        		for(i in 0..(nd.neighbors.size-1)) {
						//async 
                                        		val npt:Point = Point.make(nd.neighbors(i));
                                        		broadcast(npt, source, Vote);

							if(!loadValue.equals(0)) {
								val ipt:Long = aNode(0);
								var retVal:Long = loadweight(nval(aNode)+ipt);
							}
					}
                        	nd.receive.add(Vote); 
                	}
                }	 
        }	
	
	/** 
	 * Consensus decision is made based on the majority of votes.
	 *
	 * @param L	Global variable.
	 * @param H	Global variable.
	 * @param G	Global variable.
	 */
	def voteDecision(val L:Long, val H:Long, val G:Long) {
                //finish 
                        for(i in D) {
                                //async 
				at(D(i)) {
                                        var v0:Long=0; var v1:Long=0; var maj:Long=-1; var tally:Long=0; var j:Long=0; var threshold:Long=0;
                                        var nd:Nd = allNodes(i);

                                        for(j=0; j<nd.receive.size(); j++) {
                                                var rec:Long = nd.receive.get(j);
                                                if(rec == 1)
                                                        v1++;
                                                else
                                                        v0++;
                                        }

                                        if (nd.vote(nd.neighbors.size) == 1)
                                                v1++;
                                        else
                                                v0++;
                                                
                                        if(v1 > v0) {
                                                maj=1;
                                                tally=v1;
                                        }
                                        else {
                                                maj=0;
                                                tally=v0;
                                        }

                                        threshold = gToss == 0? L: H;
                                        if(tally > threshold)
                                                nd.vote(nd.neighbors.size) = maj;
                                        else
                                                nd.vote(nd.neighbors.size) = 0;

                                        if(tally > G && nd.permanent == 0) {
                                                nd.decision = maj;
                                                nd.permanent = 1;
                                        }
                                        nd.receive.clear();
                                        nd.voteSent = new Rail[Boolean](totalNodes);
                                        nd.voteSent(i(0)) = true;

					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
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
	def printDecision(var fileName:String) {
        	try {
 			var fl:File = new File(fileName);
			fw:FileWriter = new FileWriter(fl);
			var str:String;
			str="\nNon traitor nodes: ";
			for(var j:Long=0; j<str.length(); j++) {
				var jInt:Int = j as Int;
				var ch:Char = str.charAt(jInt);
				fw.writeChar(ch);
			}
                        for(i in D) {
                      		val pt:Point = i;
                      		var iden:Long = at(D(pt)) allNodes(pt).id;
                      		var dec:Long = at(D(pt)) allNodes(pt).decision;
                      		var perma:Long = at(D(pt)) allNodes(pt).permanent;
                      		var stat:Boolean = at(D(pt)){
                      					var isTrait:Boolean = false;
                      					if(ifTraitor(pt))
                      						isTrait = true;
                      					isTrait	
                      		};
                      		if(!stat) {
                      			str="\nNode " + iden + ":\t" + "Decision Taken: " + dec + "\t Permanent: " + perma;
                      			for(var j:Long=0; j<str.length(); j++) {	
						var jInt:Int = j as Int;
						var ch:Char = str.charAt(jInt);
						fw.writeChar(ch);
					}
				}	
                      	}	
                	str="\nTraitor nodes: ";
                	for(var j:Long=0; j<str.length(); j++) {
				var jInt:Int = j as Int;
				var ch:Char = str.charAt(jInt);
				fw.writeChar(ch);
			}
                        for(i in D) {
                      		val pt:Point = i;
                      		var iden:Long = at(D(pt)) allNodes(pt).id;
                      		var dec:Long = at(D(pt)) allNodes(pt).decision;
                      		var perma:Long = at(D(pt)) allNodes(pt).permanent;
                      		var stat:Boolean = at(D(pt)){
                      					var isTrait:Boolean = false;
                      					if(ifTraitor(pt))
                      						isTrait = true;
                      					isTrait	
                      		};
                      		if(stat) {
                      			str="\nNode " + iden + ":\t" + "Decision Taken: " + dec + "\t Permanent: " + perma;
                      			for(var j:Long=0; j<str.length(); j++) {	
						var jInt:Int = j as Int;
						var ch:Char = str.charAt(jInt);
						fw.writeChar(ch);
					}
				}	
                      	}	
	                fw.close();
        	}
        	catch(ex: x10.lang.Exception){}
	}

	/** Validates the output resulting from the execution of the algorithm. */
        def outputVerifier() {
                var sum:Long=0;
		for(i in D) {
                	val pt:Point = i;
                	var decs:Long = at(D(pt)) allNodes(pt).decision;
                	var stat:Boolean = at(D(pt)){
                      				var isTrait:Boolean = false;
                      				if(ifTraitor(pt))
                      					isTrait = true;
                      				isTrait	
                      	};
                      	if(!stat)
                        	sum = sum + decs;
                }        

                if((sum == totalNodes - traitorCount) || (sum == 0))
                        Console.OUT.println("Output verified");
        }	
}

/**
 * <code>Nd</code> specifies the structure for each abstract node
 * part of the Byzantine algorithm.
 */
class Nd 
{
	/** Specifies the identifier for each node. */
	var id:Long;
	
	/** Specifies the decision of a node. */
        var decision:Long;                    
        
        /** Specifies the decision made by a node is set premanent or not. */
        var permanent:Long;
        
        /** Set of vote to be send a node. */
        var vote:Rail[Long];
        
        /** Enumerates the neighbors of a node. */
        var neighbors:Rail[Long];    
  
  	/** Represents the nodes which have sent their vote. */
        var voteSent:Rail[Boolean];
        
        /** Set of votes received. */ 
        var receive:ArrayList[Long] = new ArrayList[Long]();
}
