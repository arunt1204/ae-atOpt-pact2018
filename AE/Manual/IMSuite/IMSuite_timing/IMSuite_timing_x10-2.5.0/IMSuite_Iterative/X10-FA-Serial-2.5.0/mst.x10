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
        
        var adj_graph:Array[Long], nodes:Long; //weightMatrix:Array[Long], ;
        val Infinity  = Long.MAX_VALUE;
        
        /** Abstract node representation as a distributed array. */
        var nodeSet:DistArray[node];

	/** Parameters to enable execution with load */
	var loadValue:long=0; 

	/** Load sum represented as a distributed array. */
	var nval:DistArray[long];
        
        /** Region and Distribution specification. */
	var R: Region;	var D: Dist;
	
	/** Other Distributed Array used. */
	var cagain:DistArray[boolean];
        var tagain:DistArray[boolean];
        var invagain:DistArray[boolean];
        var setCheck:DistArray[boolean];
        var checkflag:DistArray[boolean];	

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
				ms.loadValue = Long.parse(args(i+1));
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
		ms.R = Region.make(0, (ms.nodes-1));
		
		/** Creation of a Block Distribution. */
    		ms.D = Dist.makeBlock(ms.R);
    		//ms.D = Dist.makeUnique();
    		//ms.R = ms.D.region;
    		
    		/** Distribution of nodes. */
	  	ms.nodeSet = DistArray.make[node](ms.D);
               
		ms.nval = DistArray.make[long](ms.D);
 
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
		
		var count:Long = 0;
		var cflag:Boolean = false;
                var startTime:long = System.nanoTime();
                outer: while(true) {
                	ms.checkflag = DistArray.make[Boolean](ms.D);
                        ms.mstCreate(count, weightMatrix);
                        count++;
			cflag = ms.fragmentCheck();
			if(cflag)
				continue outer;
                        break;
                }
                var finishTime:long = System.nanoTime();
                var estimatedTime:long = finishTime - startTime;
                Console.OUT.println("Start Time: " + startTime + "\t Finish Time: " + finishTime + "\t Estimated Time: " + estimatedTime);

                ms.printOutput(outputFile);
                if(flag)
			ms.outputVerifier(weightMatrix); 

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
					var index:Long = i.operator()(0);
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
	
	/** 
	 * Generates a minimum spanning tree.
	 *
	 * @param	phase		Current phase.
	 * @param	weightMatrix	Edge Weight array.
	 */
	def mstCreate(val phase:Long, val weightMatrix:Array[Long]) {
                var counter:Long=0;
                var again:boolean = false;
                
                //finish 
                	for(i in D) {
	              		//async 
				at(D(i)) {
                			var index:Long = i.operator()(0);
                			if(index == nodeSet(i).fragmentRoot) {
						nodeSet(i).startSignal = START;
                                		checkflag(i) = true;
					}	

					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
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

                //finish 
                	for(i in D) {
	              		//async 
				at(D(i)) {
                			if(checkflag(i)) {
			                	var pt:Point = Point.make(nodeSet(i).pair.u);
			                	setSignal(pt, CHANGE);
			                	childToRemove(nodeSet(i).pair.u, pt);
			                }

					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
					}	
				}       
			}
		
		for(i in D) {
			at(D(i))	nodeSet(i).changeSignal = nodeSet(i).tempChangeSignal;
		}	
		invagain = DistArray.make[boolean](D);
		invertPath(counter);			                			

		//finish 
			for(i in D) {
                		//async 
				at(D(i)) {
                			var index:Long = i.operator()(0);
		                	if(nodeSet(i).fragmentRoot == Infinity)
			                	nodeSet(i).fragmentRoot = index;

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
                			var index:Long = i.operator()(0);
					if(index == nodeSet(i).fragmentRoot) {
						val pt:Point = Point.make(nodeSet(i).pair.v);
						sendJoinSignal(index, pt);
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
                			var index:Long = i.operator()(0);
                			var pt:Point = Point.make(nodeSet(i).pair.v);
                			checkflag(i) = false;			
					if(nodeSet(i).fragmentRoot == index && nodeSet(i).pair.u == index && getFragmentRoot(pt) != index)
						checkflag(i) = true; 

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
                			if(checkflag(i))
                				fragmentAdd(i);

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
                			val index:Long = i(0);
					if(nodeSet(i).fragmentRoot == index)
						nodeSet(i).trans = true;

					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
					}
				}
			}
		
		transmit();

		//finish 
			for(i in D) {	
                		//async 
				at(D(i)) {			
					nodeSet(i).startSignal = -1;    
					nodeSet(i).tempStartSignal = -1;
	                        	nodeSet(i).joinSignal.clear();
	                        	nodeSet(i).changeChild = -1;
	                        	nodeSet(i).trans = false;
	                        	nodeSet(i).tempTrans = false;
	                        	nodeSet(i).bluePairs.clear();
	                        	nodeSet(i).tempChangeSignal = -1;

					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
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
        	//finish 
			for(i in D) {	
                		//async 
				at(D(i)) {
        				if(nodeSet(i).startSignal == START && nodeSet(i).children.size() > 0) {
        					nodeSet(i).startSignal = -1;
        					nodeSet(i).tempStartSignal = -1;
        					for(var c:Long=0; c<nodeSet(i).children.size(); c++) {
        						var pt:Point = Point.make(nodeSet(i).children.get(c));
        						setSignal(pt, START);
        					}	
        					setCheck(i) = true;	
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
        				nodeSet(i).startSignal = nodeSet(i).tempStartSignal;

					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
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
        def findNode(val froots:Long, val weightMatrix:Array[Long]) {
        	var count:Long = 0;
        	var check:boolean = false;
        	do {
        		//finish 
				for(i in D) {	
                			//async 
					at(D(i)) {
        					if(nodeSet(i).startSignal == START) {
        						var index:Long = i(0);
        						if(nodeSet(i).children.size() > 0) {
								if(nodeSet(i).bluePairs.size() == nodeSet(i).children.size()) {
									nodeSet(i).pair.u = index; nodeSet(i).pair.v = index;
									findMinimum(i, weightMatrix);
									var bdg:blueEdge = new blueEdge();
									for(var j:Long=0; j<nodeSet(i).bluePairs.size(); j++) {
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
        					nodeSet(i).startSignal = nodeSet(i).tempStartSignal;
        					for(var j:Long=0; j<nodeSet(i).tempPairs.size(); j++)
        						nodeSet(i).bluePairs.add(nodeSet(i).tempPairs.get(j));
        					nodeSet(i).tempPairs.clear();

						if(!loadValue.equals(0)) {
							val ipt:Long = i(0);
							nval(i) = loadweight(nval(i)+ipt);
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
        def findMinimum(val aNode:Point, val weightMatrix:Array[Long]) {
        	at(D(aNode)){
	                var min:Long = Infinity;
			var index:Long = aNode(0);
	                for(var i:Long=0; i<nodes; i++) {
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
        def setBlueEdge(val aNode:Point, val pu:Long, val pv:Long) {
        	at(D(aNode)){
        		var bdg:blueEdge = new blueEdge();
        		bdg.setPair(pu, pv);
        		nodeSet(aNode).tempPairs.add(bdg);
        	}	
        }

	/**
	 * Performs the task of merging two fragments.
	 * 
	 * @param  aNode	Node whose fragment will merge with another fragment.
	 */
        def fragmentAdd(val aNode:Point) {
        	at(D(aNode)){
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
	def transmit() {
        	var check:boolean = false;
        	do {
        		tagain = DistArray.make[boolean](D);
        		//finish 
				for(i in D) {	
                			//async 
					at(D(i)) {
        					if(nodeSet(i).trans) {
        						nodeSet(i).trans = false;
        						nodeSet(i).tempTrans = false;
        						if(nodeSet(i).children.size() > 0)
        							tagain(i) = true;
        						for(var j:Long=0; j<nodeSet(i).children.size(); j++) {
	                	       				var child:Point = Point.make(nodeSet(i).children.get(j));
	                	       				setFragmentRoot(child, nodeSet(i).fragmentRoot);
	                	       				setTransFlag(child);
							}
        					}

						if(!loadValue.equals(0)) {
							val ipt:Long = i(0);
							nval(i) = loadweight(nval(i)+ipt);
						}
        				}
        			}
       			for(i in D) {
				at(D(i)) nodeSet(i).trans = nodeSet(i).tempTrans;
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
        def invertPath(val froots:Long) {
        	var count:Long = 0;
        	var check:boolean = false;
        	do {
        		//finish 
				for(i in D) {	
                			//async 
					at(D(i)) {
        					if(nodeSet(i).changeSignal == CHANGE) {
        						var index:Long = i(0);
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
				                                for(var j:Long=nodeSet(i).children.size()-1; j>=0; j--)	{
                	                				var child:Long = nodeSet(i).children.get(j);
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
        					nodeSet(i).changeSignal = nodeSet(i).tempChangeSignal;

						if(!loadValue.equals(0)) {
							val ipt:Long = i(0);
							nval(i) = loadweight(nval(i)+ipt);
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
        	var frag:Long = at(D(pt)) nodeSet(pt).fragmentRoot;
        	var boolRet:Boolean = false;
		
		for(var i:Long=1; i<nodes; i++) {
			val cpt:Point = Point.make(i);
	        	var nfrag:Long = at(D(cpt)) nodeSet(cpt).fragmentRoot;
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
        def childToRemove(val child:Long, val parent:Point) {
        	at(D(parent)){
	        	nodeSet(parent).changeChild = child;
	        }	
        }

	/**
	 * Sets the signal of a node as <code>START<\code> or <code>CHANGE<\code>.
	 * @param  aNode	node for which signal has to be set
	 * @param  uSignal	Value of signal.
	 */
        def setSignal(val aNode:Point, val uSignal:Long) {
        	at(D(aNode)){
			if(uSignal == CHANGE)
				nodeSet(aNode).tempChangeSignal = CHANGE;
			else {
				nodeSet(aNode).tempStartSignal = START; 
			}		                     
		}	   
        }
        
        /**
	 * Adds a child to the set of children of a node.
	 * @param  aNode 	node which will get a new child.
	 * @param  child	node which is assigned a new parent node.
	 */
        def addChild(val aNode:Point, val child:Long) {
        	at(D(aNode)){
	        	nodeSet(aNode).children.add(child); 
		}                		
        }

	/**
	 * Sets the parent of <code>aNode<\code>.
	 * @param  aNode 	node which will be assigned to a new parent node.
	 * @param  Parent	node which is assigned a new child node.
	 */
        def setParent(val aNode:Point, val Parent:Long) {
        	at(D(aNode)){
	                nodeSet(aNode).parent = Parent;
		}	                
        }

	/**
	 * Sets the fragment root to <code>root<\code>.
	 * @param  aNode 	node for which fragment root has to be set.
	 * @param  root		new fragment root.
	 */	
        def setFragmentRoot(val aNode:Point, val root:Long) {
        	at(D(aNode)){
	                nodeSet(aNode).fragmentRoot = root;
		}	                
        }

	/**
	 * Provides the fragment root for <code>aNode<\code>.
	 * @param   aNode 	node whose fragment root is required.
	 * @return  		fragment root of <code>aNode<\code>.
	 */
        def getFragmentRoot(val aNode:Point):Long {
        	var retVal:Long = at(D(aNode)) nodeSet(aNode).fragmentRoot;
                return retVal;
        }
        
        /**
	 * Sets the transmit flag.
	 * @param  child	node whose flag has to be set.
	 */
        def setTransFlag(val child:Point) {
        	at(D(child)){
	        	nodeSet(child).tempTrans = true;
	        }	
        }

	/**
	 * Transmits the <code>JOIN<\code> signal.
	 * @param  sender 	sender of the signal.
	 * @param  receiver	receiver of the signal.
	 */
        def sendJoinSignal(val sender:Long, val reciever:Point) {
        	at(D(reciever)){
        		var mgr:Merge = new Merge();
        		mgr.setData(JOIN, sender);
        		nodeSet(reciever).joinSignal.add(mgr);
		}		         	
        }

	/** 
	 * Writes the output to the user specified file.
	 * 
	 * @param  fileName	Name of the file in which output has to be stored.
	 * @throws 		input output exception if a failure in write occurs.
	 */
        def printOutput(var fileName:String) {
        	try {
 			var fl:File = new File(fileName);
			fw:FileWriter = new FileWriter(fl);
			var str:String;
	                var flag:Boolean;
			val pt:Point = Point.make(0);
			var frag:Long = at(D(pt)) nodeSet(pt).fragmentRoot;
			str="Root: " + frag + "\n";
			for(var j:Long=0; j<str.length(); j++) {
				var jInt:Int = j as Int;
				var ch:Char = str.charAt(jInt);
				fw.writeChar(ch);
			}
			str="All (U,V) Pairs where U is parent and V is child \n";
			for(var j:Long=0; j<str.length(); j++) {
				var jInt:Int = j as Int;
				var ch:Char = str.charAt(jInt);
				fw.writeChar(ch);
			}	
	                for(var i:Long=0; i<nodes; i++) {
                        	flag = false;
                        	val ipt:Point = Point.make(i);
				var childSet:ArrayList[Long] = new ArrayList[Long]();
				childSet = at(D(ipt)) nodeSet(ipt).children;
                        	for(var j:Long=0; j<childSet.size(); j++) {
                        		str="(" + i + "," + childSet.get(j) + ") ";
					for(var k:Long=0; k<str.length(); k++) {
						var kInt:Int = k as Int;
						var ch:Char = str.charAt(kInt);
						fw.writeChar(ch);
					}
					flag = true;
                        	}

                        	if(flag)
                                	fw.writeChar('\n');             
			}
			fw.close();
		} catch(ex: x10.lang.Exception){}
	}	
        
        /** Validates the output resulting from the execution of the algorithm. */  
        def outputVerifier(val weightMatrix:Array[Long]) {
        	val pt:Point = Point.make(0);
        	var root:Long = at(D(pt)) nodeSet(pt).fragmentRoot;
        	var i:Long, j:Long;
        	var treeNodes:ArrayList[Long] = new ArrayList[Long]();
        	var treePart:Rail[Boolean] = new Rail[Boolean](nodes);
        	var sumWeight:Long=0; var checkSum:Long=0;
        	treeNodes.add(root);
        	treePart(root) = true;
        	
        	while(treeNodes.size() < nodes) {
        		var minWeight:Long = Long.MAX_VALUE;
        		var minNode:Long = -1;
        		for(i=0; i<treeNodes.size(); i++) {
        			var aNode:Long = treeNodes.get(i);
        			for(j=0; j<nodes; j++) {
        				if(adj_graph(aNode,j) == 1 && !treePart(j) && weightMatrix(aNode,j) < minWeight) {
        					minWeight = weightMatrix(aNode,j);
        					minNode = j;
        				}
        			}
        		}
        		treePart(minNode) = true;
        		sumWeight+=minWeight;
        		treeNodes.add(minNode);
        	}
        	
        	for(i=0; i<nodes; i++) {
        		val ipt:Point = Point.make(i);
        		var childSet:ArrayList[Long] = new ArrayList[Long]();
			childSet = at(D(ipt)) nodeSet(ipt).children;
        		for(j=0; j<childSet.size(); j++) {
        			var child:Long = childSet.get(j);
        			checkSum = checkSum + weightMatrix(i,child);
        		}
        	}
        	
        	if(checkSum == sumWeight) {
        		var nodeCheck:Rail[Long] = new Rail[Long](nodes);
			var flag:Boolean = false;
			for(i=0; i<nodes; i++) {
				val ipt:Point = Point.make(i);
        			var childSet:ArrayList[Long] = new ArrayList[Long]();
				childSet = at(D(ipt)) nodeSet(ipt).children;
				if(childSet.size() > 0) {
					for(j=0; j<childSet.size(); j++)
						nodeCheck(childSet.get(j)) = nodeCheck(childSet.get(j))+1;
				}
			}	
			nodeCheck(root)=nodeCheck(root)+1;
			for(i=0; i<nodes; i++)
				if(nodeCheck(i) != 1)
					flag = true;
 			if(!flag)       	
        			Console.OUT.println("Output verified");
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
        
        var startSignal:Long, tempStartSignal:Long;
        var changeSignal:Long, tempChangeSignal:Long, changeChild:Long;
        var trans:boolean, tempTrans:boolean;
        
        /** Stores a blue edge pair. */
        var pair:blueEdge = new blueEdge();
        
        /** Stores the received join signals. */
        var joinSignal:ArrayList[Merge] = new ArrayList[Merge]();
        
        /** Stores received blue edge pairs. */
	var bluePairs:ArrayList[blueEdge] = new ArrayList[blueEdge]();
	var tempPairs:ArrayList[blueEdge] = new ArrayList[blueEdge]();
 	
 	/** Identifies the children of a node. */
        var children:ArrayList[Long] = new ArrayList[Long]();
}
