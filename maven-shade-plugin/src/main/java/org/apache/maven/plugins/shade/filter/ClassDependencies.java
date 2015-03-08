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

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import org.codehaus.plexus.util.IOUtil;
import static com.google.common.base.Preconditions.*;

/**
 * The Class ClassDependenciesBuilder.
 */
final class ClassDependencies {
  static final String CLASS_SUFFIX = ".class";
  static final String SERVICES_ENTRY_PREFIX = "META-INF/services/";
  private static final Splitter SERVICES_SPLITTER =
      Splitter.on(CharMatcher.anyOf("\n\r"))
          .trimResults()
          .omitEmptyStrings();

  private final SetMultimap<String, String> services = HashMultimap.create();
  private final Multimap<String, String> dependencies = HashMultimap.create(100, 100);
  private final Set<String> classes = Sets.newHashSetWithExpectedSize(1000);
  private final Set<String> included = Sets.newHashSetWithExpectedSize(100);
  private final List<SimpleFilter> currentFilters = Lists.newArrayList();

  private final List<SimpleFilter> filters;

  ClassDependencies(List<SimpleFilter> filters) {
    this.filters = filters;
  }

  String readClass(InputStream input) throws IOException {
    DependenciesClassAdapter adapter = DependenciesClassAdapter.readFrom(input);
    dependencies.putAll(adapter.getName(), adapter.getDependencies());
    return adapter.getName();
  }

  private class TransitiveDependenciesCollector {
    private final Set<String> all = Sets.newHashSet();

    void add(String typeName) {
      if (all.add(typeName)) {
        addAll(dependencies.get(typeName));
      }
    }

    void addAll(Iterable<String> typeNames) {
      for (String typeName : typeNames) {
        add(typeName);
      }
    }
  }

  final Set<String> buildAllUnusedClasses() {
    TransitiveDependenciesCollector collecter = new TransitiveDependenciesCollector();
    collecter.addAll(included);
    collectUsedServiceProviders(collecter);
    return Sets.difference(classes, collecter.all);
  }

  private void collectUsedServiceProviders(TransitiveDependenciesCollector collecter) {
    for (Entry<String, Collection<String>> providers : services.asMap().entrySet()) {
      if (collecter.all.contains(providers.getKey())) {
        collecter.addAll(providers.getValue());
      }
    }
  }

  /**
   * Switch on when using on artifact, where each class should be is included, such as this,
   * originating artifact
   */
  boolean allClassesSpecificallyIncluded;

  void addJar(File jarFile) throws IOException {
    setupCurrentFilters(jarFile);
    traverse(jarFile);
    currentFilters.clear();
  }

  private void setupCurrentFilters(File jarFile) {
    checkState(currentFilters.isEmpty());
    for (SimpleFilter filter : filters) {
      if (filter.canFilter(jarFile)) {
        currentFilters.add(filter);
      }
    }
  }

  protected void readEntry(String entryName, InputStream inputStream) throws IOException {
    if (entryName.startsWith(SERVICES_ENTRY_PREFIX)) {
      readServiceEntry(entryName, inputStream);
    } else if (entryName.endsWith(CLASS_SUFFIX)) {
      readClassEntry(entryName, inputStream);
    }
  }

  private void readServiceEntry(String name, InputStream inputStream) throws IOException {
    if (!isIncluded(name)) {
      return;
    }
    String typeName = toClassFilePath(name.replace(SERVICES_ENTRY_PREFIX, ""));

    if (typeName.isEmpty()) {
      return;
    }

    boolean forcedInclusion = isSpecificallyIncluded(name);
    if (forcedInclusion) {
      included.add(typeName);
    }

    String content = IOUtil.toString(inputStream, Charsets.UTF_8.name());
    for (String providerType : SERVICES_SPLITTER.split(content)) {
      String providerName = toClassFilePath(providerType);
      services.put(typeName, providerName);

      if (forcedInclusion) {
        included.add(providerName);
      }
    }
  }

  static String toClassFilePath(String providerType) {
    return providerType.replace('.', '/');
  }

  private void readClassEntry(String entryName, InputStream inputStream) throws IOException {
    String typeName = readClass(inputStream);
    classes.add(typeName);

    if (isSpecificallyIncluded(entryName)) {
      included.add(typeName);
    }
  }

  private boolean isIncluded(String name) {
    for (SimpleFilter filter : currentFilters) {
      if (filter.isFiltered(name)) {
        return false;
      }
    }
    return true;
  }

  private boolean isSpecificallyIncluded(String name) {
    if (allClassesSpecificallyIncluded) {
      return true;
    }
    for (SimpleFilter filter : currentFilters) {
      if (filter.isSpecificallyIncluded(name)) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("resource")
  final void traverse(File jarFile) throws IOException {
    JarInputStream jarInputStream =
        new JarInputStream(
            new BufferedInputStream(
                new FileInputStream(jarFile)));
    try {
      jarInputStream.getManifest();
      for (JarEntry jarEntry; (jarEntry = jarInputStream.getNextJarEntry()) != null;) {
        readEntry(jarEntry.getName(), jarInputStream);
      }
    } finally {
      jarInputStream.close();
    }
  }
}
