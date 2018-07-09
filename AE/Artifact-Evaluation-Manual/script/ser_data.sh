
#speedup1

KERNEL=$1
PATH_FOLDER=$2

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

echo "Amount of serialized data across all place-change operations of IMSuite Benchmark kernel $KERNEL.x10 (FIGURE_NAME)"

#compiling part
KERNEL_FILE=$KERNEL".x10"

#base
cd ./x10-base_SB/x10.dist/bin/
./x10c $PATH_FOLDER/IMSuite/IMSuite_timing_data/IMSuite_timing_x10-2.5.0/IMSuite_Iterative/X10-FA-2.5.0/$KERNEL_FILE

#AT-Opt
cd ../../..
cd ./x10-atOpt_SB/x10.dist/bin/
./x10c $PATH_FOLDER/IMSuite/IMSuite_timing_data/IMSuite_timing_x10-2.5.0/IMSuite_Iterative/X10-FA-2.5.0/$KERNEL_FILE

cd ../../..

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
	cd ./x10-base_SB/x10.dist/bin/
	export X10_NPLACES=1
	export X10_NTHREADS=1

	timeout 1800 ./x10 $KERNEL -in $PATH_FOLDER/IMSuite/IMSuite_Input/$INPUT_FILE > base_temp.txt
	
	DATA_BYTES=0;
	DYNAMIC_CALLS=0;
	while IFS='' read -r line || [ -n "$line" ]; do
		DATA_BYTES=`expr $DATA_BYTES + $line`
		DYNAMIC_CALLS=`expr $DYNAMIC_CALLS + 1`
	done < "base_temp.txt"

	DATA_BYTES=$( echo "scale=2;$DATA_BYTES / 1073741824" | bc )
	
	echo "Total Dynamic AT calls made: $DYNAMIC_CALLS"
	echo " "
	echo "Amount of data serialized in Base x10 compiler: $DATA_BYTES GB"
	
	echo " "
	rm base_temp.txt
	rm *.class
	rm *.java
	
	#AT-Opt	
	cd ../../..
	cd ./x10-atOpt_SB/x10.dist/bin/
	export X10_NPLACES=1
	export X10_NTHREADS=1 

	timeout 1800 ./x10 $KERNEL -in $PATH_FOLDER/IMSuite/IMSuite_Input/$INPUT_FILE > atOpt_temp.txt
	
	DATA_BYTES1=0;

	while IFS='' read -r line || [ -n "$line" ]; do
		DATA_BYTES1=`expr $DATA_BYTES1 + $line`
	done < "atOpt_temp.txt"

	DATA_BYTES1=$( echo "scale=2;$DATA_BYTES1 / 1073741824" | bc )
	
	echo "Amount of data serialized in AT-Opt x10 compiler: $DATA_BYTES1 GB"

	rm atOpt_temp.txt
	rm *.class
	rm *.java
	echo " "

echo "-----------------------------------------------------------------------------------------------"
echo " "
