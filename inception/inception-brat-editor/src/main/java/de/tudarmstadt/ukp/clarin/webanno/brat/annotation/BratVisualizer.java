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
package de.tudarmstadt.ukp.clarin.webanno.brat.annotation;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AbstractAjaxBehavior;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.handler.TextRequestHandler;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.support.wicket.WicketUtil;

/**
 * Base class for displaying a BRAT visualization. Override methods {@code #getCollectionData()} and
 * {@code #getDocumentData()} to provide the actual data.
 */
public abstract class BratVisualizer
    extends Panel
{
    private static final Logger LOG = LoggerFactory.getLogger(BratVisualizer.class);
    private static final long serialVersionUID = -1537506294440056609L;

    protected static final String EMPTY_DOC = "{text: ''}";

    protected WebMarkupContainer vis;

    protected AbstractAjaxBehavior collProvider;

    protected AbstractAjaxBehavior docProvider;

    private @SpringBean DocumentService repository;

    public BratVisualizer(String id, IModel<?> aModel)
    {
        super(id, aModel);

        vis = new WebMarkupContainer("vis");
        vis.setOutputMarkupId(true);

        // Provides collection-level information like type definitions, styles, etc.
        collProvider = new AbstractAjaxBehavior()
        {
            private static final long serialVersionUID = 1L;

            @Override
            public void onRequest()
            {
                getRequestCycle().scheduleRequestHandlerAfterCurrent(
                        new TextRequestHandler("application/json", "UTF-8", getCollectionData()));
            }
        };

        // Provides the actual document contents
        docProvider = new AbstractAjaxBehavior()
        {
            private static final long serialVersionUID = 1L;

            @Override
            public void onRequest()
            {
                getRequestCycle().scheduleRequestHandlerAfterCurrent(
                        new TextRequestHandler("application/json", "UTF-8", getDocumentData()));
            }
        };

        add(vis);
        add(collProvider, docProvider);
    }

    public abstract String getDocumentData();

    protected String getCollectionData()
    {
        return "{}";
    }

    private String bratRenderCommand(String aJson)
    {
        String str = WicketUtil.wrapInTryCatch("Wicket.$('" + vis.getMarkupId()
                + "').dispatcher.post('renderData', [" + aJson + "]);");
        return str;
    }

    public void render(AjaxRequestTarget aTarget)
    {
        LOG.debug("[{}][{}] render", getMarkupId(), vis.getMarkupId());

        // Controls whether rendering should happen within the AJAX request or after the AJAX
        // request. Doing it within the request has the benefit of the browser only having to
        // recalculate the layout once at the end of the AJAX request (at least theoretically)
        // while deferring the rendering causes the AJAX request to complete faster, but then
        // the browser needs to recalculate its layout twice - once of any Wicket components
        // being re-rendered and once for the brat view to re-render.
        final boolean deferredRendering = false;

        StringBuilder js = new StringBuilder();

        if (deferredRendering) {
            js.append("setTimeout(function() {");
        }

        js.append(bratRenderCommand(getDocumentData()));

        if (deferredRendering) {
            js.append("}, 0);");
        }

        aTarget.appendJavaScript(js);
    }
}
