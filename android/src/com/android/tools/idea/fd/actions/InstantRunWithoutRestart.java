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
package com.android.tools.idea.fd.actions;

import com.android.ddmlib.IDevice;
import com.android.tools.fd.client.UpdateMode;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunSettings;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Ref;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Action which performs an instant run, without restarting
 */
public class InstantRunWithoutRestart extends AnAction {
  public InstantRunWithoutRestart() {
    this("Perform Instant Run", AndroidIcons.RunIcons.Replay);
  }

  protected InstantRunWithoutRestart(String title, @NotNull Icon icon) {
    super(title, null, icon);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Module module = LangDataKeys.MODULE.getData(e.getDataContext());
    if (module == null) {
      return;
    }
    perform(module);
  }

  private void perform(Module module) {
    Project project = module.getProject();
    if (!InstantRunSettings.isInstantRunEnabled(project) || !InstantRunManager.isPatchableApp(module)) {
      return;
    }
    List<IDevice> devices = InstantRunManager.findDevices(project);
    InstantRunManager manager = InstantRunManager.get(project);
    for (IDevice device : devices) {
      if (InstantRunManager.isAppRunning(device, module)) {
        if (InstantRunManager.buildIdsMatch(device, module)) {
          performUpdate(manager, device, getUpdateMode(), module);
        } else {
          InstantRunManager.postBalloon(MessageType.ERROR,
                                        "Local Gradle build id doesn't match what's installed on the device; full build required",
                                        project);
        }
        break;
      }
    }
  }

  private static void performUpdate(@NotNull InstantRunManager manager,
                                    @NotNull IDevice device,
                                    @NotNull UpdateMode updateMode,
                                    @Nullable Module module) {
    AndroidFacet facet = manager.findAppModule(module);
    if (facet != null) {
      AndroidGradleModel model = AndroidGradleModel.get(facet);
      if (model != null) {
        runGradle(manager, device, model, facet, updateMode);
      }
    }
  }

  private static void runGradle(@NotNull final InstantRunManager manager,
                                @NotNull final IDevice device,
                                @NotNull final AndroidGradleModel model,
                                @NotNull final AndroidFacet facet,
                                @NotNull final UpdateMode updateMode) {
    File arsc = InstantRunManager.findResourceArsc(facet);
    final long arscBefore = arsc != null ? arsc.lastModified() : 0L;

    // Clean out *old* patch files (e.g. from a previous build such that if you for example
    // only change a resource, we don't redeploy the same .dex file over and over!
    // This should be performed by the Gradle plugin; this is a temporary workaround.
    InstantRunManager.removeOldPatches(model);

    final Project project = facet.getModule().getProject();
    final GradleInvoker invoker = GradleInvoker.getInstance(project);

    final Ref<GradleInvoker.AfterGradleInvocationTask> reference = Ref.create();
    final GradleInvoker.AfterGradleInvocationTask task = new GradleInvoker.AfterGradleInvocationTask() {
      @Override
      public void execute(@NotNull GradleInvocationResult result) {
        // Get rid of listener. We should add more direct task listening to the GradleTasksExecutor; this
        // seems race-condition and unintentional side effect prone.
        invoker.removeAfterGradleInvocationTask(reference.get());

        // Build is done: send message to app etc
        manager.pushChanges(device, model, facet, updateMode, arscBefore);
      }
    };
    reference.set(task);
    invoker.addAfterGradleInvocationTask(task);
    String taskName = InstantRunManager.getIncrementalDexTask(model, facet.getModule());
    invoker.executeTasks(Collections.singletonList(taskName));
  }

  @NotNull
  protected UpdateMode getUpdateMode() {
    return UpdateMode.HOT_SWAP;
  }
}
