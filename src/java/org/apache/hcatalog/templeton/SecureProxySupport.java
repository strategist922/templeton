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
import java.io.IOException;
import java.security.PrivilegedExceptionAction;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.HiveMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.thrift.TException;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;

/**
 * Helper class to run jobs using Kerberos security.  Always safe to
 * use these methods, it's a noop if security is not enabled.
 */
public class SecureProxySupport {
    private Path tokenPath;
    private String hcatToken;
    private boolean isEnabled;

    public SecureProxySupport() {
        isEnabled = UserGroupInformation.isSecurityEnabled();
    }

    /**
     * The file where we store the auth token
     */
    public Path getTokenPath() { return( tokenPath ); }

    /**
     * The token to pass to hcat.
     */
    public String getHcatToken() { return( hcatToken ); }

    /**
     * Create the delegation token.
     */
    public Path open(String user, Configuration conf)
        throws IOException, InterruptedException
    {
        close();
        if (isEnabled) {
            File t = File.createTempFile("templeton", null);
            tokenPath = new Path(t.toURI());
            writeProxyDelegationToken(user, conf, tokenPath);
            try {
                hcatToken = buildHcatDelegationToken(user);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
        return tokenPath;
    }

    /**
     * Cleanup
     */
    public void close() {
        if (tokenPath != null) {
            new File(tokenPath.toUri()).delete();
            tokenPath = null;
        }
    }

    /**
     * Add Hadoop env variables.
     */
    public void addEnv(Map<String, String> env) {
        if (isEnabled) {
            env.put(UserGroupInformation.HADOOP_TOKEN_FILE_LOCATION,
                    getTokenPath().toUri().getPath());
        }
    }

    /**
     * Add hcat args.
     */
    public void addArgs(List<String> args) {
        if (isEnabled) {
            args.add("-D");
            args.add("hive.metastore.token.signature=" + getHcatToken());
        }
    }

    private void writeProxyDelegationToken(String user,
                                           final Configuration conf,
                                           final Path tokenPath)
        throws IOException, InterruptedException
    {
        final UserGroupInformation ugi
            = UserGroupInformation.createProxyUser(user,
                                                   UserGroupInformation.getLoginUser());
        ugi.doAs(new PrivilegedExceptionAction<Object>() {
                     public Object run() throws IOException {
                         FileSystem fs = FileSystem.get(conf);
                         Token<?> token
                             = fs.getDelegationToken(ugi.getShortUserName());
                         Credentials cred = new Credentials();
                         cred.addToken(token.getService(), token);
                         cred.writeTokenStorageFile(tokenPath, conf);
                         return null;
                     }
                 });
    }

    private String buildHcatDelegationToken(String user)
        throws IOException, InterruptedException, MetaException, TException
    {
        HiveConf c = new HiveConf();
        final HiveMetaStoreClient client = new HiveMetaStoreClient(c);
        final UserGroupInformation ugi
            = UserGroupInformation.createProxyUser(user,
                                                   UserGroupInformation.getLoginUser());
        String s = ugi.doAs(new PrivilegedExceptionAction<String>() {
                                public String run()
                                    throws IOException, MetaException, TException
                                {
                                    String u = ugi.getUserName();
                                    return client.getDelegationToken(u);
                                }
                            });
        return s;
    }
}