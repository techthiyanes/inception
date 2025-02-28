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
package de.tudarmstadt.ukp.clarin.webanno.api.annotation;

import static java.util.Collections.unmodifiableList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ClassUtils;
import org.apache.uima.cas.CAS;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import com.googlecode.wicket.jquery.ui.widget.menu.IMenuItem;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.action.AnnotationActionHandler;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.config.AnnotationAutoConfiguration;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.exception.AnnotationException;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.VID;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VDocument;

/**
 * <p>
 * This class is exposed as a Spring Component via
 * {@link AnnotationAutoConfiguration#annotationEditorExtensionRegistry}.
 * </p>
 */
public class AnnotationEditorExtensionRegistryImpl
    implements AnnotationEditorExtensionRegistry
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final List<AnnotationEditorExtension> extensionsProxy;

    private List<AnnotationEditorExtension> extensions;

    public AnnotationEditorExtensionRegistryImpl(
            @Lazy @Autowired(required = false) List<AnnotationEditorExtension> aExtensions)
    {
        extensionsProxy = aExtensions;
    }

    @EventListener
    public void onContextRefreshedEvent(ContextRefreshedEvent aEvent)
    {
        init();
    }

    /* package private */ void init()
    {
        List<AnnotationEditorExtension> exts = new ArrayList<>();

        if (extensionsProxy != null) {
            exts.addAll(extensionsProxy);
            AnnotationAwareOrderComparator.sort(exts);

            for (AnnotationEditorExtension fs : exts) {
                log.debug("Found annotation editor extension: {}",
                        ClassUtils.getAbbreviatedName(fs.getClass(), 20));
            }
        }

        log.info("Found [{}] annotation editor extensions", exts.size());

        extensions = unmodifiableList(exts);
    }

    @Override
    public List<AnnotationEditorExtension> getExtensions()
    {
        return extensions;
    }

    @Override
    public AnnotationEditorExtension getExtension(String aId)
    {
        if (aId == null) {
            return null;
        }
        else {
            return extensions.stream().filter(ext -> aId.equals(ext.getBeanName())).findFirst()
                    .orElse(null);
        }
    }

    @Override
    public void fireAction(AnnotationActionHandler aActionHandler, AnnotatorState aModelObject,
            AjaxRequestTarget aTarget, CAS aCas, VID aParamId, String aAction)
        throws IOException, AnnotationException
    {
        for (AnnotationEditorExtension ext : getExtensions()) {

            if (!ext.getBeanName().equals(aParamId.getExtensionId())) {
                continue;
            }
            ext.handleAction(aActionHandler, aModelObject, aTarget, aCas, aParamId, aAction);
        }
    }

    @Override
    public void fireRenderRequested(AnnotatorState aState)
    {
        for (AnnotationEditorExtension ext : getExtensions()) {
            ext.renderRequested(aState);
        }
    }

    @Override
    public void fireRender(CAS aCas, AnnotatorState aState, VDocument aVdoc, int aWindowBeginOffset,
            int aWindowEndOffset)
    {
        for (AnnotationEditorExtension ext : getExtensions()) {
            ext.render(aCas, aState, aVdoc, aWindowBeginOffset, aWindowEndOffset);
        }
    }

    @Override
    public void generateContextMenuItems(List<IMenuItem> aItems)
    {
        for (AnnotationEditorExtension ext : getExtensions()) {
            ext.generateContextMenuItems(aItems);
        }
    }
}
