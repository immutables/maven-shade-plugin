package org.apache.maven.plugins.shade.resource;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.plugins.shade.relocation.Relocator;
import org.codehaus.plexus.util.IOUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Resources transformer that appends entries in META-INF/services resources into
 * a single resource. For example, if there are several
 * META-INF/services/org.apache.maven.project.ProjectBuilder
 * resources spread across many JARs the individual entries will all be concatenated into a single
 * META-INF/services/org.apache.maven.project.ProjectBuilder resource packaged into the resultant
 * JAR produced
 * by the shading process.
 * @author jvanzyl
 */
public class ServicesResourceTransformer
    implements ResourceTransformer
{
  private static final String SERVICES_PATH = "META-INF/services";

  private final Map<String, Set<String>> serviceEntries = new LinkedHashMap<String, Set<String>>();

  public boolean canTransformResource(String resource) {
    return resource.startsWith(SERVICES_PATH);
  }

  public void processResource(String resource, InputStream is, List<Relocator> relocators)
      throws IOException {
    Set<String> serviceLines = getServiceLines(resource);

    for (String line : readAllLines(is)) {
      if (!line.isEmpty()) {
        serviceLines.add(relocateIfPossible(relocators, line));
      }
    }

    is.close();
  }

  private Set<String> getServiceLines(String resource) {
    Set<String> lines = serviceEntries.get(resource);
    if (lines == null) {
      lines = new LinkedHashSet<String>();
      serviceEntries.put(resource, lines);
    }
    return lines;
  }

  private String[] readAllLines(InputStream is) throws IOException {
    return IOUtil.toString(is, "utf-8").replace('\r', '|').replace('\n', '|').split("\\|");
  }

  private String relocateIfPossible(List<Relocator> relocators, String line) {
    for (Relocator relocator : relocators) {
      if (relocator.canRelocateClass(line)) {
        return relocator.relocateClass(line);
      }
    }
    return line;
  }

  public boolean hasTransformedResource() {
    return !serviceEntries.isEmpty();
  }

  public void modifyOutputStream(JarOutputStream jos)
      throws IOException {
    for (Entry<String, Set<String>> entry : serviceEntries.entrySet()) {
      jos.putNextEntry(new JarEntry(entry.getKey()));
      jos.write(toResourceBytes(entry.getValue()));
    }
  }

  private byte[] toResourceBytes(Set<String> value) throws IOException {
    StringBuilder builder = new StringBuilder();
    for (String line : value) {
      builder.append(line).append('\n');
    }
    return builder.toString().getBytes("utf-8");
  }
}
