1) To run an X10-FA and X10-FAC kernel pre-installation of X10 is required. 
The following example depicts the compilation and execution of an X10-FA and X10-FAC kernel.

Kernel		:	bfsBellmanFord.x10
Input File	:	inputbfsBellmanFord_128_-spar_max.txt
Input Size	:	128 nodes

Compilation	: 	x10c bfsBellmanFord.x10
	
In UP Model	:
Set X10_NUMPLACES=128
Execution	: 
x10 bfsBellmanFord -in inputbfsBellmanFord_128_-spar_max.txt

In SP Model	:
Set X10_NUMPLACES=128
Execution	: 
x10 bfsBellmanFord -in inputbfsBellmanFord_128_-spar_max.txt

In MP Model
Set X10_NUMPLACES=64
Execution	: 
x10 bfsBellmanFord -in inputbfsBellmanFord_128_-spar_max.txt


2) To run an HJ-FA and HJ-FAP kernel pre-installation of HJ is required.

Kernel		:	bfsBellmanFord.hj
Input File	:	inputbfsBellmanFord_128_-spar_max.txt
Input Size	:	128 nodes

Compilation	: 	hjc bfsBellmanFord.hj

Execution	:
hj -places 1:128 bfsBellmanFord -in inputbfsBellmanFord_128_-spar_max.txt

For rest of the details visit http://www.cse.iitm.ac.in/~krishna/imsuite/faq.html .
 
