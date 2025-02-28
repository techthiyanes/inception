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
package de.tudarmstadt.ukp.inception.curation.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import java.io.File;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import de.tudarmstadt.ukp.clarin.webanno.api.export.FullProjectExportRequest;
import de.tudarmstadt.ukp.clarin.webanno.api.export.ProjectExportTaskMonitor;
import de.tudarmstadt.ukp.clarin.webanno.api.export.ProjectImportRequest;
import de.tudarmstadt.ukp.clarin.webanno.export.model.ExportedProject;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.inception.curation.merge.ThresholdBasedMergeStrategyFactory;
import de.tudarmstadt.ukp.inception.curation.merge.ThresholdBasedMergeStrategyFactoryImpl;
import de.tudarmstadt.ukp.inception.curation.merge.ThresholdBasedMergeStrategyTraits;
import de.tudarmstadt.ukp.inception.curation.model.CurationWorkflow;
import de.tudarmstadt.ukp.inception.curation.service.CurationService;

public class CurationWorkflowExporterTest
{
    private @Mock CurationService curationService;
    private Project project;

    private CurationWorkflowExporter sut;

    private ThresholdBasedMergeStrategyFactory factory;

    @BeforeEach
    public void setUp()
    {
        openMocks(this);

        project = new Project();
        project.setId(1l);
        project.setName("Test Project");

        sut = new CurationWorkflowExporter(curationService);

        factory = new ThresholdBasedMergeStrategyFactoryImpl();
    }

    @Test
    public void thatExportingWorks() throws Exception
    {
        when(curationService.readOrCreateCurationWorkflow(project)).thenReturn(curationWorkflow());

        // Export the project and import it again
        ArgumentCaptor<CurationWorkflow> captor = runExportImportAndFetchCurationWorkflow();

        // Check that after re-importing the exported projects, they are identical to the original
        assertThat(captor.getAllValues()).usingRecursiveFieldByFieldElementComparator()
                .containsExactly(curationWorkflow());
    }

    private ArgumentCaptor<CurationWorkflow> runExportImportAndFetchCurationWorkflow()
        throws Exception
    {
        // Export the project
        FullProjectExportRequest exportRequest = new FullProjectExportRequest(project, null, false);
        ProjectExportTaskMonitor monitor = new ProjectExportTaskMonitor(project, null, "test");
        ExportedProject exportedProject = new ExportedProject();
        File file = mock(File.class);

        sut.exportData(exportRequest, monitor, exportedProject, file);

        // Import the project again
        ArgumentCaptor<CurationWorkflow> captor = ArgumentCaptor.forClass(CurationWorkflow.class);
        doNothing().when(curationService).createOrUpdateCurationWorkflow(captor.capture());

        ProjectImportRequest importRequest = new ProjectImportRequest(true);
        ZipFile zipFile = mock(ZipFile.class);
        sut.importData(importRequest, project, exportedProject, zipFile);

        return captor;
    }

    private CurationWorkflow curationWorkflow()
    {
        CurationWorkflow curationWorkflow = new CurationWorkflow();
        curationWorkflow.setProject(project);
        curationWorkflow.setMergeStrategy(factory.getId());
        factory.writeTraits(curationWorkflow, new ThresholdBasedMergeStrategyTraits());
        return curationWorkflow;
    }
}
