/*
 * Licensed to the Technische Universität Darmstadt under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The Technische Universität Darmstadt 
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.diam.editor.lazydetails;

import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.adapter.TypeAdapter.decodeTypeName;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.wicket.request.IRequestParameters;
import org.apache.wicket.util.string.StringValue;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.CasProvider;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.AnnotationEditorExtensionRegistry;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.exception.AnnotationException;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.feature.FeatureSupportRegistry;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer.LayerSupportRegistry;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.VID;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.Renderer;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VLazyDetailResult;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.security.model.User;
import de.tudarmstadt.ukp.inception.diam.editor.actions.LazyDetailsHandler;
import de.tudarmstadt.ukp.inception.diam.editor.config.DiamEditorAutoConfig;
import de.tudarmstadt.ukp.inception.diam.model.ajax.LazyDetail;
import de.tudarmstadt.ukp.inception.diam.model.ajax.LazyDetailsResponse;

/**
 * <p>
 * This class is exposed as a Spring Component via
 * {@link DiamEditorAutoConfig#lazyDetailsLookupService}.
 * </p>
 */
public class LazyDetailsLookupServiceImpl
    implements LazyDetailsLookupService
{
    private final AnnotationSchemaService annotationService;
    private final AnnotationEditorExtensionRegistry extensionRegistry;
    private final LayerSupportRegistry layerSupportRegistry;
    private final FeatureSupportRegistry featureSupportRegistry;

    public LazyDetailsLookupServiceImpl(AnnotationSchemaService aAnnotationService,
            AnnotationEditorExtensionRegistry aExtensionRegistry,
            LayerSupportRegistry aLayerSupportRegistry,
            FeatureSupportRegistry aFeatureSupportRegistry)
    {
        annotationService = aAnnotationService;
        extensionRegistry = aExtensionRegistry;
        layerSupportRegistry = aLayerSupportRegistry;
        featureSupportRegistry = aFeatureSupportRegistry;
    }

    @Override
    public LazyDetailsResponse actionLookupNormData(IRequestParameters request, VID paramId,
            CasProvider aCas, SourceDocument aSourceDocument, User aUser, int windowBeginOffset,
            int windowEndOffset)
        throws AnnotationException, IOException
    {
        LazyDetailsResponse response = new LazyDetailsResponse(LazyDetailsHandler.COMMAND);

        // We interpret the databaseParam as the feature which we need to look up the feature
        // support
        StringValue databaseParam = request
                .getParameterValue(LazyDetailsHandler.PARAM_LAZY_DETAIL_DATABASE);

        // We interpret the key as the feature value or as a kind of query to be handled by the
        // feature support
        StringValue keyParam = request.getParameterValue(LazyDetailsHandler.PARAM_LAZY_DETAIL_KEY);

        StringValue layerParam = request.getParameterValue(LazyDetailsHandler.PARAM_TYPE);

        if (layerParam.isEmpty() || databaseParam.isEmpty()) {
            return response;
        }

        Project project = aSourceDocument.getProject();
        String database = databaseParam.toString();
        long layerId = decodeTypeName(layerParam.toString());
        AnnotationLayer layer = annotationService.getLayer(project, layerId)
                .orElseThrow(() -> new AnnotationException(
                        "Layer with ID [" + layerId + "] does not exist in project " + project));

        // Check where the query needs to be routed: to an editor extension or to a feature support
        if (paramId.isSynthetic()) {
            if (keyParam.isEmpty()) {
                return response;
            }

            AnnotationFeature feature = annotationService.getFeature(database, layer);

            String extensionId = paramId.getExtensionId();
            response.setResults(extensionRegistry.getExtension(extensionId)
                    .renderLazyDetails(aSourceDocument, aUser, paramId, feature,
                            keyParam.toString())
                    .stream().map(d -> new LazyDetail(d.getLabel(), d.getValue()))
                    .collect(Collectors.toList()));
            return response;
        }

        List<VLazyDetailResult> details;

        // Is it a layer-level lazy detail?
        if (Renderer.QUERY_LAYER_LEVEL_DETAILS.equals(database)) {
            details = layerSupportRegistry.getLayerSupport(layer)
                    .createRenderer(layer, () -> annotationService.listAnnotationFeature(layer))
                    .renderLazyDetails(aCas.get(), paramId, windowBeginOffset, windowEndOffset);
        }
        // Is it a feature-level lazy detail?
        else {
            AnnotationFeature feature = annotationService.getFeature(database, layer);
            details = featureSupportRegistry.findExtension(feature).orElseThrow()
                    .renderLazyDetails(feature, keyParam.toString());
        }

        response.setResults(details.stream().map(d -> new LazyDetail(d.getLabel(), d.getValue()))
                .collect(toList()));

        return response;
    }

}
