
#speedup1

KERNEL=$1
PATH_FOLDER=$2

echo "KERNEL: $KERNEL.x10"
echo "Amount of serialized data across all place-change operations of IMSuite Benchmark kernel $KERNEL.x10"

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
