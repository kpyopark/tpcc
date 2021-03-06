package com.codefutures.tpcc;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.slf4j.LoggerFactory;
import org.mariadb.jdbc.MariaDbPoolDataSource;
import org.slf4j.Logger;

public class Tpcc implements TpccConstants {

    private static final Logger logger = LoggerFactory.getLogger(Tpcc.class);
    private static final boolean DEBUG = logger.isDebugEnabled();

    public static final String VERSION = "1.0.1";

    private static final String DRIVER = "DRIVER";
    private static final String DATASOURCE = "DATASOURCE";
    private static final String WAREHOUSECOUNT = "WAREHOUSECOUNT";
    // private static final String DATABASE = "DATABASE";
    private static final String USER = "USER";
    private static final String PASSWORD = "PASSWORD";
    private static final String CONNECTIONS = "CONNECTIONS";
    private static final String RAMPUPTIME = "RAMPUPTIME";
    private static final String DURATION = "DURATION";
    private static final String JDBCURL = "JDBCURL";
    // private static final String JOINS = "JOINS";

    private static final String PROPERTIESFILE = "tpcc.properties";

    /* Global SQL Variables */

    private String jdbcDriver;
    private String jdbcDataSource;
    private String jdbcUrl;
    private String dbUser;
    private String dbPassword;
    private final boolean joins = true;

    private int numWare;
    private int numConn;
    private int rampupTime;
    private int measureTime;
    private int fetchSize = 100;

    private int num_node; /* number of servers that consists of cluster i.e. RAC (0:normal mode) */
    private static final String TRANSACTION_NAME[] = { "NewOrder", "Payment", "Order Stat", "Delivery", "Slev" };

    private final int[] success = new int[TRANSACTION_COUNT];
    private final int[] late = new int[TRANSACTION_COUNT];
    private final int[] retry = new int[TRANSACTION_COUNT];
    private final int[] failure = new int[TRANSACTION_COUNT];

    private int[][] success2;
    private int[][] late2;
    private int[][] retry2;
    private int[][] failure2;
    public static volatile boolean counting_on = false;

    private final int[] success2_sum = new int[TRANSACTION_COUNT];
    private final int[] late2_sum = new int[TRANSACTION_COUNT];
    private final int[] retry2_sum = new int[TRANSACTION_COUNT];
    private final int[] failure2_sum = new int[TRANSACTION_COUNT];

    private final int[] prev_s = new int[5];
    private final int[] prev_l = new int[5];

    private final double[] max_rt = new double[5];

    private Properties properties;
    private InputStream inputStream;

    public static volatile int activate_transaction = 0;

    private Properties jdbcProps = null;
    DataSource ds = null;

    public Tpcc() {
        // Empty.
    }

    private void init() {
        logger.info("Loading properties from: " + PROPERTIESFILE);

        properties = new Properties();
        try {
            inputStream = new FileInputStream(PROPERTIESFILE);
            properties.load(inputStream);
        } catch (final IOException e) {
            throw new RuntimeException("Error loading properties file", e);
        }

    }

    /**
     * It's only used in the Driver Mode. In the DataSource mode, only jdbcUrl properties will be used.
     * @return
     */
    private Properties makeJdbcDriverProperties() {
        this.jdbcProps = new Properties();
        jdbcProps.setProperty("user", dbUser);
        jdbcProps.setProperty("password", dbPassword);
        jdbcProps.setProperty("useServerPrepStmts", "true");
        jdbcProps.setProperty("cachePrepStmts", "true");

        final File connPropFile = new File("conf/jdbc-connection.properties");
        if (connPropFile.exists()) {
            logger.info("Loading JDBC connection properties from " + connPropFile.getAbsolutePath());
            try {
                final FileInputStream is = new FileInputStream(connPropFile);
                jdbcProps.load(is);
                is.close();

                if (logger.isDebugEnabled()) {
                    logger.debug("Connection properties: {");
                    final Set<Map.Entry<Object, Object>> entries = jdbcProps.entrySet();
                    for (final Map.Entry<Object, Object> entry : entries) {
                        logger.debug(entry.getKey() + " = " + entry.getValue());
                    }

                    logger.debug("}");
                }

            } catch (final IOException e) {
                logger.error("", e);
            }
        } else {
            logger.warn(connPropFile.getAbsolutePath() + " does not exist! Using default connection properties");
        }
        return this.jdbcProps;
    }

    private DataSource makeDataSource() {
        if( jdbcDataSource != null ) {
            try {
                Constructor<DataSource> cstr = (Constructor<DataSource>)Class.forName(jdbcDataSource).getConstructor(new Class[0]);
                this.ds = cstr.newInstance();
                // Settting url / dbuser / password
                // TODO : replace below codes with DataSourceBuilder class. 
                if(this.ds instanceof org.mariadb.jdbc.MariaDbPoolDataSource) {
                    MariaDbPoolDataSource mpds = (MariaDbPoolDataSource)this.ds;
                    mpds.setUrl(this.jdbcUrl);
                    mpds.setUser(this.dbUser);
                    mpds.setPassword(this.dbPassword);
                }
            } catch (Exception e) {
                throw new RuntimeException(e.toString());
            }
            return this.ds;
        } else {
            return this.ds = new SimpleDriverDelegatorDataSource(jdbcDriver, this.jdbcUrl, this.jdbcProps);
        }
    }

    private void testDataSource() {
        int maxConTest = 5;
        int conPos = 0;
        Connection con[] = new Connection[maxConTest];
        try {
            for(; conPos < maxConTest; conPos++) {
                con[conPos] = this.ds.getConnection();
            }
            for (; conPos < maxConTest; conPos++) {
                con[conPos].close();
                con[conPos] = null;
            }
        } catch (Exception e) {
            logger.error(e.toString());
            throw new RuntimeException("Multi connections can't be established. ");
        } finally {
            conPos = 0;
            for (; conPos < maxConTest; conPos++) {
                try {
                    con[conPos].close();
                } catch (Exception nne) { nne.printStackTrace(); } 
                con[conPos] = null;
            }
        }
    }

    private int runBenchmark(final boolean overridePropertiesFile, final String[] argv) {

        System.out.println("***************************************");
        System.out.println("****** Java TPC-C Load Generator ******");
        System.out.println("***************************************");

        /* initialize */
        RtHist.histInit();
        activate_transaction = 1;


        for (int i = 0; i < TRANSACTION_COUNT; i++) {
            success[i] = 0;
            late[i] = 0;
            retry[i] = 0;
            failure[i] = 0;

            prev_s[i] = 0;
            prev_l[i] = 0;

            max_rt[i] = 0.0;
        }
        
        /* number of node (default 0) */
        num_node = 0;

        if (overridePropertiesFile) {
            for (int i = 0; i < argv.length; i = i + 2) {
                if (argv[i].equals("-u")) {
                    dbUser = argv[i + 1];
                } else if (argv[i].equals("-p")) {
                    dbPassword = argv[i + 1];
                } else if (argv[i].equals("-w")) {
                    numWare = Integer.parseInt(argv[i + 1]);
                } else if (argv[i].equals("-c")) {
                    numConn = Integer.parseInt(argv[i + 1]);
                } else if (argv[i].equals("-r")) {
                    rampupTime = Integer.parseInt(argv[i + 1]);
                } else if (argv[i].equals("-t")) {
                    measureTime = Integer.parseInt(argv[i + 1]);
                } else if (argv[i].equals("-j")) {
                    jdbcDriver = argv[i + 1];                
                } else if (argv[i].equals("-d")) {
                        jdbcDriver = argv[i + 1];
                } else if (argv[i].equals("-l")) {
                    jdbcUrl = argv[i + 1];
                } else if (argv[i].equals("-f")) {
                    fetchSize = Integer.parseInt(argv[i + 1]);
//                } else if (argv[i].equals("-J")) {
//                    joins = Boolean.parseBoolean(argv[i + 1]);
                } else {
                    System.out.println("Incorrect Argument: " + argv[i]);
                    System.out.println("The possible arguments are as follows: ");
                    System.out.println("-h [database host]");
                    System.out.println("-d [database name]");
                    System.out.println("-u [database username]");
                    System.out.println("-p [database password]");
                    System.out.println("-w [number of warehouses]");
                    System.out.println("-c [number of connections]");
                    System.out.println("-r [ramp up time]");
                    System.out.println("-t [duration of the benchmark (sec)]");
                    System.out.println("-j [jdbc driver]");
                    System.out.println("-d [jdbc datasource]");
                    System.out.println("-l [jdbc url]");
                    System.out.println("-h [jdbc fetch size]");
                    System.out.println("-J [joins (true|false) default true]");
                    System.exit(-1);

                }
            }
        } else {

            dbUser = properties.getProperty(USER);
            dbPassword = properties.getProperty(PASSWORD);
            numWare = Integer.parseInt(properties.getProperty(WAREHOUSECOUNT));
            numConn = Integer.parseInt(properties.getProperty(CONNECTIONS));
            rampupTime = Integer.parseInt(properties.getProperty(RAMPUPTIME));
            measureTime = Integer.parseInt(properties.getProperty(DURATION));
            jdbcDriver = properties.getProperty(DRIVER);
            jdbcDataSource = properties.getProperty(DATASOURCE);
            jdbcUrl = properties.getProperty(JDBCURL);
            final String jdbcFetchSize = properties.getProperty("JDBCFETCHSIZE");
            //joins = Boolean.parseBoolean(properties.getProperty(JOINS));

            if (jdbcFetchSize != null) {
                fetchSize = Integer.parseInt(jdbcFetchSize);
            }

        }
        if (num_node > 0) {
            if (numWare % num_node != 0) {
                logger.error(" [warehouse] value must be devided by [num_node].");
                return 1;
            }
            if (numConn % num_node != 0) {
                logger.error("[connection] value must be devided by [num_node].");
                return 1;
            }
        }


        if (jdbcDriver == null && jdbcDataSource == null) {
            throw new RuntimeException("Java Driver and jdbcDataSource is null.");
        }
        if (jdbcUrl == null) {
            throw new RuntimeException("JDBC Url is null.");
        }
        if (dbUser == null) {
            throw new RuntimeException("User is null.");
        }
        if (dbPassword == null) {
            throw new RuntimeException("Password is null.");
        }
        if (numWare < 1) {
            throw new RuntimeException("Warehouse count has to be greater than or equal to 1.");
        }
        if (numConn < 1) {
            throw new RuntimeException("Connections has to be greater than or equal to 1.");
        }
        if (rampupTime < 1) {
            throw new RuntimeException("Rampup time has to be greater than or equal to 1.");
        }
        if (measureTime < 1) {
            throw new RuntimeException("Duration has to be greater than or equal to 1.");
        }


        // Init 2-dimensional arrays.
        success2 = new int[TRANSACTION_COUNT][numConn];
        late2 = new int[TRANSACTION_COUNT][numConn];
        retry2 = new int[TRANSACTION_COUNT][numConn];
        failure2 = new int[TRANSACTION_COUNT][numConn];

        //long delay1 = measure_time*1000;

        System.out.printf("<Parameters>\n");

        System.out.printf("     [driver]: %s\n", jdbcDriver);
        System.out.printf(" [datasource]: %s\n", jdbcDataSource);
        System.out.printf("        [URL]: %s\n", jdbcUrl);
        System.out.printf("       [user]: %s\n", dbUser);
        System.out.printf("       [pass]: %s\n", dbPassword);
        System.out.printf("      [joins]: %b\n", joins);


        System.out.printf("  [warehouse]: %d\n", numWare);
        System.out.printf(" [connection]: %d\n", numConn);
        System.out.printf("     [rampup]: %d (sec.)\n", rampupTime);
        System.out.printf("    [measure]: %d (sec.)\n", measureTime);


        Util.seqInit(10, 10, 1, 1, 1);

        /* set up database datasource */
        makeJdbcDriverProperties();
        makeDataSource();

        testDataSource();

        /* set up threads */

        if (DEBUG) logger.debug("Creating TpccThread");
        final ExecutorService executor = Executors.newFixedThreadPool(numConn, new NamedThreadFactory("tpcc-thread"));

        // Start each server.

        for (int i = 0; i < numConn; i++) {
            final Runnable worker = new TpccThread(i, numWare, numConn,
                    ds, fetchSize,
                    success, late, retry, failure, success2, late2, retry2, failure2, joins);
            executor.execute(worker);
        }

        if (rampupTime > 0) {
            // rampup time
            System.out.printf("\nRAMPUP START.\n\n");
            try {
                Thread.sleep(rampupTime * 1000);
            } catch (final InterruptedException e) {
                logger.error("Rampup wait interrupted", e);
            }
            System.out.printf("\nRAMPUP END.\n\n");
        }

        // measure time
        System.out.printf("\nMEASURING START.\n\n");

        // start counting
        counting_on = true;

        // loop for the measure_time
        final long startTime = System.currentTimeMillis();
        final DecimalFormat df = new DecimalFormat("#,##0.0");
        long runTime = 0;
        long newTime = 0;
        int totsuccess, totfailure, totlate, totretry;
        while ((runTime = (newTime = System.currentTimeMillis()) - startTime) < measureTime * 1000) {
            // System.out.println("Current execution time lapse: " + df.format(runTime / 1000.0f) + " seconds");
            totsuccess = totfailure = totlate = totretry = 0;
            for (int i = 0; i < TRANSACTION_COUNT; i++) {
                totsuccess += success[i];
                totfailure += failure[i];
                totlate += late[i];
                totretry += retry[i];    
            }
            System.out.printf("time:elasped:success:failure:late:retry=%s:%s:%d:%d:%d:%d \n", new Date(newTime), df.format(runTime / 1000.0f), totsuccess, totfailure, totlate, totretry);
            try {
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                logger.error("Sleep interrupted", e);
            }
        }
        final long actualTestTime = System.currentTimeMillis() - startTime;

        // show results
        System.out.println("---------------------------------------------------");
        /*
         *  Raw Results 
         */

        System.out.println("<Raw Results>");
        for (int i = 0; i < TRANSACTION_COUNT; i++) {
            System.out.printf("  |%s| sc:%d  lt:%d  rt:%d  fl:%d \n",
                    TRANSACTION_NAME[i], success[i], late[i], retry[i], failure[i]);
        }
        System.out.printf(" in %f sec.\n", actualTestTime / 1000.0f);

        /*
        * Raw Results 2
        */
        System.out.println("<Raw Results2(sum ver.)>");
        for (int i = 0; i < TRANSACTION_COUNT; i++) {
            success2_sum[i] = 0;
            late2_sum[i] = 0;
            retry2_sum[i] = 0;
            failure2_sum[i] = 0;
            for (int k = 0; k < numConn; k++) {
                success2_sum[i] += success2[i][k];
                late2_sum[i] += late2[i][k];
                retry2_sum[i] += retry2[i][k];
                failure2_sum[i] += failure2[i][k];
            }
        }
        for (int i = 0; i < TRANSACTION_COUNT; i++) {
            System.out.printf("  |%s| sc:%d  lt:%d  rt:%d  fl:%d \n",
                    TRANSACTION_NAME[i], success2_sum[i], late2_sum[i], retry2_sum[i], failure2_sum[i]);
        }

        System.out.println("<Constraint Check> (all must be [OK])\n [transaction percentage]");
        int j = 0;
        int i;
        for (i = 0; i < TRANSACTION_COUNT; i++) {
            j += (success[i] + late[i]);
        }

        double f = 100.0 * (float) (success[1] + late[1]) / (float) j;
        System.out.printf("        Payment: %f%% (>=43.0%%)", f);
        if (f >= 43.0) {
            System.out.printf(" [OK]\n");
        } else {
            System.out.printf(" [NG] *\n");
        }
        f = 100.0 * (float) (success[2] + late[2]) / (float) j;
        System.out.printf("   Order-Status: %f%% (>= 4.0%%)", f);
        if (f >= 4.0) {
            System.out.printf(" [OK]\n");
        } else {
            System.out.printf(" [NG] *\n");
        }
        f = 100.0 * (float) (success[3] + late[3]) / (float) j;
        System.out.printf("       Delivery: %f%% (>= 4.0%%)", f);
        if (f >= 4.0) {
            System.out.printf(" [OK]\n");
        } else {
            System.out.printf(" [NG] *\n");
        }
        f = 100.0 * (float) (success[4] + late[4]) / (float) j;
        System.out.printf("    Stock-Level: %f%% (>= 4.0%%)", f);
        if (f >= 4.0) {
            System.out.printf(" [OK]\n");
        } else {
            System.out.printf(" [NG] *\n");
        }

        /*
        * Response Time
        */
        System.out.printf(" [response time (at least 90%% passed)]\n");

        for (int n = 0; n < TRANSACTION_NAME.length; n++) {
            f = 100.0 * (float) success[n] / (float) (success[n] + late[n]);
            if (DEBUG) logger.debug("f: " + f + " success[" + n + "]: " + success[n] + " late[" + n + "]: " + late[n]);
            System.out.printf("      %s: %f%% ", TRANSACTION_NAME[n], f);
            if (f >= 90.0) {
                System.out.printf(" [OK]\n");
            } else {
                System.out.printf(" [NG] *\n");
            }
        }

        double total = 0.0;
        for (j = 0; j < TRANSACTION_COUNT; j++) {
            total = total + success[j] + late[j];
            System.out.println(" " + TRANSACTION_NAME[j] + " Total: " + (success[j] + late[j]));
        }

        final float tpcm = (success[0] + late[0]) * 60000f / actualTestTime;

        System.out.println();
        System.out.println("<TpmC>");
        System.out.println(tpcm + " TpmC");

        // stop threads
        System.out.printf("\nSTOPPING THREADS\n");
        activate_transaction = 0;

        executor.shutdown();
        try {
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            System.out.println("Timed out waiting for executor to terminate");
        }

        //TODO: To be implemented better later.
        //RtHist.histReport();
        return 0;

    }

    public static void main(final String[] argv) {

        System.out.println("TPCC version " + VERSION + " Number of Arguments: " + argv.length);


        // dump information about the environment we are running in
        final String sysProp[] = {
                "os.name",
                "os.arch",
                "os.version",
                "java.runtime.name",
                "java.vm.version",
                "java.library.path"
        };

        for (final String s : sysProp) {
            logger.info("System Property: " + s + " = " + System.getProperty(s));
        }

        final DecimalFormat df = new DecimalFormat("#,##0.0");
        System.out.println("maxMemory = " + df.format(Runtime.getRuntime().totalMemory() / (1024.0 * 1024.0)) + " MB");

        final Tpcc tpcc = new Tpcc();

        int ret = 0;

        if (argv.length == 0) {

            System.out.println("Using the properties file for configuration.");
            tpcc.init();
            ret = tpcc.runBenchmark(false, argv);

        } else {
            if ((argv.length % 2) == 0) {
                System.out.println("Using the command line arguments for configuration.");
                ret = tpcc.runBenchmark(true, argv);
            } else {
                System.out.println("Invalid number of arguments.");
                System.out.println("The possible arguments are as follows: ");
                System.out.println("-h [database host]");
                System.out.println("-d [database name]");
                System.out.println("-u [database username]");
                System.out.println("-p [database password]");
                System.out.println("-w [number of warehouses]");
                System.out.println("-c [number of connections]");
                System.out.println("-r [ramp up time]");
                System.out.println("-t [duration of the benchmark (sec)]");
                System.out.println("-j [java driver]");
                System.out.println("-l [jdbc url]");
                System.out.println("-h [jdbc fetch size]");
                System.exit(-1);
            }

        }


        System.out.println("Terminating process now");
        System.exit(ret);
    }


}

