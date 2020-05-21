package com.codefutures.tpcc;

import java.sql.Connection;

import javax.sql.DataSource;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class TpccThread extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(TpccThread.class);
    private static final boolean DEBUG = logger.isDebugEnabled();

    /**
     * Dedicated JDBC connection for this thread.
     */
    Driver driver;
    Connection conn;
    DataSource ds;

    int number;
    int is_local;
    int num_ware;
    int num_conn;
    int fetchSize;

    private int[] success;
    private int[] late;
    private int[] retry;
    private int[] failure;

    private int[][] success2;
    private int[][] late2;
    private int[][] retry2;
    private int[][] failure2;

    private boolean joins;

    //TpccStatements pStmts;

    public TpccThread(int number,
                      int num_ware, int num_conn, DataSource ds , int fetchSize,
                      int[] success, int[] late, int[] retry, int[] failure,
                      int[][] success2, int[][] late2, int[][] retry2, int[][] failure2, boolean joins) {

        this.number = number;
        this.num_conn = num_conn;
        this.num_ware = num_ware;
        this.fetchSize = fetchSize;

        this.success = success;
        this.late = late;
        this.retry = retry;
        this.failure = failure;

        this.success2 = success2;
        this.late2 = late2;
        this.retry2 = retry2;
        this.failure2 = failure2;
        this.joins = joins;
        
        // Create a driver instance.
        driver = new Driver(ds, fetchSize,
                success, late, retry, failure,
                success2, late2, retry2, failure2, joins);

    }

    public void run() {

        try {
            if (DEBUG) {
                logger.debug("Starting driver with: number: " + number + " num_ware: " + num_ware + " num_conn: " + num_conn);
            }

            driver.runTransaction(number, num_ware, num_conn);

        } catch (Throwable e) {
            logger.error("Unhandled exception", e);
        }

    }

}

