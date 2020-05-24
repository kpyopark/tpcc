#!/bin/sh

dbclusterid=cdktpccteststack-testynjcorptpcctestvpctpccaurora-1q875s8o4wn8u
# cluster_desc=`aws rds describe-db-clusters --db-cluster-identifier ${dbclusterid}`
# endpoint=`echo ${cluster_desc} | jq '.DBClusters[0].Endpoint' | sed 's/"//g'`
# master_ip=`nslookup ${endpoint} | grep Address | grep -v "#53" | awk '{print $2}'`
# rr_id=`echo ${cluster_desc} | jq '.DBClusters[0].DBClusterMembers[] | select(.IsClusterWriter==false).DBInstanceIdentifier'`

exit

retries=5,6,8,10,12,15

for rtoCount in retries
do
  # 0. retrieve master_ip address and read replica node id for blocking and fail-over operation.
  cluster_desc=`aws rds describe-db-clusters --db-cluster-identifier ${dbclusterid}`
  endpoint=`echo ${cluster_desc} | jq '.DBClusters[0].Endpoint' | sed 's/"//g'`
  master_ip=`nslookup ${endpoint} | grep Address | grep -v "#53" | awk '{print $2}'`
  rr_id=`echo ${cluster_desc} | jq '.DBClusters[0].DBClusterMembers[] | select(.IsClusterWriter==false).DBInstanceIdentifier'`

  # 1. setup tcp OS parameters. 
  sysctl -w net.ipv4.tcp_retries2=${rtoCount}

  # 2. start tpcc benchmark program
  java -classpath target/tpcc-1.0.0-SNAPSHOT-jar-with-dependencies.jar com.codefutures.tpcc.Tpcc 2> output_${rtoCount}.txt 1> output_${rtoCount}.txt &
  pid=$!

  # 3. Sleep 60 seconds before to block master_ip packet in/out.
  sleep 60

  # 4. add rule chain in iptables to block RDS master-ip (To simulate underlying host crash)
  iptables -A INPUT -s ${master_ip} -j DROP
  iptables -A OUTPUT -d ${master_ip} -j DROP

  # 5. To simulate monitoring delay, sleep 15 seconds. 
  sleep 15

  # 6. Change master node to RR to simulate fail-over under host failure. 
  aws rds failover-db-cluster --db-cluster-identifier ${dbclusterid} --target-db-instance-identifier ${rr_id}

  # 7. Wait to till the benchmark application would be finished.
  wait ${pid}

done

