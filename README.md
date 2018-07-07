# Artifact-Evaluation-for-AT-Opt-PACT-2018-paper
Artifact Evaluation for research paper "Optimizing Remote Data Transfers in X10", PACT 2018.

Artifact Evaluation Reproduction for "Optimizing Remote Data Transfers in X10", PACT 2018. 
------------------------------------------------------------------------------------------

This repository contains artifacts and sourcecodes
to reproduce experiments from the PACT 2018 research paper 
titled "Optimizing Remote Data Transfers in X10"

Our evalutions are done on Linux based Operation system.


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

2) For Manual evalution, the below required software needs to be installed in any one of the system.
* g++ (preferred version 5.4.0)
* Apache ant software (preferred version 1.9.6 )
* Java software
	- preferred jvm jdk1.8.0_151 (or) java-8-oracle
	- After installation, set JAVA_HOME path in ~/.bashrc to point to the installed JAVA bin


Benchmarks:
-----------
1) Taken from IMSuite Benchmark kernels (http://www.cse.iitm.ac.in/~krishna/imsuite/). Already, included in the package. 


Installation, Execution and Validation of results:
--------------------------------------------------
1) For Evaluation using Virtual Machine see README.md file in ./Artifact-Evaluation-VM folder

2) For Evaluation using Manual method see README.md file in ./Artifact-Evaluation-Manual folder

3) For Evaluating AT-Opt technique againt varing input sizes (not discussed in the paper), look at 
web page http://www.cse.iitm.ac.in/~krishna/imsuite/. In the ./Artifact-Evaluation-VM folder,  "x10-base" folder is 
the baseline compiler and "x10-atOpt" folder is the AT-Opt compiler (has our techniques implemented).  


Troubleshooting:
----------------
(1) For some cases, while running the kernels the compiler may throw "host not found" or "place exceptions" 
in both the x10 base and x10 AT-Opt compiler. It is a noted bug in the existing compiler. 
Hence, for such cases re-run the script for the individual kernel alone.


If anything in unclear, or any unexpected results occur, please report it to the authors.
