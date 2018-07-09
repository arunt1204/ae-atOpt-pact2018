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
 * dominatingSet aims to find a dominating set among a set of nodes.
 * The algorithm also uses the concept of node coloring for identifying
 * which node will be part of dominating set.
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
public class dominatingSet 
{
	var adj_graph:Array[Int], nodes:Int;
	
	/** Colors for node. */
	val WHITE = 0;
	val GREY = 1;
	val BLACK = 2;
	
	/** Abstract node representation as a distributed array. */
	var vertexSet:DistArray[Vertex];
	
	/** Region and Distribution specification. */
	var R: Region;	var D: Dist;
	
	/** Other Distributed Array used. */
	var domSet:DistArray[Boolean];
	var change:DistArray[Boolean];
	var rs:DistArray[Random];	

	/** Counter for measuring total communication (as a distributed array). */ 
	var cmess:DistArray[Int];
	
	/** Counter for measuring total atomics (as a distributed array). */ 
	var ciso:DistArray[Int];

	/** 
	 * Acts as the starting point for the program execution. 
	 * <code>main</code> performs the task of accepting the input from the user 
	 * specified file, calling <code>createDominatingSet<\code> method, 
	 * printing the output and validating the result.
	 *
	 * @param args 		array of runtime arguments.
	 * @throws Exception	if File handling operation illegal. 
	 */
	public static def main(args:Array[String](1)) throws Exception {
		var inputFile:String = "inputdominatingSet", outputFile:String = "outputdominatingSet";
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
		var ds:dominatingSet = new dominatingSet();
		ds.nodes = Int.parse(s);
		ds.adj_graph = new Array[Int]((0..(ds.nodes-1))*(0..(ds.nodes-1)));

		/** Region creation. */		
		ds.R = 0..(ds.nodes-1);

		/** Creation of a Block Distribution. */
    		ds.D = Dist.makeBlock(ds.R);
    		//ds.D = Dist.makeUnique();
    		//ds.R = ds.D.region;
    		
    		/** Distribution of nodes. */
    		ds.vertexSet = DistArray.make[Vertex](ds.D);
    		
    		/** Some more data getting distributed. */
    		ds.domSet = DistArray.make[Boolean](ds.D);
    		ds.change = DistArray.make[Boolean](ds.D);
		ds.rs = DistArray.make[Random](ds.D);	
   		
   		/** Distribution of communication counters. */
    		ds.cmess = DistArray.make[Int](ds.D);
    		
    		/** Distribution of atomic counters. */
	  	ds.ciso = DistArray.make[Int](ds.D);
		
		try {
			j=0;
			while((s = fr.readLine()) != null) {
				for(i=0; i<s.length(); i++) {
					var ch:Char=s.charAt(i);
					if(ch=='0')
						ds.adj_graph(j,i) = 0;
					else
						ds.adj_graph(j,i) = 1;	
				}
				j++;
			}
		} catch(eof: x10.io.EOFException){}
		
		ds.initialize();
		
		ds.createDominatingSet();
		
		/** Call to method for computing total Communication and Atomics. */
		ds.messCount();
	}
	
	/** Computes total Communication and Atmoic Operations. */ 
	def messCount() {
		var msum:Int=0, isum:Int=0;
		for(var i:Int=0; i<nodes; i++) {
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
		clocked finish {
			for(i in D) {
				clocked async at(D(i)) {
					var j:Int=0; var k:Int=0;
					var index:Int = i.operator()(0);
					vertexSet(i) = new Vertex();
					vertexSet(i).Wv = 1;
					for(j=0; j<nodes; j++)
						if(adj_graph(index,j) == 1)
							vertexSet(i).Wv++;
					vertexSet(i).color = WHITE;
					vertexSet(i).WHat = 0;
					vertexSet(i).support = 0;
					vertexSet(i).neighbours = new Array[Int]((0..(vertexSet(i).Wv-1)));
					vertexSet(i).whiteNeighbours = new Array[Int]((0..(vertexSet(i).Wv-1)));
						
					for(j=0; j<nodes; j++)
						if(adj_graph(index,j) == 1)
						{
							vertexSet(i).neighbours(k) = j;
							vertexSet(i).whiteNeighbours(k) = j;
							k++;
						}
					vertexSet(i).neighbours(k) = index;
					vertexSet(i).whiteNeighbours(k) = index;
					rs(i) = new Random(index);
					
					Clock.advanceAll();
					
					var length:Int; var count:Int = vertexSet(i).neighbours.size; var retArray:Array[Int];
					var flag:Boolean;
					for(j=0; j<vertexSet(i).neighbours.size; j++)
						vertexSet(i).N2v.add(vertexSet(i).neighbours(j));
					for(j=0; j<vertexSet(i).neighbours.size; j++)
						if(vertexSet(i).neighbours(j) != index) {
							var pt:Point = Point.make(vertexSet(i).neighbours(j));
							retArray = getNeighborArray(pt);
							
							for(k=0; k<retArray.size; k++) {
								flag = false;
								for(var l:Int=0; l<vertexSet(i).N2v.size(); l++)
									if(vertexSet(i).N2v.get(l) == retArray(k)) {
										flag = true;
										break;
									}
								if(!flag)
									vertexSet(i).N2v.add(retArray(k));
							}
						}
				}
			}		
		}
	}
	
	/** Aims to create a dominating set from a set of nodes. */
	def createDominatingSet() {
		val h0 = here;
		clocked finish {
			for(i in D) {
				clocked async at(D(i)) {
					val h1 = here;
					
					/** 
					 * Checking for remote data access in isolation.
					 * If remote data access then increment counter for communication.
					 */
					atomic {
						if(h0 != h1)
							cmess(i) = cmess(i) + 1;
					}
					
					while(vertexSet(i).Wv != 0) {
						var someVal:Double = (Math.log(vertexSet(i).Wv)/Math.log(2));
						vertexSet(i).Wtilde = (someVal as Int);
						someVal = Math.pow(2, vertexSet(i).Wtilde);
						vertexSet(i).Wtilde = (someVal as Int);
						
						Clock.advanceAll();
						
						val index:Int = i(0);			
						vertexSet(i).WHat = 0;						
						for(var j:Int=0; j<vertexSet(i).N2v.size(); j++)
							if(index != vertexSet(i).N2v.get(j)) {
								val pt:Point = Point.make(vertexSet(i).N2v.get(j));
								if(getColor(pt) == WHITE) {
									var n2value:Int = getWtilde(pt);
									if(vertexSet(i).WHat < n2value)
										vertexSet(i).WHat=n2value;
								}		
							}
						if(vertexSet(i).WHat < 	vertexSet(i).Wtilde)
							vertexSet(i).WHat = vertexSet(i).Wtilde;		
					
						if(vertexSet(i).WHat == vertexSet(i).Wtilde)
							vertexSet(i).active = true;
						else
							vertexSet(i).active = false;
						
						Clock.advanceAll();
							
						vertexSet(i).support = 0;
						for(var j:Int=0; j<vertexSet(i).neighbours.size; j++)
							if(index != vertexSet(i).neighbours(j)) {
								var pt:Point = Point.make(vertexSet(i).neighbours(j));
								if(getActiveState(pt))
									vertexSet(i).support++;
							}
						if(vertexSet(i).active)
							vertexSet(i).support++;
						
						Clock.advanceAll();
										
						vertexSet(i).Sv = 0;
						for(var j:Int=0; j<vertexSet(i).whiteNeighbours.size; j++)
							if(index != vertexSet(i).whiteNeighbours(j)) {
								var pt:Point = Point.make(vertexSet(i).whiteNeighbours(j));
								if(vertexSet(i).Sv < getSupport(pt))
									vertexSet(i).Sv = getSupport(pt);
							}
						if(vertexSet(i).Sv < vertexSet(i).support)
							vertexSet(i).Sv = vertexSet(i).support;
						vertexSet(i).candidate = false;
						
						if(vertexSet(i).active) {
							var probab:Double = rs(i).nextDouble(),	ratio:Double = 1.0/vertexSet(i).Sv;
							if(probab <= ratio)
								vertexSet(i).candidate = true;
						}
						
						Clock.advanceAll();
						
						vertexSet(i).Cv = 0;
						for(var j:Int=0; j<vertexSet(i).whiteNeighbours.size; j++)
							if(index != vertexSet(i).whiteNeighbours(j)) {
								var pt:Point = Point.make(vertexSet(i).whiteNeighbours(j));
								if(getCandidate(pt))
									vertexSet(i).Cv++;
							}
						if(vertexSet(i).candidate)
							vertexSet(i).Cv++;
						
						Clock.advanceAll();
										
						var sumCv:Int=0;				
						for(var j:Int=0; j<vertexSet(i).whiteNeighbours.size; j++)
							if(index != vertexSet(i).whiteNeighbours(j)) {
								var pt:Point = Point.make(vertexSet(i).whiteNeighbours(j));
								sumCv = sumCv + getCv(pt);
							}
						sumCv = sumCv + vertexSet(i).Cv;		
							
						if(vertexSet(i).candidate && sumCv <= 3*vertexSet(i).Wv) {
							for(var j:Int=0; j<vertexSet(i).neighbours.size; j++)
								if(index != vertexSet(i).neighbours(j)) {
									var npt:Point = Point.make(vertexSet(i).neighbours(j));
									sendneighbor(npt, index);
								}
							vertexSet(i).domSetCandidate = true;
						}
						
						Clock.advanceAll();
						
						if(vertexSet(i).domSetCandidate) {
							var maxId:Int = index;
							for(var j:Int=0; j<vertexSet(i).neighborCandidate.size(); j++)
								if(maxId < vertexSet(i).neighborCandidate.get(j))
									maxId = vertexSet(i).neighborCandidate.get(j);
							if(maxId == index) {
								domSet(i) = true;
								vertexSet(i).color = BLACK;
								for(var j:Int=0; j<vertexSet(i).neighbours.size; j++)
									if(index != vertexSet(i).neighbours(j)) {
										var npt:Point = Point.make(vertexSet(i).neighbours(j));
										setClearFlag(npt);
										setColor(npt);
									}	
								vertexSet(i).cflag = true;
							}
							vertexSet(i).domSetCandidate = false;	
						}
						vertexSet(i).neighborCandidate.clear();
						
						Clock.advanceAll();
						
						if(vertexSet(i).cflag) {
							vertexSet(i).Wv = 0;
							vertexSet(i).cflag = false;
							vertexSet(i).active = false;
						}
						
						Clock.advanceAll();
							
						if(vertexSet(i).Wv != 0) {
							vertexSet(i).Wv = 0;
							for(var j:Int=0; j<vertexSet(i).neighbours.size; j++) {
								var pt:Point = Point.make(vertexSet(i).neighbours(j));
								if(WHITE == getColor(pt))
									vertexSet(i).Wv++;
							}		
							
							var k:Int=0;
							vertexSet(i).whiteNeighbours = new Array[Int]((0..(vertexSet(i).Wv-1)));
							for(var j:Int=0; j<vertexSet(i).neighbours.size; j++) {
								var pt:Point = Point.make(vertexSet(i).neighbours(j));
								if(WHITE == getColor(pt)) {
									vertexSet(i).whiteNeighbours(k) = vertexSet(i).neighbours(j);
									k++;
								}
							}
							
							if(vertexSet(i).Wv == 0) {
								vertexSet(i).color = BLACK;
								domSet(i) = true;	
							}
						}
					}
				}
			}
							
		}
	}
	
	/** 
	 * Adds a node as the neighbor of some other node.
	 *
	 * @param npt		Node which receives a new neighbor.
	 * @param index		Neighbor candidate.
	 */
	def sendneighbor(val npt:Point, val index:Int) {
		val h0 = here;
		at(D(npt)){
			val h1 = here;
			atomic{
				/** If remote data access then increment counter for communication. */
				if( h1 != h0)
					cmess(npt) = cmess(npt) + 1;
					
				/** Inside existing atomic operation, increment counter for atomic. */	
				ciso(npt) = ciso(npt) + 1;
					
				vertexSet(npt).neighborCandidate.add(index);
			}
		}
	}
	
	/** 
	 * Clears the flag.
	 *
	 * @param npt	Node whose flag has to be cleared.
	 */
	def setClearFlag(val npt:Point) {
		val h0 = here;
		at(D(npt)){
			val h1 = here;
			atomic{
				/** If remote data access then increment counter for communication. */
				if( h1 != h0)
					cmess(npt) = cmess(npt) + 1;
					
				/** Inside existing atomic operation, increment counter for atomic. */	
				ciso(npt) = ciso(npt) + 1;
				
				vertexSet(npt).cflag = true;
			}
		}
	}

	/** 
	 * Set the count of White neighbors. 
	 *
	 * @param aNode		Node whose <code>Wv<\code> has o be set.
	 */
	def setWv(val aNode:Point) {
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
			vertexSet(aNode).Wv = 0;
		}	
	}

	/** 
	 * Provides the set of neighbors of a node. 
	 * 
	 * @param   aNode	Node whose neighbors are required.			
	 * @return 		neighbors of <code>anode<\code>.
	 */
	def getNeighborArray(val aNode:Point):Array[Int] {
		val h0 = here;
		at(D(aNode)){
			val h1 = here;	
			/** 
			 * Checking for remote data access in isolation.
			 * If remote data access then increment counter for communication.
			 */
			atomic {
				if( h1 != h0)
					cmess(aNode) = cmess(aNode) + 1;
			}
		}

		var neigh:Array[Int] = at(D(aNode)) vertexSet(aNode).neighbours;
		return neigh;
	}
	
	/** 
	 * Provides the value of <code>Wtilde<\code> for a node. 
	 * 
	 * @param   j	Node whose <code>Wtilde<\code> is required.
	 * @return 	<code>Wtilde<\code> of node <code>j<\code>.
	 */
	def getWtilde(val j:Point):Int {
		val h0 = here;
		at(D(j)){
			val h1 = here;	
			/** 
			 * Checking for remote data access in isolation.
			 * If remote data access then increment counter for communication.
			 */
			atomic{
				if( h1 != h0)
					cmess(j) = cmess(j) + 1;
			}
		}
		var nval:Int = at(D(j)) vertexSet(j).Wtilde;
		return nval;
	}
	
	/** 
	 * Provides the value of <code>active<\code> variable for a node. 
	 * 
	 * @param   j	Node whose <code>active<\code> is required.
	 * @return 	<code>active<\code> of node <code>j<\code>.
	 */
	def getActiveState(val j:Point):Boolean {
		val h0 = here;
		at(D(j)){
			val h1 = here;	
			/** 
			 * Checking for remote data access in isolation.
			 * If remote data access then increment counter for communication.
			 */
			atomic{
				if( h1 != h0)
					cmess(j) = cmess(j) + 1;
			}
		}
		
		var nval:Boolean = at(D(j)) vertexSet(j).active;
		return nval;
	}
	
	/** 
	 * Provides the value of <code>support<\code> for a node. 
	 * 
	 * @param   j	Node whose <code>support<\code> is required.
	 * @return 	<code>support<\code> of node <code>j<\code>.
	 */
	def getSupport(val j:Point):Int {
		val h0 = here;
		at(D(j)){
			val h1 = here;	
			/** 
			 * Checking for remote data access in isolation.
			 * If remote data access then increment counter for communication.
			 */
			atomic{
				if( h1 != h0)
					cmess(j) = cmess(j) + 1;
			}
		}
		
		var nval:Int = at(D(j)) vertexSet(j).support;
		return nval;
	}
	
	/** 
	 * Provides the value of <code>candidate<\code> for a node. 
	 * 
	 * @param   j	Node whose <code>candidate<\code> is required.
	 * @return 	<code>candidate<\code> of node <code>j<\code>.
	 */
	def getCandidate(val j:Point):Boolean {
		val h0 = here;
		at(D(j)){
			val h1 = here;	
			/** 
			 * Checking for remote data access in isolation.
			 * If remote data access then increment counter for communication.
			 */
			atomic {
				if( h1 != h0)
					cmess(j) = cmess(j) + 1;
			}
		}
		
		var nval:Boolean = at(D(j)) vertexSet(j).candidate;
		return nval;
	}
	
	/** 
	 * Provides the value of <code>Cv<\code> for a node. 
	 * 
	 * @param   j	Node whose <code>Cv<\code> is required.
	 * @return 	<code>Cv<\code> of node <code>j<\code>.
	 */
	def getCv(val j:Point):Int {
		val h0 = here;
		at(D(j)){
			val h1 = here;	
			/** 
			 * Checking for remote data access in isolation.
			 * If remote data access then increment counter for communication.
			 */
			atomic {
				if( h1 != h0)
					cmess(j) = cmess(j) + 1;
			}
		}
		
		var nval:Int = at(D(j)) vertexSet(j).Cv;
		return nval;
	}
	
	/** 
	 * Provides the color information for a node. 
	 * 
	 * @param   j	Node whose color is required.
	 * @return 	<code>Wtilde<\code> of a node.
	 */
	def getColor(val j:Point):Int {
		val h0 = here;
		at(D(j)){
			val h1 = here;	
			/** 
			 * Checking for remote data access in isolation.
			 * If remote data access then increment counter for communication.
			 */
			atomic {
				if( h1 != h0)
					cmess(j) = cmess(j) + 1;
			}
		}
		
		var nval:Int = at(D(j)) vertexSet(j).color;
		return nval;
	}
	
	/** 
	 * Sets the color of a node to <code>GRAY<\code> for a node.
	 *
	 * @param   j	Node whose color is to be set.
	 */	
	def setColor(val j:Point)
	{
		val h0 = here;
		at(D(j)){
			val h1 = here;	
			/** 
			 * Checking for remote data access in isolation.
			 * If remote data access then increment counter for communication.
			 */
			atomic {
				if( h1 != h0)
					cmess(j) = cmess(j) + 1;
			}

			if(vertexSet(j).color == WHITE)
				vertexSet(j).color = GREY;
		}		
	}
}

/**
 * <code>Vertex</code> specifies the structure for each abstract node
 * part of the Dominating Set.
 */
class Vertex
{
	/** Specifies the neighbors of a node. */
	var neighbours:Array[Int];
	
	/** Specifies the white neighbors of a node. */
	var whiteNeighbours:Array[Int];
	
	/** Specifies the count of white neighbors of a node. */
	var Wv:Int;
	var Wtilde:Int;
	var WHat:Int;
	var support:Int;
	var Sv:Int;
	var Cv:Int;
	var color:Int;
	var cflag:Boolean;
	var active:Boolean;	
	var candidate:Boolean;
	
	/** Specifies information whether the node can compete to be part of dominating Set. */
	var domSetCandidate:Boolean;
	
	/** Set of neighbors till distance two. */
	var N2v:ArrayList[Int] = new ArrayList[Int]();
	var neighborCandidate:ArrayList[Int] = new ArrayList[Int]();
}
