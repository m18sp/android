/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.plugin

import com.android.tools.idea.resourceExplorer.model.DesignAsset
import org.jetbrains.android.facet.AndroidFacet
import javax.swing.JPanel

private val supportedFileTypes = setOf("svg")

/**
 * Importer for SVGs
 */
class SVGImporter : ResourceImporter {
  override fun getPresentableName() = "SVG Importer"

  override fun getConfigurationPanel(facet: AndroidFacet,
                                     callback: ConfigurationDoneCallback)
    : JPanel? {
    callback.configurationDone()
    return null
  }

  override fun userCanEditQualifiers() = true

  override fun getSupportedFileTypes() = supportedFileTypes

  override fun getSourcePreview(asset: DesignAsset): DesignAssetRenderer? =
    DesignAssetRendererManager.getInstance().getViewer(SVGAssetRenderer::class.java)

  override fun getImportPreview(asset: DesignAsset): DesignAssetRenderer? = getSourcePreview(asset)
}
