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

import java.io.File;;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.exec.ExecuteException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobID;
import org.apache.hadoop.mapred.JobProfile;
import org.apache.hadoop.mapred.JobStatus;
import org.apache.hadoop.mapred.JobTracker;
import org.apache.hadoop.mapred.TempletonJobTracker;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hcatalog.templeton.tool.TempletonJarJob;
import org.apache.hcatalog.templeton.tool.TempletonStreamJob;

/**
 * Delegate a Templeton job to the backend Hadoop service.
 */
public class DelegatorService {
    public static final String TEMPLETON_JAR = System.getenv("TEMPLETON_JAR");
    public static final String STREAM_CLASS = TempletonStreamJob.class.getName();
    public static final String JAR_CLASS = TempletonJarJob.class.getName();
    public static final String STREAMING_JAR =
        System.getenv("HADOOP_HOME")
        + "/contrib/streaming/hadoop-streaming-0.20.203.0.jar";

    public static String[] CONF_FILENAMES = {
        "core-default.xml", "core-site.xml", "mapred-default.xml", "mapred-site.xml"
    };

    private static volatile DelegatorService theSingleton;

    /**
     * Retrieve the singleton.
     */
    public static synchronized DelegatorService getInstance() {
        if (theSingleton == null)
            theSingleton = new DelegatorService();
        return theSingleton;
    }

    private static ExecService execService = ExecService.getInstance();

    /**
     * Run date on the local server using the ExecService.
     */
    public ExecBean runDate(String user)
        throws NotAuthorizedException, BusyException, ExecuteException, IOException
    {
        return execService.run(user, "date", null, null);
    }

    /**
     * Run hcat on the local server using the ExecService.  This is
     * the backend of the ddl web service.
     */
    public ExecBean runHcat(String user, String exec, String group, String permissions)
        throws NotAuthorizedException, BusyException, ExecuteException, IOException
    {
        ArrayList<String> args = new ArrayList<String>();
        args.add("-e");
        args.add(exec);
        if (group != null) {
            args.add("-g");
            args.add(group);
        }
        if (permissions != null) {
            args.add("-p");
            args.add(permissions);
        }

        return execService.run(user, ExecService.HCAT, args, null);
    }

    /**
     * Submit a streaming job to the MapReduce queue.  We do this by
     * running the hadoop executable on the local server using the
     * ExecService.  This allows us to easily verify that the user
     * identity is being securely used.
     *
     * This is the backend of the mapreduce/streaming web service.
     */
    public EnqueueBean runStreaming(String user, List<String> inputs,
                                    String output, String mapper, String reducer)
        throws NotAuthorizedException, BusyException, QueueException,
        ExecuteException, IOException
    {
        ArrayList<String> args = new ArrayList<String>();
        args.add("jar");
        args.add(TEMPLETON_JAR);
        args.add(STREAM_CLASS);
        args.add("-libjars");
        args.add(STREAMING_JAR);
        for (String input : inputs) {
            args.add("-input");
            args.add(input);
        }
        args.add("-output");
        args.add(output);
        args.add("-mapper");
        args.add(mapper);
        args.add("-reducer");
        args.add(reducer);

        HashMap<String, String> env = new HashMap<String, String>();
        env.put("HADOOP_CLASSPATH", STREAMING_JAR);

        ExecBean exec = execService.run(user, ExecService.HADOOP, args, env);
        if (exec.exitCode != 0)
            throw new QueueException("invalid exit code", exec);
        String id = TempletonStreamJob.extractJobId(exec.stdout);
        if (id == null)
            throw new QueueException("Unable to get job id", exec);

        return new EnqueueBean(id, exec);
    }

    /**
     * Submit a job to the MapReduce queue.  We do this by running the
     * hadoop executable on the local server using the ExecService.
     * This allows us to easily verify that the user identity is being
     * securely used.
     *
     * This is the backend of the mapreduce/jar web service.
     */
    public EnqueueBean runJar(String user, String jar, String mainClass,
                              List<String> jarArgs)
        throws NotAuthorizedException, BusyException, QueueException,
        ExecuteException, IOException
    {
        if (user != null)
            throw new QueueException("Not implemented", null);

        ArrayList<String> args = new ArrayList<String>();
        args.add("jar");
        args.add(TEMPLETON_JAR);
        args.add(JAR_CLASS);
        args.add(jar);
        args.add(mainClass);
        args.addAll(jarArgs);

        ExecBean exec = execService.run(user, ExecService.HADOOP, args, null);
        if (exec.exitCode != 0)
            throw new QueueException("invalid exit code", exec);
        String id = TempletonStreamJob.extractJobId(exec.stdout);
        if (id == null)
            throw new QueueException("Unable to get job id", exec);

        return new EnqueueBean(id, exec);
    }

    private Configuration loadConf() {
        Configuration conf = new Configuration();

        for (String fname : CONF_FILENAMES) {
            String full = System.getenv("HADOOP_HOME") + "/conf/" + fname;
            File f = new File(full);
            if (f.exists())
                conf.addResource(new Path(full));
        }

        return conf;
    }

    /**
     * Fetch the status of a given job id in the queue.
     */
    public QueueStatusBean jobStatus(String user, String id)
        throws NotAuthorizedException, BadParam, IOException
    {
        UserGroupInformation ugi = UserGroupInformation.createRemoteUser(user);
        Configuration conf = loadConf();
        TempletonJobTracker tracker = null;
        try {
            tracker = new TempletonJobTracker(ugi,
                                              JobTracker.getAddress(conf),
                                              conf);
            JobID jobid = JobID.forName(id);
            JobStatus status = tracker.getJobStatus(jobid);
            JobProfile profile = tracker.getJobProfile(jobid);
            return new QueueStatusBean(status, profile);
        } catch (IllegalStateException e) {
            throw new BadParam(e.getMessage());
        } finally {
            if (tracker != null)
                tracker.close();
        }
    }
}