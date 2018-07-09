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
 * byzantine aims to reach a consensus among a set of nodes.
 * Nodes can be classified as traitors and non-traitors. 
 * Traitors aim to disrupt the consensus.
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
public class byzantine
{
	var adj_graph:Array[Int], traitorId:Array[Int], randomVote:Array[Int], totalNodes:Int, nodes:Int, traitorCount:Int;
        var ifTraitor:DistArray[Boolean];
        
        /** Abstract node representation as a distributed array. */
        var allNodes:DistArray[Nd];
        
        /** Region and Distribution specification. */
	var R: Region;		var D: Dist;

        /** Global coin toss value. */
        val gToss = coinToss();
        
        /** Counter for measuring total asyncs (as a distributed array). */ 
	var casync:DistArray[Int];
	
	/** Counter for measuring total finishes (as a distributed array). */ 
	var cfinish:DistArray[Int];
	
	/** Counter for measuring total communication (as a distributed array). */ 
	var cmess:DistArray[Int];
	
	/** Counter for measuring total atomics (as a distributed array). */ 
	var catom:DistArray[Int];

        /** 
	 * Sets the value of global coin toss.
	 * The value can be either 0 or 1.
	 *
	 * @return 	value of coin toss.
	 */
        final def coinToss():Int {
                var r:Random = new Random(100);
                var ret:Int = r.nextInt(2);
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
        public static def main(args:Array[String](1)) throws Exception {
                var inputFile:String = "inputbyzantine.txt", outputFile:String = "outputbyzantine.txt";
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
                val bz:byzantine = new byzantine();
                bz.totalNodes = Int.parse(s);
                bz.traitorCount = bz.totalNodes/8 - 1;
                bz.nodes = bz.totalNodes - bz.traitorCount;
                bz.adj_graph = new Array[Int]((0..(bz.totalNodes-1))*(0..(bz.totalNodes-1)));
                bz.randomVote = new Array[Int]((0..(bz.totalNodes-1)));
                bz.traitorId = new Array[Int]((0..(bz.traitorCount-1)));

		/** Region creation. */
		bz.R = 0..(bz.totalNodes-1);
		
		/** Creation of a Block Distribution. */
		bz.D = Dist.makeBlock(bz.R);
		
		/** Distribution of nodes. */
		bz.allNodes = DistArray.make[Nd](bz.D);
		
		/** Some more data getting distributed. */
                bz.ifTraitor = DistArray.make[Boolean](bz.D);
                
                /** Distribution of async counters. */
	  	bz.casync = DistArray.make[Int](bz.D);
	  	
	  	/** Distribution of finish counters. */
	  	bz.cfinish = DistArray.make[Int](bz.D);
	  	
	  	/** Distribution of communication counters. */
	  	bz.cmess = DistArray.make[Int](bz.D);
	  	
	  	/** Distribution of atomic counters. */
	  	bz.catom = DistArray.make[Int](bz.D);

                /** Global parameters used for consensus. */
                val L = (5*bz.totalNodes/8)+1;
	        val H = (3*bz.totalNodes/4)+1;
        	val G = (7*bz.totalNodes/8);
        	
                j=0;
		while(!((s = fr.readLine()).equals(" "))) {
			for(i=0; i<s.length(); i++) {
				var ch:Char=s.charAt(i);
				if(ch=='0')
					bz.adj_graph(j,i) = 0;
				else
					bz.adj_graph(j,i) = 1;	
			}
			j++;
		}
		
		while(!((s = fr.readLine()).equals(" "))) {
			for(i=0; i<s.length(); i++) {
				var ch:Char=s.charAt(i);
				if(ch=='0')
					bz.randomVote(i) = 0;
				else
					bz.randomVote(i) = 1;	
			}
		}
		
		try {	
			j=0;
			while((s = fr.readLine()) != null) {
				bz.traitorId(j) = Int.parse(s);
                	        j++;
			}
		} catch(eof: x10.io.EOFException){}	
		
		for(pt in bz.D) {
			val statTrait:Array[Int] = bz.traitorId;
			at(bz.D(pt)){
				var ipt:Int = pt(0);
				for(var ii:Int=0; ii<statTrait.size; ii++)
					if(statTrait(ii) == ipt) 
						bz.ifTraitor(pt) = true; 
			}
		}

                bz.initialize();
                
                for(i=0; i<bz.totalNodes/8+1; i++) {
                        bz.voteAll();
                        bz.voteDecision(L, H, G);
                }
                
		/** Call to method for computing total Async, Finish, Communication and Atomics. */
		bz.countValue();
        }
        
        /** Computes total Async, Finish, Communication and Atomic Operations. */ 
	def countValue() {
		var smess:Int=0, sasync:Int=0, sfinish:Int=0, satom:Int=0;
		var temp:Int=0;
		
		for(i in D) {
			temp = at(D(i)) cmess(i);
			smess = smess+temp;
			temp = at(D(i)) casync(i);
			sasync = sasync+temp;
			temp = at(D(i)) catom(i);
			satom = satom+temp;
			temp = at(D(i)) cfinish(i);
			sfinish = sfinish+temp;
		}
		
		Console.OUT.println(sfinish);
		Console.OUT.println(sasync);
		Console.OUT.println(smess);
		Console.OUT.println(satom);
	}

        /** Initializes all the fields of the abstract node. */ 
        def initialize() {
        	finish {
                        for(i in D) {
                                async at(D(i)) {
					allNodes(i) = new Nd();
					var Nde:Nd = allNodes(i);
					var index:Int = i(0);
                                        Nde.id = index;
                                        Nde.permanent=0;
                                        var counter:Int=0, j:int=0; 
                                        for(j=0; j<totalNodes; j++)
                                                if(adj_graph(index,j) == 1)
                                                        counter++;
					Nde.neighbors = new Array[Int]((0..(counter-1)));
					counter=0;
					for(j=0; j<totalNodes; j++)
                                                if(adj_graph(index,j) == 1) {
                                                	Nde.neighbors(counter) = j;
                                                        counter++;
                                                }        
					Nde.voteSent = new Array[Boolean](R);
					Nde.voteSent(index) = true;
					Nde.vote = new Array[Int]((0..(Nde.neighbors.size)));
					if(ifTraitor(i)) {
						var r:Random = new Random(index);
						for(j=0; j<Nde.neighbors.size; j++)
							Nde.vote(j) = r.nextInt(2);
						Nde.vote(Nde.neighbors.size) = randomVote(index);	
					}
					else
						Nde.vote(Nde.neighbors.size) = randomVote(index);		
                                }
                        }
    		}
	}

	/** Transmits the vote of each node to its neighbors.*/
        def voteAll() {
	        val fpt:Point = Point.make(0);
		at(D(fpt)){
			/** Finish statements, increment counter for finish. */
			cfinish(fpt) = cfinish(fpt) + 1;
		}
		
		val h0 = here;
        	finish {
                        for(i in D) {
                                async at(D(i)) {
                                	val h1 = here;
					atomic{
						/** If remote data access then increment counter for communication. */ 
						if(h0 != h1)
							cmess(i) = cmess(i) + 1;
						
						/** Async statements, increment counter for async. */	
						casync(i) = casync(i) + 1;
					}
					
                                	var index:Int = i(0);
                                	if(ifTraitor(i)) {
					        for(var j:Int=0; j<allNodes(i).neighbors.size; j++) {
	                                        	val pt:Point = Point.make(allNodes(i).neighbors(j));
                                                	broadcast(pt, index, allNodes(i).vote(j));
						}	                                        	        
					}
					else {
						for(var j:Int=0; j<allNodes(i).neighbors.size; j++) {
	                                        	val pt:Point = Point.make(allNodes(i).neighbors(j));
                                                	broadcast(pt, index, allNodes(i).vote(allNodes(i).neighbors.size));
						}
                               		}
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
	def broadcast(val aNode:Point, val source:Int, val Vote:Int) {
        	val h0 = here;
        	
        	at(D(aNode)){
        		val h1 = here;
        		
        		var nd:Nd = new Nd();
        		nd = allNodes(aNode);
                	var sflag:Boolean=false;
	                
	                atomic {
	                	/** If remote data access then increment counter for communication. */ 
        			if(h0 != h1)
        				cmess(aNode) = cmess(aNode) + 1;
        			
        			/** Inside existing atomic operation, increment counter for atomic. */			
        			catom(aNode) = catom(aNode) + 1;
        					
        			if(!nd.voteSent(source)) {
        				sflag = true;
        				nd.voteSent(source) = true;
        			}
        		}
        	        if(sflag) {
        	        	atomic{
        	        		/** Finish statements, increment counter for finish. */
        	        		cfinish(aNode) = cfinish(aNode) + 1;
        	        	}
        	        	
				finish {
                        		for(i in 0..(nd.neighbors.size-1)) {
						async {
                                        		atomic{ 
                                        			/** Async statements, increment counter for async. */
        	        					casync(aNode) = casync(aNode) + 1;
        	        				}
        	        	
                                        		val npt:Point = Point.make(nd.neighbors(i));
                                        		broadcast(npt, source, Vote);
                                        	}
					}
				}
                        
                        	atomic {
                        		/** Inside existing atomic operation, increment counter for atomic. */			
                        		catom(aNode) = catom(aNode) + 1;
                        		
                        	       	nd.receive.add(Vote);   
				}
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
	def voteDecision(val L:Int, val H:Int, val G:Int) {
        	val pt:Point = Point.make(0);
		at(D(pt)){
			/** Finish statements, increment counter for finish. */
			cfinish(pt) = cfinish(pt) + 1;
		}
		
		val h0 = here;
                finish {
                        for(i in D) {
                                async at(D(i)) {
                                	val h1 = here;
					atomic{
						/** If remote data access then increment counter for communication. */ 
						if(h0 != h1)
							cmess(i) = cmess(i) + 1;
						
						/** Async statements, increment counter for async. */	
						casync(i) = casync(i) + 1;
					}
					
                                        var v0:Int=0; var v1:Int=0; var maj:Int=-1; var tally:Int=0; var j:Int=0; var threshold:Int=0;
                                        var nd:Nd = allNodes(i);

                                        for(j=0; j<nd.receive.size(); j++) {
                                                var rec:Int = nd.receive.get(j);
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
                                        nd.voteSent = new Array[Boolean](R);
                                        nd.voteSent(i(0)) = true;
                                }
                        }
                }
        }
}

/**
 * <code>Nd</code> specifies the structure for each abstract node
 * part of the Byzantine algorithm.
 */
class Nd 
{
	/** Specifies the identifier for each node. */
	var id:Int;
	
	/** Specifies the decision of a node. */
        var decision:Int;                    
        
        /** Specifies the decision made by a node is set premanent or not. */
        var permanent:Int;
        
        /** Set of vote to be send a node. */
        var vote:Array[Int];
        
        /** Enumerates the neighbors of a node. */
        var neighbors:Array[Int];    
  
  	/** Represents the nodes which have sent their vote. */
        var voteSent:Array[Boolean];
        
        /** Set of votes received. */ 
        var receive:ArrayList[Int] = new ArrayList[Int]();
}
