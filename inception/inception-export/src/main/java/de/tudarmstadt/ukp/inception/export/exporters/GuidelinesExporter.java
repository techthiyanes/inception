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
package de.tudarmstadt.ukp.inception.export.exporters;

import static org.apache.commons.io.FileUtils.copyInputStreamToFile;
import static org.apache.commons.io.FileUtils.forceMkdir;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.ukp.clarin.webanno.api.ProjectService;
import de.tudarmstadt.ukp.clarin.webanno.api.export.FullProjectExportRequest;
import de.tudarmstadt.ukp.clarin.webanno.api.export.ProjectExportTaskMonitor;
import de.tudarmstadt.ukp.clarin.webanno.api.export.ProjectExporter;
import de.tudarmstadt.ukp.clarin.webanno.api.export.ProjectImportRequest;
import de.tudarmstadt.ukp.clarin.webanno.export.model.ExportedProject;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.support.ZipUtils;
import de.tudarmstadt.ukp.inception.export.config.DocumentImportExportServiceAutoConfiguration;

/**
 * <p>
 * This class is exposed as a Spring Component via
 * {@link DocumentImportExportServiceAutoConfiguration#guidelinesExporter}.
 * </p>
 */
public class GuidelinesExporter
    implements ProjectExporter
{
    public static final String GUIDELINE = "guideline";
    private static final String GUIDELINES_FOLDER = "/" + GUIDELINE;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ProjectService projectService;

    public GuidelinesExporter(ProjectService aProjectService)
    {
        projectService = aProjectService;
    }

    /**
     * Copy Project guidelines from the file system of this project to the export folder
     */
    @Override
    public void exportData(FullProjectExportRequest aRequest, ProjectExportTaskMonitor aMonitor,
            ExportedProject aExProject, File aStage)
        throws Exception
    {
        File guidelineDir = new File(aStage + GUIDELINES_FOLDER);
        FileUtils.forceMkdir(guidelineDir);
        File annotationGuidlines = projectService.getGuidelinesFolder(aRequest.getProject());

        if (annotationGuidlines.exists()) {
            for (File annotationGuideline : annotationGuidlines.listFiles()) {
                FileUtils.copyFileToDirectory(annotationGuideline, guidelineDir);
            }
        }
    }

    /**
     * Copy guidelines from the exported project
     * 
     * @param aZip
     *            the ZIP file.
     * @param aProject
     *            the project.
     * @throws IOException
     *             if an I/O error occurs.
     */
    @Override
    public void importData(ProjectImportRequest aRequest, Project aProject,
            ExportedProject aExProject, ZipFile aZip)
        throws Exception
    {
        for (Enumeration<? extends ZipEntry> zipEnumerate = aZip.entries(); zipEnumerate
                .hasMoreElements();) {
            ZipEntry entry = (ZipEntry) zipEnumerate.nextElement();

            // Strip leading "/" that we had in ZIP files prior to 2.0.8 (bug #985)
            String entryName = ZipUtils.normalizeEntryName(entry);

            if (entryName.startsWith(GUIDELINE + "/")) {
                String fileName = FilenameUtils.getName(entry.getName());
                if (fileName.trim().isEmpty()) {
                    continue;
                }
                File guidelineDir = projectService.getGuidelinesFolder(aProject);
                forceMkdir(guidelineDir);
                copyInputStreamToFile(aZip.getInputStream(entry), new File(guidelineDir, fileName));

                log.info("Imported guideline [" + fileName + "] for project [" + aProject.getName()
                        + "] with id [" + aProject.getId() + "]");
            }
        }
    }
}
