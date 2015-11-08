/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.testartifact;

import com.android.builder.model.BaseArtifact;
import com.android.builder.model.SourceProvider;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.customizer.dependency.Dependency;
import com.android.tools.idea.gradle.customizer.dependency.DependencySet;
import com.android.tools.idea.gradle.customizer.dependency.LibraryDependency;
import com.android.tools.idea.gradle.customizer.dependency.ModuleDependency;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static com.android.builder.model.AndroidProject.ARTIFACT_UNIT_TEST;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.android.facet.IdeaSourceProvider.getAllSourceFolders;

/**
 * Caches following important GlobalSearchScope related to test artifact of a module:
 * <ul>
 *   <li>Scope for android test source</li>
 *   <li>Scope for unit test source</li>
 *   <li>Scope that is excluded for android test source (unit test's source / library / module dependencies)</li>
 *   <li>Scope that is excluded for unit test source</li>
 * </ul>
 */
public class TestArtifactSearchScopes {
  private static Map<Module, TestArtifactSearchScopes> scopes = Maps.newHashMap();

  @Nullable
  public static TestArtifactSearchScopes get(@NotNull Module module) {
    if (scopes.containsKey(module)) {
      return scopes.get(module);
    }
    return null;
  }

  /**
   * Initialize the scopes of all android gradle module belong to {@param project}.
   * Need to be invoked after every gradle sync.
   */
  public static void initializeScopes(@NotNull Project project) {
    for (Map.Entry<Module, TestArtifactSearchScopes> entry : ImmutableList.copyOf(scopes.entrySet())) {
      if (entry.getValue().myModule.getProject() == project) {
        scopes.remove(entry.getKey());
      }
    }

    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      AndroidGradleModel model = AndroidGradleModel.get(module);
      if (model != null) {
        scopes.put(module, new TestArtifactSearchScopes(module, model));
      }
    }
  }

  private AndroidGradleModel myAndroidModel;
  private Module myModule;

  private TestArtifactSearchScopes(@NotNull Module module, @NotNull AndroidGradleModel model) {
    myModule = module;
    myAndroidModel = model;
  }

  private GlobalSearchScope androidTestSourceScope;
  private GlobalSearchScope unitTestSourceScope;
  private GlobalSearchScope androidTestExcludeScope;
  private GlobalSearchScope unitTestExcludeScope;

  public boolean isAndroidTestSource(@NotNull VirtualFile vFile) {
    return getAndroidTestSourceScope().accept(vFile);
  }

  public boolean isUnitTestSource(@NotNull VirtualFile vFile) {
    return getUnitTestSourceScope().accept(vFile);
  }

  @NotNull
  public GlobalSearchScope getAndroidTestSourceScope() {
    if (androidTestSourceScope == null) {
      androidTestSourceScope = getSourceScope(ARTIFACT_ANDROID_TEST);
    }
    return androidTestSourceScope;
  }

  @NotNull
  public GlobalSearchScope getUnitTestSourceScope() {
    if (unitTestSourceScope == null) {
      unitTestSourceScope = getSourceScope(ARTIFACT_UNIT_TEST);
    }
    return unitTestSourceScope;
  }

  @NotNull
  public GlobalSearchScope getAndroidTestExcludeScope() {
    if (androidTestExcludeScope == null) {
      androidTestExcludeScope = getExcludedScope(ARTIFACT_ANDROID_TEST);
    }
    return androidTestExcludeScope;
  }

  @NotNull
  public GlobalSearchScope getUnitTestExcludeScope() {
    if (unitTestExcludeScope == null) {
      unitTestExcludeScope = getExcludedScope(ARTIFACT_UNIT_TEST);
    }
    return unitTestExcludeScope;
  }

  @NotNull
  private GlobalSearchScope getSourceScope(@NotNull String artifactName) {
    Set<VirtualFile> roots = Sets.newHashSet();
    Set<File> files = Sets.newHashSet();

    // TODO consider generated source
    for (SourceProvider sourceProvider : myAndroidModel.getTestSourceProviders(artifactName)) {
      files.addAll(getAllSourceFolders(sourceProvider));
    }

    for (File file : files) {
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
      if (virtualFile != null) {
        roots.add(virtualFile);
      }
      // TODO deal with the file that not exist for now but may created by user later
    }
    return new FileRootSearchScope(myModule.getProject(), roots);
  }

  @NotNull
  private GlobalSearchScope getExcludedDependenciesScope(@NotNull String artifactName) {
    Project project = myModule.getProject();
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
    Set<Library> excludedLibrary = Sets.newHashSet();
    Set<Module> excludedModules = Sets.newHashSet();
    Set<VirtualFile> excludeRoots = Sets.newHashSet();

    BaseArtifact unitTestArtifact = myAndroidModel.getUnitTestArtifactInSelectedVariant();
    BaseArtifact androidTestArtifact = myAndroidModel.getAndroidTestArtifactInSelectedVariant();

    boolean isAndroidTestArtifact = ARTIFACT_ANDROID_TEST.equals(artifactName);

    BaseArtifact excludeArtifact = isAndroidTestArtifact ? unitTestArtifact : androidTestArtifact;
        if (excludeArtifact != null) {
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(excludeArtifact.getClassesFolder());
      if (virtualFile != null) {
        excludeRoots.add(virtualFile);
      }
    }

    DependencySet androidTestDependencies = new DependencySet();
    DependencySet unitTestDependecies = new DependencySet();
    if (unitTestArtifact != null) {
      Dependency.populate(unitTestDependecies, unitTestArtifact, DependencyScope.TEST);
    }
    if (androidTestArtifact != null) {
      Dependency.populate(androidTestDependencies, androidTestArtifact, DependencyScope.TEST);
    }

    DependencySet wanted = isAndroidTestArtifact ? androidTestDependencies : unitTestDependecies;
    DependencySet unwanted = isAndroidTestArtifact ? unitTestDependecies : androidTestDependencies;

    for (LibraryDependency dependency : unwanted.onLibraries()) {
      Library library = libraryTable.getLibraryByName(dependency.getName());
      if (library != null) {
        excludedLibrary.add(library);
      }
    }

    for (LibraryDependency dependency : wanted.onLibraries()) {
      Library library = libraryTable.getLibraryByName(dependency.getName());
      if (library != null) {
        excludedLibrary.remove(library);
      }
    }

    Module []allModules = ModuleManager.getInstance(project).getModules();

    for (ModuleDependency dependency : unwanted.onModules()) {
      Module module = dependency.getModule(allModules, null);
      if (module != null) {
        excludedModules.add(module);
      }
    }

    for (ModuleDependency dependency : wanted.onModules()) {
      Module module = dependency.getModule(allModules, null);
      if (module != null) {
        excludedModules.remove(module);
      }
    }

    for (Library library : excludedLibrary) {
      for (VirtualFile vFile : library.getFiles(OrderRootType.CLASSES)) {
        excludeRoots.add(vFile);
        if (vFile.getFileSystem() instanceof JarFileSystem) {
          File file = virtualToIoFile(vFile);
          VirtualFile jarFile = LocalFileSystem.getInstance().findFileByIoFile(file);
          excludeRoots.add(jarFile); // For easier compiler output filter
        }
      }
    }

    // This depends on all the modules are using explicit dependencies in android studio
    for (Module module : excludedModules) {
      for (ContentEntry entry : ModuleRootManager.getInstance(module).getContentEntries()) {
        excludeRoots.addAll(Arrays.asList(entry.getSourceFolderFiles()));

        VirtualFile vFile = ModuleRootManager.getInstance(module).getModuleExtension(CompilerModuleExtension.class).getCompilerOutputPath();
        excludeRoots.add(vFile); // For easier compiler output filter
      }
    }

    return new FileRootSearchScope(project, excludeRoots);
  }

  @NotNull
  private GlobalSearchScope getExcludedScope(@NotNull String artifactName) {
    GlobalSearchScope excludedSource;
    GlobalSearchScope excludedLibs = getExcludedDependenciesScope(artifactName);
    if (ARTIFACT_ANDROID_TEST.equals(artifactName)) {
      excludedSource = getUnitTestSourceScope();
    }
    else {
      excludedSource = getAndroidTestSourceScope();
    }
    return excludedSource.uniteWith(excludedLibs);
  }
}