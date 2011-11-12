/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hcatalog.templeton;

import java.io.File;
import java.net.URL;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.VersionInfo;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.jersey.spi.container.servlet.WebComponent;

/**
 * The configuration for Templeton.  This merges the normal Hadoop
 * configuration with the Templeton specific variables.
 *
 * The Templeton configuration variables are described in
 * templeton-default.xml
 *
 * The Templeton specific configuration is split into three layers
 *
 * 1. templeton-default.xml - All the configuration variables that
 *    Templeton needs.  These are the defaults that ship with the app
 *    and should only be changed be the app developers.
 *
 * 2. templeton-dist.xml - The (possibly empty) configuration that can
 *    set variables for a particular distribution, such as an RPM
 *    file.
 *
 * 3. templeton-site.xml - The (possibly empty) configuration that the
 *    system administrator can set variables for their Hadoop cluster.
 *
 * The configuration files are loaded in this order with later files
 * overriding earlier ones.
 *
 * To find the configuration files, we first attempt to load a file
 * from the CLASSPATH and then look in the directory specified in the
 * TEMPLETON_HOME environment variable.
 *
 * In addition the configuration files may access the special env
 * variable env for all environment variables.  For example, the
 * hadoop executable could be specified using:
 *<pre>
 *      ${env.HADOOP_PREFIX}/bin/hadoop
 *</pre>
 */
public class AppConfig extends Configuration {
    public static final String[] HADOOP_CONF_FILENAMES = {
        "core-default.xml", "core-site.xml", "mapred-default.xml", "mapred-site.xml"
    };

    public static final String[] HADOOP_PREFIX_VARS = {
        "HADOOP_PREFIX", "HADOOP_HOME"
    };

    public static final String TEMPLETON_HOME_VAR = "TEMPLETON_HOME";

    public static final String[] TEMPLETON_CONF_FILENAMES = {
        "templeton-default.xml",
        "templeton-dist.xml",
        "templeton-site.xml"
    };

    public static final String TEMPLETON_JAR_NAME    = "templeton.jar";
    public static final String STREAMING_JAR_NAME    = "templeton.streaming.jar";
    public static final String HADOOP_NAME           = "templeton.hadoop";
    public static final String HCAT_NAME             = "templeton.hcat";
    public static final String PIG_ARCHIVE_NAME      = "templeton.pig.archive";
    public static final String PIG_PATH_NAME         = "templeton.pig.path";
    public static final String HIVE_ARCHIVE_NAME     = "templeton.hive.archive";
    public static final String HIVE_PATH_NAME        = "templeton.hive.path";
    public static final String SUDO_NAME             = "templeton.sudo";
    public static final String EXEC_ENVS_NAME        = "templeton.exec.envs";
    public static final String EXEC_ENCODING_NAME    = "templeton.exec.encoding";
    public static final String EXEC_TIMEOUT_NAME     = "templeton.exec.timeout";
    public static final String EXEC_MAX_PROCS_NAME   = "templeton.exec.max-procs";

    private static final Log LOG = LogFactory.getLog(AppConfig.class);

    private static volatile AppConfig theSingleton;

    /**
     * Retrieve the singleton.
     */
    public static synchronized AppConfig getInstance() {
        if (theSingleton == null)
            theSingleton = new AppConfig();
        return theSingleton;
    }

    public AppConfig() {
        init();
        LOG.info("Using Hadoop version " + VersionInfo.getVersion());
    }

    private void init() {
        for (Map.Entry<String, String> e : System.getenv().entrySet())
            set("env." + e.getKey(), e.getValue());

        String hadoopConfDir = getHadoopConfDir();
        for (String fname : HADOOP_CONF_FILENAMES)
            loadOneFileConfig(hadoopConfDir, fname);

        String templetonDir = getTempletonDir();
        for (String fname : TEMPLETON_CONF_FILENAMES)
            if (! loadOneClasspathConfig(fname))
                loadOneFileConfig(templetonDir, fname);

        // What a horrible place to do this.  Needs to move into the
        // logging config file.
        Logger filterLogger = Logger.getLogger(WebComponent.class.getName());
        if (filterLogger != null)
            filterLogger.setLevel(Level.SEVERE);
    }

    public static String getHadoopPrefix() {
        for (String var : HADOOP_PREFIX_VARS) {
            String x = System.getenv(var);
            if (x != null)
                return x;
        }
        return null;
    }


    public static String getHadoopConfDir() {
        String x = System.getenv("HADOOP_CONF_DIR");
        if (x != null)
            return x;

        String prefix = getHadoopPrefix();
        if (prefix != null)
            return new File(prefix, "conf").getAbsolutePath();

        return null;
    }

    public static String getTempletonDir() {
        return System.getenv(TEMPLETON_HOME_VAR);
    }

    private boolean loadOneFileConfig(String dir, String fname) {
        if (dir != null) {
            File f = new File(dir, fname);
            if (f.exists()) {
                addResource(new Path(f.getAbsolutePath()));
                LOG.info("loaded config file " + f.getAbsolutePath());
                return true;
            }
        }
        return false;
    }

    private boolean loadOneClasspathConfig(String fname) {
        URL x = getResource(fname);
        if (x != null) {
            addResource(x);
            LOG.info("loaded config from classpath  " + x);
            return true;
        }

        return false;
    }

    public String templetonJar()  { return get(TEMPLETON_JAR_NAME); }
    public String clusterHadoop() { return get(HADOOP_NAME); }
    public String clusterHcat()   { return get(HCAT_NAME); }
    public String pigPath()       { return get(PIG_PATH_NAME); }
    public String pigArchive()    { return get(PIG_ARCHIVE_NAME); }
    public String hivePath()      { return get(HIVE_PATH_NAME); }
    public String hiveArchive()   { return get(HIVE_ARCHIVE_NAME); }
    public String sudoPath()      { return get(SUDO_NAME); }

    public String streamingJar() {
        return get(STREAMING_JAR_NAME);
    }
}