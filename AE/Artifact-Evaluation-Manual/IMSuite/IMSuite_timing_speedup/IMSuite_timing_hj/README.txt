To run an HJ-FA and HJ-FAP kernel pre-installation of HJ is required.

Kernel		:	bfsBellmanFord.hj
Input File	:	inputbfsBellmanFord_128_-spar_max.txt
Output File	:	output.txt
Input Size	:	128 nodes

Compilation	: 	hjc bfsBellmanFord.hj

Execution	:
hj -places 1:128 bfsBellmanFord -in inputbfsBellmanFord_128_-spar_max.txt -out output.txt -ver


For rest of the details visit http://www.cse.iitm.ac.in/~krishna/imsuite/faq.html .
 
