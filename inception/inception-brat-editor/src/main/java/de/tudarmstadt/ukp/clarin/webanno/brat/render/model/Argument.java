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
package de.tudarmstadt.ukp.clarin.webanno.brat.render.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.VID;
import de.tudarmstadt.ukp.inception.support.json.BeanAsArraySerializer;

/**
 * The Arguments used during arc annotation in the form of [["Arg1","p_21346"],["Arg2","p_21341"]]
 * to denote a given arc annotation such as dependency parsing and coreference resolution
 *
 */
@JsonSerialize(using = BeanAsArraySerializer.class)
@JsonPropertyOrder(value = { "label", "target" })
public class Argument
{
    /**
     * Argument label.
     */
    private final String label;

    /**
     * The target span annotation.
     */
    private final VID target;

    public Argument(String aLabel, int aTarget)
    {
        // REC: It is fully ok that we only have "int" as the type for target, since right now only
        // spans can be the target of an argument. However, we internally wrap this as a VID
        // in order to always use the same identifier type when talking with brat (VID renders as
        // as String value, while "int" would render as a numeric value).
        this(aLabel, new VID(aTarget));
    }

    public Argument(String aLabel, VID aTarget)
    {
        label = aLabel;
        target = aTarget;
    }

    public String getLabel()
    {
        return label;
    }

    public VID getTarget()
    {
        return target;
    }
}
