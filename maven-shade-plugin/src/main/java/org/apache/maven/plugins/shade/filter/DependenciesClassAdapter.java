/*
 * Copyright 2010-2011 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.maven.plugins.shade.filter;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.ClassRemapper;

final class DependenciesClassAdapter extends ClassRemapper {
  private static final DeepVisitor ev = new DeepVisitor();

  static DependenciesClassAdapter readFrom(InputStream input) throws IOException {
    final DependenciesClassAdapter v = new DependenciesClassAdapter();
    new ClassReader(input).accept(v, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
    return v;
  }

  private DependenciesClassAdapter() {
    super(ev, new CollectingRemapper());
  }

  String getName() {
    return className;
  }

  Set<String> getDependencies() {
    return ((CollectingRemapper) super.remapper).classes;
  }

  private static class CollectingRemapper extends Remapper {
    final Set<String> classes = new HashSet<String>(256);

    @Override
    public String map(String className) {
      classes.add(className);
      return className;
    }
  }

  private static class DeepVisitor extends ClassVisitor {
    DeepVisitor() {
      super(Opcodes.ASM5);
    }

    private static final AnnotationVisitor av = new AnnotationVisitor(Opcodes.ASM5) {

      @Override
      public AnnotationVisitor visitAnnotation(String name, String desc) {
        return this;
      }

      @Override
      public AnnotationVisitor visitArray(String name) {
        return this;
      }
    };

    private static final MethodVisitor mv = new MethodVisitor(Opcodes.ASM5) {

      @Override
      public AnnotationVisitor visitAnnotationDefault() {
        return av;
      }

      @Override
      public AnnotationVisitor visitAnnotation(String desc,
          boolean visible) {
        return av;
      }

      @Override
      public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
        return av;
      }

      @Override
      public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        return av;
      }
    };

    private static final FieldVisitor fieldVisitor = new FieldVisitor(Opcodes.ASM4) {
      @Override
      public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        return av;
      }

      @Override
      public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        return av;
      }
    };

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return av;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc,
        String signature, Object value) {
      return fieldVisitor;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc,
        String signature, String[] exceptions) {
      return mv;
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      return av;
    }
  }
}
