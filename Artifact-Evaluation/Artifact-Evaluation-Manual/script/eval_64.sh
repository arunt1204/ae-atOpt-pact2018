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

scp ./script/timestamp_64.sh $HOME_PATH
scp ./script/speedup_64.sh $HOME_PATH

#condition to check the benchmark name is correct ot not



if [ "$4" = "timestamp" ]; then
	if [ "$BENCHMARK" = "all" ]; then
		$BASHPATH timestamp_64.sh "bfsBellmanFord" $HOST1 $HOST2 $HOME_PATH > ./Results/64_cores/all_kernels_timestamp.txt
		$BASHPATH timestamp_64.sh "bfsDijkstra" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_timestamp.txt
		$BASHPATH timestamp_64.sh "byzantine" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_timestamp.txt
		$BASHPATH timestamp_64.sh "dijkstraRouting" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_timestamp.txt
		$BASHPATH timestamp_64.sh "dominatingSet" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_timestamp.txt
		$BASHPATH timestamp_64.sh "kcommitte" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_timestamp.txt
		$BASHPATH timestamp_64.sh "leader_elect_dp" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_timestamp.txt
		$BASHPATH timestamp_64.sh "leader_elect_hs" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_timestamp.txt
		$BASHPATH timestamp_64.sh "leader_elect_lcr" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_timestamp.txt
		$BASHPATH timestamp_64.sh "mis" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_timestamp.txt
		$BASHPATH timestamp_64.sh "mst" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_timestamp.txt
		$BASHPATH timestamp_64.sh "vertexColoring" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_timestamp.txt
	elif [ "$BENCHMARK" = "bfsBellmanFord" ] || [ "$BENCHMARK" = "bfsDijkstra" ] || [ "$BENCHMARK" = "dominatingSet" ] || [ "$BENCHMARK" = "kcommitte" ] || [ "$BENCHMARK" = "leader_elect_dp" ] || 	[ "$BENCHMARK" = "mis" ] || [ "$BENCHMARK" = "mst" ] || [ "$BENCHMARK" = "dijkstraRouting" ] || [ "$BENCHMARK" = "vertexColoring" ] || [ "$BENCHMARK" = "byzantine" ] || [ "$BENCHMARK" = 	"leader_elect_hs" ] || [ "$BENCHMARK" = "leader_elect_lcr" ]; then
		FILE_TEMP1=$BENCHMARK"_timestamp.txt"
		$BASHPATH timestamp_64.sh $BENCHMARK $HOST1 $HOST2 $HOME_PATH > ./Results/64_cores/$FILE_TEMP1
	else 
		echo "Benchmark name is incorrect"
	fi
elif [ "$4" = "speedup" ]; then
	if [ "$BENCHMARK" = "all" ]; then
		$BASHPATH speedup_64.sh "bfsBellmanFord" $HOST1 $HOST2 $HOME_PATH > ./Results/64_cores/all_kernels_speedup.txt
		$BASHPATH speedup_64.sh "bfsDijkstra" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_speedup.txt
		$BASHPATH speedup_64.sh "byzantine" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_speedup.txt
		$BASHPATH speedup_64.sh "dijkstraRouting" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_speedup.txt
		$BASHPATH speedup_64.sh "dominatingSet" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_speedup.txt
		$BASHPATH speedup_64.sh "kcommitte" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_speedup.txt
		$BASHPATH speedup_64.sh "leader_elect_dp" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_speedup.txt
		$BASHPATH speedup_64.sh "leader_elect_hs" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_speedup.txt
		$BASHPATH speedup_64.sh "leader_elect_lcr" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_speedup.txt
		$BASHPATH speedup_64.sh "vertexColoring" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_speedup.txt
		$BASHPATH speedup_64.sh "mis" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_speedup.txt
		$BASHPATH speedup_64.sh "mst" $HOST1 $HOST2 $HOME_PATH >> ./Results/64_cores/all_kernels_speedup.txt
	elif [ "$BENCHMARK" = "bfsBellmanFord" ] || [ "$BENCHMARK" = "bfsDijkstra" ] || [ "$BENCHMARK" = "dominatingSet" ] || [ "$BENCHMARK" = "kcommitte" ] || [ "$BENCHMARK" = "leader_elect_dp" ] || 	[ "$BENCHMARK" = "mis" ] || [ "$BENCHMARK" = "mst" ] || [ "$BENCHMARK" = "dijkstraRouting" ] || [ "$BENCHMARK" = "vertexColoring" ] || [ "$BENCHMARK" = "byzantine" ] || [ "$BENCHMARK" = 	"leader_elect_hs" ] || [ "$BENCHMARK" = "leader_elect_lcr" ]; then
		FILE_TEMP2=$BENCHMARK"_speedup.txt"
		$BASHPATH speedup_64.sh $BENCHMARK $HOST1 $HOST2 $HOME_PATH > ./Results/64_cores/$FILE_TEMP2
	else 
		echo "Benchmark name is incorrect"
	fi
else 
	echo "specify timestamp/speedup correctly"
fi


rm timestamp_64.sh
rm speedup_64.sh
