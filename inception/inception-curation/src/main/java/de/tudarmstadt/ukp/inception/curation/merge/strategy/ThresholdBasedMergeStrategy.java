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
package de.tudarmstadt.ukp.inception.curation.merge.strategy;

import static java.util.Comparator.comparing;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.CasDiff.Configuration;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.CasDiff.ConfigurationSet;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.CasDiff.DiffResult;

public class ThresholdBasedMergeStrategy
    implements MergeStrategy
{
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static final String BEAN_NAME = "thresholdBased";

    /**
     * Number of users who have to annotate an item for it to be considered. If less than the given
     * number of users have voted, the item is considered <i>Incomplete</i>.
     */
    private final int userThreshold;

    /**
     * Percentage of annotations the majority vote has to have over the second-most voted. If it is
     * set to 0, only items with a tie are considered <i>Disputed</i>. If this is set to 1, any item
     * which has not been voted for unanimously, is considered <i>Disputed</i>. A value of 0.5
     * indicates that an item is <i>Disputed</i> if one the most vote has not at least twice as many
     * votes as the second-most voted.<br />
     * Confidence is computed as abs(correct-wrong)/max(correct,wrong).
     */
    private final double confidenceThreshold;

    public ThresholdBasedMergeStrategy(int aUserThreshold, double aConfidenceThreshold)
    {
        super();
        userThreshold = aUserThreshold;
        confidenceThreshold = aConfidenceThreshold;
    }

    @Override
    public Optional<Configuration> chooseConfigurationToMerge(DiffResult aDiff,
            ConfigurationSet aCfgs)
    {
        List<Configuration> cfgsAboveUserThreshold = aCfgs.getConfigurations().stream() //
                .filter(cfg -> cfg.getCasGroupIds().size() >= userThreshold) //
                .sorted(comparing((Configuration cfg) -> cfg.getCasGroupIds().size()).reversed()) //
                .collect(Collectors.toList());

        if (cfgsAboveUserThreshold.isEmpty()) {
            LOG.trace(" `-> Not merging as no configuration meets the user threshold [{}]",
                    userThreshold);
            return Optional.empty();
        }

        Configuration best = cfgsAboveUserThreshold.get(0);
        Optional<Configuration> secondBest = Optional.empty();
        if (cfgsAboveUserThreshold.size() > 1) {
            secondBest = Optional.of(cfgsAboveUserThreshold.get(1));
        }

        double bestVoteCount = best.getCasGroupIds().size();
        double secondBestVoteCount = secondBest.map(cfg -> cfg.getCasGroupIds().size()).orElse(0);
        double confidence = ((bestVoteCount - secondBestVoteCount) / bestVoteCount);

        if (confidence > 0.0 && confidence >= confidenceThreshold) {
            return Optional.of(best);
        }

        // DISPUTED
        LOG.trace(" `-> Not merging as confidence [{}] is zero or does not meet the threshold [{}]",
                confidence, confidenceThreshold);
        return Optional.empty();
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE) //
                .append("userThreshold", userThreshold)//
                .append("confidenceThreshold", confidenceThreshold).toString();
    }
}
