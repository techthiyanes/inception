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
package de.tudarmstadt.ukp.inception.curation.service;

import static de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.CasDiff.doDiffSingle;
import static de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.CasDiff.getDiffAdapters;
import static de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.LinkCompareBehavior.LINK_ROLE_AS_LABEL;
import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.List;
import java.util.Map;

import org.apache.uima.UIMAException;
import org.apache.uima.cas.CAS;
import org.slf4j.Logger;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.CasDiff.DiffResult;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.api.DiffAdapter;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.support.StopWatch;
import de.tudarmstadt.ukp.inception.curation.config.CurationServiceAutoConfiguration;
import de.tudarmstadt.ukp.inception.curation.merge.CasMerge;
import de.tudarmstadt.ukp.inception.curation.merge.strategy.MergeStrategy;

/**
 * <p>
 * This class is exposed as a Spring Component via
 * {@link CurationServiceAutoConfiguration#curationMergeService}.
 * </p>
 */
public class CurationMergeServiceImpl
    implements CurationMergeService
{
    private final static Logger LOG = getLogger(lookup().lookupClass());

    private final AnnotationSchemaService annotationService;

    public CurationMergeServiceImpl(AnnotationSchemaService aAnnotationService)
    {
        annotationService = aAnnotationService;
    }

    @Override
    public void mergeCasses(SourceDocument aDocument, String aTargetCasUserName, CAS aTargetCas,
            Map<String, CAS> aCassesToMerge, MergeStrategy aMergeStrategy,
            List<AnnotationLayer> aLayers)
        throws UIMAException
    {
        List<DiffAdapter> adapters = getDiffAdapters(annotationService, aLayers);

        DiffResult diff;
        try (StopWatch watch = new StopWatch(LOG, "CasDiff")) {
            diff = doDiffSingle(adapters, LINK_ROLE_AS_LABEL, aCassesToMerge, 0,
                    aTargetCas.getDocumentText().length()).toResult();
        }

        try (StopWatch watch = new StopWatch(LOG, "CasMerge")) {
            CasMerge casMerge = new CasMerge(annotationService);
            casMerge.setMergeStrategy(aMergeStrategy);
            casMerge.reMergeCas(diff, aDocument, aTargetCasUserName, aTargetCas, aCassesToMerge);
        }
    }
}
