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
 * mst aims to find a minimum spanning tree from a given graph 
 * consisting of a set of nodes and edges.
 * The main intuition behind the algorithm is selection of a 
 * blue edge (minimum weighted edge joining a fragment with 
 * another fragment).
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
public class mst
{
	/** Signals to indicate. */
        val START = 0;
        val JOIN = 1;
        val CHANGE = 2;
        
        var adj_graph:Array[Long], nodes:Long;
        val Infinity  = Long.MAX_VALUE;
        
        /** Abstract node representation as a distributed array. */
        var nodeSet:DistArray[node];

	/** Region and Distribution specification. */        
	var R: Region;	var D: Dist;

	/** Other Distributed Array used. */        
        var flag:DistArray[Boolean];

	/** Counter for measuring total asyncs (as a distributed array). */ 
	var casync:DistArray[Long];
	
	/** Counter for measuring total finishes (as a distributed array). */ 
	var cfinish:DistArray[Long];
	
	/** Counter for measuring total communication (as a distributed array). */ 
	var cmess:DistArray[Long];
	
	/** Counter for measuring total atomics (as a distributed array). */ 
	var catom:DistArray[Long];

	/** 
	 * Acts as the starting point for the program execution. 
	 * <code>main</code> performs the task of accepting the input 
	 * from the user specified file, creaton of MST, printing 
	 * the output and validating the result.
	 *
	 * @param args 		array of runtime arguments.
	 * @throws Exception	if File handling operation illegal. 
	 */
	public static def main(args:Rail[String]) throws Exception {
		var inputFile:String = "inputmst.txt", outputFile:String = "outputmst.txt";
		var i:Long,j:Long;
		var flag:Boolean = false;
		var ms:mst = new mst();

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
				i++;
			}	
			else
				Console.OUT.println("Wrong option spcified");		
		}

		var fl:File = new File(inputFile);
		var fr:FileReader = new FileReader(fl);
		var s:String = fr.readLine();
                ms.nodes = Long.parse(s);
                ms.adj_graph = new Array[Long](Region.make(0..(ms.nodes-1), 0..(ms.nodes-1)));
                var weightMatrix:Array[Long] = new Array[Long](Region.make(0..(ms.nodes-1), 0..(ms.nodes-1)));

		/** Region creation. */			
		ms.R = Region.make(0,(ms.nodes-1));
		
		/** Creation of a Block Distribution. */
    		ms.D = Dist.makeBlock(ms.R);
    		//ms.D = Dist.makeUnique();
    		//ms.R = ms.D.region;
    		
    		/** Distribution of nodes. */
	  	ms.nodeSet = DistArray.make[node](ms.D);
               
		/** Distribution of async counters. */
	  	ms.casync = DistArray.make[Long](ms.D);
	  	
	  	/** Distribution of finish counters. */
	  	ms.cfinish = DistArray.make[Long](ms.D);
	  	
	  	/** Distribution of communication counters. */
	  	ms.cmess = DistArray.make[Long](ms.D);
	  	
	  	/** Distribution of atomic counters. */
	  	ms.catom = DistArray.make[Long](ms.D);
 
                try {
			j=0;
			while(!((s = fr.readLine()).equals(" "))) {
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
		
		try {
			j=0;
                	while(j<ms.nodes) {
                	        i=0;
                	        while(i<ms.nodes) {
                	                s = fr.readLine();
                	                weightMatrix(j,i) = Long.parse(s);
                	                i++;
                	        }
                	        j++;
                	}
		} catch(eof: x10.io.EOFException){}

                ms.initialize();
		
		var cflag:Boolean = false;
                outer: while(true) {
                	ms.flag = DistArray.make[Boolean](ms.D);
                        ms.mstCreate(weightMatrix);
			cflag = ms.fragmentCheck();
			if(cflag)
				continue outer;
                        break;
                }

		/** Call to method for computing total Async, Finish, Communication and Atomics. */
		ms.countValue();
        }

	/** Computes total Async, Finish, Communication and Atomic Operations. */ 
	def countValue() {
		var smess:Long=0, sasync:Long=0, sfinish:Long=0, satom:Long=0;
		var temp:Long=0;
		
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
					var index:Long = i.operator()(0);
					nodeSet(i) = new node();
                	        	nodeSet(i).parent = Infinity;
                	        	nodeSet(i).fragmentRoot = index;
                	        	nodeSet(i).startSignal = START;
                	        	nodeSet(i).changeChild=-1;
                	        }	
			}
		}	                        
        }

	/** 
	 * Generates a minimum spanning tree.
	 *
	 * @param	weightMatrix	Edge Weight array.
	 */
        def mstCreate(val weightMatrix:Array[Long]) {
                val fpt:Point = Point.make(0);
		at(D(fpt)){
			/** Finish statements, increment counter for finish. */
			cfinish(fpt) = cfinish(fpt) + 8;
		}
		
		val h0 = here;
		
                clocked finish {
                	for(i in D) {
	              		clocked async at(D(i)) {
                			val h1 = here;
					atomic {
						/** If remote data access then increment counter for communication. */ 
						if(h0 != h1)
							cmess(i) = cmess(i) + 1;
						
						/** Async statements, increment counter for async. */	
						casync(i) = casync(i) + 1;
					}

                			var index:Long = i.operator()(0);
                			if(index == nodeSet(i).fragmentRoot) {
						nodeSet(i).startSignal = START;
                                		findNode(i, weightMatrix);
                                		flag(i) = true;
					}

					Clock.advanceAll();

                			if(flag(i))
			                	invertPath(i, nodeSet(i).pair.u);

					Clock.advanceAll();

		                	if(nodeSet(i).fragmentRoot == Infinity)
			                	nodeSet(i).fragmentRoot = index;

					Clock.advanceAll();

					if(index == nodeSet(i).fragmentRoot) {
						val pt:Point = Point.make(nodeSet(i).pair.v);
						sendJoinSignal(index, pt);
					}

					Clock.advanceAll();

                			var pt:Point = Point.make(nodeSet(i).pair.v);
                			flag(i) = false;			
					if(nodeSet(i).fragmentRoot == index && nodeSet(i).pair.u == index && getFragmentRoot(pt) != index)
						flag(i) = true;

					Clock.advanceAll();

					if(flag(i))
						fragmentAdd(i);

					Clock.advanceAll();

					if(nodeSet(i).fragmentRoot == index)
						transmit(i, nodeSet(i).fragmentRoot);

					Clock.advanceAll();

					nodeSet(i).startSignal = -1;
	                        	nodeSet(i).joinSignal.clear();
	                        	nodeSet(i).changeChild = -1;
	                        	nodeSet(i).changeSignal = -1;
	                        }
			}                       	
                }
        }

	/** 
         * Performs the task of finding blue edge for the fragment.
         * 
         * @param  	froots		Root of a fragment.
         * @param	weightMatrix	Edge Weight array.
         */
        def findNode(val aNode:Point, val weightMatrix:Array[Long]) {
        	val h0 = here;
        	at(D(aNode)){
        		val h1 = here;
			atomic {
				/** If remote data access then increment counter for communication. */ 
				if(h0 != h1)
					cmess(aNode) = cmess(aNode) + 1;
			}

	                var flag:Boolean=false;
        	        if(nodeSet(aNode).startSignal == START) {
        	        	var index:Long = aNode.operator()(0);
				nodeSet(aNode).pair.u = index;          nodeSet(aNode).pair.v = index;
        	                if(nodeSet(aNode).children.size() == 0) {
                                	findMinimum(aNode, weightMatrix);
                                	flag=true;
                        	}

                        	if(!flag) {
					atomic {
						/** Finish statements, increment counter for finish. */
						cfinish(aNode) = cfinish(aNode) + 1;
					}

					finish {
						for(c in 0..(nodeSet(aNode).children.size()-1)) {
                                			async {
                                				val child:Long = nodeSet(aNode).children.get(c);
                                				val cpt: Point = Point.make(child);
                                        			setSignal(cpt, START);
                                        			findNode(cpt, weightMatrix);
                                        			setSignal(cpt, -1);
								var cpair:blueEdge = new blueEdge();
								cpair = getPair(cpt);
								
								atomic
								{
									casync(aNode) = casync(aNode) + 1;
									catom(aNode) = catom(aNode) + 1;

                                        				if(weightMatrix(nodeSet(aNode).pair.u, nodeSet(aNode).pair.v) > weightMatrix(cpair.u, cpair.v))
                                        					nodeSet(aNode).pair.setPair(cpair.u, cpair.v);
                                        			}
                                        		}
						}
					}
                                }
				
                               	for(var i:Long=0; i<nodes; i++) {
                               		var ipt:Point = Point.make(i);
                              		if(getFragmentRoot(ipt) != nodeSet(aNode).fragmentRoot && adj_graph(index,i) == 1)
						if(weightMatrix(nodeSet(aNode).pair.u, nodeSet(aNode).pair.v) > weightMatrix(index, i))
							nodeSet(aNode).pair.setPair(index, i);
				}							                                                        
                        }
                }
        }


	/** 
         * Finds the minimum weighted edge having <code>aNode<\code> as
         * one of the vertices.
         *
         * @param  	aNode		Node whose minimum weighted edge has to be determined.
         * @param	weightMatrix	Edge Weight array.
         */
        def findMinimum(val aNode:Point, val weightMatrix:Array[Long]) {
        	val h0 = here;
        	at(D(aNode)){
        		val h1 = here;
        		atomic{
        			/** If remote data access then increment counter for communication. */ 
        			cmess(aNode) = cmess(aNode) + 1;
        		}
	
	                var min:Long = Infinity;
			var index:Long = aNode.operator()(0);
			
	                for(var i:Long=0; i<nodes; i++) {
	                	var ipt:Point = Point.make(i);
	                        if(adj_graph(index, i) == 1 && getFragmentRoot(ipt) != nodeSet(aNode).fragmentRoot)
	                                if(min > weightMatrix(index, i)) {
	                                        min = weightMatrix(index, i);
	                                        nodeSet(aNode).pair.setPair(index, i);
	                                }
			}	                                
		}	                                
        }

	/**
	 * Performs the task of merging two fragments.
	 * 
	 * @param  aNode	Node whose fragment will merge with another fragment.
	 */
        def fragmentAdd(val aNode:Point) {
        	val h0 = here;
        	at(D(aNode)){
        		val h1 = here;
        		atomic{
        			/** If remote data access then increment counter for communication. */ 
        			cmess(aNode) = cmess(aNode) + 1;
        		}

        		var index:Long = aNode.operator()(0);		
	        	var flag:Boolean = false;
	        	if(nodeSet(aNode).joinSignal.size() > 0) {
	        		var mgr:Merge = new Merge();
	        		for(var i:Long=0; i<nodeSet(aNode).joinSignal.size(); i++) {
	        			mgr = nodeSet(aNode).joinSignal.get(i);
	        			if(mgr.v == nodeSet(aNode).pair.v) {
	        				flag = true;
	        				break;
	        			}
	        		}
	        		
	        		if(flag) {
					if(index < nodeSet(aNode).pair.v) {
	        				nodeSet(aNode).fragmentRoot = index;
	        				addChild(aNode, nodeSet(aNode).pair.v);
	        				var cpt:Point = Point.make(nodeSet(aNode).pair.v);	
						setParent(cpt, index);
						setFragmentRoot(cpt, index);
	        			}		        		
	        		}
	        	}
	        	if(!flag) {
	        		var pt:Point = Point.make(nodeSet(aNode).pair.v);
				addChild(pt, index);
				nodeSet(aNode).parent = nodeSet(aNode).pair.v;
				nodeSet(aNode).fragmentRoot = Infinity;        	
	        	}
		}	        	
	}

	/** Transmits the root of a fragment to all the other nodes of the fragment. */
	def transmit(val aNode:Point, val uNode:Long) {
        	val h0 = here;
        	at(D(aNode)){
        		val h1 = here;
	        	atomic{
	        		/** If remote data access then increment counter for communication. */ 
	        		if(h0 != h1)
	        			cmess(aNode) = cmess(aNode) + 1;
	        		
	        		/** Finish statements, increment counter for finish. */	
        			cfinish(aNode) = cfinish(aNode) + 1;
        		}
        		
        	        finish {
        	        	for(i in 0..(nodeSet(aNode).children.size()-1)) {
        	        		async {
        					atomic { 
        						/** Async statements, increment counter for async. */
        						casync(aNode) = casync(aNode) + 1;
        					}

        	        			val cpt:Point = Point.make(nodeSet(aNode).children.get(i));
        	        			setFragmentRoot(cpt, uNode);
        	                		transmit(cpt, uNode);	
					}
				}	                           		
        	        }
		}        	        
        }

	/**
	 * Inverts the path, that is if the root and source of the pair (u)
	 * are different then the parent-child relationships on path from
	 * u to root are reversed.
	 *
	 * @param  aNode	node on path from root to <code>uNode<\code>
	 * @param  uNode	source of the pair (u)
	 */
        def invertPath(val aNode:Point, val uNode:Long) {
        	val h0 = here;
        	at(D(aNode)){
        		val h1 = here;
        		atomic {
        			/** If remote data access then increment counter for communication. */ 
        			cmess(aNode) = cmess(aNode) + 1;
        		}

        		var index:Long = aNode.operator()(0);
	                if(index == uNode) {
        	                nodeSet(aNode).fragmentRoot = Infinity;
        	                if(nodeSet(aNode).parent != Infinity) {
        	                        nodeSet(aNode).children.add(nodeSet(aNode).parent);
        	                        var pt:Point = Point.make(nodeSet(aNode).parent);
        	                        setSignal(pt, CHANGE);
                                
        	                        childToRemove(index, pt);
        	                        nodeSet(aNode).parent = Infinity;
        	                }
        	        }

        	        else {
        	        	atomic {
	        			/** Finish statements, increment counter for finish. */
	        			cfinish(aNode) = cfinish(aNode) + 1;
	        		}
	        		
        	        	finish {
        	        		for(i in 0..(nodeSet(aNode).children.size()-1)) {
        	                		async {
        						atomic {
        							/** Async statements, increment counter for async. */
        							casync(aNode) = casync(aNode) + 1;
				        		}

			                        	val cpt:Point = Point.make(nodeSet(aNode).children.get(i));
        			                        invertPath(cpt, uNode);
						}
					}					        		                        
        	                }

				if(nodeSet(aNode).changeSignal == CHANGE) {
                        		nodeSet(aNode).fragmentRoot = uNode;
					nodeSet(aNode).children.remove(nodeSet(aNode).changeChild);
	
                        	        if(nodeSet(aNode).parent != Infinity) {
                        	        	var pt:Point = Point.make(nodeSet(aNode).parent);
                        	        	setSignal(pt, CHANGE);
                        	                nodeSet(aNode).children.add(nodeSet(aNode).parent);
                        	                childToRemove(index, pt);
                        	                        
                        	                nodeSet(aNode).parent = nodeSet(aNode).changeChild;
                        	                nodeSet(aNode).changeChild = -1;
					}
                        	        else {
                        	        	nodeSet(aNode).parent = nodeSet(aNode).changeChild;
                        	                nodeSet(aNode).changeChild = -1;
					}
                        	}
                	}
        	}
	}        	
        
        def fragmentCheck():Boolean
        {
		val h0 = here;
        	val pt:Point = Point.make(0);
        	var frag:Long = at(D(pt)) nodeSet(pt).fragmentRoot;
        	var boolRet:Boolean = false;
		
		for(var i:Long=1; i<nodes; i++)
		{
			val cpt:Point = Point.make(i);
	        	var nfrag:Long = at(D(cpt)) nodeSet(cpt).fragmentRoot;
	        	if(frag != nfrag)
	        	{
	        		boolRet = true;
	        		break;
	        	}	

			at(D(cpt)) {
				val h1 = here;
				
				/** If remote data access then increment counter for communication. */ 
				if(h0 != h1)
		        		cmess(cpt) = cmess(cpt) + 1;
	        	}
		}
		
		return boolRet;		
        }
       
       	/**
	 * Specifies the child to remove from a node. 
	 * @param  child	node which has to be unmarked as a child.
	 * @param  parent	node whose child has to be removed.
	 */
        def childToRemove(val child:Long, val parent:Point) {
        	val h0 = here;
        	at(D(parent)){
        		val h1 = here;
        		atomic{
        			/** If remote data access then increment counter for communication. */ 
        			if(h0 != h1)
	        			cmess(parent) = cmess(parent) + 1;
        		}
	        	nodeSet(parent).changeChild = child;
	        }	
        }

	/**
	 * Sets the signal of a node as <code>START<\code> or <code>CHANGE<\code>.
	 * @param  aNode	node for which signal has to be set
	 * @param  uSignal	Value of signal.
	 */
        def setSignal(val aNode:Point, val uSignal:Long) {
		val h0 = here;
        	at(D(aNode)){
			val h1 = here;

			if(uSignal == CHANGE)
				nodeSet(aNode).changeSignal = CHANGE;
			else if(uSignal == START)
             	           	nodeSet(aNode).startSignal = START;
			else
				nodeSet(aNode).startSignal = -1;		                     
			atomic{
				/** If remote data access then increment counter for communication. */ 
				if(h0 != h1)
	        			cmess(aNode) = cmess(aNode) + 1;
        		}
		}	   
        }
        
        /**
	 * Adds a child to the set of children of a node.
	 * @param  aNode 	node which will get a new child.
	 * @param  child	node which is assigned a new parent node.
	 */
        def addChild(val aNode:Point, val child:Long) {
        	val h0 = here;
        	at(D(aNode)){
        		val h1 = here;
	        	atomic{
	                	nodeSet(aNode).children.add(child);
	                	
	                	/** If remote data access then increment counter for communication. */ 
	                	if(h0 != h1)
		                	cmess(aNode) = cmess(aNode) + 1;
		                
		                /** Async statements, increment counter for async. */	
	                	catom(aNode) = catom(aNode) + 1;
                	}
		}                		
        }

	/**
	 * Provides the destination node (v) of the blue edge pair.
	 * 
	 * @param   aNode	node whose blue edge pair information needed
	 * @return		destination node (v) of blue edge pair
	 */
        def getPairV(val aNode:Point):Long {
		val h0 = here;
        	at(D(aNode)){
        		val h1 = here;
        		atomic {
        			/** If remote data access then increment counter for communication. */ 
        			if(h0 != h1)
	        			cmess(aNode) = cmess(aNode) + 1;
        		}
        	}
        	var retVal:Long = at(D(aNode)) nodeSet(aNode).pair.v;
                return retVal;
        }
        
        /**
	 * Provides the blue edge pair.
	 * 
	 * @param   aNode	node whose blue edge pair needed
	 * @return		blue edge pair
	 */
        def getPair(val aNode:Point):blueEdge {
		val h0 = here;
        	at(D(aNode)){
        		val h1 = here;
        		atomic {
        			/** If remote data access then increment counter for communication. */ 
        			if(h0 != h1)
	        			cmess(aNode) = cmess(aNode) + 1;
        		}
        	}
        	var bedge:blueEdge = new blueEdge();
        	bedge.u = at(D(aNode)) nodeSet(aNode).pair.u;
        	bedge.v = at(D(aNode)) nodeSet(aNode).pair.v;
                return bedge;
        }

	/**
	 * Sets the parent of <code>aNode<\code>.
	 * 
	 * @param  aNode	node whose parent needs to be changed.
	 * @param  Parent	node which will act as parent
	 */
        def setParent(val aNode:Point, val Parent:Long) {
        	val h0 = here;
        	at(D(aNode)){
        		val h1 = here;
	                nodeSet(aNode).parent = Parent;
	                atomic {
	                	/** If remote data access then increment counter for communication. */ 
	                	if(h0 !=  h1)
		                	cmess(aNode) = cmess(aNode) + 1;
	                }
		}	                
        }

	/**
	 * Sets the fragment root of <code>aNode<\code>.
	 * 
	 * @param  aNode	node whose fragment root needs to be updated
	 * @param  root		node which will act as fragment rooot
	 */
        def setFragmentRoot(val aNode:Point, val root:Long) {
        	val h0 = here;
        	at(D(aNode)){
        		val h1 = here;
	                nodeSet(aNode).fragmentRoot = root;
	                atomic {
	                	/** If remote data access then increment counter for communication. */ 
	                	if(h0 !=  h1)
		                	cmess(aNode) = cmess(aNode) + 1;
	                }
		}	                
        }

	/**
	 * Provides the fragment root of <code>aNode<\code>.
	 * 
	 * @param   aNode	node whose fragment root needed
	 * @return		fragment root
	 */
        def getFragmentRoot(val aNode:Point):Long {
		val h0 = here;
        	at(D(aNode)){
        		val h1 = here;
        		atomic {
        			/** If remote data access then increment counter for communication. */ 
        			if(h0 != h1)
        				cmess(aNode) = cmess(aNode) + 1;
        		}
        	}
        	var retVal:Long = at(D(aNode)) nodeSet(aNode).fragmentRoot;
                return retVal;
        }

	/**
	 * Sends the join signal to a specified node.
	 * 
	 * @param  sender	node which sends the <code>JOIN<\code> signal
	 * @param  receiver	node which will receive the signal.will act as fragment rooot
	 */
        def sendJoinSignal(val sender:Long, val receiver:Point) {
        	val h0 = here;
        	at(D(receiver)){
        		val h1 = here;
	        	atomic {
	        		/** Inside existing atomic operation, increment counter for atomic. */
        			catom(receiver) = catom(receiver) + 1;
        			
        			/** If remote data access then increment counter for communication. */ 
        			if(h0 != h1)
	        			cmess(receiver) = cmess(receiver) + 1;

        			var mgr:Merge = new Merge();
        			mgr.setData(JOIN, sender);
        	        	nodeSet(receiver).joinSignal.add(mgr);
			}
		}			                	
        }
}

/** Specifies the structure for sending a merge request. */
class Merge
{
	var join:Long;
	var v:Long;
	def setData(var join:Long, var v:Long) {
		this.join = join;
		this.v = v;
	}
}

/** Specifies the structure for a blue edge pair. */
class blueEdge
{
        var u:Long; var v:Long;
        def setPair(var u:Long, var v:Long) {
                this.u = u;
                this.v = v;
        }
}

/**
 * <code>node</code> specifies the structure for each abstract node,
 * part of the MST algorithm.
 */
class node
{
	/** Specifies the fragment root for a node. */
        var fragmentRoot:Long;
        
        /** States the parent of a node. */
        var parent:Long;
        
        var startSignal:Long;
        var changeSignal:Long, changeChild:Long;
        
        /** Stores a blue edge pair. */
        var pair:blueEdge = new blueEdge();
        
        /** Stores the received join signals. */
        var joinSignal:ArrayList[Merge] = new ArrayList[Merge]();
        
 	/** Identifies the children of a node. */
        var children:ArrayList[Long] = new ArrayList[Long]();
}
