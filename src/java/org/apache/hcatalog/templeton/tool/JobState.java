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
package org.apache.hcatalog.templeton.tool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;

/**
 * The persistent state of a job.  The state is stored in one of the
 * supported storage systems.
 */
public class JobState {

    private static final Log LOG = LogFactory.getLog(JobState.class);

    private String id;

    // Storage is instantiated in the constructor
    private TempletonStorage storage = null;

    private static TempletonStorage.Type type = TempletonStorage.Type.JOB;
    
    private Configuration config = null;

    public JobState(String id, Configuration conf)
        throws IOException
    {
        this.id = id;
        config = conf;
        storage = getStorage(conf);
    }

    public void delete()
        throws IOException
    {
        try {
            storage.delete(type, id);
        } catch (Exception e) {
            // Error getting children of node -- probably node has been deleted
            LOG.info("Couldn't delete " + id);
        }
    }
    
    /**
     * Get an instance of the selected storage class.  Defaults to
     * ZooKeeper storage if none is specified.
     * @return
     */
    public static TempletonStorage getStorage(Configuration conf) {
        TempletonStorage storage = null;
        try {
            storage = (TempletonStorage)
                Class.forName(conf.get(TempletonStorage.STORAGE_CLASS))
                	.newInstance();
            storage.openStorage(conf);
        } catch (Exception e) {
            LOG.warn("No storage method found: " + e.getMessage());
            try {
                storage = new ZooKeeperStorage();
            } catch (Exception ex) {
                LOG.error("Couldn't create storage.");
            }
        }
        return storage;
    }

    /**
     * For storage methods that require a connection, this is a hint
     * that it's time to close the connection.
     */
    public void close() throws IOException {
        storage.closeStorage();
    }

    //
    // Properties
    //

    /**
     * This job id.
     */
    public String getId() {
        return id;
    }

    /**
     * The percent complete of a job
     */
    public String  getPercentComplete()
        throws IOException
    {
        return getField("percentComplete");
    }
    public void setPercentComplete(String percent)
        throws IOException
    {
        setField("percentComplete", percent);
    }

    /**
     * The child id of TempletonControllerJob
     */
    public String  getChildId()
        throws IOException
    {
        return getField("childid");
    }
    public void setChildId(String childid)
        throws IOException
    {
        setField("childid", childid);
    }

    /**
     * Add a jobid to the list of children of this job.
     *
     * @param jobid
     * @throws IOException
     */
    public void addChild(String jobid) throws IOException {
        String jobids = "";
        try {
            jobids = getField("children");
        } catch (Exception e) {
            // There are none or they're not readable.
        }
        if (!jobids.equals("")) {
            jobids += ",";
        }
        jobids += jobid;
        setField("children", jobids);
    }

    /**
     * Get a list of jobstates for jobs that are children of this job.
     * @return
     * @throws IOException
     */
    public List<JobState> getChildren() throws IOException {
        ArrayList<JobState> children = new ArrayList<JobState>();
        for (String jobid : getField("children").split(",")) {
            children.add(new JobState(jobid, config));
        }
        return children;
    }

    /**
     * Save a comma-separated list of jobids that are children
     * of this job.
     * @param jobids
     * @throws IOException
     */
    public void setChildren(String jobids) throws IOException {
        setField("children", jobids);
    }

    /**
     * Set the list of child jobs of this job
     * @param children
     */
    public void setChildren(List<JobState> children) throws IOException {
        String val = "";
        for (JobState jobstate : children) {
            if (!val.equals("")) {
                val += ",";
            }
            val += jobstate.getId();
        }
        setField("children", val);
    }

    /**
     * The system exit value of the job.
     */
    public Long getExitValue()
        throws IOException
    {
        return getLongField("exitValue");
    }
    public void setExitValue(long exitValue)
        throws IOException
    {
        setLongField("exitValue", exitValue);
    }

    /**
     * When this job was created.
     */
    public Long getCreated()
        throws IOException
    {
        return getLongField("created");
    }
    public void setCreated(long created)
        throws IOException
    {
        setLongField("created", created);
    }

    /**
     * The user who started this job.
     */
    public String getUser()
        throws IOException
    {
        return getField("user");
    }
    public void setUser(String user)
        throws IOException
    {
        setField("user", user);
    }

    /**
     * The url callback
     */
    public String getCallback()
        throws IOException
    {
        return getField("callback");
    }
    public void setCallback(String callback)
        throws IOException
    {
        setField("callback", callback);
    }

    /**
     * The status of a job once it is completed.
     */
    public String getCompleteStatus()
        throws IOException
    {
        return getField("completed");
    }
    public void setCompleteStatus(String complete)
        throws IOException
    {
        setField("completed", complete);
    }

    /**
     * The time when the callback was sent.
     */
    public Long getNotifiedTime()
        throws IOException
    {
        return getLongField("notified");
    }
    public void setNotifiedTime(long notified)
        throws IOException
    {
        setLongField("notified", notified);
    }

    //
    // Helpers
    //

    /**
     * Fetch an integer field from the ZK strore.
     */
    public Long getLongField(String name)
        throws IOException
    {
        String s = storage.getField(type, id, name);
        if (s == null)
            return null;
        else {
            try {
                return new Long(s);
            } catch (NumberFormatException e) {
                LOG.error("templeton: bug " + name + " " + s + " : "+ e);
                return null;
            }
        }
    }

    /**
     * Store a String field from the ZK store.
     */
    public void setField(String name, String val)
        throws IOException
    {
        try {
            storage.saveField(type, id, name, val);
        } catch (NotFoundException ne) {
            throw new IOException(ne.getMessage());
        }
    }

    public String getField(String name)
        throws IOException
    {
        return storage.getField(type, id, name);
    }

    /**
     * Store a long field.
     *
     * @param name
     * @param val
     * @throws IOException
     */
    public void setLongField(String name, long val)
        throws IOException
    {
        try {
            storage.saveField(type, id, name, String.valueOf(val));
        } catch (NotFoundException ne) {
            throw new IOException("Job " + id + " was not found: " +
                                  ne.getMessage());
        }
    }

    /**
     * Get an id for each currently existing job, which can be used to create
     * a JobState object.
     *
     * @param conf
     * @return
     * @throws IOException
     */
    public static List<String> getJobs(Configuration conf) throws IOException {
        try {
            return getStorage(conf).getAllForType(type);
        } catch (Exception e) {
            throw new IOException("Can't get jobs", e);
        }
    }
}
