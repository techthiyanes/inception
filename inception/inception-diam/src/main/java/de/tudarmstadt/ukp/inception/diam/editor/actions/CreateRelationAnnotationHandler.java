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
package de.tudarmstadt.ukp.inception.diam.editor.actions;

import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.selectAnnotationByAddr;

import java.io.IOException;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.feedback.IFeedback;
import org.apache.wicket.request.IRequestParameters;
import org.apache.wicket.request.Request;
import org.springframework.core.annotation.Order;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.exception.AnnotationException;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.Selection;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.VID;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.page.AnnotationPageBase;
import de.tudarmstadt.ukp.inception.diam.editor.config.DiamEditorAutoConfig;
import de.tudarmstadt.ukp.inception.diam.model.ajax.DefaultAjaxResponse;

/**
 * <p>
 * This class is exposed as a Spring Component via
 * {@link DiamEditorAutoConfig#createRelationAnnotationHandler}.
 * </p>
 */
@Order(EditorAjaxRequestHandler.PRIO_ANNOTATION_HANDLER)
public class CreateRelationAnnotationHandler
    extends EditorAjaxRequestHandlerBase
{
    public static final String COMMAND = "arcOpenDialog";

    @Override
    public String getCommand()
    {
        return COMMAND;
    }

    @Override
    public DefaultAjaxResponse handle(AjaxRequestTarget aTarget, Request aRequest)
    {
        try {
            AnnotationPageBase page = getPage();
            CAS cas = page.getEditorCas();
            actionArc(aTarget, aRequest.getRequestParameters(), cas);
            return new DefaultAjaxResponse(getAction(aRequest));
        }
        catch (Exception e) {
            return handleError("Unable to load data", e);
        }
    }

    private void actionArc(AjaxRequestTarget aTarget, IRequestParameters request, CAS aCas)
        throws IOException, AnnotationException
    {
        AnnotationPageBase page = getPage();

        VID origin = VID.parse(request.getParameterValue(PARAM_ORIGIN_SPAN_ID).toString());
        VID target = VID.parse(request.getParameterValue(PARAM_TARGET_SPAN_ID).toString());

        if (origin.isSynthetic() || target.isSynthetic()) {
            page.error("Relations cannot be created from/to synthetic annotations");
            aTarget.addChildren(page, IFeedback.class);
            return;
        }

        AnnotationFS originFs = selectAnnotationByAddr(aCas, origin.getId());
        AnnotationFS targetFs = selectAnnotationByAddr(aCas, target.getId());

        AnnotatorState state = page.getModelObject();
        Selection selection = state.getSelection();
        selection.selectArc(originFs, targetFs);

        page.getAnnotationActionHandler().actionCreateOrUpdate(aTarget, aCas);
    }
}
