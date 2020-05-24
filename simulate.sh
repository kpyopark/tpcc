#!/bin/sh

dbclusterid=cdktpccteststack-testynjcorptpcctestvpctpccaurora-1q875s8o4wn8u
# cluster_desc=`aws rds describe-db-clusters --db-cluster-identifier ${dbclusterid}`
# endpoint=`echo ${cluster_desc} | jq '.DBClusters[0].Endpoint' | sed 's/"//g'`
# master_ip=`nslookup ${endpoint} | grep Address | grep -v "#53" | awk '{print $2}'`
# rr_id=`echo ${cluster_desc} | jq '.DBClusters[0].DBClusterMembers[] | select(.IsClusterWriter==false).DBInstanceIdentifier'`

retries=(5 6 8 10 12 15)

for rtoCount in "${retries[@]}"
do
  echo "Start retransmission test - retries count for ${rtoCount}."
  # 0. retrieve master_ip address and read replica node id for blocking and fail-over operation.
  cluster_desc=`aws rds describe-db-clusters --db-cluster-identifier ${dbclusterid}`
  endpoint=`echo ${cluster_desc} | jq '.DBClusters[0].Endpoint' | sed 's/"//g'`
  master_ip=`nslookup ${endpoint} | grep Address | grep -v "#53" | awk '{print $2}'`
  rr_id=`echo ${cluster_desc} | jq '.DBClusters[0].DBClusterMembers[] | select(.IsClusterWriter==false).DBInstanceIdentifier' | sed 's/"//g'`

  echo "endpoint : ${endpoint}"
  echo "master ip : ${master_ip}"
  echo "read replica id : ${rr_id}"

  # 1. setup tcp OS parameters. 
  sudo sysctl -w net.ipv4.tcp_retries2=${rtoCount}
  echo "tpc_retries2 has set ${rtnCount}"

  # 2. start tpcc benchmark program$a
  echo "start benchmark program."
  java -classpath target/tpcc-1.0.0-SNAPSHOT-jar-with-dependencies.jar com.codefutures.tpcc.Tpcc 2> output_${rtoCount}.txt 1> output_${rtoCount}.txt &
  pid=$!

  # 3. Sleep 60 seconds before to block master_ip packet in/out.
  echo "wait 60 seconds."
  sleep 60

  # 4. add rule chain in iptables to block RDS master-ip (To simulate underlying host crash)
  echo "block all packets related with db master ip."
  sudo iptables -A INPUT -s ${master_ip} -j DROP
  sudo iptables -A OUTPUT -d ${master_ip} -j DROP

  # 5. To simulate monitoring delay, sleep 15 seconds$a
  echo "sleep 15 seconds." 
  sleep 15

  # 6. Change master node to RR to simulate fail-over under host failure. 
  echo "Failover to RR instance - ${rr_id}. "
  aws rds failover-db-cluster --db-cluster-identifier ${dbclusterid} --target-db-instance-identifier ${rr_id}

  # 7. Wait to till the benchmark application would be finished.
  echo "Waiting benchmark application. ${pid}"
  wait ${pid}

  # 8. Remove drop rules in iptables
  echo "remove all drop rules in iptables."
  sudo iptables -D INPUT 1
  sudo iptables -D OUTPUT 1

  echo "End Turn"

done

