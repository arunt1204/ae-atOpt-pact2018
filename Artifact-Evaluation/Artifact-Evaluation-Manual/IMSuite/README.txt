1) To run an X10-FA, X10-FAC, X10-FA-2.5.0 or X10-FAC-2.5.0 kernel pre-installation of X10 is required. 
The following example depicts the compilation and execution of an X10-FA, X10-FAC, X10-FA-2.5.0 or X10-FAC-2.5.0 kernel.

Kernel		:	bfsBellmanFord.x10
Input File	:	inputbfsBellmanFord_128_-spar_max.txt
Output File	:	output.txt
Input Size	:	128 nodes

Compilation	: 	x10c bfsBellmanFord.x10
	
In UP Model	:
Set X10_NUMPLACES=128
Execution	: 
x10 bfsBellmanFord -in inputbfsBellmanFord_128_-spar_max.txt -out output.txt -ver

In SP Model	:
Set X10_NUMPLACES=128
Execution	: 
x10 bfsBellmanFord -in inputbfsBellmanFord_128_-spar_max.txt -out output.txt -ver

In MP Model
Set X10_NUMPLACES=64
Execution	: 
x10 bfsBellmanFord -in inputbfsBellmanFord_128_-spar_max.txt -out output.txt -ver


2) To run an HJ-FA and HJ-FAP kernel pre-installation of HJ is required.

Kernel		:	bfsBellmanFord.hj
Input File	:	inputbfsBellmanFord_128_-spar_max.txt
Output File	:	output.txt
Input Size	:	128 nodes

Compilation	: 	hjc bfsBellmanFord.hj

Execution	:
hj -places 1:128 bfsBellmanFord -in inputbfsBellmanFord_128_-spar_max.txt -out output.txt -ver


3) To generate an input for an algorithm corresponding input generator can be used. 
All the input generators are coded in java. 
The following example depicts the compilation and execution of an input generator.

Kernel:		inputGeneratorBFSBellman.java
Input File:	inputbfsBellmanFord_128_-spmax.txt
Input Size:	128 nodes

Compilation:	javac inputGeneratorBFSBellman.java

Execution:
java inputGeneratorBFSBellman -in inputbfsBellmanFord_128_-spmax.txt -sz 128 -spmax
Other options can also be passed at runtime.

Options specific to each input generator are written as comments inside the
input generator program.

For rest of the details visit http://www.cse.iitm.ac.in/~krishna/imsuite/faq.html .
 
