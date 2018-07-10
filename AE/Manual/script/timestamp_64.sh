#!/bin/bash

#runner1

KERNEL=$1
host1="test"
host2="test"
PATH_FOLDER="test"

echo "KERNEL: $KERNEL.x10"

#not part of computation
FIGURE_NAME=""
if [ "$KERNEL" = "dijkstraRouting" ]; then
	FIGURE_NAME="DR-256"
elif [ "$KERNEL" = "vertexColoring" ]; then
	FIGURE_NAME="VC-256"
elif [ "$KERNEL" = "byzantine" ]; then
	FIGURE_NAME="BY-128"
elif [ "$KERNEL" = "leader_elect_hs" ]; then
	FIGURE_NAME="HS-256"
elif [ "$KERNEL" = "leader_elect_lcr" ]; then
	FIGURE_NAME="LCR-256"
elif [ "$KERNEL" = "bfsBellmanFord" ]; then
	FIGURE_NAME="BF-256"
elif [ "$KERNEL" = "bfsDijkstra" ]; then
	FIGURE_NAME="DST-256"
elif [ "$KERNEL" = "dominatingSet" ]; then
	FIGURE_NAME="DS-256"
elif [ "$KERNEL" = "kcommitte" ]; then
	FIGURE_NAME="KC-256"
elif [ "$KERNEL" = "leader_elect_dp" ]; then
	FIGURE_NAME="DP-256"
elif [ "$KERNEL" = "vertexColoring" ]; then
	FIGURE_NAME="LCR-256"
elif [ "$KERNEL" = "mis" ]; then
	FIGURE_NAME="MIS-256"
elif [ "$KERNEL" = "mst" ]; then
	FIGURE_NAME="MST-256"
fi

if [ $# -eq 4 ]; then
	echo "execution time of IMSuite Benchmark kernel $KERNEL.x10 ($FIGURE_NAME) on two node systems"
	host1=$2
	host2=$3
	PATH_FOLDER=$4
	echo "$host1" > hostnames.txt
	echo "$host2" >> hostnames.txt
fi

if [ $# -eq 3 ]; then
	echo "execution of IMSuite Benchmark kernel $KERNEL.x10 ($FIGURE_NAME) on one node system"
	host1=$2
	PATH_FOLDER=$4
	echo "$host1" > hostnames.txt
fi

scp ./hostnames.txt ./x10-base/x10.dist/bin/
scp ./hostnames.txt ./x10-atOpt/x10.dist/bin/
rm hostnames.txt


#compiling part
KERNEL_FILE=$KERNEL".x10"
#base
cd ./x10-base/x10.dist/bin/
./x10c++ -x10rt sockets $PATH_FOLDER/IMSuite/IMSuite_timing/IMSuite_timing_x10-2.5.0/IMSuite_Iterative/X10-FA-2.5.0/$KERNEL_FILE

#AT-Opt
cd ../../..
cd ./x10-atOpt/x10.dist/bin/
./x10c++ -x10rt sockets $PATH_FOLDER/IMSuite/IMSuite_timing/IMSuite_timing_x10-2.5.0/IMSuite_Iterative/X10-FA-2.5.0/$KERNEL_FILE

echo " "

#the input file name 
if [ "$KERNEL" = "dijkstraRouting" ]; then
	INPUT_FILE="IMSuiteInput_256/input"$KERNEL"_256_-spmax_-weq.txt"
elif [ "$KERNEL" = "vertexColoring" ]; then
	INPUT_FILE="IMSuiteInput_256/input"$KERNEL"_256_-chain.txt"
elif [ "$KERNEL" = "byzantine" ]; then
	INPUT_FILE="IMSuiteInput_128/input"$KERNEL"_128_-spmax.txt"
elif [ "$KERNEL" = "leader_elect_hs" ] || [ "$KERNEL" = "leader_elect_lcr" ]; then
	INPUT_FILE="IMSuiteInput_256/input"$KERNEL"_256.txt"
else 
	INPUT_FILE="IMSuiteInput_256/input"$KERNEL"_256_-spmax.txt"
fi


#execution
for x in 2 4 8 16 32 64
do
	z=`expr 64 / $x`
	#base
	cd ../../..
	cd ./x10-base/x10.dist/bin/
	export X10_HOSTLIST=hostnames.txt
	export X10_NPLACES=$x
	export X10_NTHREADS=$z 
	echo "C$x:"
	echo "PLACE=$x"
	echo "THREADS=$z"
	echo "----------"
	echo "Base x10 compiler:"
	timeout 1800 sudo ./a.out -in $PATH_FOLDER/IMSuite/IMSuite_Input/$INPUT_FILE -ver
	echo " "
	
	#AT-Opt	
	cd ../../..
	cd ./x10-atOpt/x10.dist/bin/
	export X10_HOSTLIST=hostnames.txt
	export X10_NPLACES=$x
	export X10_NTHREADS=$z 
	echo "AT-Opt x10 compiler:"
	timeout 1800 sudo ./a.out -in $PATH_FOLDER/IMSuite/IMSuite_Input/$INPUT_FILE -ver
	echo " "
done

echo " "
echo "-----------------------------------------------------------------------------------------------"
echo " "
