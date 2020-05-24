Java TPC-C
==========

This project is a Java implementation of the TPC-C benchmark.
This project is modified to use mariadb jdbc datasource and to test mariadb jdbc fail-over transition time.

I add a test script (simulate.sh)

Before to test fail-over transition time, you should modify tpcc.properties and simulate.sh files. 
You should set aws credentials or set EC2 instance profile(Role) to execute 'aws rds describe-cluster' and 'aws rds failover-db-cluster' apis.

And simulate.sh script includes some 'sudo' commands to simulate underlying host failure.
So, you should execute this script like below.

<code>
$ sudo -u ec2-user ./simulate.sh
</code>

This script will make 6 output_{$tcp_retries}.txt files. 


=========
Compiling
=========

Use this command to compile the code and produce a fat jar.

```
mvn package assembly:single
```

========
Database
========

To create the tpcc schema in MySQL:

```
cd database
mysql -u root
> create database tpcc;
> use tpcc;
> source create_tables.sql
> source add_fkey_idx.sql
```

It is possible to load data without the foreign keys and indexes in place and then add those
after loading data to improve loading times.

=================================
Generating and loading TPC-C data
=================================

Data can be loaded directly into a MySQL instance and can also be generated to CSV files that
can be loaded into MySQL later using LOAD DATA INFILE.

In `tpcc.properties` set the MODE to either CSV or JDBC.

To run the load process:

```
java -classpath target/tpcc-1.0.0-SNAPSHOT-jar-with-dependencies.jar com.codefutures.tpcc.TpccLoad
```

It is possible to load data into shards where the warehouse ID is used as a shard key. The
SHARDCOUNT and SHARDID properties must be set correctly when generating or loading data.

This option requires the use of a JDBC driver that supports automatic sharding, such as
dbShards (http://www.dbshards.com).

===========================
Running the TPC-C Benchmark
===========================

Review the TPC-C settings in `tpcc.properties`, then run this command To run the tpcc benchmarks:

```
java -classpath target/tpcc-1.0.0-SNAPSHOT-jar-with-dependencies.jar com.codefutures.tpcc.Tpcc
```

Bugs can be reported to support@codefutures.com.

(c) 2014 CodeFutures Corporation.
