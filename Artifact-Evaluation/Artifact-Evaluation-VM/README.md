Artifact Evaluation Reproduction for "Optimizing Remote Data Transfers in X10", PACT 2018. 
------------------------------------------------------------------------------------------

Evaluation using Virtual Machine:
---------------------------------


This repository contains artifacts and sourcecodes
to reproduce experiments from the PACT 2018 research paper 
titled "Optimizing Remote Data Transfers in X10"

Our evalutions are done on Linux based Operation system.

This folder contains a Linux (Ubuntu 16.04) virtual image and a README.md file


Hardware pre-requisities:
-------------------------
Any of the following architectures:
* Two Intel-based System with each system having 32 cores (32 CPUs). Total 64 cores.
* Two AMD-based System with each system having 16 cores (16 CPUs). Total 32 cores.
 

Software pre-requisites:
------------------------
1) For Evaluation using Virtual Machine required software are already installed in the 
provided image.
A VirtualBox is required to install the image and do evaluation.


Installation:
-------------
1) Create a Virtual Machine via VirtualBox using the image available in the Googleo drive (https://drive.google.com/open?id=1qn2lO5vUWRuujJ-y3cxc3-rJr_G9yq_4).

2) Login Credentials for Virtual Machine:
	- User Name: ubuntu-16
	- Password: admin123


Some notations:
---------------
* base -> the x10 base compiler which does not apply the techniques discussed in the paper.
* AT-Opt -> the x10 at-Opt compiler which apply the techniques discussed in the paper.
* Host1 -> hostname of the first system.
* Host2 -> hostname of the second system.
* kernel_name -> IMSuite benchmark kernel names.
	- bfsBellmanFord (BFS)
	- bfsDijkstra (DST)
	- kcommitte (KC)
	- leader_elect_dp (DP)
	- mis
	- mst
	- leader_elect_hs (HS)
	- leader_elect_lcr (LCR)
	- dijkstraRouting (DR)
	- vertexColoring (VC)
	- byzantine (BY)
	- dominatingSet (DS)
	- all ~ denotes all the kernels
		
* timestamp -> denotes the type of output generation. It generates output file for a kernel and it 
		contains the start, end and total time taken for execution.
* Start Time -> Time at which the kernel starts executing.
* Finish Time -> Time at which the kernel finishes executing.
* Estimated Time -> Total time taken to execute the kernel.
* speedup -> denotes the type of output generation. It generates output file for a kernel and it 
		      contains total time taken for execution, and speedup(s) obtained.


Running the Evaluations:
------------------------
Both the "base" and "at-Opt" compilers were build already in the Virtual Machine. Hence, we now directly 
proceed for the kernel evaluation.


To run for two systems with total cores 64 (for Figure 15a in paper):
* Open a new terminal. Run the command 1 in terminal. "$ cd ./Documents/Artifact-Evaluation-VM/script".

* Run the script "eval_64.sh" using the command $ sudo bash eval_64.sh <kernel_name> <HOST1_NAME> <HOST2_NAME> <timestamp or speedup>
	- eg1: $ sudo bash eval_64.sh bfsBellmanFord intel1 intel2 speedup
	- eg2: $ sudo bash eval_64.sh all localhost localhost speedup
	- eg3: $ sudo bash eval_64.sh mis intel1 intel2 timestamp
 


To run for two systems with total cores 32 (for Figure 15b in paper):
* Open a new terminal. Run the command in 1 terminal. "$ cd ./Documents/Artifact-Evaluation-VM/script".

* Run the script "eval_32.sh" using the command $ sudo bash eval_64.sh <kernel_name> <HOST1_NAME> <HOST2_NAME> <timestamp or speedup>
	- eg1: $ sudo bash eval_32.sh bfsDijkstra amd1 amd2 speedup
	- eg2: $ sudo bash eval_32.sh all localhost localhost speedup
	- eg3: $ sudo bash eval_32.sh mst amd1 amd2 timestamp



To calculate the total amount of data serialized acrossed AT calls (for Figure 14 in paper):
* Open a new terminal. Run the command 1 in terminal. "$ cd ./Documents/Artifact-Evaluation-VM/script".

* Run the command 2 in the terminal. "$ sudo bash eval_ser_data.sh <kernel_name>"
	- eg1: $ sudo bash eval_ser_data.sh bfsBellmanFord
	- eg2: $ sudo bash eval_ser_data.sh all

* Few kernels ( like BY, DR, DS, HS, LCR and MST) have more amount of "AT" calls. Hence, the 
total amount of time to calculate the amount of serialized data for all kernels will take an average time
of 5-6 hours.


Note: Two systems (intel1 and intel2 (or) amd1 and amd2) should be able to communicate each other 
without any permission requests. Reason: During kernel executions program control shifts between these
two nodes. Hence, care should be taken such that permission request/denied shoudn't be prompted to make control shits. 	

Validation of results:
----------------------
The output files are available in the folder /Documents/Artifact-Evaluation-VM/Result.

(i) A kernel is evaluated for varying number of places from C2 to C64 (for 64 total cores) or C2 to C32 (for 32 total cores).

(ii) "64/32_cores" folder: <kernel_name>_timestamp.txt file shows the start time, finish time and total execution time of a kernel for varying places (C2 to C64).

(iii) "64/32_cores" folder: <kernel_name>_speedup.txt file shows total execution time of a kernel and obtained speedups for varying places (C2 to C64) as shown in the figure 15a and 15b.

(iv) "data_serialized" folder: <kernel_name>_data_serialized.txt file shows the total "AT" calls made during run-time, and total amount of data serialized for base and AT-Opt x10 compiler.


Notes/Troubleshooting:
----------------------
* We recommend not to run scripts in parallel.

* If you don't have the required Hardware pre-requisities,
	- For no two systems available: give both <HOST1_NAME> and <HOST2_NAME> value as "localhost".
	- Lesser cores (CPUs) in a system: run the same command as it is. It may have an impact on the obtained speedup shown in the paper.

* For some cases, while running the kernels the compiler may throw "host not found" or "place exceptions" 
in both the x10 base and x10 AT-Opt compiler. It is a noted bug in the existing compiler. 
Hence, for such cases re-run the script for the individual kernel alone.

* We recommend to re-run the script for the individual kernel for other issues as well.



If anything in unclear, or any unexpected results occur, please report it to the authors.				
		