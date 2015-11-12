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
package com.android.tools.idea.ui.properties.core;

import com.android.tools.idea.ui.properties.ObservableProperty;
import com.google.common.base.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for all properties that need to support being set to or returning a {@code null}
 * value.
 * <p/>
 * Designed with an interface that emulates Guava's {@link Optional}.
 */
public abstract class OptionalProperty<T> extends ObservableProperty<Optional<T>> implements ObservableOptional<T> {

  public abstract void setValue(@NotNull T value);

  public abstract void clear();

  public abstract void setNullableValue(@Nullable T value);
}