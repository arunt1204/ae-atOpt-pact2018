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
        
        var adj_graph:Array[Int], nodes:Int; //weightMatrix:Array[Int], ;
        val Infinity  = Int.MAX_VALUE;
        
        /** Abstract node representation as a distributed array. */
        var nodeSet:DistArray[node];
        
        /** Region and Distribution specification. */
	var R: Region;	var D: Dist;
	
	/** Other Distributed Array used. */
	var cagain:DistArray[boolean];
        var tagain:DistArray[boolean];
        var invagain:DistArray[boolean];
        var setCheck:DistArray[boolean];
        var checkflag:DistArray[boolean];
        
        /** Counter for measuring total communication (as a distributed array). */ 
	var cmess:DistArray[Int];

	/** 
	 * Acts as the starting point for the program execution. 
	 * <code>main</code> performs the task of accepting the input 
	 * from the user specified file, creaton of MST, printing 
	 * the output and validating the result.
	 *
	 * @param args 		array of runtime arguments.
	 * @throws Exception	if File handling operation illegal. 
	 */
	public static def main(args:Array[String](1)) throws Exception {
		var inputFile:String = "inputmst.txt", outputFile:String = "outputmst.txt";
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
                var ms:mst = new mst();
                ms.nodes = Int.parse(s);
                ms.adj_graph = new Array[Int]((0..(ms.nodes-1))*(0..(ms.nodes-1)));
                var weightMatrix:Array[Int] = new Array[Int]((0..(ms.nodes-1))*(0..(ms.nodes-1)));

		/** Region creation. */		
		ms.R = 0..(ms.nodes-1);
		
		/** Creation of a Block Distribution. */
    		ms.D = Dist.makeBlock(ms.R);
    		//ms.D = Dist.makeUnique();
    		//ms.R = ms.D.region;
    		
    		/** Distribution of nodes. */
	  	ms.nodeSet = DistArray.make[node](ms.D);
	  	
    		/** Distribution of communication counters. */
    		ms.cmess = DistArray.make[Int](ms.D);
                
                try {
			j=0;
			while(!((s = fr.readLine()).equals(" "))) {
				for(i=0; i<s.length(); i++) {
					var ch:Char=s.charAt(i);
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
                	                weightMatrix(j,i) = Int.parse(s);
                	                i++;
                	        }
                	        j++;
                	}
		} catch(eof: x10.io.EOFException){}

                ms.initialize();
		
		var count:Int = 0;
		var cflag:Boolean = false;
                var startTime:long = System.nanoTime();
                outer: while(true) {
                	ms.checkflag = DistArray.make[Boolean](ms.D);
                        ms.mstCreate(count, weightMatrix);
                        count++;
			cflag = ms.fragmentCheck();

			/** Every iteration marks a round. */			
			ms.messCount();
			ms.cmess = DistArray.make[Int](ms.D);
			
			if(cflag)
				continue outer;
                        break;
                }
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
					var index:Int = i.operator()(0);
					nodeSet(i) = new node();
                	        	nodeSet(i).parent = Infinity;
                	        	nodeSet(i).fragmentRoot = index;
                	        	nodeSet(i).startSignal = START;
                	        	nodeSet(i).tempStartSignal = START;
                	        	nodeSet(i).tempChangeSignal = -1;
                	        	nodeSet(i).changeChild=-1;
                	        }	
			}
		}	                        
        }
	
	/** 
	 * Generates a minimum spanning tree.
	 *
	 * @param	phase		Current phase.
	 * @param	weightMatrix	Edge Weight array.
	 */
	def mstCreate(val phase:Int, val weightMatrix:Array[Int]) {
                var counter:Int=0;
                var again:boolean = false;
                val h0 = here;
                finish {
                	for(i in D) {
	              		async at(D(i)) {
                			val h1 = here;
                			
                			/** 
				 	 * Checking for remote data access in isolation.
				 	 * If remote data access then increment counter for communication.
				 	 */
                			if(h0 != h1)
                				cmess(i) = cmess(i) + 10;
                				
                			var index:Int = i.operator()(0);
                			if(index == nodeSet(i).fragmentRoot) {
						nodeSet(i).startSignal = START;
                                		checkflag(i) = true;
					}	
                		}
			}
                }
                
		for(i in D) {
			val check:Boolean = at(D(i)) checkflag(i);
			if(check)
				counter++;
		}
		do {
                	setCheck = DistArray.make[boolean](D);
                	again = setChildSignal();
		}while(again);
		
		cagain = DistArray.make[boolean](D);
		findNode(counter, weightMatrix);
                
                finish {
                	for(i in D) {
	              		async at(D(i)) {
                			if(checkflag(i)) {
			                	var pt:Point = Point.make(nodeSet(i).pair.u);
			                	setSignal(pt, CHANGE);
			                	childToRemove(nodeSet(i).pair.u, pt);
			                }	
				}       
			}
		}
		
		for(i in D) {
			at(D(i))	nodeSet(i).changeSignal = nodeSet(i).tempChangeSignal;
		}	
		
		invagain = DistArray.make[boolean](D);
		invertPath(counter);			                			
		finish {
			for(i in D) {
                		async at(D(i)) {
                			var index:Int = i.operator()(0);
		                	if(nodeSet(i).fragmentRoot == Infinity)
			                	nodeSet(i).fragmentRoot = index;
				}	                	
			}
		}
		
		finish {
			for(i in D) {
                		async at(D(i)) {
                			var index:Int = i.operator()(0);
					if(index == nodeSet(i).fragmentRoot) {
						val pt:Point = Point.make(nodeSet(i).pair.v);
						sendJoinSignal(index, pt);
					}						
				}
			}
		}
		
		finish {
			for(i in D) {	
                		async at(D(i)) {
                			var index:Int = i.operator()(0);
                			var pt:Point = Point.make(nodeSet(i).pair.v);
                			checkflag(i) = false;			
					if(nodeSet(i).fragmentRoot == index && nodeSet(i).pair.u == index && getFragmentRoot(pt) != index)
						checkflag(i) = true; 
				}
			}
		}
		
		finish {
			for(i in D) {	
                		async at(D(i)) {
                			if(checkflag(i))
                				fragmentAdd(i);
                		}
                	}
                }		
		
		finish {
			for(i in D) {	
                		async at(D(i)) {
                			val index:Int = i(0);
					if(nodeSet(i).fragmentRoot == index)
						nodeSet(i).trans = true;
				}
			}
		}
		
		transmit();				
		
		finish {
			for(i in D) {	
                		async at(D(i)) {			
					nodeSet(i).startSignal = -1;    
					nodeSet(i).tempStartSignal = -1;
	                        	nodeSet(i).joinSignal.clear();
	                        	nodeSet(i).changeChild = -1;
	                        	nodeSet(i).trans = false;
	                        	nodeSet(i).tempTrans = false;
	                        	nodeSet(i).bluePairs.clear();
	                        	nodeSet(i).tempChangeSignal = -1;
	                        }
			}	                        	
                }
        }

	/**
	 * Signals the children of a node to Start. 
	 *
	 * @return 	true if signal has to be transmitted further deep in the tree.
	 */
        def setChildSignal():boolean {
        	val h0 = here;
        	finish {
			for(i in D) {
                		async at(D(i)) {
        				if(nodeSet(i).startSignal == START && nodeSet(i).children.size() > 0) {
        					nodeSet(i).startSignal = -1;
        					nodeSet(i).tempStartSignal = -1;
        					for(var c:Int=0; c<nodeSet(i).children.size(); c++) {
        						var pt:Point = Point.make(nodeSet(i).children.get(c));
        						setSignal(pt, START);
        					}	
        					setCheck(i) = true;	
        				}			
        			}
        		}
        	}
        	
        	finish {
			for(i in D) {
                		async at(D(i)) {
                			val h1 = here;
                			
                			/** If remote data access then increment counter for communication. */
                			if(h0 != h1)
                				cmess(i) = cmess(i) + 3;
                				
        				nodeSet(i).startSignal = nodeSet(i).tempStartSignal;
        			}
        		}
        	}
        	
        	var retVal:boolean = false;
        	for(i in D) {
        		var atVal:boolean = at(D(i)) setCheck(i);
        		if(atVal)
        			retVal = true;
        	}	
        	return retVal;		
        }
        
        /** 
         * Performs the task of finding blue edge for the fragment.
         * 
         * @param  	froots		Root of a fragment.
         * @param  	weightMatrix	Edge weight array.
         */
        def findNode(val froots:Int, val weightMatrix:Array[Int]) {
        	val h0 = here;
        	var count:Int = 0;
        	var check:boolean = false;
        	do {
        		finish {
				for(i in D) {	
                			async at(D(i)) {
        					if(nodeSet(i).startSignal == START) {
        						var index:Int = i(0);
        						if(nodeSet(i).children.size() > 0) {
								if(nodeSet(i).bluePairs.size() == nodeSet(i).children.size()) {
									nodeSet(i).pair.u = index; nodeSet(i).pair.v = index;
									findMinimum(i, weightMatrix);
									var bdg:blueEdge = new blueEdge();
									for(var j:Int=0; j<nodeSet(i).bluePairs.size(); j++) {
										bdg = nodeSet(i).bluePairs.get(j);
										if(weightMatrix(nodeSet(i).pair.u, nodeSet(i).pair.v) > weightMatrix(bdg.u, bdg.v))
											nodeSet(i).pair.setPair(bdg.u, bdg.v);
									}
									
									nodeSet(i).startSignal = -1;
       									nodeSet(i).tempStartSignal = -1;
									if(index != nodeSet(i).fragmentRoot) {
										var pt:Point = Point.make(nodeSet(i).parent);
										setBlueEdge(pt, nodeSet(i).pair.u, nodeSet(i).pair.v);
        									setSignal(pt, START);
        								}
        								else	
        									cagain(i) = true;
								}        					
        						}
        						else {
								nodeSet(i).pair.u = index; nodeSet(i).pair.v = index;
        							findMinimum(i, weightMatrix);
        							nodeSet(i).startSignal = -1;
       								nodeSet(i).tempStartSignal = -1;
        							if(index != nodeSet(i).fragmentRoot) {
        								var pt:Point = Point.make(nodeSet(i).parent);
        								setBlueEdge(pt, nodeSet(i).pair.u, nodeSet(i).pair.v);
        								setSignal(pt, START);
        							}
        							else
        								cagain(i) = true;
        						}
        					}
        				}
        			}
        		}
        	
        		finish {
				for(i in D) {	
                			async at(D(i)) {
                				val h1 = here;
                				
                				/** If remote data access then increment counter for communication. */
                				if(h0 != h1)
                					cmess(i) = cmess(i) + 3;
                			
        					nodeSet(i).startSignal = nodeSet(i).tempStartSignal;
        					for(var j:Int=0; j<nodeSet(i).tempPairs.size(); j++)
        						nodeSet(i).bluePairs.add(nodeSet(i).tempPairs.get(j));
        					nodeSet(i).tempPairs.clear();
        				}
        			}
        		}
        		
        		count=0;
        		check = false;
        		for(i in D) {
        			var fvalue:boolean = at(D(i)) cagain(i);
        			if(fvalue)
        				count++;
        		}		
        		if(count != froots)
        			check = true;
        	}while(check);	
        }	
        
        /** 
         * Finds the minimum weighted edge having <code>aNode<\code> as
         * one of the vertices.
         *
         * @param  	aNode		Node whose minimum weighted edge has to be determined.
         * @param  	weightMatrix	Edge weight array.
         */
        def findMinimum(val aNode:Point, val weightMatrix:Array[Int]) {
        	val h0 = here;
        	at(D(aNode)){
				val h1 = here;
        			/** 
				 * Checking for remote data access in isolation.
				 * If remote data access then increment counter for communication.
				 */
        			atomic{
                			if(h0 != h1)
                				cmess(aNode) = cmess(aNode) + 1;
        			}
        		
	                var min:Int = Infinity;
			var index:Int = aNode(0);
	                for(var i:Int=0; i<nodes; i++) {
	                	var pt:Point = Point.make(i);
	                        if(adj_graph(index, i) == 1 && getFragmentRoot(pt) != nodeSet(aNode).fragmentRoot)
	                                if(min > weightMatrix(index, i)) {
	                                        min = weightMatrix(index, i);
	                                        nodeSet(aNode).pair.setPair(index, i);
	                                }
			}
		}
        }
        
	/**
         * Adds a blue edge pair for a node.
         *
         * @param  aNode	Node which receives a new blue edge pair information.
         * @param  pu		Source of the pair (or node part of same fragment).
         * @param  pv		Destination of the pair (or node part of other fragment).
         */        
        def setBlueEdge(val aNode:Point, val pu:Int, val pv:Int) {
        	val h0 = here;
        	at(D(aNode)){
			val h1 = here;
	        	atomic{
	        		/** If remote data access then increment counter for communication. */
	        		if(h0 != h1)
	        			cmess(aNode) = cmess(aNode) + 1;
	        		
        			var bdg:blueEdge = new blueEdge();
        			bdg.setPair(pu, pv);
        			nodeSet(aNode).tempPairs.add(bdg);
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
        		/** 
			 * Checking for remote data access in isolation.
			 * If remote data access then increment counter for communication.
			 */
        		atomic{
                		if(h0 != h1)
                			cmess(aNode) = cmess(aNode) + 1;
        		}
        	
        		var index:Int = aNode.operator()(0);		
	        	var flag:Boolean = false;
	        	if(nodeSet(aNode).joinSignal.size() > 0) {
	        		var mgr:Merge = new Merge();
	        		for(var i:Int=0; i<nodeSet(aNode).joinSignal.size(); i++) {
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
	def transmit() {
        	val h0 = here;
        	var check:boolean = false;
        	do {
        		tagain = DistArray.make[boolean](D);
        		finish {
				for(i in D) {
                			async at(D(i)) {
        					if(nodeSet(i).trans) {
        						nodeSet(i).trans = false;
        						nodeSet(i).tempTrans = false;
        						if(nodeSet(i).children.size() > 0)
        							tagain(i) = true;
        							
        						for(var j:Int=0; j<nodeSet(i).children.size(); j++) {
	                	       				var child:Point = Point.make(nodeSet(i).children.get(j));
	                	       				setFragmentRoot(child, nodeSet(i).fragmentRoot);
	                	       				setTransFlag(child);
							}
        					}
        				}
        			}
        		}
        	
       			for(i in D) {
				at(D(i)){
        				val h1 = here;
        				
        				/** If remote data access then increment counter for communication. */
                			if(h0 != h1)
                				cmess(i) = cmess(i) + 3;

					nodeSet(i).trans = nodeSet(i).tempTrans;
				}	
			}	
        		
        		check = false;
        		for(i in D) {
        			var retVal:boolean = at(D(i)) tagain(i);
        			if(retVal) {
        				check = true;
        				break;
        			}
        		}	
        	}while(check);		
        }

	/**
	 * Inverts the path, that is if the root and source of the pair (u)
	 * are different then the parent-child relationships on path from
	 * u to root are reversed.
	 *
	 * @param  froots	source of the pair (u).
	 */
        def invertPath(val froots:Int) {
        	val h0 = here;
        	var count:Int = 0;
        	var check:boolean = false;
        	do {
        		finish {
				for(i in D) {	
                			async at(D(i)) {
        					if(nodeSet(i).changeSignal == CHANGE) {
        						var index:Int = i(0);
        						nodeSet(i).changeSignal = -1;
        						nodeSet(i).tempChangeSignal = -1;
        						
        						if(index == nodeSet(i).changeChild) {
        							nodeSet(i).fragmentRoot = Infinity;
        							if(nodeSet(i).parent != Infinity) {
                	                				nodeSet(i).children.add(nodeSet(i).parent);
                	                				var pt:Point = Point.make(nodeSet(i).parent);
                	                				setSignal(pt, CHANGE);
                	                				childToRemove(index, pt);
                	                				nodeSet(i).parent = Infinity;
                	        				}
                	        				else
                	        					invagain(i) = true;
        						}
        						else {
        							nodeSet(i).fragmentRoot = nodeSet(i).changeChild;
				                                for(var j:Int=nodeSet(i).children.size()-1; j>=0; j--)	{
                	                				var child:Int = nodeSet(i).children.get(j);
									if(child == nodeSet(i).changeChild)
                	                        				nodeSet(i).children.remove(child);
								}
                	                			if(nodeSet(i).parent != Infinity) {
                	                				var pt:Point = Point.make(nodeSet(i).parent);
                	                				setSignal(pt, CHANGE);
                	                        			nodeSet(i).children.add(nodeSet(i).parent);
									childToRemove(index, pt);
									nodeSet(i).parent = nodeSet(i).changeChild;
								}
                	                			else {
                	                				nodeSet(i).parent = nodeSet(i).changeChild;
                	                				invagain(i) = true;
                	                			}	
								nodeSet(i).changeChild = -1;
        						}
        					}
        				}
        			}
        		}
        		
        		finish {
				for(i in D) {
                			async at(D(i)) {
	        				val h1 = here;
	        				
	        				/** If remote data access then increment counter for communication. */
	                			if(h0 != h1)
	                				cmess(i) = cmess(i) + 3;

        					nodeSet(i).changeSignal = nodeSet(i).tempChangeSignal;
        				}
        			}
        		}			
        	
        		count=0;
        		check = false;
        		for(i in D) {
        			var retVal:boolean = at(D(i)) invagain(i);
        			if(retVal)
        				count++;
        		}		
        		if(count != froots)
        			check = true;
        	}while(check);
        }        	
        
        /*
         * Determines if all nodes are in same fregment or not.
         *
         * @return	True if all nodes in same fragment.
         */
        def fragmentCheck():Boolean {
        	val pt:Point = Point.make(0);
        	var frag:Int = at(D(pt)) nodeSet(pt).fragmentRoot;
        	var boolRet:Boolean = false;
        	val ncount:Int = nodes;
        	val h0 = here;
        	
        	at(D(pt)){
        		val h1 = here;
        		
        		/** If remote data access then increment counter for communication. */
        		if(h0 != h1)
        			cmess(pt) = cmess(pt) + ncount;
        	}
		
		for(var i:Int=1; i<nodes; i++) {
			val cpt:Point = Point.make(i);
	        	var nfrag:Int = at(D(cpt)) nodeSet(cpt).fragmentRoot;
	        	if(frag != nfrag) {
	        		boolRet = true;
	        		break;
	        	}	
		}
		
		return boolRet;		
        }
       
       	/**
	 * Specifies the child to remove from a node. 
	 * @param  child	node which has to be unmarked as a child.
	 * @param  parent	node whose child has to be removed.
	 */
        def childToRemove(val child:Int, val parent:Point) {
        	val h0 = here;
        	at(D(parent)){
        		val h1 = here;
        		
        		/** If remote data access then increment counter for communication. */
        		if(h0 != h1)
        			cmess(parent) = cmess(parent) + 1;
        	
	        	nodeSet(parent).changeChild = child;
	        }	
        }

	/**
	 * Sets the signal of a node as <code>START<\code> or <code>CHANGE<\code>.
	 * @param  aNode	node for which signal has to be set
	 * @param  uSignal	Value of signal.
	 */
        def setSignal(val aNode:Point, val uSignal:Int) {
        	val h0 = here;
        	at(D(aNode)){
        		val h1 = here;
        		
        		/** If remote data access then increment counter for communication. */
        		if(h0 != h1)
        			cmess(aNode) = cmess(aNode) + 1;	
        		
			if(uSignal == CHANGE)
				nodeSet(aNode).tempChangeSignal = CHANGE;
			else {
				atomic {
					nodeSet(aNode).tempStartSignal = START;
				}	
			}		                     
		}	   
        }
        
        /**
	 * Adds a child to the set of children of a node.
	 * @param  aNode 	node which will get a new child.
	 * @param  child	node which is assigned a new parent node.
	 */
        def addChild(val aNode:Point, val child:Int) {
        	val h0 = here;
        	
        	at(D(aNode)){
			val h1 = here;
	        	atomic{
	                	nodeSet(aNode).children.add(child);
	                	
	                	/** If remote data access then increment counter for communication. */
	        		if(h0 != h1)
        				cmess(aNode) = cmess(aNode) + 1;
                	}
		}                		
        }

	/**
	 * Sets the parent of <code>aNode<\code>.
	 * @param  aNode 	node which will be assigned to a new parent node.
	 * @param  Parent	node which is assigned a new child node.
	 */
        def setParent(val aNode:Point, val Parent:Int) {
        	val h0 = here;
        	at(D(aNode)){
        		val h1 = here;
        		
        		/** If remote data access then increment counter for communication. */
        		if(h0 != h1)
        			cmess(aNode) = cmess(aNode) + 1;
        			
	                nodeSet(aNode).parent = Parent;
		}	                
        }

	/**
	 * Sets the fragment root to <code>root<\code>.
	 * @param  aNode 	node for which fragment root has to be set.
	 * @param  root		new fragment root.
	 */
        def setFragmentRoot(val aNode:Point, val root:Int) {
        	val h0 = here;
        	at(D(aNode)){
        		val h1 = here;
        		
        		/** If remote data access then increment counter for communication. */
        		if(h0 != h1)
        			cmess(aNode) = cmess(aNode) + 1;
        	
	                nodeSet(aNode).fragmentRoot = root;
		}	                
        }

	/**
	 * Provides the fragment root for <code>aNode<\code>.
	 * @param   aNode 	node whose fragment root is required.
	 * @return  		fragment root of <code>aNode<\code>.
	 */
        def getFragmentRoot(val aNode:Point):Int {
        	val h0 = here;
        	at(D(aNode)){
        		val h1 = here;
        		
        		/** If remote data access then increment counter for communication. */
        		if(h0 != h1)
        			cmess(aNode) = cmess(aNode) + 1;
        	}
        	
        	var retVal:Int = at(D(aNode)) nodeSet(aNode).fragmentRoot;
                return retVal;
        }
        
        /**
	 * Sets the transmit flag.
	 * @param  child	node whose flag has to be set.
	 */
        def setTransFlag(val child:Point) {
        	val h0 = here;
        	at(D(child)){
        		val h1 = here;
        		
        		/** If remote data access then increment counter for communication. */
        		if(h0 != h1)
        			cmess(child) = cmess(child) + 1;
        			
	        	nodeSet(child).tempTrans = true;
	        }	
        }

	/**
	 * Transmits the <code>JOIN<\code> signal.
	 * @param  sender 	sender of the signal.
	 * @param  receiver	receiver of the signal.
	 */
        def sendJoinSignal(val sender:Int, val reciever:Point) {
        	val h0 = here;
        	at(D(reciever)){
			val h1 = here;
	        	atomic
        		{
        			var mgr:Merge = new Merge();
        			mgr.setData(JOIN, sender);
        	        	nodeSet(reciever).joinSignal.add(mgr);
        	        	
	                	/** If remote data access then increment counter for communication. */
	        		if(h0 != h1)
        				cmess(reciever) = cmess(reciever) + 1;
			}
		}			                	
        }
}

/** Specifies the structure for sending a merge request. */
class Merge
{
	var join:Int;
	var v:Int;
	def setData(var join:Int, var v:Int) {
		this.join = join;
		this.v = v;
	}
}

/** Specifies the structure for a blue edge pair. */
class blueEdge
{
        var u:Int; var v:Int;
        def setPair(var u:Int, var v:Int) {
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
        var fragmentRoot:Int;
        
        /** States the parent of a node. */
        var parent:Int;
        
        var startSignal:Int, tempStartSignal:Int;
        var changeSignal:Int, tempChangeSignal:Int, changeChild:Int;
        var trans:boolean, tempTrans:boolean;
        
        /** Stores a blue edge pair. */
        var pair:blueEdge = new blueEdge();
        
        /** Stores the received join signals. */
        var joinSignal:ArrayList[Merge] = new ArrayList[Merge]();
        
        /** Stores received blue edge pairs. */
	var bluePairs:ArrayList[blueEdge] = new ArrayList[blueEdge]();
	var tempPairs:ArrayList[blueEdge] = new ArrayList[blueEdge]();
 	
 	/** Identifies the children of a node. */
        var children:ArrayList[Int] = new ArrayList[Int]();
}
