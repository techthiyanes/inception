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

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.apache.uima.UIMAException;
import org.apache.uima.cas.CAS;
import org.springframework.security.access.prepost.PreAuthorize;

import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;

public interface CurationDocumentService
{
    String SERVICE_NAME = "curationDocumentService";

    // --------------------------------------------------------------------------------------------
    // Methods related to curation
    // --------------------------------------------------------------------------------------------

    /**
     * Create a curation annotation document under a special user named as "CURATION_USER"
     *
     * @param aCas
     *            the CAS.
     * @param document
     *            the source document.
     * @throws IOException
     *             if an I/O error occurs.
     */
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_USER')")
    void writeCurationCas(CAS aCas, SourceDocument document, boolean aUpdateTimestamp)
        throws IOException;

    void upgradeCurationCas(CAS aCurCas, SourceDocument document) throws UIMAException, IOException;

    /**
     * Get a curation document for the given {@link SourceDocument}
     *
     * @param document
     *            the source document.
     * @return the curation CAS.
     * @throws IOException
     *             if an I/O error occurs.
     */
    CAS readCurationCas(SourceDocument document) throws IOException;

    void deleteCurationCas(SourceDocument document) throws IOException;

    List<SourceDocument> listCuratableSourceDocuments(Project aProject);

    Optional<Long> getCurationCasTimestamp(SourceDocument aDocument) throws IOException;

    /**
     * List all curated source documents.
     *
     * @param project
     *            the project.
     * @return the source documents.
     */
    List<SourceDocument> listCuratedDocuments(Project project);

    boolean isCurationFinished(SourceDocument aDocument);

    /**
     * Check if there already is a curation CAS for the given document
     * 
     * @throws IOException
     */
    boolean existsCurationCas(SourceDocument aDocument) throws IOException;
}
