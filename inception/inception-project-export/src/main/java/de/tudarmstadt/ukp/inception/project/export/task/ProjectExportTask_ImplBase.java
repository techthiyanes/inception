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
package de.tudarmstadt.ukp.inception.project.export.task;

import static de.tudarmstadt.ukp.clarin.webanno.api.export.ProjectExportTaskState.CANCELLED;
import static de.tudarmstadt.ukp.clarin.webanno.api.export.ProjectExportTaskState.COMPLETED;
import static de.tudarmstadt.ukp.clarin.webanno.api.export.ProjectExportTaskState.FAILED;
import static de.tudarmstadt.ukp.clarin.webanno.api.export.ProjectExportTaskState.RUNNING;
import static de.tudarmstadt.ukp.clarin.webanno.support.logging.Logging.KEY_PROJECT_ID;
import static de.tudarmstadt.ukp.clarin.webanno.support.logging.Logging.KEY_REPOSITORY_PATH;
import static de.tudarmstadt.ukp.clarin.webanno.support.logging.Logging.KEY_USERNAME;
import static java.lang.String.format;

import java.io.File;
import java.nio.channels.ClosedByInterruptException;

import javax.servlet.ServletContext;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.api.export.ProjectExportRequest_ImplBase;
import de.tudarmstadt.ukp.clarin.webanno.api.export.ProjectExportTaskHandle;
import de.tudarmstadt.ukp.clarin.webanno.api.export.ProjectExportTaskMonitor;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.support.logging.LogMessage;
import de.tudarmstadt.ukp.inception.project.export.model.ProjectExportTask;

public abstract class ProjectExportTask_ImplBase<R extends ProjectExportRequest_ImplBase>
    implements ProjectExportTask<R>, InitializingBean
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    // The task needs to hold on to the handle because it is used in a WeakHashMap in
    // ProjectExportService to allow access to tasks.
    private final ProjectExportTaskHandle handle;
    private final String username;
    private ProjectExportTaskMonitor monitor;
    private final Project project;
    private final R request;

    private @Autowired ServletContext servletContext;
    private @Autowired DocumentService documentService;
    private @Autowired SimpMessagingTemplate msgTemplate;

    public ProjectExportTask_ImplBase(Project aProject, R aRequest, String aUsername)
    {
        project = aProject;
        request = aRequest;
        username = aUsername;

        handle = new ProjectExportTaskHandle();
    }

    @Override
    public void afterPropertiesSet() throws Exception
    {
        monitor = new NotifyingProjectExportTaskMonitor(project, handle, request.getTitle(),
                msgTemplate);
        monitor.setCreateTime(System.currentTimeMillis());
    }

    @Override
    public void run()
    {
        try {
            // We are in a new thread. Set up thread-specific MDC
            MDC.put(KEY_USERNAME, username);
            MDC.put(KEY_PROJECT_ID, String.valueOf(project.getId()));
            MDC.put(KEY_REPOSITORY_PATH, documentService.getDir().toString());

            monitor.setState(RUNNING);
            monitor.setUrl(format("%s/ui/export/%s", servletContext.getContextPath(),
                    monitor.getHandle().getRunId()));

            File exportedFile = export(request, monitor);

            monitor.setExportedFile(exportedFile);
            monitor.setStateAndProgress(COMPLETED, 100);
        }
        catch (ClosedByInterruptException | InterruptedException e) {
            monitor.setStateAndProgress(CANCELLED, 100);
        }
        catch (Throwable e) {
            // This marks the progression as complete and causes ProgressBar#onFinished
            // to be called where we display the messages
            // Message needs to be aded before setting the state, otherwise the notification for the
            // message may be throttled and it may never be displayed
            monitor.addMessage(LogMessage.error(this, "Unexpected error during project export: %s",
                    ExceptionUtils.getRootCauseMessage(e)));
            monitor.setStateAndProgress(FAILED, 100);
            log.error("Unexpected error during project export", e);
        }
    }

    public abstract File export(R aRequest, ProjectExportTaskMonitor aMonitor) throws Exception;

    @Override
    public R getRequest()
    {
        return request;
    }

    @Override
    public ProjectExportTaskMonitor getMonitor()
    {
        return monitor;
    }

    @Override
    public ProjectExportTaskHandle getHandle()
    {
        return handle;
    }
}
