Artifact Evaluation Reproduction for "Optimizing Remote Data Transfers in X10", PACT 2018. 
------------------------------------------------------------------------------------------

Evaluation using Manual Method:
---------------------------------


This repository contains artifacts and sourcecodes
to reproduce experiments from the PACT 2018 research paper 
titled "Optimizing Remote Data Transfers in X10"

Our evalutions are done on Linux based Operation system.

This folder contains 7 sub-folders and a README.md file


Hardware pre-requisities:
-------------------------
We recommend the following architectures any of the following architectures:
* An Intel System with two nodes, each node having 32 cores (32 CPUs). Total 64 cores.
* An AMD System, with two nodes, each node having 16 cores (16 CPUs). Total 32 cores.
 

Software pre-requisites:
------------------------
1) For Manual evalution, the below required software needs to be installed in the system.
* g++ (preferred version 5.4.0)
* Apache ant software (preferred version 1.9.6 )
* Java software
	- preferred jvm jdk1.8.0_151 (or) java-8-oracle
	- After installation, set JAVA_HOME path in ~/.bashrc to point to the installed JAVA bin


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


Installation:
-------------
Ensure the required Software pre-requisites are installed in the system. Steps to download and build the compilers:

* Open a new terminal and run the below commands.

* "$ git clone https://github.com/arunt1204/ae-atOpt-pact2018.git"

* Let $ATHOME = path to ae-atOpt-pact2018 folder/Artifact-Evaluation/Artifact-Evaluation-Manual

* In terminal enter $ cd $ATHOME/script

* Building base x10 compilers:
	-  "$ bash build_x10_base.sh". 
	-  "$ bash build_x10_base_SB.sh".
	-  Average Build time should take around 14-15 minutes.

* Building AT-Opt x10 compilers:
	-  "$ bash build_x10_AT_Opt.sh". 
	-  "$ bash build_x10_AT_Opt_SB.sh".
	-  Average Build time should take around 14-15 minutes.


Running the Evaluations:
------------------------
* Open a new terminal and run the below commands. "$ cd $ATHOME/script".

* For establishing the impact of AT-Opt, when the programs are run on a 64 core machine (similar to the setup used for Figure 15a): "$ sudo bash eval_64.sh <kernel_name> <HOST1_NAME> <HOST2_NAME> <timestamp or speedup>"
	- eg1: $ sudo bash eval_64.sh all localhost localhost speedup  
	- eg2: $ sudo bash eval_64.sh bfsBellmanFord intel1 intel2 speedup
	- eg3: $ sudo bash eval_64.sh mis intel1 intel2 timestamp
	- On our personal laptop (intel-i7 processor, 4 cores and 16GB RAM), the script took around one hour to complete.

* For establishing the impact of AT-Opt, when the programs are run on a 32 core machine (similar to the setup used for Figure 15b): "$ sudo bash eval_64.sh <kernel_name> <HOST1_NAME> <HOST2_NAME> <timestamp or speedup>"
	- eg1: $ sudo bash eval_32.sh all localhost localhost speedup
	- eg2: $ sudo bash eval_32.sh bfsDijkstra amd1 amd2 speedup 
	- eg3: $ sudo bash eval_32.sh mst amd1 amd2 timestamp
	- On our laptop, the script took around 40 minute to complete.

* To establish the impact of AT-Opt when the programs are run on a single node 32 core machine: "$ sudo ./eval_32_one.sh <kernel_name> <HOST1_NAME> <timestamp or speedup>"
	- eg1: $ sudo bash eval_32_one.sh all localhost speedup

* To establish the results shown in Figure 14: "$ sudo bash eval_ser_data.sh all". 
	- eg1: $ sudo bash eval_ser_data.sh bfsBellmanFord
	- eg2: $ sudo bash eval_ser_data.sh all
	- On our laptop, the script took around five hours to complete for all kernels.

* For the sake of convenience, we have kept the generated result's files at $ATHOME/sample-results


Note: Two nodes (intel1 and intel2 (or) amd1 and amd2) should be able to communicate each other 
without any permission requests. Reason: During kernel executions program control shifts between these
two nodes. Hence, care should be taken such that permission request/denied shoudn't be prompted to make control shits. 


Validation of results:
----------------------
The output files are available in the folder /Artifact-Evaluation-for-AT-Opt-PACT-2018-paper/Artifact-Evaluation-VM/Results.

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
		
