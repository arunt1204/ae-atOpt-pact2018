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
        
        var adj_graph:Array[Int], nodes:Int;
        val Infinity  = Int.MAX_VALUE;
        
        /** Abstract node representation as a distributed array. */
        var nodeSet:DistArray[node];

	/** Parameters to enable execution with load */
	var loadValue:long=0; 

	/** Load sum represented as a distributed array. */
	var nval:DistArray[long];
        
	/** Region and Distribution specification. */        
	var R: Region;	var D: Dist;

	/** Other Distributed Array used. */        
        var flag:DistArray[Boolean];

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
               
		ms.nval = DistArray.make[Long](ms.D);
 
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
		
		var cflag:Boolean = false;
                var startTime:long = System.nanoTime();
                outer: while(true) {
                	ms.flag = DistArray.make[Boolean](ms.D);
                        ms.mstCreate(weightMatrix);
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
        	finish {
			for(i in D) {
				async at(D(i)) {
					var index:Int = i.operator()(0);
					nodeSet(i) = new node();
                	        	nodeSet(i).parent = Infinity;
                	        	nodeSet(i).fragmentRoot = index;
                	        	nodeSet(i).startSignal = START;
                	        	nodeSet(i).changeChild=-1;
                	        } } }
        }

	/** 
	 * Generates a minimum spanning tree.
	 *
	 * @param	weightMatrix	Edge Weight array.
	 */
        def mstCreate(val weightMatrix:Array[Int]) {
                clocked finish {
                	for(i in D) {
	              		clocked async at(D(i)) {
                			var index:Int = i.operator()(0);
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

					if(!loadValue.equals(0)) {
						nval(i) = loadweight(nval(i)+index);
					} } } }
        }

	/** 
         * Performs the task of finding blue edge for the fragment.
         * 
         * @param  	froots		Root of a fragment.
         * @param	weightMatrix	Edge Weight array.
         */
        def findNode(val aNode:Point, val weightMatrix:Array[Int]) {
        	at(D(aNode)){
	                var flag:Boolean=false;
        	        if(nodeSet(aNode).startSignal == START) {
        	        	var index:Int = aNode.operator()(0);
				nodeSet(aNode).pair.u = index;          nodeSet(aNode).pair.v = index;
        	                if(nodeSet(aNode).children.size() == 0) {
                                	findMinimum(aNode, weightMatrix);
                                	flag=true;
                        	}

                        	if(!flag) {
					finish {
						for(c in 0..(nodeSet(aNode).children.size()-1)) {
                                			async {
                                				val child:Int = nodeSet(aNode).children.get(c);
                                				val cpt: Point = Point.make(child);
                                        			setSignal(cpt, START);
                                        			findNode(cpt, weightMatrix);
                                        			setSignal(cpt, -1);
								var cpair:blueEdge = getPair(cpt);
								
								atomic {
                                        				if(weightMatrix(nodeSet(aNode).pair.u, nodeSet(aNode).pair.v) > weightMatrix(cpair.u, cpair.v))
                                        					nodeSet(aNode).pair.setPair(cpair.u, cpair.v);
                                        			}

								if(!loadValue.equals(0)) {
									at(D(cpt)) {
										nval(cpt) = loadweight(nval(cpt)+child);
									}
								} } } } }
                               	for(var i:Int=0; i<nodes; i++) {
                               		var ipt:Point = Point.make(i);
                              		if(getFragmentRoot(ipt) != nodeSet(aNode).fragmentRoot && adj_graph(index,i) == 1)
						if(weightMatrix(nodeSet(aNode).pair.u, nodeSet(aNode).pair.v) > weightMatrix(index, i))
							nodeSet(aNode).pair.setPair(index, i);
				} } }
	}


	/** 
         * Finds the minimum weighted edge having <code>aNode<\code> as
         * one of the vertices.
         *
         * @param  	aNode		Node whose minimum weighted edge has to be determined.
         * @param	weightMatrix	Edge Weight array.
         */
        def findMinimum(val aNode:Point, val weightMatrix:Array[Int]) {
        	at(D(aNode)){	
	                var min:Int = Infinity;
			var index:Int = aNode.operator()(0);
			
	                for(var i:Int=0; i<nodes; i++) {
	                	var ipt:Point = Point.make(i);
	                        if(adj_graph(index, i) == 1 && getFragmentRoot(ipt) != nodeSet(aNode).fragmentRoot)
	                                if(min > weightMatrix(index, i)) {
	                                        min = weightMatrix(index, i);
	                                        nodeSet(aNode).pair.setPair(index, i);
	                                }
			} }	                                
        }

	/**
	 * Performs the task of merging two fragments.
	 * 
	 * @param  aNode	Node whose fragment will merge with another fragment.
	 */
        def fragmentAdd(val aNode:Point) {
        	at(D(aNode)){
        		var index:Int = aNode.operator()(0);		
	        	var flag:Boolean = false;
	        	if(nodeSet(aNode).joinSignal.size() > 0) {
	        		var mgr:Merge = new Merge();
	        		for(var i:Int=0; i<nodeSet(aNode).joinSignal.size(); i++) {
	        			mgr = nodeSet(aNode).joinSignal.get(i);
	        			if(mgr.v == nodeSet(aNode).pair.v) {
	        				flag = true;
	        				break;
	        			} }
	        		if(flag) {
					if(index < nodeSet(aNode).pair.v) {
	        				nodeSet(aNode).fragmentRoot = index;
	        				addChild(aNode, nodeSet(aNode).pair.v);
	        				var cpt:Point = Point.make(nodeSet(aNode).pair.v);	
						setParent(cpt, index);
						setFragmentRoot(cpt, index);
	        			} } }
	        	if(!flag) {
	        		var pt:Point = Point.make(nodeSet(aNode).pair.v);
				addChild(pt, index);
				nodeSet(aNode).parent = nodeSet(aNode).pair.v;
				nodeSet(aNode).fragmentRoot = Infinity;        	
	        	} }
	}

	/** Transmits the root of a fragment to all the other nodes of the fragment. */
	def transmit(val aNode:Point, val uNode:Int) {
        	at(D(aNode)){
        	        finish {
        	        	for(i in 0..(nodeSet(aNode).children.size()-1)) {
        	        		async {
        	        			val cpt:Point = Point.make(nodeSet(aNode).children.get(i));
        	        			setFragmentRoot(cpt, uNode);
        	                		transmit(cpt, uNode);	

						if(!loadValue.equals(0)) {
							at(D(cpt)) {
								var child:Int = cpt(0);
								nval(cpt) = loadweight(nval(cpt)+child);
							} } } } }
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
        def invertPath(val aNode:Point, val uNode:Int) {
        	at(D(aNode)){
        		var index:Int = aNode.operator()(0);
	                if(index == uNode) {
        	                nodeSet(aNode).fragmentRoot = Infinity;
        	                if(nodeSet(aNode).parent != Infinity) {
        	                        nodeSet(aNode).children.add(nodeSet(aNode).parent);
        	                        var pt:Point = Point.make(nodeSet(aNode).parent);
        	                        setSignal(pt, CHANGE);
                                
        	                        childToRemove(index, pt);
        	                        nodeSet(aNode).parent = Infinity;
        	                } }
        	        else {
        	        	finish {
        	        		for(i in 0..(nodeSet(aNode).children.size()-1)) {
        	                		async {
			                        	val cpt:Point = Point.make(nodeSet(aNode).children.get(i));
        			                        invertPath(cpt, uNode);

							if(!loadValue.equals(0)) {
								at(D(cpt)) {
									var child:Int = cpt(0);
									nval(cpt) = loadweight(nval(cpt)+child);
								} } } } }

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
					} } } }
	}        	
        
        def fragmentCheck():Boolean
        {
        	val pt:Point = Point.make(0);
        	var frag:Int = at(D(pt)) nodeSet(pt).fragmentRoot;
        	var boolRet:Boolean = false;
		
		for(var i:Int=1; i<nodes; i++)
		{
			val cpt:Point = Point.make(i);
	        	var nfrag:Int = at(D(cpt)) nodeSet(cpt).fragmentRoot;
	        	if(frag != nfrag)
	        	{
	        		boolRet = true;
	        		break;
	        	} }
		return boolRet;		
        }
       
       	/**
	 * Specifies the child to remove from a node. 
	 * @param  child	node which has to be unmarked as a child.
	 * @param  parent	node whose child has to be removed.
	 */
        def childToRemove(val child:Int, val parent:Point) {
        	at(D(parent)){	nodeSet(parent).changeChild = child; }
        }

	/**
	 * Sets the signal of a node as <code>START<\code> or <code>CHANGE<\code>.
	 * @param  aNode	node for which signal has to be set
	 * @param  uSignal	Value of signal.
	 */
        def setSignal(val aNode:Point, val uSignal:Int) {
        	at(D(aNode)){
			if(uSignal == CHANGE)
				nodeSet(aNode).changeSignal = CHANGE;
			else if(uSignal == START)
             	           	nodeSet(aNode).startSignal = START;
			else
				nodeSet(aNode).startSignal = -1;		                     
		}	   
        }
        
        /**
	 * Adds a child to the set of children of a node.
	 * @param  aNode 	node which will get a new child.
	 * @param  child	node which is assigned a new parent node.
	 */
        def addChild(val aNode:Point, val child:Int) {
        	at(D(aNode)){
	        	atomic{ nodeSet(aNode).children.add(child); }
		}                		
        }

	/**
	 * Provides the destination node (v) of the blue edge pair.
	 * 
	 * @param   aNode	node whose blue edge pair information needed
	 * @return		destination node (v) of blue edge pair
	 */
        def getPairV(val aNode:Point):Int {
        	var retVal:Int = at(D(aNode)) nodeSet(aNode).pair.v;
                return retVal;
        }
        
        /**
	 * Provides the blue edge pair.
	 * 
	 * @param   aNode	node whose blue edge pair needed
	 * @return		blue edge pair
	 */
        def getPair(val aNode:Point):blueEdge {
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
        def setParent(val aNode:Point, val Parent:Int) {
        	at(D(aNode)){	nodeSet(aNode).parent = Parent; }
        }

	/**
	 * Sets the fragment root of <code>aNode<\code>.
	 * 
	 * @param  aNode	node whose fragment root needs to be updated
	 * @param  root		node which will act as fragment rooot
	 */
        def setFragmentRoot(val aNode:Point, val root:Int) {
        	at(D(aNode)){	nodeSet(aNode).fragmentRoot = root; }
        }

	/**
	 * Provides the fragment root of <code>aNode<\code>.
	 * 
	 * @param   aNode	node whose fragment root needed
	 * @return		fragment root
	 */
        def getFragmentRoot(val aNode:Point):Int {
        	var retVal:Int = at(D(aNode)) nodeSet(aNode).fragmentRoot;
                return retVal;
        }

	/**
	 * Sends the join signal to a specified node.
	 * 
	 * @param  sender	node which sends the <code>JOIN<\code> signal
	 * @param  receiver	node which will receive the signal.will act as fragment rooot
	 */
        def sendJoinSignal(val sender:Int, val receiver:Point) {
        	at(D(receiver)){
	        	atomic {
        			var mgr:Merge = new Merge();
        			mgr.setData(JOIN, sender);
        	        	nodeSet(receiver).joinSignal.add(mgr);
			} }
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
			var frag:Int = at(D(pt)) nodeSet(pt).fragmentRoot;
			str="Root: " + frag + "\n";
			for(var j:Int=0; j<str.length(); j++) {
				var ch:Char = str.charAt(j);
				fw.writeChar(ch);
			}
			str="All (U,V) Pairs where U is parent and V is child \n";
			for(var j:Int=0; j<str.length(); j++) {
				var ch:Char = str.charAt(j);
				fw.writeChar(ch);
			}	
	                for(var i:Int=0; i<nodes; i++) {
                        	flag = false;
                        	val ipt:Point = Point.make(i);
				var childSet:ArrayList[Int] = new ArrayList[Int]();
				childSet = at(D(ipt)) nodeSet(ipt).children;
                        	for(var j:Int=0; j<childSet.size(); j++) {
                        		str="(" + i + "," + childSet.get(j) + ") ";
					for(var k:Int=0; k<str.length(); k++) {
						var ch:Char = str.charAt(k);
						fw.writeChar(ch);
					}
					flag = true;
                        	}
                        	if(flag)
                                	fw.writeChar('\n');             
			}
			fw.close();
		}
		catch(ex: x10.lang.Exception){}
	}	

	/** Validates the output resulting from the execution of the algorithm. */          
        def outputVerifier(val weightMatrix:Array[Int]) {
        	val pt:Point = Point.make(0);
        	var root:Int = at(D(pt)) nodeSet(pt).fragmentRoot;
        	var i:Int, j:Int;
        	var treeNodes:ArrayList[Int] = new ArrayList[Int]();
        	var treePart:Array[Boolean] = new Array[Boolean]((0..(nodes-1)));
        	var sumWeight:Int=0; var checkSum:Int=0;
        	treeNodes.add(root);
        	treePart(root) = true;
        	
        	while(treeNodes.size() < nodes) {
        		var minWeight:Int = Int.MAX_VALUE;
        		var minNode:Int = -1;
        		for(i=0; i<treeNodes.size(); i++) {
        			var aNode:Int = treeNodes.get(i);
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
        		var childSet:ArrayList[Int] = new ArrayList[Int]();
			childSet = at(D(ipt)) nodeSet(ipt).children;
        		for(j=0; j<childSet.size(); j++) {
        			var child:Int = childSet.get(j);
        			checkSum = checkSum + weightMatrix(i,child);
        		}
        	}
        	
        	if(checkSum == sumWeight) {
        		var nodeCheck:Array[Int] = new Array[Int]((0..(nodes-1)));
			var flag:Boolean = false;
			for(i=0; i<nodes; i++) {
				val ipt:Point = Point.make(i);
        			var childSet:ArrayList[Int] = new ArrayList[Int]();
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
        
        var startSignal:Int;
        var changeSignal:Int, changeChild:Int;
        
        /** Stores a blue edge pair. */
        var pair:blueEdge = new blueEdge();
        
        /** Stores the received join signals. */
        var joinSignal:ArrayList[Merge] = new ArrayList[Merge]();
        
 	/** Identifies the children of a node. */
        var children:ArrayList[Int] = new ArrayList[Int]();
}
