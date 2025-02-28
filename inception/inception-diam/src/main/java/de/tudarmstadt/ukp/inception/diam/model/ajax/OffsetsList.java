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
package de.tudarmstadt.ukp.inception.diam.model.ajax;

import java.util.ArrayList;

/**
 * List of {@link Offsets}. Required so Jackson knows the generic type of the list when converting
 * an array of offsets from JSON to Java.
 * 
 * @deprecated Need to check if we want to keep this for DIAM
 */
@Deprecated
public class OffsetsList
    extends ArrayList<Offsets>
{
    // See
    // http://stackoverflow.com/questions/6173182/spring-json-convert-a-typed-collection-like-listmypojo

    private static final long serialVersionUID = 1441338116416225186L;
}
