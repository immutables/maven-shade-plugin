/*
    Copyright 2015 Immutables Authors and Contributors

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package org.apache.maven.plugins.shade.filter;

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

import com.google.common.base.Objects;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import static org.apache.maven.plugins.shade.filter.ClassDependencies.*;

/**
 * A filter that prevents the inclusion of classes not required in the final jar.
 * @author Torsten Curdt
 */
public class MinijarFilter
    implements Filter
{

  private final Log log;
  private final Set<String> unusedClasses;

  private int classesKept;
  private int classesRemoved;
  private final List<SimpleFilter> simpleFilters;

  /**
   * Instantiates a new minijar filter.
   * @param project the project
   * @param log the log
   * @param simpleFilters the simple filters
   * @throws IOException Signals that an I/O exception has occurred.
   * @since 1.6
   */
  @SuppressWarnings({"unchecked"})
  public MinijarFilter(MavenProject project, Log log, List<SimpleFilter> simpleFilters)
      throws IOException {
    this.log = log;
    this.simpleFilters = Objects.firstNonNull(simpleFilters, Collections.<SimpleFilter>emptyList());
    ClassDependencies dependenciesBuilder = new ClassDependencies(this.simpleFilters);

    // for this project artifact all files will be considered included
    dependenciesBuilder.allClassesSpecificallyIncluded = true;
    readDependencyJar(dependenciesBuilder, project.getArtifact());
    dependenciesBuilder.allClassesSpecificallyIncluded = false;

    for (Artifact dependencyArtifact : project.getArtifacts()) {
      readDependencyJar(dependenciesBuilder, dependencyArtifact);
    }

    // Note: this version do not remove packages. Reason is that they are usually small, contain no
    // method execution bytecode, but may hold runtime readable annotations. I don't see
    // any justification to either remove them blindly or rigously inspect if they are potentially
    // used.

    unusedClasses = dependenciesBuilder.buildAllUnusedClasses();
  }

  private void readDependencyJar(ClassDependencies dependenciesBuilder, Artifact artifact) throws IOException {
    try {
      dependenciesBuilder.addJar(artifact.getFile());
    } catch (ZipException e) {
      log.warn(String.format("Dependency %s in file %s could not read as JAR file. File is probably corrupt",
          artifact,
          artifact.getFile()));
    } catch (ArrayIndexOutOfBoundsException e) {
      // trap ArrayIndexOutOfBoundsExceptions caused by malformed dependency classes (MSHADE-107)
      log.warn(artifact + " could not be analyzed for minimization; dependency is probably malformed.");
    }
  }

  public boolean canFilter(File jar) {
    return true;
  }

  public boolean isFiltered(String name) {
    if (name.endsWith(CLASS_SUFFIX)) {
      String typeName = name.substring(0, name.length() - CLASS_SUFFIX.length());

      if (unusedClasses.contains(typeName)) {
        log.debug("Removing unused class: " + typeName);
        classesRemoved += 1;
        return true;
      }

      classesKept += 1;
      return false;
    }

    if (name.startsWith(SERVICES_ENTRY_PREFIX)) {
      String typeName = ClassDependencies.toClassFilePath(
          name.substring(SERVICES_ENTRY_PREFIX.length()));

      if (unusedClasses.contains(typeName)) {
        log.debug("Removing services for unused class: " + name);
        return true;
      }
    }

    return false;
  }

  public void finished() {
    int classesTotal = classesRemoved + classesKept;
    log.info("Minimized " + classesTotal + " -> " + classesKept + " (" + 100 * classesKept / classesTotal + "%)");
  }
}
