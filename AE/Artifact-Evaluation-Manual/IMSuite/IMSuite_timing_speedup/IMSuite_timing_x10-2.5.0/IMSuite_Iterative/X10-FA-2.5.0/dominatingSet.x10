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
 * dominatingSet aims to find a dominating set among a set of nodes.
 * The algorithm also uses the concept of node coloring for identifying
 * which node will be part of dominating set.
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
public class dominatingSet 
{
	var adj_graph:Array[Long], nodes:Long;
	
	/** Colors for node. */
	val WHITE = 0;
	val GREY = 1;
	val BLACK = 2;
	
	/** Abstract node representation as a distributed array. */
	var vertexSet:DistArray[Vertex];

	/** Parameters to enable execution with load */
	var loadValue:long=0; 

	/** Load sum represented as a distributed array. */
	var nval:DistArray[long];
	
	/** Region and Distribution specification. */
	var R: Region;	var D: Dist;
	
	/** Other Distributed Array used. */
	var domSet:DistArray[Boolean];
	var change:DistArray[Boolean];
	var rs:DistArray[Random];		

	/** 
	 * Acts as the starting point for the program execution. 
	 * <code>main</code> performs the task of accepting the input from the user 
	 * specified file, calling <code>createDominatingSet<\code> method, 
	 * printing the output and validating the result.
	 *
	 * @param args 		array of runtime arguments.
	 * @throws Exception	if File handling operation illegal. 
	 */
	public static def main(args:Rail[String]) throws Exception {
		var inputFile:String = "inputdominatingSet", outputFile:String = "outputdominatingSet";
		var i:Long,j:Long;
		var flag:Boolean = false;
		var ds:dominatingSet = new dominatingSet();
		
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
				ds.loadValue = Long.parse(args(i+1));
				i++;
			}	
			else
				Console.OUT.println("Wrong option spcified");		
		}

		var fl:File = new File(inputFile);
		var fr:FileReader = new FileReader(fl);
		var s:String = fr.readLine();
		ds.nodes = Long.parse(s);
		ds.adj_graph = new Array[Long](Region.make(0..(ds.nodes-1), 0..(ds.nodes-1)));

		/** Region creation. */		
		ds.R = Region.make(0, (ds.nodes-1));

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
		ds.nval = DistArray.make[long](ds.D);
	
		try {
			j=0;
			while((s = fr.readLine()) != null) {
				for(i=0; i<s.length(); i++) {
					var iInt:Int = i as Int;
					var ch:Char=s.charAt(iInt);
					if(ch=='0')
						ds.adj_graph(j,i) = 0;
					else
						ds.adj_graph(j,i) = 1;	
				}
				j++;
			}
		} catch(eof: x10.io.EOFException){}
		
		ds.initialize();

		var startTime:long = System.nanoTime();
		ds.createDominatingSet();
		var finishTime:long = System.nanoTime();
		var estimatedTime:long = finishTime - startTime;
		//Console.OUT.println("Start Time: " + startTime + "\t Finish Time: " + finishTime + "\t Estimated Time: " + estimatedTime);
		Console.OUT.println(estimatedTime);

		ds.printDominatingSet(outputFile);
		if(flag)
			ds.outputVerifier();

		if(!ds.loadValue.equals(0)) {
			var sumval:double=0;
			for(i=0; i<ds.nodes; i++) {
				val pt:Point = Point.make(i);
				var arrSum:Long = ds.getNval(pt); 
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
					var j:Long=0; var k:Long=0;
					var index:Long = i.operator()(0);
					vertexSet(i) = new Vertex();
					vertexSet(i).Wv = 1;
					for(j=0; j<nodes; j++)
						if(adj_graph(index,j) == 1)
							vertexSet(i).Wv++;
					vertexSet(i).color = WHITE;
					vertexSet(i).WHat = 0;
					vertexSet(i).support = 0;
					vertexSet(i).neighbours = new Rail[Long](vertexSet(i).Wv);
					vertexSet(i).whiteNeighbours = new Rail[Long](vertexSet(i).Wv);
						
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
				}
			}		
		}
		
		finish {
			for(i in D) {
				async at(D(i)) {
					var j:Long; var length:Long; var count:Long = vertexSet(i).neighbours.size; var retArray:Rail[Long];
					var flag:Boolean;
					var index:Long = i.operator()(0);
					for(j=0; j<vertexSet(i).neighbours.size; j++)
						vertexSet(i).N2v.add(vertexSet(i).neighbours(j));
					for(j=0; j<vertexSet(i).neighbours.size; j++)
						if(vertexSet(i).neighbours(j) != index) {
							var pt:Point = Point.make(vertexSet(i).neighbours(j));
							retArray = getNeighborArray(pt);
							
							for(var k:Long=0; k<retArray.size; k++) {
								flag = false;
								for(var l:Long=0; l<vertexSet(i).N2v.size(); l++)
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
		var check:Boolean;
		do {
			change = DistArray.make[Boolean](D);
			finish {
				for(i in D) {
					async at(D(i)) {
						if(vertexSet(i).Wv != 0) {
							var someVal:Double = (Math.log(vertexSet(i).Wv)/Math.log(2));
							vertexSet(i).Wtilde = (someVal as Long);
							someVal = Math.pow(2, vertexSet(i).Wtilde);
							vertexSet(i).Wtilde = (someVal as Long);
						}

						if(!loadValue.equals(0)) {
							val ipt:Long = i(0);
							nval(i) = loadweight(nval(i)+ipt);
						}
					}
				}
			}										

			finish	{
				for(i in D) {
					async at(D(i)) {
						if(vertexSet(i).Wv != 0) {
							val index:Long = i(0);			
							vertexSet(i).WHat = 0;						
							for(var j:Long=0; j<vertexSet(i).N2v.size(); j++)
								if(index != vertexSet(i).N2v.get(j)) {
									val pt:Point = Point.make(vertexSet(i).N2v.get(j));
									if(getColor(pt) == WHITE) {
										var n2value:Long = getWtilde(pt);
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
						}
		
						if(!loadValue.equals(0)) {
							val ipt:Long = i(0);
							nval(i) = loadweight(nval(i)+ipt);
						}
					}
				}
			}
			
			finish	{
				for(i in D) {
					async at(D(i)) {
						if(vertexSet(i).Wv != 0) {
							val index:Long = i(0);					
							vertexSet(i).support = 0;
							for(var j:Long=0; j<vertexSet(i).neighbours.size; j++)
								if(index != vertexSet(i).neighbours(j)) {
									var pt:Point = Point.make(vertexSet(i).neighbours(j));
									if(getActiveState(pt))
										vertexSet(i).support++;
								}
							if(vertexSet(i).active)
								vertexSet(i).support++;
						}
				
						if(!loadValue.equals(0)) {
							val ipt:Long = i(0);
							nval(i) = loadweight(nval(i)+ipt);
						}
					}
				}
			}											
			
			finish	{
				for(i in D) {
					async at(D(i)) {
						if(vertexSet(i).Wv != 0) {
							val index:Long = i(0);
							vertexSet(i).Sv = 0;
							for(var j:Long=0; j<vertexSet(i).whiteNeighbours.size; j++)
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
						}

						if(!loadValue.equals(0)) {
							val ipt:Long = i(0);
							nval(i) = loadweight(nval(i)+ipt);
						}
					}
				}
			}				
			
			finish	{
				for(i in D) {
					async at(D(i)) {
						if(vertexSet(i).Wv != 0) {
							val index:Long = i(0);						
							vertexSet(i).Cv = 0;
							for(var j:Long=0; j<vertexSet(i).whiteNeighbours.size; j++)
								if(index != vertexSet(i).whiteNeighbours(j)) {
									var pt:Point = Point.make(vertexSet(i).whiteNeighbours(j));
									if(getCandidate(pt))
										vertexSet(i).Cv++;
								}
							if(vertexSet(i).candidate)
								vertexSet(i).Cv++;			
						}

						if(!loadValue.equals(0)) {
							val ipt:Long = i(0);
							nval(i) = loadweight(nval(i)+ipt);
						}
					}
				}
			}
			
			finish	{
				for(i in D) {
					async at(D(i)) {
						if(vertexSet(i).Wv != 0) {
							val index:Long = i(0);	
							var sumCv:Long=0;				
							for(var j:Long=0; j<vertexSet(i).whiteNeighbours.size; j++)
								if(index != vertexSet(i).whiteNeighbours(j)) {
									var pt:Point = Point.make(vertexSet(i).whiteNeighbours(j));
									sumCv = sumCv + getCv(pt);
								}
							sumCv = sumCv + vertexSet(i).Cv;		
							if(vertexSet(i).candidate && sumCv <= 3*vertexSet(i).Wv) {
								for(var j:Long=0; j<vertexSet(i).neighbours.size; j++)
									if(index != vertexSet(i).neighbours(j)) {
										var npt:Point = Point.make(vertexSet(i).neighbours(j));
										sendneighbor(npt, index);
									}
								vertexSet(i).domSetCandidate = true;
							}
						}

						if(!loadValue.equals(0)) {
							val ipt:Long = i(0);
							nval(i) = loadweight(nval(i)+ipt);
						}
					}
				}
			}						
			
			finish	{
				for(i in D) {
					async at(D(i)) {
						if(vertexSet(i).domSetCandidate && vertexSet(i).Wv != 0) {
							val index:Long = i(0);
							var maxId:Long = index;
							for(var j:Long=0; j<vertexSet(i).neighborCandidate.size(); j++)
								if(maxId < vertexSet(i).neighborCandidate.get(j))
									maxId = vertexSet(i).neighborCandidate.get(j);
							if(maxId == index) {
								domSet(i) = true;
								vertexSet(i).color = BLACK;
								for(var j:Long=0; j<vertexSet(i).neighbours.size; j++)
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

						if(!loadValue.equals(0)) {
							val ipt:Long = i(0);
							nval(i) = loadweight(nval(i)+ipt);
						}
					}
				}
			}
			
			finish	{
				for(i in D) {
					async at(D(i)) {
						if(vertexSet(i).Wv != 0 && vertexSet(i).cflag) {
							vertexSet(i).Wv = 0;
							vertexSet(i).cflag = false;
							vertexSet(i).active = false;
						}

						if(!loadValue.equals(0)) {
							val ipt:Long = i(0);
							nval(i) = loadweight(nval(i)+ipt);
						}	
					}
				}
			}							
			
			finish	{
				for(i in D) {
					async at(D(i)) {
						if(vertexSet(i).Wv != 0) {				
							vertexSet(i).Wv = 0;
							for(var j:Long=0; j<vertexSet(i).neighbours.size; j++) {
								var pt:Point = Point.make(vertexSet(i).neighbours(j));
								if(WHITE == getColor(pt))
									vertexSet(i).Wv++;
							}		
							
							var k:Long=0;
							vertexSet(i).whiteNeighbours = new Rail[Long](vertexSet(i).Wv);
							for(var j:Long=0; j<vertexSet(i).neighbours.size; j++) {
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
							else	
								change(i) = true;
						}

						if(!loadValue.equals(0)) {
							val ipt:Long = i(0);
							nval(i) = loadweight(nval(i)+ipt);
						}
					}
				}
			}
			check = checkChange();
							
		}while(check);
	}
	
	/** Tells whether for any node the color exists <code>WHITE\code> or not.
	 * 
	 * @return	true if some node color is <code>WHITE<\code>.
	 */
	def checkChange():Boolean {
		var counter:Long = 0;
		var flag:Boolean=false;
		for(var i:Long=0; i<nodes; i++) {
			val pt:Point = Point.make(i);
			flag = at(D(pt)) change(pt);
			if(flag)
				counter++;
		}
		
		if(counter > 0)
			return true;
		else
			return false; 	
	}
	
	/** 
	 * Adds a node as the neighbor of some other node.
	 *
	 * @param npt		Node which receives a new neighbor.
	 * @param index		Neighbor candidate.
	 */
	def sendneighbor(val npt:Point, val index:Long) {
		at(D(npt)){
			atomic{ vertexSet(npt).neighborCandidate.add(index); }
		}
	}
	
	/** 
	 * Clears the flag.
	 *
	 * @param npt	Node whose flag has to be cleared.
	 */
	def setClearFlag(val npt:Point) {
		at(D(npt)){ 
			atomic{ vertexSet(npt).cflag = true; }
		}
	}

	/** 
	 * Set the count of White neighbors. 
	 *
	 * @param aNode		Node whose <code>Wv<\code> has o be set.
	 */
	def setWv(val aNode:Point) {
		at(D(aNode))	vertexSet(aNode).Wv = 0;
	}

	/** 
	 * Provides the set of neighbors of a node. 
	 * 
	 * @param   aNode	Node whose neighbors are required.			
	 * @return 		neighbors of <code>anode<\code>.
	 */
	def getNeighborArray(val aNode:Point):Rail[Long] {
		var neigh:Rail[Long] = at(D(aNode)) vertexSet(aNode).neighbours;
		return neigh;
	}
	
	/** 
	 * Provides the value of <code>Wtilde<\code> for a node. 
	 * 
	 * @param   j	Node whose <code>Wtilde<\code> is required.
	 * @return 	<code>Wtilde<\code> of node <code>j<\code>.
	 */
	def getWtilde(val j:Point):Long {
		var nval:Long = at(D(j)) vertexSet(j).Wtilde;
		return nval;
	}
	
	/** 
	 * Provides the value of <code>active<\code> variable for a node. 
	 * 
	 * @param   j	Node whose <code>active<\code> is required.
	 * @return 	<code>active<\code> of node <code>j<\code>.
	 */
	def getActiveState(val j:Point):Boolean {
		var nval:Boolean = at(D(j)) vertexSet(j).active;
		return nval;
	}
	
	/** 
	 * Provides the value of <code>support<\code> for a node. 
	 * 
	 * @param   j	Node whose <code>support<\code> is required.
	 * @return 	<code>support<\code> of node <code>j<\code>.
	 */
	def getSupport(val j:Point):Long {
		var nval:Long = at(D(j)) vertexSet(j).support;
		return nval;
	}
	
	/** 
	 * Provides the value of <code>candidate<\code> for a node. 
	 * 
	 * @param   j	Node whose <code>candidate<\code> is required.
	 * @return 	<code>candidate<\code> of node <code>j<\code>.
	 */
	def getCandidate(val j:Point):Boolean {
		var nval:Boolean = at(D(j)) vertexSet(j).candidate;
		return nval;
	}
	
	/** 
	 * Provides the value of <code>Cv<\code> for a node. 
	 * 
	 * @param   j	Node whose <code>Cv<\code> is required.
	 * @return 	<code>Cv<\code> of node <code>j<\code>.
	 */
	def getCv(val j:Point):Long {
		var nval:Long = at(D(j)) vertexSet(j).Cv;
		return nval;
	}
	
	/** 
	 * Provides the color information for a node. 
	 * 
	 * @param   j	Node whose color is required.
	 * @return 	<code>Wtilde<\code> of a node.
	 */
	def getColor(val j:Point):Long {
		var nval:Long = at(D(j)) vertexSet(j).color;
		return nval;
	}
	
	/** 
	 * Sets the color of a node to <code>GRAY<\code> for a node.
	 *
	 * @param   j	Node whose color is to be set.
	 */
	def setColor(val j:Point) {
		at(D(j)){
			if(vertexSet(j).color == WHITE)
				vertexSet(j).color = GREY;
		}		
	}

	/** 
	 * Writes the output to the user specified file.
	 * 
	 * @param  fileName	Name of the file in which output has to be stored.
	 * @throws 		input output exception if a failure in write occurs.
	 */
	def printDominatingSet(var fileName:String) {
		try {
 			var fl:File = new File(fileName);
			fw:FileWriter = new FileWriter(fl);
			var str:String;
			for(var i:Long=0; i<nodes; i++) {
				val pt:Point = Point.make(i);
				var index:Boolean = at(D(pt)){
						var flag:Boolean=false;
						if(domSet(pt))
							flag = true;
						flag
				};
				if(index) {
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
		var domNodes:Rail[Boolean] = new Rail[Boolean](nodes);
		
		for(i=0; i<nodes; i++) {
			val pt: Point = Point.make(i);
			var isdom: Boolean = at(D(pt)){
						var lflag:Boolean=false;
						if(domSet(pt))
							lflag = true;
						lflag
			};			
			if(isdom) {
				domNodes(i) = true;
				for(var j:Long=0; j<nodes; j++)
					if(adj_graph(i,j) == 1)
						setNodes(j) = true;
			}			
		}				
		
		for(i=0; i<nodes; i++) {
			if(domNodes(i) && setNodes(i)) {
				flag = true;
				break;
			}
			else if(!setNodes(i) && !domNodes(i)) {
				flag = true;
				break;
			}	
		}
		if(!flag)
			Console.OUT.println("Output verified");
	}
}

/**
 * <code>Vertex</code> specifies the structure for each abstract node
 * part of the Dominating Set.
 */
class Vertex
{
	/** Specifies the neighbors of a node. */
	var neighbours:Rail[Long];
	
	/** Specifies the white neighbors of a node. */
	var whiteNeighbours:Rail[Long];
	
	/** Specifies the count of white neighbors of a node. */
	var Wv:Long;
	var Wtilde:Long;
	var WHat:Long;
	var support:Long;
	var Sv:Long;
	var Cv:Long;
	var color:Long;
	var cflag:Boolean;
	var active:Boolean;	
	var candidate:Boolean;
	
	/** Specifies information whether the node can compete to be part of dominating Set. */
	var domSetCandidate:Boolean;
	
	/** Set of neighbors till distance two. */
	var N2v:ArrayList[Long] = new ArrayList[Long]();
	var neighborCandidate:ArrayList[Long] = new ArrayList[Long]();
}
