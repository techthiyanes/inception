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
package de.tudarmstadt.ukp.clarin.webanno.api.annotation.util;

import static org.apache.uima.cas.CAS.FEATURE_BASE_NAME_BEGIN;
import static org.apache.uima.cas.CAS.FEATURE_BASE_NAME_END;
import static org.apache.uima.cas.CAS.FEATURE_BASE_NAME_LANGUAGE;
import static org.apache.uima.cas.impl.Serialization.deserializeCASComplete;
import static org.apache.uima.cas.impl.Serialization.serializeCASComplete;
import static org.apache.uima.fit.util.CasUtil.getType;
import static org.apache.uima.fit.util.CasUtil.select;
import static org.apache.uima.fit.util.CasUtil.selectAt;
import static org.apache.uima.fit.util.CasUtil.selectCovering;
import static org.apache.uima.fit.util.CasUtil.selectSingle;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.ArrayFS;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.impl.CASCompleteSerializer;
import org.apache.uima.cas.impl.CASImpl;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.fit.util.FSUtil;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.CasCreationUtils;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.adapter.FeatureFilter;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.LinkWithRoleModel;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;

/**
 * Contain Methods for updating CAS Objects directed from brat UI, different utility methods to
 * process the CAS such getting the sentence address, determine page numbers,...
 */
public class WebAnnoCasUtil
{
    private static final String PROP_ENFORCE_CAS_THREAD_LOCK = "webanno.debug.enforce_cas_thread_lock";

    private static final boolean ENFORCE_CAS_THREAD_LOCK = System
            .getProperty(PROP_ENFORCE_CAS_THREAD_LOCK, "true").equals("true");

    public static CAS createCas(TypeSystemDescription aTSD) throws ResourceInitializationException
    {
        CAS cas = CasCreationUtils.createCas(aTSD, null, null);

        if (ENFORCE_CAS_THREAD_LOCK) {
            cas = (CAS) Proxy.newProxyInstance(cas.getClass().getClassLoader(),
                    new Class[] { CAS.class }, new ThreadLockingInvocationHandler(cas));
        }

        return cas;
    }

    public static CAS createCas() throws ResourceInitializationException
    {
        return createCas(null);
    }

    /**
     * Creates a copy of the given CAS.
     * 
     * @param aOriginal
     *            the original CAS
     * @return the copy
     * @throws UIMAException
     *             if there was a problem preparing the copy.
     */
    public static CAS createCasCopy(CAS aOriginal) throws UIMAException
    {
        CAS copy = CasCreationUtils.createCas((TypeSystemDescription) null, null, null);
        CASCompleteSerializer serializer = serializeCASComplete((CASImpl) getRealCas(aOriginal));
        deserializeCASComplete(serializer, (CASImpl) getRealCas(copy));
        return copy;
    }

    public static CAS getRealCas(CAS aCas)
    {
        if (!ENFORCE_CAS_THREAD_LOCK) {
            return aCas;
        }

        if (!Proxy.isProxyClass(aCas.getClass())) {
            return aCas;
        }

        ThreadLockingInvocationHandler handler = (ThreadLockingInvocationHandler) Proxy
                .getInvocationHandler(aCas);
        return (CAS) handler.getTarget();
    }

    public static void transferCasOwnershipToCurrentThread(CAS aCas)
    {
        if (!ENFORCE_CAS_THREAD_LOCK) {
            return;
        }

        if (!Proxy.isProxyClass(aCas.getClass())) {
            return;
        }

        ThreadLockingInvocationHandler handler = (ThreadLockingInvocationHandler) Proxy
                .getInvocationHandler(aCas);
        handler.transferOwnershipToCurrentThread();
    }

    /**
     * Return true if these two annotations agree on every non slot features
     */
    public static boolean isEquivalentSpanAnnotation(AnnotationFS aFs1, AnnotationFS aFs2,
            FeatureFilter aFilter)
    {
        // Check offsets (because they are excluded by shouldIgnoreFeatureOnMerge())
        if (aFs1.getBegin() != aFs2.getBegin() || aFs1.getEnd() != aFs2.getEnd()) {
            return false;
        }

        // Check the features (basically limiting to the primitive features)
        for (Feature f1 : aFs1.getType().getFeatures()) {
            if (aFilter != null && !aFilter.isAllowed(aFs1, f1)) {
                continue;
            }

            Object value1 = getFeatureValue(aFs1, f1);

            Feature f2 = aFs2.getType().getFeatureByBaseName(f1.getShortName());
            Object value2 = f2 != null ? getFeatureValue(aFs2, f2) : getDefaultFeatureValue(f1);

            if (!Objects.equals(value1, value2)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Do not check on agreement on Position and SOfa feature - already checked
     */
    public static boolean isBasicFeature(Feature aFeature)
    {
        // FIXME The two parts of this OR statement seem to be redundant. Also the order
        // of the check should be changes such that equals is called on the constant.
        return aFeature.getName().equals(CAS.FEATURE_FULL_NAME_SOFA)
                || aFeature.toString().equals("uima.cas.AnnotationBase:sofa");
    }

    /**
     * Get the feature value of this {@code Feature} on this annotation
     */
    public static Object getFeatureValue(FeatureStructure aFS, Feature aFeature)
    {
        switch (aFeature.getRange().getName()) {
        case CAS.TYPE_NAME_STRING:
            return aFS.getFeatureValueAsString(aFeature);
        case CAS.TYPE_NAME_BOOLEAN:
            return aFS.getBooleanValue(aFeature);
        case CAS.TYPE_NAME_FLOAT:
            return aFS.getFloatValue(aFeature);
        case CAS.TYPE_NAME_INTEGER:
            return aFS.getIntValue(aFeature);
        case CAS.TYPE_NAME_BYTE:
            return aFS.getByteValue(aFeature);
        case CAS.TYPE_NAME_DOUBLE:
            return aFS.getDoubleValue(aFeature);
        case CAS.TYPE_NAME_LONG:
            return aFS.getLongValue(aFeature);
        case CAS.TYPE_NAME_SHORT:
            return aFS.getShortValue(aFeature);
        default:
            return null;
        // return aFS.getFeatureValue(aFeature);
        }
    }

    /**
     * Get the feature value of this {@code Feature} on this annotation
     */
    public static Object getDefaultFeatureValue(Feature aFeature)
    {
        switch (aFeature.getRange().getName()) {
        case CAS.TYPE_NAME_STRING:
            return null;
        case CAS.TYPE_NAME_BOOLEAN:
            return false;
        case CAS.TYPE_NAME_FLOAT:
            return 0.0f;
        case CAS.TYPE_NAME_INTEGER:
            return 0;
        case CAS.TYPE_NAME_BYTE:
            return (byte) 0;
        case CAS.TYPE_NAME_DOUBLE:
            return 0.0d;
        case CAS.TYPE_NAME_LONG:
            return 0l;
        case CAS.TYPE_NAME_SHORT:
            return (short) 0;
        default:
            return null;
        }
    }

    /**
     * Annotation a and annotation b are the same if they have the same address.
     *
     * @param a
     *            a FS.
     * @param b
     *            a FS.
     * @return if both FSes are the same.
     */
    public static boolean isSame(FeatureStructure a, FeatureStructure b)
    {
        if (a == null || b == null) {
            return false;
        }

        if (a.getCAS() != b.getCAS()) {
            return false;
        }

        return getAddr(a) == getAddr(b);
    }

    /**
     * Check if the two given begin offsets are within the same sentence. If the second offset is at
     * the end of the sentence, it is no longer considered to be part of the sentence. Mind that
     * annotations in UIMA are half-open intervals <code>[begin,end)</code>. If there is no sentence
     * covering the offsets, the method returns <code>false</code>.
     *
     * @param aCas
     *            the CAS.
     * @param aBegin1
     *            the reference offset.
     * @param aBegin2
     *            the comparison offset.
     * @return if the two offsets are within the same sentence.
     */
    public static boolean isBeginInSameSentence(CAS aCas, int aBegin1, int aBegin2)
    {
        return selectCovering(aCas, getType(aCas, Sentence.class), aBegin1, aBegin1).stream()
                .filter(s -> s.getBegin() <= aBegin1 && aBegin1 < s.getEnd())
                .filter(s -> s.getBegin() <= aBegin2 && aBegin2 < s.getEnd()).findFirst()
                .isPresent();
    }

    /**
     * Check if the begin/end offsets are within the same sentence. If the end offset is at the end
     * of the sentence, it is considered to be part of the sentence. Mind that annotations in UIMA
     * are half-open intervals <code>[begin,end)</code>. If there is no sentence covering the
     * offsets, the method returns <code>false</code>.
     *
     * @param aCas
     *            the CAS.
     * @param aBegin
     *            the reference offset.
     * @param aEnd
     *            the comparison offset.
     * @return if the two offsets are within the same sentence.
     */
    public static boolean isBeginEndInSameSentence(CAS aCas, int aBegin, int aEnd)
    {
        var sentenceIndex = aCas.getAnnotationIndex(getType(aCas, Sentence.class));

        if (sentenceIndex.isEmpty()) {
            throw new IllegalArgumentException("Unable to check if start and end offsets are in "
                    + "the same sentence because the CAS contains no sentences!");
        }

        return StreamSupport.stream(sentenceIndex.spliterator(), false) //
                .filter(s -> s.getBegin() <= aBegin && aBegin < s.getEnd()) //
                .filter(s -> s.getBegin() <= aEnd && aEnd <= s.getEnd()) //
                .findFirst().isPresent();
    }

    public static int getAddr(FeatureStructure aFS)
    {
        return ((CASImpl) aFS.getCAS()).ll_getFSRef(aFS);
    }

    public static AnnotationFS selectAnnotationByAddr(CAS aCas, int aAddress)
    {
        return selectByAddr(aCas, AnnotationFS.class, aAddress);
    }

    public static FeatureStructure selectFsByAddr(CAS aCas, int aAddress)
    {
        return aCas.getLowLevelCAS().ll_getFSForRef(aAddress);
    }

    public static <T extends AnnotationFS> AnnotationFS selectByAddr(CAS aCas, Class<T> aType,
            int aAddress)
    {
        // Check that the type passed is actually an annotation type
        CasUtil.getAnnotationType(aCas, aType);

        return aCas.getLowLevelCAS().ll_getFSForRef(aAddress);
    }

    /**
     * Get the sentence for this CAS based on the begin and end offsets. This is basically used to
     * transform sentence address in one CAS to other sentence address for different CAS
     *
     * @param aCas
     *            the CAS.
     * @param aBegin
     *            the begin offset.
     * @return the sentence.
     */
    public static AnnotationFS selectSentenceAt(CAS aCas, int aBegin)
    {
        return CasUtil.select(aCas, getType(aCas, Sentence.class)).stream()
                .filter(s -> s.getBegin() == aBegin).findFirst().orElse(null);
    }

    public static AnnotationFS createToken(CAS aCas, int aBegin, int aEnd)
    {
        return aCas.createAnnotation(getType(aCas, Token.class), aBegin, aEnd);
    }

    public static AnnotationFS createSentence(CAS aCas, int aBegin, int aEnd)
    {
        return aCas.createAnnotation(getType(aCas, Sentence.class), aBegin, aEnd);
    }

    public static boolean exists(CAS aCas, Type aType)
    {
        return !aCas.select(aType).isEmpty();
        // return aCas.getAnnotationIndex(aType).iterator().hasNext();
    }

    /**
     * Get overlapping annotations where selection overlaps with annotations.<br>
     * Example: if annotation is (5, 13) and selection covered was from (7, 12); the annotation (5,
     * 13) is returned as overlapped selection <br>
     * If multiple annotations are [(3, 8), (9, 15), (16, 21)] and selection covered was from (10,
     * 18), overlapped annotation [(9, 15), (16, 21)] should be returned
     *
     * @param aCas
     *            a CAS containing the annotation.
     * @param aType
     *            a UIMA type.
     * @param aBegin
     *            begin offset.
     * @param aEnd
     *            end offset.
     * @return a return value.
     */
    public static List<AnnotationFS> selectOverlapping(CAS aCas, Type aType, int aBegin, int aEnd)
    {

        List<AnnotationFS> annotations = new ArrayList<>();
        for (AnnotationFS t : select(aCas, aType)) {
            if (t.getBegin() >= aEnd) {
                break;
            }
            // not yet there
            if (t.getEnd() <= aBegin) {
                continue;
            }
            annotations.add(t);
        }

        return annotations;
    }

    /**
     * Get the internal address of the first sentence annotation from CAS. This will be used as a
     * reference for moving forward/backward sentences positions
     *
     * @param aCas
     *            The CAS object assumed to contains some sentence annotations
     * @return the sentence number or -1 if aCas don't have sentence annotation
     */
    public static AnnotationFS getFirstSentence(CAS aCas)
    {
        AnnotationFS firstSentence = null;
        for (AnnotationFS s : select(aCas, getType(aCas, Sentence.class))) {
            firstSentence = s;
            break;
        }
        return firstSentence;
    }

    /**
     * Get the current sentence based on the annotation begin/end offset
     *
     * @param aCas
     *            the CAS.
     * @param aBegin
     *            the begin offset.
     * @param aEnd
     *            the end offset.
     * @return the sentence.
     */
    public static AnnotationFS getCurrentSentence(CAS aCas, int aBegin, int aEnd)
    {
        AnnotationFS currentSentence = null;
        for (AnnotationFS sentence : selectSentences(aCas)) {
            if (sentence.getBegin() <= aBegin && sentence.getEnd() > aBegin
                    && sentence.getEnd() <= aEnd) {
                currentSentence = sentence;
                break;
            }
        }
        return currentSentence;
    }

    /**
     * Get the sentence based on the annotation begin offset
     *
     * @param aCas
     *            the CAS.
     * @param aBegin
     *            the begin offset.
     * @return the sentence.
     */
    public static AnnotationFS selectSentenceCovering(CAS aCas, int aBegin)
    {
        AnnotationFS currentSentence = null;
        for (AnnotationFS sentence : select(aCas, getType(aCas, Sentence.class))) {
            if (sentence.getBegin() <= aBegin && sentence.getEnd() > aBegin) {
                currentSentence = sentence;
                break;
            }
        }
        return currentSentence;
    }

    public static AnnotationFS getNextToken(CAS aCas, int aBegin, int aEnd)
    {
        Type tokenType = getType(aCas, Token.class);

        AnnotationFS currentToken = selectAt(aCas, tokenType, aBegin, aEnd).stream().findFirst()
                .orElse(null);
        // thid happens when tokens such as Dr. OR Ms. selected with double
        // click, which make seletected text as Dr OR Ms
        if (currentToken == null) {
            currentToken = selectAt(aCas, tokenType, aBegin, aEnd + 1).stream().findFirst()
                    .orElse(null);
        }
        AnnotationFS nextToken = null;

        for (AnnotationFS token : CasUtil.selectFollowing(aCas, tokenType, currentToken, 1)) {
            nextToken = token;
        }

        return nextToken;
    }

    public static <T extends AnnotationFS> T getNext(T aRef)
    {
        CAS cas = aRef.getCAS();
        AnnotationIndex<AnnotationFS> idx = cas.getAnnotationIndex(aRef.getType());
        FSIterator<AnnotationFS> it = idx.iterator(aRef);

        if (!it.isValid()) {
            return null;
        }

        // First match is a hit?
        if (it.get() == aRef) {
            it.moveToNext();
            return it.isValid() ? (T) it.get() : null;
        }

        // Seek left until we hit the last FS that is no longer equal to the current
        boolean moved = false;
        while (it.isValid() && idx.compare(it.get(), aRef) == 0) {
            it.moveToPrevious();
            moved = true;
        }

        if (moved) {
            it.moveToNext();
        }

        while (it.isValid() && idx.compare(it.get(), aRef) == 0) {
            if (it.get() == aRef) {
                it.moveToNext();
                return it.isValid() ? (T) it.get() : null;
            }
        }

        return null;
    }

    public static <T extends AnnotationFS> T getPrev(T aRef)
    {
        CAS cas = aRef.getCAS();
        AnnotationIndex<AnnotationFS> idx = cas.getAnnotationIndex(aRef.getType());
        FSIterator<AnnotationFS> it = idx.iterator(aRef);

        if (!it.isValid()) {
            return null;
        }

        // First match is a hit?
        if (it.get() == aRef) {
            it.moveToPrevious();
            return it.isValid() ? (T) it.get() : null;
        }

        // Seek left until we hit the last FS that is no longer equal to the current
        boolean moved = false;
        while (it.isValid() && idx.compare(it.get(), aRef) == 0) {
            it.moveToPrevious();
            moved = true;
        }

        if (moved) {
            it.moveToNext();
        }

        while (it.isValid() && idx.compare(it.get(), aRef) == 0) {
            if (it.get() == aRef) {
                it.moveToPrevious();
                return it.isValid() ? (T) it.get() : null;
            }
        }

        return null;
    }

    /**
     * Get the sentence number at this specific position
     *
     * @param aCas
     *            the CAS.
     * @param aBeginOffset
     *            the begin offset.
     * @return the sentence number.
     */
    public static int getSentenceNumber(CAS aCas, int aBeginOffset)
    {
        int sentenceNumber = 0;

        Type sentenceType = getType(aCas, Sentence.class);
        Collection<AnnotationFS> sentences = select(aCas, sentenceType);
        if (sentences.isEmpty()) {
            throw new IndexOutOfBoundsException("No sentences");
        }

        for (AnnotationFS sentence : select(aCas, sentenceType)) {
            if (sentence.getBegin() <= aBeginOffset && aBeginOffset <= sentence.getEnd()) {
                sentenceNumber++;
                break;
            }
            sentenceNumber++;
        }
        return sentenceNumber;
    }

    public static Collection<AnnotationFS> selectSentences(CAS aCas)
    {
        return CasUtil.select(aCas, getType(aCas, Sentence.class));
    }

    public static Collection<AnnotationFS> selectTokens(CAS aCas)
    {
        return CasUtil.select(aCas, getType(aCas, Token.class));
    }

    public static Collection<AnnotationFS> selectTokensCovered(CAS aCas, int aBegin, int aEnd)
    {
        return CasUtil.selectCovered(aCas, getType(aCas, Token.class), aBegin, aEnd);
    }

    public static Collection<AnnotationFS> selectTokensCovered(AnnotationFS aCover)
    {
        return CasUtil.selectCovered(aCover.getCAS(), getType(aCover.getCAS(), Token.class),
                aCover);
    }

    /**
     * For a span annotation, if a sub-token is selected, display the whole text so that the user is
     * aware of what is being annotated, based on
     * {@link WebAnnoCasUtil#selectOverlapping(CAS, Type, int, int)} ISSUE - Affected text not
     * correctly displayed in annotation dialog (Bug #272)
     *
     * @param aCas
     *            the CAS.
     * @param aBeginOffset
     *            the begin offset.
     * @param aEndOffset
     *            the end offset.
     * @return the selected text.
     */
    public static String getSelectedText(CAS aCas, int aBeginOffset, int aEndOffset)
    {
        List<AnnotationFS> tokens = selectOverlapping(aCas, getType(aCas, Token.class),
                aBeginOffset, aEndOffset);
        StringBuilder seletedTextSb = new StringBuilder();
        for (AnnotationFS token : tokens) {
            seletedTextSb.append(token.getCoveredText()).append(" ");
        }
        return seletedTextSb.toString();
    }

    public static boolean isNativeUimaType(String aType)
    {
        Validate.notNull(aType, "Type must not be null");

        switch (aType) {
        case CAS.TYPE_NAME_ANNOTATION:
        case CAS.TYPE_NAME_ANNOTATION_BASE:
        case CAS.TYPE_NAME_ARRAY_BASE:
        case CAS.TYPE_NAME_BOOLEAN:
        case CAS.TYPE_NAME_BOOLEAN_ARRAY:
        case CAS.TYPE_NAME_BYTE:
        case CAS.TYPE_NAME_BYTE_ARRAY:
        case CAS.TYPE_NAME_DOCUMENT_ANNOTATION:
        case CAS.TYPE_NAME_DOUBLE:
        case CAS.TYPE_NAME_DOUBLE_ARRAY:
        case CAS.TYPE_NAME_EMPTY_FLOAT_LIST:
        case CAS.TYPE_NAME_EMPTY_FS_LIST:
        case CAS.TYPE_NAME_EMPTY_INTEGER_LIST:
        case CAS.TYPE_NAME_EMPTY_STRING_LIST:
        case CAS.TYPE_NAME_FLOAT:
        case CAS.TYPE_NAME_FLOAT_ARRAY:
        case CAS.TYPE_NAME_FLOAT_LIST:
        case CAS.TYPE_NAME_FS_ARRAY:
        case CAS.TYPE_NAME_FS_LIST:
        case CAS.TYPE_NAME_INTEGER:
        case CAS.TYPE_NAME_INTEGER_ARRAY:
        case CAS.TYPE_NAME_INTEGER_LIST:
        case CAS.TYPE_NAME_LIST_BASE:
        case CAS.TYPE_NAME_LONG:
        case CAS.TYPE_NAME_LONG_ARRAY:
        case CAS.TYPE_NAME_NON_EMPTY_FLOAT_LIST:
        case CAS.TYPE_NAME_NON_EMPTY_FS_LIST:
        case CAS.TYPE_NAME_NON_EMPTY_INTEGER_LIST:
        case CAS.TYPE_NAME_NON_EMPTY_STRING_LIST:
        case CAS.TYPE_NAME_SHORT:
        case CAS.TYPE_NAME_SHORT_ARRAY:
        case CAS.TYPE_NAME_SOFA:
        case CAS.TYPE_NAME_STRING:
        case CAS.TYPE_NAME_STRING_ARRAY:
        case CAS.TYPE_NAME_STRING_LIST:
        case CAS.TYPE_NAME_TOP:
            return true;
        }

        return false;
    }

    public static boolean isPrimitiveFeature(FeatureStructure aFS, String aFeatureName)
    {
        Feature feature = aFS.getType().getFeatureByBaseName(aFeatureName);

        if (feature == null) {
            throw new IllegalArgumentException("Type [" + aFS.getType().getName()
                    + "] has no feature called [" + aFeatureName + "]");
        }

        return isPrimitiveType(feature.getRange());
    }

    public static boolean isPrimitiveType(Type aType)
    {
        switch (aType.getName()) {
        case CAS.TYPE_NAME_STRING: // fallthrough
        case CAS.TYPE_NAME_BOOLEAN: // fallthrough
        case CAS.TYPE_NAME_FLOAT: // fallthrough
        case CAS.TYPE_NAME_INTEGER:
            return true;
        default:
            return false;
        }
    }

    public static <T> T getFeature(FeatureStructure aFS, String aFeatureName)
    {
        Feature feature = aFS.getType().getFeatureByBaseName(aFeatureName);

        if (feature == null) {
            throw new IllegalArgumentException("Type [" + aFS.getType().getName()
                    + "] has no feature called [" + aFeatureName + "]");
        }

        switch (feature.getRange().getName()) {
        case CAS.TYPE_NAME_STRING:
            return (T) aFS.getStringValue(feature);
        case CAS.TYPE_NAME_BOOLEAN:
            return (T) (Boolean) aFS.getBooleanValue(feature);
        case CAS.TYPE_NAME_FLOAT:
            return (T) (Float) aFS.getFloatValue(feature);
        case CAS.TYPE_NAME_INTEGER:
            return (T) (Integer) aFS.getIntValue(feature);
        default:
            throw new IllegalArgumentException("Cannot get value of feature [" + feature.getName()
                    + "] with type [" + feature.getRange().getName() + "]");
        }
    }

    /**
     * Set a feature value.
     *
     * @param aFS
     *            the feature structure.
     * @param aFeature
     *            the feature within the annotation whose value to set. If this parameter is
     *            {@code null} then nothing happens.
     * @param aValue
     *            the feature value.
     */
    public static void setFeature(FeatureStructure aFS, AnnotationFeature aFeature, Object aValue)
    {
        if (aFeature == null) {
            return;
        }

        Feature feature = aFS.getType().getFeatureByBaseName(aFeature.getName());

        if (feature == null) {
            throw new IllegalArgumentException("On [" + aFS.getType().getName() + "] the feature ["
                    + aFeature.getName() + "] does not exist.");
        }

        switch (aFeature.getMultiValueMode()) {
        case NONE: {
            String effectiveType = aFeature.getType();
            if (effectiveType.contains(":")) {
                effectiveType = CAS.TYPE_NAME_STRING;
            }

            // Sanity check
            if (!Objects.equals(effectiveType, feature.getRange().getName())) {
                throw new IllegalArgumentException("On [" + aFS.getType().getName() + "] feature ["
                        + aFeature.getName() + "] actual type [" + feature.getRange().getName()
                        + "] does not match expected feature type [" + effectiveType + "].");
            }

            switch (effectiveType) {
            case CAS.TYPE_NAME_STRING:
                aFS.setStringValue(feature, (String) aValue);
                break;
            case CAS.TYPE_NAME_BOOLEAN:
                aFS.setBooleanValue(feature, aValue != null ? (boolean) aValue : false);
                break;
            case CAS.TYPE_NAME_FLOAT:
                aFS.setFloatValue(feature, aValue != null ? (float) aValue : 0.0f);
                break;
            case CAS.TYPE_NAME_INTEGER:
                aFS.setIntValue(feature, aValue != null ? (int) aValue : 0);
                break;
            default:
                throw new IllegalArgumentException(
                        "Cannot set value of feature [" + aFeature.getName() + "] with type ["
                                + feature.getRange().getName() + "] to [" + aValue + "]");
            }
            break;
        }
        case ARRAY: {
            switch (aFeature.getLinkMode()) {
            case WITH_ROLE: {
                // Get type and features - we need them later in the loop
                setLinkFeature(aFS, aFeature, (List<LinkWithRoleModel>) aValue, feature);
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported link mode ["
                        + aFeature.getLinkMode() + "] on feature [" + aFeature.getName() + "]");
            }
            break;
        }
        default:
            throw new IllegalArgumentException("Unsupported multi-value mode ["
                    + aFeature.getMultiValueMode() + "] on feature [" + aFeature.getName() + "]");
        }
    }

    private static void setLinkFeature(FeatureStructure aFS, AnnotationFeature aFeature,
            List<LinkWithRoleModel> aValue, Feature feature)
    {
        Type linkType = aFS.getCAS().getTypeSystem().getType(aFeature.getLinkTypeName());
        Feature roleFeat = linkType.getFeatureByBaseName(aFeature.getLinkTypeRoleFeatureName());
        Feature targetFeat = linkType.getFeatureByBaseName(aFeature.getLinkTypeTargetFeatureName());

        // Create all the links
        // FIXME: actually we could re-use existing link link feature structures
        List<FeatureStructure> linkFSes = new ArrayList<>();

        if (aValue != null) {
            // remove duplicate links
            Set<LinkWithRoleModel> links = new HashSet<>(aValue);
            for (LinkWithRoleModel e : links) {
                // Skip links that have been added in the UI but where the target has not
                // yet been
                // set
                if (e.targetAddr == -1) {
                    continue;
                }

                FeatureStructure link = aFS.getCAS().createFS(linkType);
                link.setStringValue(roleFeat, e.role);
                link.setFeatureValue(targetFeat, selectFsByAddr(aFS.getCAS(), e.targetAddr));
                linkFSes.add(link);
            }
        }
        setLinkFeatureValue(aFS, feature, linkFSes);

    }

    public static void setLinkFeatureValue(FeatureStructure aFS, Feature aFeature,
            List<FeatureStructure> linkFSes)
    {
        // Create a new array if size differs otherwise re-use existing one
        ArrayFS array = (ArrayFS) WebAnnoCasUtil.getFeatureFS(aFS, aFeature.getShortName());
        if (array == null || (array.size() != linkFSes.size())) {
            array = aFS.getCAS().createArrayFS(linkFSes.size());
        }

        // Fill in links
        array.copyFromArray(linkFSes.toArray(new FeatureStructure[linkFSes.size()]), 0, 0,
                linkFSes.size());

        aFS.setFeatureValue(aFeature, array);
    }

    /**
     * Set a feature value.
     *
     * @param aFS
     *            the feature structure.
     * @param aFeatureName
     *            the feature within the annotation whose value to set.
     * @param aValue
     *            the feature value.
     */
    public static void setFeatureFS(FeatureStructure aFS, String aFeatureName,
            FeatureStructure aValue)
    {
        Feature labelFeature = aFS.getType().getFeatureByBaseName(aFeatureName);
        aFS.setFeatureValue(labelFeature, aValue);
    }

    /**
     * Get a feature value.
     *
     * @param aFS
     *            the feature structure.
     * @param aFeatureName
     *            the feature within the annotation whose value to set.
     * @return the feature value.
     */
    public static FeatureStructure getFeatureFS(FeatureStructure aFS, String aFeatureName)
    {
        return aFS.getFeatureValue(aFS.getType().getFeatureByBaseName(aFeatureName));
    }

    public static boolean isRequiredFeatureMissing(AnnotationFeature aFeature, FeatureStructure aFS)
    {
        return aFeature.isRequired() && CAS.TYPE_NAME_STRING.equals(aFeature.getType())
                && StringUtils.isBlank(FSUtil.getFeature(aFS, aFeature.getName(), String.class));
    }

    public static FeatureStructure createDocumentMetadata(CAS aCas)
    {
        Type type = getType(aCas, DocumentMetaData.class);
        FeatureStructure dmd;
        if (aCas.getDocumentText() != null) {
            dmd = aCas.createAnnotation(type, 0, aCas.getDocumentText().length());
        }
        else {
            dmd = aCas.createAnnotation(type, 0, 0);
        }

        // If there is already a DocumentAnnotation copy it's information and delete it
        FeatureStructure da = aCas.getDocumentAnnotation();
        if (da != null) {
            FSUtil.setFeature(dmd, FEATURE_BASE_NAME_LANGUAGE,
                    FSUtil.getFeature(da, FEATURE_BASE_NAME_LANGUAGE, String.class));
            FSUtil.setFeature(dmd, FEATURE_BASE_NAME_BEGIN,
                    FSUtil.getFeature(da, FEATURE_BASE_NAME_BEGIN, Integer.class));
            FSUtil.setFeature(dmd, FEATURE_BASE_NAME_END,
                    FSUtil.getFeature(da, FEATURE_BASE_NAME_END, Integer.class));
            aCas.removeFsFromIndexes(da);
        }
        else if (aCas.getDocumentText() != null) {
            FSUtil.setFeature(dmd, FEATURE_BASE_NAME_BEGIN, 0);
            FSUtil.setFeature(dmd, FEATURE_BASE_NAME_END, aCas.getDocumentText().length());
        }
        aCas.addFsToIndexes(dmd);
        return dmd;
    }

    public static FeatureStructure getDocumentMetadata(CAS aCas)
    {
        Type type = getType(aCas, DocumentMetaData.class);
        FeatureStructure dmd;
        try {
            dmd = selectSingle(aCas, type);
        }
        catch (IllegalArgumentException e) {
            dmd = createDocumentMetadata(aCas);
        }

        return dmd;
    }

    public static void copyDocumentMetadata(CAS aSourceView, CAS aTargetView)
    {
        // First get the DMD then create. In case the get fails, we do not create.
        FeatureStructure dmd = getDocumentMetadata(aSourceView);
        FeatureStructure docMetaData = createDocumentMetadata(aTargetView);
        FSUtil.setFeature(docMetaData, "collectionId",
                FSUtil.getFeature(dmd, "collectionId", String.class));
        FSUtil.setFeature(docMetaData, "documentBaseUri",
                FSUtil.getFeature(dmd, "documentBaseUri", String.class));
        FSUtil.setFeature(docMetaData, "documentId",
                FSUtil.getFeature(dmd, "documentId", String.class));
        FSUtil.setFeature(docMetaData, "documentTitle",
                FSUtil.getFeature(dmd, "documentTitle", String.class));
        FSUtil.setFeature(docMetaData, "documentUri",
                FSUtil.getFeature(dmd, "documentUri", String.class));
        FSUtil.setFeature(docMetaData, "isLastSegment",
                FSUtil.getFeature(dmd, "isLastSegment", Boolean.class));
        FSUtil.setFeature(docMetaData, CAS.FEATURE_BASE_NAME_LANGUAGE,
                FSUtil.getFeature(dmd, CAS.FEATURE_BASE_NAME_LANGUAGE, String.class));
    }

    public static void setDocumentId(CAS aCas, String aID)
    {
        FeatureStructure dmd = getDocumentMetadata(aCas);
        FSUtil.setFeature(dmd, "documentId", aID);
    }

    public static String getDocumentId(CAS aCas)
    {
        try {
            Type type = getType(aCas, DocumentMetaData.class);
            FeatureStructure dmd = selectSingle(aCas, type);
            return FSUtil.getFeature(dmd, "documentId", String.class);
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String getDocumentUri(CAS aCas)
    {
        try {
            Type type = getType(aCas, DocumentMetaData.class);
            FeatureStructure dmd = selectSingle(aCas, type);
            return FSUtil.getFeature(dmd, "documentUri", String.class);
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String getDocumentTitle(CAS aCas)
    {
        try {
            Type type = getType(aCas, DocumentMetaData.class);
            FeatureStructure dmd = selectSingle(aCas, type);
            return FSUtil.getFeature(dmd, "documentTitle", String.class);
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Set<FeatureStructure> findAllFeatureStructures(CAS aCas)
    {
        Set<FeatureStructure> allFSes = new LinkedHashSet<>();
        ((CASImpl) aCas).walkReachablePlusFSsSorted(allFSes::add, null, null, null);
        return allFSes;
    }
}
