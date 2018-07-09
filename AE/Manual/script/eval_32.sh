#!/bin/bash

cd ..

BASHPATH=$(whereis bash | cut -f2 -d' ')
if [ ! -f $BASHPATH ]
then
	echo "Unable to find path to bash."
fi


if [ $# -lt 4 ]
then
  echo "usage: sh eval.sh <Benchmark name> <host_name_1> <host_name_2> <time/speedup>"
  exit
fi

BENCHMARK=$1
HOST1=$2
HOST2=$3
HOME_PATH=`pwd`

scp ./script/timestamp_32.sh $HOME_PATH
scp ./script/speedup_32.sh $HOME_PATH

#condition to check the benchmark name is correct ot not



if [ "$4" = "timestamp" ]; then
	if [ "$BENCHMARK" = "all" ]; then
		echo "Executing for bfsBellmanFord:"
		$BASHPATH timestamp_32.sh "bfsBellmanFord" $HOST1 $HOST2 $HOME_PATH | tee ./Results/32_cores/all_kernels_timestamp.txt
		echo "Executing for bfsDijkstra: "
		$BASHPATH timestamp_32.sh "bfsDijkstra" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_kernels_timestamp.txt
		echo "Executing for byzantine: "
		$BASHPATH timestamp_32.sh "byzantine" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_kernels_timestamp.txt	
		echo "Executing for dijkstraRouting: "	
		$BASHPATH timestamp_32.sh "dijkstraRouting" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_kernels_timestamp.txt	
		echo "Executing for dominatingSet: "	
		$BASHPATH timestamp_32.sh "dominatingSet" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_kernels_timestamp.txt	
		echo "Executing for kcommitte: "	
		$BASHPATH timestamp_32.sh "kcommitte" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_kernels_timestamp.txt
		echo "Executing for leader_elect_dp: "
		$BASHPATH timestamp_32.sh "leader_elect_dp" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_kernels_timestamp.txt
		echo "Executing for leader_elect_hs: "
		$BASHPATH timestamp_32.sh "leader_elect_hs" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_kernels_timestamp.txt
		echo "Executing for leader_elect_lcr: "
		$BASHPATH timestamp_32.sh "leader_elect_lcr" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_kernels_timestamp.txt
		echo "Executing for mis: "
		$BASHPATH timestamp_32.sh "mis" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_kernels_timestamp.txt
		echo "Executing for mst: "
		$BASHPATH timestamp_32.sh "mst" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_kernels_timestamp.txt	
		echo "Executing for vertexColoring: "	
		$BASHPATH timestamp_32.sh "vertexColoring" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_kernels_timestamp.txt
	elif [ "$BENCHMARK" = "bfsBellmanFord" ] || [ "$BENCHMARK" = "bfsDijkstra" ] || [ "$BENCHMARK" = "dominatingSet" ] || [ "$BENCHMARK" = "kcommitte" ] || [ "$BENCHMARK" = "leader_elect_dp" ] || 	[ "$BENCHMARK" = "mis" ] || [ "$BENCHMARK" = "mst" ] || [ "$BENCHMARK" = "dijkstraRouting" ] || [ "$BENCHMARK" = "vertexColoring" ] || [ "$BENCHMARK" = "byzantine" ] || [ "$BENCHMARK" = 	"leader_elect_hs" ] || [ "$BENCHMARK" = "leader_elect_lcr" ]; then
		FILE_TEMP1=$BENCHMARK"_timestamp.txt"
		echo "Executing for $BENCHMARK:"
		$BASHPATH timestamp_32.sh $BENCHMARK $HOST1 $HOST2 $HOME_PATH | tee ./Results/32_cores/$FILE_TEMP1
	else 
		echo "Benchmark name is incorrect"
	fi
elif [ "$4" = "speedup" ]; then
	if [ "$BENCHMARK" = "all" ]; then
		echo "Executing for bfsBellmanFord:"
		$BASHPATH speedup_32.sh "bfsBellmanFord" $HOST1 $HOST2 $HOME_PATH > ./Results/32_cores/all_speedup.txt
		echo "Executing for bfsDijkstra:"
		$BASHPATH speedup_32.sh "bfsDijkstra" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_speedup.txt
		echo "Executing for byzantine:"
		$BASHPATH speedup_32.sh "byzantine" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_speedup.txt
		echo "Executing for dijkstraRouting:"
		$BASHPATH speedup_32.sh "dijkstraRouting" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_speedup.txt
		echo "Executing for dominatingSet:"
		$BASHPATH speedup_32.sh "dominatingSet" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_speedup.txt
		echo "Executing for kcommitte:"
		$BASHPATH speedup_32.sh "kcommitte" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_speedup.txt
		echo "Executing for leader_elect_dp:"
		$BASHPATH speedup_32.sh "leader_elect_dp" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_speedup.txt
		echo "Executing for leader_elect_hs:"	
		$BASHPATH speedup_32.sh "leader_elect_hs" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_speedup.txt
		echo "Executing for leader_elect_lcr:"
		$BASHPATH speedup_32.sh "leader_elect_lcr" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_speedup.txt
		echo "Executing for mis:"
		$BASHPATH speedup_32.sh "mis" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_speedup.txt
		echo "Executing for mst:"
		$BASHPATH speedup_32.sh "mst" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_speedup.txt
		echo "Executing for vertexColoring:"
		$BASHPATH speedup_32.sh "vertexColoring" $HOST1 $HOST2 $HOME_PATH | tee -a ./Results/32_cores/all_speedup.txt
	elif [ "$BENCHMARK" = "bfsBellmanFord" ] || [ "$BENCHMARK" = "bfsDijkstra" ] || [ "$BENCHMARK" = "dominatingSet" ] || [ "$BENCHMARK" = "kcommitte" ] || [ "$BENCHMARK" = "leader_elect_dp" ] || 	[ "$BENCHMARK" = "mis" ] || [ "$BENCHMARK" = "mst" ] || [ "$BENCHMARK" = "dijkstraRouting" ] || [ "$BENCHMARK" = "vertexColoring" ] || [ "$BENCHMARK" = "byzantine" ] || [ "$BENCHMARK" = 	"leader_elect_hs" ] || [ "$BENCHMARK" = "leader_elect_lcr" ]; then
		FILE_TEMP2=$BENCHMARK"_speedup.txt"
		echo "Executing for $BENCHMARK:"
		$BASHPATH speedup_32.sh $BENCHMARK $HOST1 $HOST2 $HOME_PATH > ./Results/32_cores/$FILE_TEMP2
	else 
		echo "Benchmark name is incorrect"
	fi
else 
	echo "specify timestamp/speedup correctly"
fi

echo "The generated results will be available at folder ATHOME/Results"

rm timestamp_32.sh
rm speedup_32.sh
