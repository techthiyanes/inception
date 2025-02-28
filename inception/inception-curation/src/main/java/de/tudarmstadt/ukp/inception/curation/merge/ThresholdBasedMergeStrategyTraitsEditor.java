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
package de.tudarmstadt.ukp.inception.curation.merge;

import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.spring.injection.annot.SpringBean;

import de.tudarmstadt.ukp.inception.curation.model.CurationWorkflow;

public class ThresholdBasedMergeStrategyTraitsEditor
    extends Panel
{
    private static final long serialVersionUID = -2412991726110951111L;

    private @SpringBean ThresholdBasedMergeStrategyFactory strategyFactory;

    private ThresholdBasedMergeStrategyTraits traits;

    public ThresholdBasedMergeStrategyTraitsEditor(String aId, IModel<CurationWorkflow> aModel)
    {
        super(aId, aModel);

        traits = strategyFactory.readTraits(aModel.getObject());

        Form<ThresholdBasedMergeStrategyTraits> form = new Form<>("form",
                CompoundPropertyModel.of(traits))
        {
            private static final long serialVersionUID = -3109239605742291123L;

            @Override
            protected void onSubmit()
            {
                super.onSubmit();

                strategyFactory.writeTraits(aModel.getObject(), traits);
            }
        };

        form.add(new NumberTextField<>("userThreshold", Integer.class).setMinimum(0));

        form.add(new NumberTextField<>("confidenceThreshold", Double.class).setMinimum(0.0d)
                .setMaximum(1.0d).setStep(0.1d));

        add(form);
    }
}
