#!/bin/bash

cd ..

BASHPATH=$(whereis bash | cut -f2 -d' ')
if [ ! -f $BASHPATH ]
then
	echo "Unable to find path to bash."
fi


if [ $# -lt 1 ]
then
  echo "usage: sh eval.sh <Benchmark name>"
  exit
fi

BENCHMARK=$1
HOME_PATH=`pwd`

scp ./script/ser_data.sh $HOME_PATH

#condition to check the benchmark name is correct ot not


if [ "$BENCHMARK" = "all" ]; then
	$BASHPATH ser_data.sh "bfsBellmanFord" $HOME_PATH | tee ./Results/data_serialized/all_data_serialized.txt
	$BASHPATH ser_data.sh "bfsDijkstra" $HOME_PATH | tee -a ./Results/data_serialized/all_data_serialized.txt
	$BASHPATH ser_data.sh "byzantine" $HOME_PATH | tee -a ./Results/data_serialized/all_data_serialized.txt
	$BASHPATH ser_data.sh "dijkstraRouting" $HOME_PATH | tee -a ./Results/data_serialized/all_data_serialized.txt
	$BASHPATH ser_data.sh "dominatingSet" $HOME_PATH | tee -a ./Results/data_serialized/all_data_serialized.txt	
	$BASHPATH ser_data.sh "kcommitte" $HOME_PATH | tee -a ./Results/data_serialized/all_data_serialized.txt
	$BASHPATH ser_data.sh "leader_elect_dp" $HOME_PATH | tee -a ./Results/data_serialized/all_data_serialized.txt
	$BASHPATH ser_data.sh "leader_elect_hs" $HOME_PATH | tee -a ./Results/data_serialized/all_data_serialized.txt
	$BASHPATH ser_data.sh "leader_elect_lcr" $HOME_PATH | tee -a ./Results/data_serialized/all_data_serialized.txt
	$BASHPATH ser_data.sh "mis" $HOME_PATH | tee -a ./Results/data_serialized/all_data_serialized.txt
	$BASHPATH ser_data.sh "mst" $HOME_PATH | tee -a ./Results/data_serialized/all_data_serialized.txt
	$BASHPATH ser_data.sh "vertexColoring" $HOME_PATH | tee -a ./Results/data_serialized/all_data_serialized.txt
elif [ "$BENCHMARK" = "bfsBellmanFord" ] || [ "$BENCHMARK" = "bfsDijkstra" ] || [ "$BENCHMARK" = "dominatingSet" ] || [ "$BENCHMARK" = "kcommitte" ] || [ "$BENCHMARK" = "leader_elect_dp" ] || 	[ "$BENCHMARK" = "mis" ] || [ "$BENCHMARK" = "mst" ] || [ "$BENCHMARK" = "dijkstraRouting" ] || [ "$BENCHMARK" = "vertexColoring" ] || [ "$BENCHMARK" = "byzantine" ] || [ "$BENCHMARK" = "leader_elect_hs" ] || [ "$BENCHMARK" = "leader_elect_lcr" ]; then
	FILE_TEMP1=$BENCHMARK"_data_serialized.txt"
	$BASHPATH ser_data.sh $BENCHMARK $HOME_PATH | tee ./Results/data_serialized/$FILE_TEMP1
else 
	echo "Benchmark name is incorrect"
fi

echo "The generated results will be available at folder ATHOME/Results"
rm ser_data.sh
