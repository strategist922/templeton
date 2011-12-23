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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.exec.ExecuteException;
import org.apache.hcatalog.templeton.tool.TempletonUtils;

/**
 * Submit a job to the MapReduce queue.  We do this by running the
 * hadoop executable on the local server using the ExecService.
 * This allows us to easily verify that the user identity is being
 * securely used.
 *
 * This is the backend of the mapreduce/jar web service.
 */
public class JarDelegator extends LauncherDelegator {
    public JarDelegator(AppConfig appConf, ExecService execService) {
        super(appConf, execService);
    }

    public EnqueueBean run(String user, String jar, String mainClass,
                           String libjars, String files,
                           List<String> jarArgs, List<String> defines,
                           String statusdir, String callback, String completedUrl)
        throws NotAuthorizedException, BadParam, BusyException, QueueException,
        ExecuteException, IOException
    {
        runAs = user;
        List<String> args = makeArgs(jar, mainClass,
                                     libjars, files, jarArgs, defines,
                                     statusdir, completedUrl);

        ExecBean exec = execService.run(user, appConf.clusterHadoop(), args, null);
        if (exec.exitcode != 0)
            throw new QueueException("invalid exit code", exec);
        String id = TempletonUtils.extractJobId(exec.stdout);
        if (id == null)
            throw new QueueException("Unable to get job id", exec);
        registerJob(id, user, callback);

        return new EnqueueBean(id, exec);
    }

    private List<String> makeArgs(String jar, String mainClass,
                                  String libjars, String files,
                                  List<String> jarArgs, List<String> defines,
                                  String statusdir, String completedUrl)
        throws BadParam, IOException
    {
        ArrayList<String> args = new ArrayList<String>();
        try {
            ArrayList<String> allFiles = new ArrayList();
            allFiles.add(TempletonUtils.hadoopFsFilename(jar, appConf));

            args.addAll(makeLauncherArgs(appConf, statusdir, completedUrl, allFiles));
            args.add("--");
            args.add(appConf.clusterHadoop());
            args.add("jar");
            args.add(TempletonUtils.hadoopFsPath(jar, appConf).getName());
            if (TempletonUtils.isset(mainClass))
                args.add(mainClass);
            if (TempletonUtils.isset(libjars)) {
                args.add("-libjars");
                args.add(TempletonUtils.hadoopFsListAsString(libjars, appConf));
            }
            if (TempletonUtils.isset(files)) {
                args.add("-files");
                args.add(TempletonUtils.hadoopFsListAsString(files, appConf));
            }

            for (String d : defines)
                args.add("-D" + d);

            args.addAll(jarArgs);
        } catch (FileNotFoundException e) {
            throw new BadParam(e.getMessage());
        } catch (URISyntaxException e) {
            throw new BadParam(e.getMessage());
        }

        return args;
    }
}
