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
 * vertexColoring aims to color the vertices of a tree with 
 * three colors.
 *
 * @author Suyash Gupta
 * @author V Krishna Nandivada
 */
public class vertexColoring 
{
 	var adj_graph:Array[Int], parent:Array[Int], nodes:Int, root:Int, label:Int, nlabel:Array[Int], colormat:Array[Int];
 	
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
 	public static def main(args:Array[String](1)) throws Exception {
		var inputFile:String = "inputVC.txt", outputFile:String = "outputVC.txt";
		var i:Int,j:Int;
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
				Console.OUT.println("Wrong option specified");		
		}

		var fl:File = new File(inputFile);
		var fr:FileReader = new FileReader(fl);
		var s:String = fr.readLine();
		vc.nodes = Int.parse(s);
		vc.parent = new Array[Int]((0..(vc.nodes-1)));
		vc.nlabel = new Array[Int]((0..(vc.nodes-1)));
		vc.adj_graph = new Array[Int]((0..(vc.nodes-1))*(0..(vc.nodes-1)));

		/** Region creation. */		
		vc.R = 0..(vc.nodes-1);
		
		/** Creation of a Block Distribution. */
    		vc.D = Dist.makeBlock(vc.R);
    		//vc.D = Dist.makeUnique();
    		//vc.R = vc.D.region;
    		
    		/** Distribution of nodes. */
    		vc.nodeSet = DistArray.make[Node](vc.D);
    		 
		vc.nval = DistArray.make[long](vc.D);

    		/** Some more data getting distributed. */
		vc.again = DistArray.make[boolean](vc.D);		

		s = fr.readLine();
		vc.root = Int.parse(s);
		try {
			j=0;
			while(!((s = fr.readLine()).equals(" "))) {
				for(i=0; i<s.length(); i++) {
					var ch:Char=s.charAt(i);
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
				vc.parent(j) = Int.parse(s);
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
		for(i in D) {
			at(D(i)) {
				var j:Int; var count:Int=0;
				val idx:Int = i.operator()(0);
				nlabel(i) = idx;
				nodeSet(i) = new Node();
				nodeSet(i).parent = parent(idx);
				for(j=0; j<nodes; j++)
					if(adj_graph(idx,j) == 1 && parent(idx) != j)
						count++;
				nodeSet(i).children = new Array[Int]((0..(count-1)));		
				count=0;
				for(j=0; j<nodes; j++)
					if(adj_graph(idx,j) == 1 && parent(idx) != j) {
						nodeSet(i).children(count) = j;
						count++;
					}
				nodeSet(i).color = nlabel(idx);	
 			}
 		}	
 		var someval:Double = (Math.log(nodes)/Math.log(2));
		label = (someval as Int);
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
		for(i in D) {
			at(D(i)) {
				again(i) = false;
				for(j in 0..(nodeSet(i).children.size-1)) {	
					val cpt:Point = Point.make(nodeSet(i).children(j));
					sendColor(cpt, nodeSet(i).color);
				}

				if(!loadValue.equals(0)) {
					val ipt:Int = i(0);
					nval(i) = loadweight(nval(i)+ipt);
				}	
			}
		}
						
		for(i in D) {
			at(D(i)) {
				val idx:Int = i.operator()(0);
				if(idx != root) {
					var xored:Int = nodeSet(i).receivedColor ^ nodeSet(i).color;
					for(var k:Int=0; k<label; k++) {
						var pval:Int = 1 << k;
						var nand:Int = xored & pval;
						if(nand == pval) {
							var nxored:Int = nodeSet(i).color & pval;
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
					val ipt:Int = i(0);
					nval(i) = loadweight(nval(i)+ipt);
				}
 			}
 		}
 	}		
 
 	/** Reduces the number of colors from six to three. */
 	def six2three() {
 		for(var x:Int=5; x>2; x--) {
 			shiftDown();
	 	 	val rt:Point = Point.make(root);
	 	 	val currentIter:Int = x;
	 	 	at(D(rt)){
	 	 		var r:Random = new Random();
	 			var ncolor:Int = r.nextInt(3);
	 			if(nodeSet(rt).color == ncolor)
 					ncolor = (ncolor+1)%3;
 				nodeSet(rt).color = ncolor;
 			}
	  
			for(i in D) {
				at(D(i)) {
					var cparent:Int=0,cchild:Int=0;
 				
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
						val ipt:Int = i(0);
						nval(i) = loadweight(nval(i)+ipt);
					}	
	 			}
	 		}
	 	}			
 	}

	/** Shifts the color of parent down to its children. */ 	
 	def shiftDown() {
		for(i in D) {
			at(D(i)) {
				for(j in 0..(nodeSet(i).children.size-1)) {
					val cpt:Point = Point.make(nodeSet(i).children(j));
					sendColor(cpt, nodeSet(i).color);
				}
	
				if(!loadValue.equals(0)) {
					val ipt:Int = i(0);
					nval(i) = loadweight(nval(i)+ipt);
				}
			}
		}	
		
		for(i  in D) {
			at(D(i)) {
				val idx:Int = i.operator()(0);
				if(idx != root)
					nodeSet(i).color = nodeSet(i).receivedColor;

				if(!loadValue.equals(0)) {
					val ipt:Int = i(0);
					nval(i) = loadweight(nval(i)+ipt);
				}
			}
		}			
 	}
 	
 	/**
	 * Provides the color of the <code>aNode<\code>.
	 * @param   aNode 	node whose color value is required.
	 * @return  		color of <code>aNode<\code>.
	 */ 	
 	def getColor(val aNode:Point):Int {
 		val Color:Int = at(D(aNode)) nodeSet(aNode).color;
 		return Color;
 	}
 	
 	/**
	 * Sends the color of the parent node to a child node.
	 * @param   childNode 	node whose color is to be changed
	 * @return  acolor	color of the parent node.
	 */
 	def sendColor(val childNode:Point, val acolor:Int) {
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
	 		for(var q:Int=0; q<nodes; q++) {
 				val pt:Point = Point.make(q);
 				val color:Int = at(D(pt)) nodeSet(pt).color;
 				str="\n Node " + q + ": \t Color " + color;
				for(var j:Int=0; j<str.length(); j++) {
					var ch:Char = str.charAt(j);
					fw.writeChar(ch);
				} 			
 			}
 		} catch(ex: x10.lang.Exception){}	
 	}
 	
	/** Validates the output resulting from the execution of the algorithm. */   	
	def outputVerifier() {
 		var k:Int;
 		var count:Int=0; 
 		var colormat:Array[Int] = new Array[Int]((0..(nodes-1)));
 		var flag:Boolean = false;
 		for(k=0; k<nodes; k++)
 			colormat(k)=-1;
   
 		for(k=0; k<nodes; k++) {
 			val pt:Point = Point.make(k);
 			val color:Int = at(D(pt)) nodeSet(pt).color;
 			if(colormat(color) <0) {
 				colormat(color)=0;
 				count++;
 			}
 		}
 		if(count <= 3) {
 			for(i in D) {
 				flag = at(D(i)){
 					var lflag:Boolean=false;
 					var idx:Int = i.operator()(0);
	 				if(idx != root) {	
 						val cpt:Point = Point.make(nodeSet(i).parent);
						val cpar:Int = at(D(cpt)) nodeSet(cpt).color;
 						if(nodeSet(i).color == cpar)
 							lflag = true;
 					}	 					
					for(var j:Int=0; j<nodeSet(i).children.size; j++) {
						val cpt:Point = Point.make(nodeSet(i).children(j));
						val cchild:Int = at(D(cpt)) nodeSet(cpt).color;
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
	var parent:Int;
	
	/** Identifies the children of a node. */				
	var children:Array[Int];
	
	/** Specifies the color of a node. */	
	var color:Int;
	
	/** Specifies the color received by a node. */
	var receivedColor:Int;
}
