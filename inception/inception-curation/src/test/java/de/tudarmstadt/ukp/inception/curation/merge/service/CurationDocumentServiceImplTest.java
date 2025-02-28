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
package de.tudarmstadt.ukp.inception.curation.merge.service;

import static de.tudarmstadt.ukp.clarin.webanno.model.PermissionLevel.ANNOTATOR;
import static org.apache.uima.fit.factory.TypeSystemDescriptionFactory.createTypeSystemDescription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;

import org.apache.uima.util.CasCreationUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.util.FileSystemUtils;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.DocumentImportExportService;
import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.api.ProjectService;
import de.tudarmstadt.ukp.clarin.webanno.api.config.RepositoryAutoConfiguration;
import de.tudarmstadt.ukp.clarin.webanno.api.dao.annotationservice.config.AnnotationSchemaServiceAutoConfiguration;
import de.tudarmstadt.ukp.clarin.webanno.api.dao.casstorage.config.CasStorageServiceAutoConfiguration;
import de.tudarmstadt.ukp.clarin.webanno.api.dao.documentservice.config.DocumentServiceAutoConfiguration;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocumentState;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.ProjectPermission;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.project.config.ProjectServiceAutoConfiguration;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.security.config.SecurityAutoConfiguration;
import de.tudarmstadt.ukp.clarin.webanno.security.model.User;
import de.tudarmstadt.ukp.inception.curation.config.CurationDocumentServiceAutoConfiguration;
import de.tudarmstadt.ukp.inception.curation.service.CurationDocumentService;

@EnableAutoConfiguration
@DataJpaTest(excludeAutoConfiguration = LiquibaseAutoConfiguration.class, showSql = false, //
        properties = { //
                "spring.main.banner-mode=off", //
                "repository.path=" + CurationDocumentServiceImplTest.TEST_OUTPUT_FOLDER })
@EntityScan({ //
        "de.tudarmstadt.ukp.clarin.webanno.model", //
        "de.tudarmstadt.ukp.clarin.webanno.security.model" })
@Import({ //
        DocumentServiceAutoConfiguration.class, //
        ProjectServiceAutoConfiguration.class, //
        CasStorageServiceAutoConfiguration.class, //
        RepositoryAutoConfiguration.class, //
        AnnotationSchemaServiceAutoConfiguration.class, //
        SecurityAutoConfiguration.class, //
        CurationDocumentServiceAutoConfiguration.class })
public class CurationDocumentServiceImplTest
{
    static final String TEST_OUTPUT_FOLDER = "target/test-output/CurationDocumentServiceImplTest";

    private @Autowired ProjectService projectService;
    private @Autowired UserDao userRepository;
    private @Autowired DocumentService documentService;
    private @Autowired CurationDocumentService sut;

    private User user;
    private Project project;

    @BeforeAll
    public static void setupClass()
    {
        FileSystemUtils.deleteRecursively(new File(TEST_OUTPUT_FOLDER));
    }

    @BeforeEach
    public void setup() throws Exception
    {
        user = userRepository.create(new User("anno1"));

        project = projectService.createProject(new Project("project"));
        projectService.createProjectPermission(
                new ProjectPermission(project, user.getUsername(), ANNOTATOR));
    }

    @Test
    public void thatFinishedDocumentsBecomeCuratable()
    {
        SourceDocument doc = documentService
                .createSourceDocument(new SourceDocument("doc", project, "text"));

        AnnotationDocument ann = documentService
                .createAnnotationDocument(new AnnotationDocument(user.getUsername(), doc));

        assertThat(sut.listCuratableSourceDocuments(project))
                .as("No curatable documents as long as no document is marked as finished")
                .isEmpty();

        documentService.setAnnotationDocumentState(ann, AnnotationDocumentState.FINISHED);

        assertThat(sut.listCuratableSourceDocuments(project)) //
                .as("Finished documents become curatable") //
                .contains(doc);
    }

    @SpringBootConfiguration
    public static class TestContext
    {
        @Bean
        DocumentImportExportService documentImportExportService(
                AnnotationSchemaService aSchemaService)
            throws Exception
        {
            var tsd = createTypeSystemDescription();
            var importService = mock(DocumentImportExportService.class);
            when(importService.importCasFromFile(any(), any(), any(), any()))
                    .thenReturn(CasCreationUtils.createCas(tsd, null, null, null));
            return importService;
        }
    }
}
