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
 * vertexColoring aims to color the vertices of a tree with 
 * three colors.
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
public class vertexColoring 
{
 	var adj_graph:Array[Long], parent:Rail[Long], nodes:Long, root:Long, label:Long, nlabel:Rail[Long], colormat:Rail[Long];
 	
 	/** Abstract node representation as a distributed array. */
 	var nodeSet:DistArray[Node];

	/** Parameters to enable execution with load */
	var loadValue:long=0; 

	/** Load sum represented as a distributed array. */
	var nval:DistArray[long];
 	
 	/** Region and Distribution specification. */
	var R: Region;	var D: Dist;
	
	/** Other Distributed Array used. */
	var again:DistArray[boolean];
 	
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
		var inputFile:String = "inputVC.txt", outputFile:String = "outputVC.txt";
		var i:Long,j:Long;
		var flag:Boolean = false;
		var vc:vertexColoring = new vertexColoring();
	
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
				vc.loadValue = Long.parse(args(i+1));
				i++;
			}	
			else
				Console.OUT.println("Wrong option spcified");		
		}

		var fl:File = new File(inputFile);
		var fr:FileReader = new FileReader(fl);
		var s:String = fr.readLine();
		vc.nodes = Long.parse(s);
		vc.parent = new Rail[Long](vc.nodes);
		vc.nlabel = new Rail[Long](vc.nodes);
		vc.adj_graph = new Array[Long](Region.make(0..(vc.nodes-1), 0..(vc.nodes-1)));

		/** Region creation. */		
		vc.R = Region.make(0, (vc.nodes-1));
		
		/** Creation of a Block Distribution. */
    		vc.D = Dist.makeBlock(vc.R);
    		//vc.D = Dist.makeUnique();
    		//vc.R = vc.D.region;
    		
    		/** Distribution of nodes. */
    		vc.nodeSet = DistArray.make[Node](vc.D);
    		
    		/** Some more data getting distributed. */
		vc.again = DistArray.make[boolean](vc.D);		
		vc.nval = DistArray.make[long](vc.D);

		s = fr.readLine();
		vc.root = Long.parse(s);
		try {
			j=0;
			while(!((s = fr.readLine()).equals(" "))) {
				for(i=0; i<s.length(); i++) {
					var iInt:Int = i as Int;
					var ch:Char=s.charAt(iInt);
					if(ch=='0')
						vc.adj_graph(j,i) = 0;
					else
						vc.adj_graph(j,i) = 1;	
				}
				j++;
			}
		} catch(eof: x10.io.EOFException){}
		
		try {
			j=0;
			while((s = fr.readLine()) != null) {
				vc.parent(j) = Long.parse(s);
				j++;
			}
		} catch(eof: x10.io.EOFException){}	
		
 		vc.initialize();
 		
		var startTime:long = System.nanoTime();
		vc.run();
 		vc.six2three();
		var finishTime:long = System.nanoTime();
		var estimatedTime:long = finishTime - startTime;
		Console.OUT.println("Start Time: " + startTime + "\t Finish Time: " + finishTime + "\t Estimated Time: " + estimatedTime);

 		vc.printOutput(outputFile);
 		if(flag)
 			vc.outputVerifier();

		if(!vc.loadValue.equals(0)) {
			var sumval:double=0;
			for(i=0; i<vc.nodes; i++) {
				val pt:Point = Point.make(i);
				var arrSum:Long = vc.getNval(pt); 
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
					var j:Long; var count:Long=0;
					val idx:Long = i.operator()(0);
					nlabel(idx) = idx;
					nodeSet(i) = new Node();
					nodeSet(i).parent = parent(idx);
					for(j=0; j<nodes; j++)
						if(adj_graph(idx,j) == 1 && parent(idx) != j)
							count++;
					nodeSet(i).children = new Rail[Long](count);		
					count=0;
					for(j=0; j<nodes; j++)
						if(adj_graph(idx,j) == 1 && parent(idx) != j) {
							nodeSet(i).children(count) = j;
							count++;
						}
					nodeSet(i).color = nlabel(idx);	
				}
 			}
 		}	
 		var someval:Double = (Math.log(nodes)/Math.log(2));
		label = (someval as Long);
 		if((1 << label) < nodes)
 			label++;
 	}

	/** Runs the algorithm till the graph consists of atmost six colors. */	
 	def run() {
 		val rt:Point = Point.make(root);
 		at(D(rt)){ nodeSet(rt).color = 0;}
		var cflag:boolean = false; 		
		do {		
			sixColor();
			cflag = checkAgain();
 		}while(cflag);				
	}

	/**
	 * Determines the number of different colors used in the graph.
	 *
	 * @return 	true if there is no color >= 6.
	 */
	def checkAgain():boolean {
		var flag:boolean = false;
		for(i in D) {
			flag = at(D(i)) again(i);
			if(flag)
				break;		
		}
		return flag;
	}
 
 	/** Reduces the number of colors used in the graph to six. */
 	def sixColor() {
		finish { 		  
			for(i in D) {
				async at(D(i)) {
					again(i) = false;
					for(j in 0..(nodeSet(i).children.size-1)) {	
						val cpt:Point = Point.make(nodeSet(i).children(j));
						sendColor(cpt, nodeSet(i).color);
					}

					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
					}	
				}	
			}
		}
						
		finish {
			for(i in D) {
				async at(D(i)) {
					val idx:Long = i.operator()(0);
					if(idx != root) {
						var xored:Long = nodeSet(i).receivedColor ^ nodeSet(i).color;
						for(var k:Long=0; k<label; k++) {
							var pval:Long = 1 << k;
							var nand:Long = xored & pval;
							if(nand == pval) {
								var nxored:Long = nodeSet(i).color & pval;
								if(nxored == 0)
									nodeSet(i).color = 2*k + 0;
								else
									nodeSet(i).color = 2*k + 1;
								break;
							} 
						}
 						if(nodeSet(i).color >= 6)
 							again(i) = true;
					}

					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
					}
 				}
 			}
 		}
 	}		
 
 	/** Reduces the number of colors from six to three. */
 	def six2three() {
 		for(var x:Long=5; x>2; x--) {
 			shiftDown();
	 	 	val rt:Point = Point.make(root);
	 	 	val currentIter:Long = x;
	 	 	at(D(rt)){
	 	 		var r:Random = new Random();
	 			var ncolor:Long = r.nextLong(3);
	 			if(nodeSet(rt).color == ncolor)
 					ncolor = (ncolor+1)%3;
 				nodeSet(rt).color = ncolor;
 			}
	  
			finish {
				for(i in D) {
					async at(D(i)) {
						var cparent:Long=0,cchild:Long=0;
 				
						if(nodeSet(i).color == currentIter) {
							val cpr:Point = Point.make(nodeSet(i).parent);
							cparent=getColor(cpr);
							if(nodeSet(i).children.size >0) {
								val cpt:Point = Point.make(nodeSet(i).children(0));
								cchild=getColor(cpt);
							}	
							if(cparent+cchild == 1)
								nodeSet(i).color=2;
							else if(cparent+cchild == 2)
								nodeSet(i).color=1;
							else if(cparent+cchild == 3) {
								if(cparent != 0 && cchild != 0)
									nodeSet(i).color=0;
								else
									nodeSet(i).color=1;
							}
							else if(cparent+cchild == 4) {
								if(cparent != 0 && cchild != 0)
									nodeSet(i).color=0;
								else
									nodeSet(i).color=1;
							}	 					
							else if(cparent+cchild == 5) {
								if(cparent != 0 && cchild != 0)
									nodeSet(i).color=0;
								else
									nodeSet(i).color=1;
							}
							else
								nodeSet(i).color = 0;
	 					}

						if(!loadValue.equals(0)) {
							val ipt:Long = i(0);
							nval(i) = loadweight(nval(i)+ipt);
						}	
	 				}
	 			}
	 		}
	 	}			
 	}

	/** Shifts the color of parent down to its children. */ 	
 	def shiftDown() {
		finish {
			for(i in D) {
				async at(D(i)) {
					for(j in 0..(nodeSet(i).children.size-1)) {
						val cpt:Point = Point.make(nodeSet(i).children(j));
						sendColor(cpt, nodeSet(i).color);
					}

					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
					}
				}
			}
		}	
		
 		finish {
			for(i  in D) {
				async at(D(i)) {
					val idx:Long = i.operator()(0);
					if(idx != root)
						nodeSet(i).color = nodeSet(i).receivedColor;

					if(!loadValue.equals(0)) {
						val ipt:Long = i(0);
						nval(i) = loadweight(nval(i)+ipt);
					}
				}
			}
		}			
 	}
 	
 	/**
	 * Provides the color of the <code>aNode<\code>.
	 * @param   aNode 	node whose color value is required.
	 * @return  		color of <code>aNode<\code>.
	 */ 	
 	def getColor(val aNode:Point):Long {
 		val Color:Long = at(D(aNode)) nodeSet(aNode).color;
 		return Color;
 	}
 	
 	/**
	 * Sends the color of the parent node to a child node.
	 * @param   childNode 	node whose color is to be changed
	 * @return  acolor	color of the parent node.
	 */
 	def sendColor(val childNode:Point, val acolor:Long) {
 		at(D(childNode)){
	 		nodeSet(childNode).receivedColor = acolor;
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
	 		for(var q:Long=0; q<nodes; q++) {
 				val pt:Point = Point.make(q);
 				val color:Long = at(D(pt)) nodeSet(pt).color;
 				str="\n Node " + q + ": \t Color " + color;
				for(var j:Long=0; j<str.length(); j++) {
					var jInt:Int = j as Int;
					var ch:Char = str.charAt(jInt);
					fw.writeChar(ch);
				} 			
 			}
 		} catch(ex: x10.lang.Exception){}	
 	}
 	
	/** Validates the output resulting from the execution of the algorithm. */   	
	def outputVerifier() {
 		var k:Long;
 		var count:Long=0; 
 		var colormat:Rail[Long] = new Rail[Long](nodes);
 		var flag:Boolean = false;
 		for(k=0; k<nodes; k++)
 			colormat(k)=-1;
   
 		for(k=0; k<nodes; k++) {
 			val pt:Point = Point.make(k);
 			val color:Long = at(D(pt)) nodeSet(pt).color;
 			if(colormat(color) <0) {
 				colormat(color)=0;
 				count++;
 			}
 		}
 		if(count <= 3) {
 			for(i in D) {
 				flag = at(D(i)){
 					var lflag:Boolean=false;
 					var idx:Long = i.operator()(0);
	 				if(idx != root) {	
 						val cpt:Point = Point.make(nodeSet(i).parent);
						val cpar:Long = at(D(cpt)) nodeSet(cpt).color;
 						if(nodeSet(i).color == cpar)
 							lflag = true;
 					}	 					
					for(var j:Long=0; j<nodeSet(i).children.size; j++) {
						val cpt:Point = Point.make(nodeSet(i).children(j));
						val cchild:Long = at(D(cpt)) nodeSet(cpt).color;
						if(nodeSet(i).color == cchild) {
							lflag = true;
							break;
						}
					}	
					lflag	
				};			
 			}
 			if(!flag)
 				Console.OUT.println("Output verified");	
 		}
 	}
}

/**
 * <code>Node</code> specifies the structure for each abstract node,
 * part of the Vertex coloring algorithm.
 */
class Node
{
	/** Specifies the parent of a node. */
	var parent:Long;
	
	/** Identifies the children of a node. */				
	var children:Rail[Long];
	
	/** Specifies the color of a node. */	
	var color:Long;
	
	/** Specifies the color received by a node. */
	var receivedColor:Long;
}
