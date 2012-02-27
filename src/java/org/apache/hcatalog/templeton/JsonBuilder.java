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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.codehaus.jackson.map.ObjectMapper;

/**
 * Helper class to build new json objects with new top level
 * properties.  Only add non-null entries.
 */
public class JsonBuilder {
    // The map we're building.
    private Map map;

    // Parse the json map.
    private JsonBuilder(String json)
        throws IOException
    {
        if (json == null)
            map = new HashMap<String, Object>();
        else
            map = jsonToMap(json);
    }

    /**
     * Create a new map object from the existing json.
     */
    public static JsonBuilder create(String json)
        throws IOException
    {
        return new JsonBuilder(json);
    }

    /**
     * Create a new map object.
     */
    public static JsonBuilder create()
        throws IOException
    {
        return new JsonBuilder(null);
    }

    /**
     * Add a non-null value to the map.
     */
    public JsonBuilder put(String name, Object val) {
        if (val != null)
            map.put(name, val);
        return this;
    }

    /**
     * Remove a value from the map.
     */
    public JsonBuilder remove(String name) {
        map.remove(name);
        return this;
    }

    /**
     * Turn the map back to json.
     */
    public String build()
        throws IOException
    {
        return mapToJson(map);
    }

    /**
     * Convert a json string to a Map.
     */
    public static Map jsonToMap(String json)
        throws IOException
    {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, Map.class);
    }

    /**
     * Convert a map to a json string.
     */
    public static String mapToJson(Object obj)
        throws IOException
    {
        ObjectMapper mapper = new ObjectMapper();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mapper.writeValue(out, obj);
        return out.toString();
    }
}