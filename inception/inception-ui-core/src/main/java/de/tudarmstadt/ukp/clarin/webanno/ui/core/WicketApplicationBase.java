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
package de.tudarmstadt.ukp.clarin.webanno.ui.core;

import static io.bit3.jsass.OutputStyle.EXPANDED;
import static io.bit3.jsass.OutputStyle.NESTED;
import static java.lang.System.currentTimeMillis;
import static org.apache.wicket.RuntimeConfigurationType.DEPLOYMENT;
import static org.apache.wicket.RuntimeConfigurationType.DEVELOPMENT;
import static org.apache.wicket.coep.CrossOriginEmbedderPolicyConfiguration.CoepMode.ENFORCING;
import static org.apache.wicket.coop.CrossOriginOpenerPolicyConfiguration.CoopMode.SAME_ORIGIN;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.Page;
import org.apache.wicket.authorization.strategies.CompoundAuthorizationStrategy;
import org.apache.wicket.authroles.authorization.strategies.role.RoleAuthorizationStrategy;
import org.apache.wicket.coep.CrossOriginEmbedderPolicyConfiguration;
import org.apache.wicket.coep.CrossOriginEmbedderPolicyRequestCycleListener;
import org.apache.wicket.devutils.stateless.StatelessChecker;
import org.apache.wicket.request.Response;
import org.apache.wicket.request.cycle.IRequestCycleListener;
import org.apache.wicket.request.cycle.PageRequestHandlerTracker;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.http.WebResponse;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.apache.wicket.request.resource.SharedResourceReference;
import org.apache.wicket.resource.JQueryResourceReference;
import org.apache.wicket.resource.loader.IStringResourceLoader;
import org.apache.wicket.resource.loader.NestedStringResourceLoader;

import com.giffing.wicket.spring.boot.starter.app.WicketBootSecuredWebApplication;

import de.agilecoders.wicket.core.Bootstrap;
import de.agilecoders.wicket.core.settings.IBootstrapSettings;
import de.agilecoders.wicket.sass.BootstrapSass;
import de.agilecoders.wicket.sass.SassCacheManager;
import de.agilecoders.wicket.sass.SassCompilerOptionsFactory;
import de.agilecoders.wicket.webjars.WicketWebjars;
import de.tudarmstadt.ukp.clarin.webanno.support.FileSystemResource;
import de.tudarmstadt.ukp.clarin.webanno.support.SettingsUtil;
import de.tudarmstadt.ukp.clarin.webanno.support.wicket.PatternMatchingCrossOriginEmbedderPolicyRequestCycleListener;
import de.tudarmstadt.ukp.clarin.webanno.ui.config.CssBrowserSelectorResourceBehavior;
import de.tudarmstadt.ukp.clarin.webanno.ui.config.FontAwesomeResourceBehavior;
import de.tudarmstadt.ukp.clarin.webanno.ui.config.JQueryJavascriptBehavior;
import de.tudarmstadt.ukp.clarin.webanno.ui.config.JQueryUIResourceBehavior;
import de.tudarmstadt.ukp.clarin.webanno.ui.config.KendoResourceBehavior;
import de.tudarmstadt.ukp.clarin.webanno.ui.core.bootstrap.CustomBootstrapSassReference;
import de.tudarmstadt.ukp.clarin.webanno.ui.core.kendo.WicketJQueryFocusPatchBehavior;
import io.bit3.jsass.Options;

/**
 * The Wicket application class. Sets up pages, authentication, theme, and other application-wide
 * configuration.
 */
public abstract class WicketApplicationBase
    extends WicketBootSecuredWebApplication
{
    @Override
    protected void init()
    {
        super.init();

        CompoundAuthorizationStrategy authorizationStrategy = new CompoundAuthorizationStrategy();
        authorizationStrategy.add(new RoleAuthorizationStrategy(this));
        getSecuritySettings().setAuthorizationStrategy(authorizationStrategy);

        getCspSettings().blocking().disabled();

        // if (DEVELOPMENT == getConfigurationType()) {
        // getCspSettings().reporting().strict().reportBack();
        // getCspSettings().reporting().unsafeInline().reportBack();
        // }

        // Enforce COEP while inheriting any exemptions that might already have been set e.g. via
        // WicketApplicationInitConfiguration beans
        getSecuritySettings().setCrossOriginEmbedderPolicyConfiguration(ENFORCING,
                getSecuritySettings().getCrossOriginEmbedderPolicyConfiguration().getExemptions()
                        .stream().toArray(String[]::new));
        getSecuritySettings().setCrossOriginOpenerPolicyConfiguration(SAME_ORIGIN);

        initStatelessChecker();

        initOnce();
    }

    @Override
    protected void validateInit()
    {
        super.validateInit();

        CrossOriginEmbedderPolicyConfiguration coepConfig = getSecuritySettings()
                .getCrossOriginEmbedderPolicyConfiguration();
        if (coepConfig.isEnabled()) {
            // Remove the CrossOriginEmbedderPolicyRequestCycleListener that was configured
            // by Wicket
            List<IRequestCycleListener> toRemove = new ArrayList<>();
            for (IRequestCycleListener listener : getRequestCycleListeners()) {
                if (listener instanceof CrossOriginEmbedderPolicyRequestCycleListener) {
                    toRemove.add(listener);
                }
            }
            toRemove.forEach(listener -> getRequestCycleListeners().remove(listener));

            getRequestCycleListeners().add(
                    new PatternMatchingCrossOriginEmbedderPolicyRequestCycleListener(coepConfig));
        }
    }

    protected void initOnce()
    {
        // Allow nested string resource resolving using "#(key)"
        initNestedStringResourceLoader();

        initWebFrameworks();

        initLogoReference();

        // Allow fetching the current page from non-Wicket code
        initPageRequestTracker();

        initServerTimeReporting();
    }

    private void initPageRequestTracker()
    {
        getRequestCycleListeners().add(new PageRequestHandlerTracker());
    }

    protected void initWebFrameworks()
    {
        initJQueryResourceReference();

        addJQueryJavascriptToAllPages();

        initBootstrap();

        addKendoResourcesToAllPages();

        addJQueryUIResourcesToAllPages();

        addFontAwesomeToAllPages();

        addCssBrowserSelectorToAllPages();
    }

    protected void initBootstrap()
    {
        SassCompilerOptionsFactory sassOptionsFactory = () -> {
            Options options = new Options();
            options.setOutputStyle(DEPLOYMENT == getConfigurationType() ? EXPANDED : NESTED);
            return options;
        };

        WicketWebjars.install(this);

        BootstrapSass.install(this, sassOptionsFactory);
        // Install a customized cache manager which can deal with SCSS files in JARs
        SassCacheManager cacheManager = new SassCacheManager(sassOptionsFactory);
        cacheManager.install(this);

        Bootstrap.install(this);

        IBootstrapSettings settings = Bootstrap.getSettings(this);
        settings.setCssResourceReference(CustomBootstrapSassReference.get());
    }

    protected void addKendoResourcesToAllPages()
    {
        getComponentInstantiationListeners().add(component -> {
            if (component instanceof Page) {
                component.add(new KendoResourceBehavior());
                component.add(new WicketJQueryFocusPatchBehavior());
            }
        });
    }

    protected void addFontAwesomeToAllPages()
    {
        getComponentInstantiationListeners().add(component -> {
            if (component instanceof Page) {
                component.add(new FontAwesomeResourceBehavior());
            }
        });
    }

    protected void addCssBrowserSelectorToAllPages()
    {
        getComponentInstantiationListeners().add(component -> {
            if (component instanceof Page) {
                component.add(new CssBrowserSelectorResourceBehavior());
            }
        });
    }

    protected void addJQueryUIResourcesToAllPages()
    {
        getComponentInstantiationListeners().add(component -> {
            if (component instanceof Page) {
                component.add(new JQueryUIResourceBehavior());
            }
        });
    }

    protected void addJQueryJavascriptToAllPages()
    {
        getComponentInstantiationListeners().add(component -> {
            if (component instanceof Page) {
                component.add(new JQueryJavascriptBehavior());
            }
        });
    }

    protected void initLogoReference()
    {
        Properties settings = SettingsUtil.getSettings();
        String logoValue = settings.getProperty(SettingsUtil.CFG_STYLE_LOGO);
        if (StringUtils.isNotBlank(logoValue) && new File(logoValue).canRead()) {
            getSharedResources().add("logo", new FileSystemResource(new File(logoValue)));
            mountResource("/assets/logo.png", new SharedResourceReference("logo"));
        }
        else {
            mountResource("/assets/logo.png", new PackageResourceReference(getLogoLocation()));
        }
    }

    protected String getLogoLocation()
    {
        return "/de/tudarmstadt/ukp/clarin/webanno/ui/core/logo/logo.png";
    }

    protected void initJQueryResourceReference()
    {
        // See:
        // https://github.com/webanno/webanno/issues/1397
        // https://github.com/sebfz1/wicket-jquery-ui/issues/311
        getJavaScriptLibrarySettings().setJQueryReference(JQueryResourceReference.getV3());
    }

    protected void initNestedStringResourceLoader()
    {
        List<IStringResourceLoader> loaders = new ArrayList<>(
                getResourceSettings().getStringResourceLoaders());
        NestedStringResourceLoader nestedLoader = new NestedStringResourceLoader(loaders,
                Pattern.compile("#\\(([^ ]*?)\\)"));
        getResourceSettings().getStringResourceLoaders().clear();
        getResourceSettings().getStringResourceLoaders().add(nestedLoader);
    }

    protected void initStatelessChecker()
    {
        if (DEVELOPMENT.equals(getConfigurationType())) {
            getComponentPostOnBeforeRenderListeners().add(new StatelessChecker());
        }
    }

    protected void initServerTimeReporting()
    {
        Properties settings = SettingsUtil.getSettings();
        if (DEVELOPMENT != getConfigurationType()
                && !"true".equalsIgnoreCase(settings.getProperty("debug.sendServerSideTimings"))) {
            return;
        }

        getRequestCycleListeners().add(new IRequestCycleListener()
        {
            @Override
            public void onEndRequest(RequestCycle cycle)
            {
                final Response response = cycle.getResponse();
                if (response instanceof WebResponse) {
                    final long serverTime = currentTimeMillis() - cycle.getStartTime();
                    ((WebResponse) response).addHeader("Server-Timing",
                            "Server-Side;desc=\"Server-side total\";dur=" + serverTime);
                }
            }
        });
    }

    @Override
    protected Class<? extends ApplicationSession> getWebSessionClass()
    {
        return ApplicationSession.class;
    }
}
