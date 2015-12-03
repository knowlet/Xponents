/**
 * Copyright 2012-2013 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 *
 * **************************************************************************
 * NOTICE This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 *
 */
package org.opensextant.extractors.geo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;
import org.opensextant.data.Geocoding;
import org.opensextant.data.LatLon;
import org.opensextant.data.Place;
import org.opensextant.extraction.TextMatch;
import org.opensextant.util.TextUtils;

/**
 * A PlaceCandidate represents a portion of a document which has been identified
 * as a possible named geographic location. It is used to collect together the
 * information from the document (the evidence), as well as the possible
 * geographic locations it could represent (the Places ). It also contains the
 * results of the final decision to include:
 * <ul>
 * <li>placeConfidenceScore - Confidence that this is actually a place and not a
 * person, organization, or other type of entity.
 * <li>bestPlace - Of all the places with the same/similar names, which place is
 * it?
 * </ul>
 */
public class PlaceCandidate extends TextMatch {

    private String textnorm = null;

    // the location this was found in the document
    // private Long start;
    // private Long end;
    // --------------Place/NotPlace stuff ----------------------
    // which rules have expressed a Place/NotPlace opinion on this PC
    private final Set<String> rules = new HashSet<>();
    // the confidence adjustments provided by the Place/NotPlace rules
    private final List<Double> placeConfidences = new ArrayList<>();
    // --------------Disambiguation stuff ----------------------
    // the places along with their disambiguation scores
    private final Map<String, ScoredPlace> scoredPlaces = new HashMap<>();
    // the list of PlaceEvidences accumulated from the document about this PC
    private final List<PlaceEvidence> evidence = new ArrayList<>();
    // The chosen, best place:
    private ScoredPlace chosen = null;
    private Set<String> hierarchicalPaths = new HashSet<>();

    // basic constructor
    public PlaceCandidate() {
    }

    /**
     * If caller is willing to claim an explicit choice, so be it. Otherwise
     * unchosen places go to disambiguation.
     */
    public void choose(Place geo) {
        if (geo instanceof ScoredPlace) {
            chosen = (ScoredPlace) geo;
        } else if (scoredPlaces.containsKey(geo.getKey())) {
            chosen = scoredPlaces.get(geo.getKey());
        } else {
            //             
        }
    }

    /**
     * Default chooser routine. Wrapper around getBestPlace()
     * choose() takes an action, which incurs some performance cost.
     * getChosen() is a getter to give you the result, without invoking the action.
     * 
     * <pre>
     * chose()
     * geo = getChosen()
     * </pre>
     */
    public void choose() {
        getBestPlace();
    }

    /**
     *
     * @return normalized version of text.
     */
    public String getTextnorm() {
        if (textnorm == null) {
            textnorm = TextUtils.removePunctuation(TextUtils.removeDiacritics(getText())).toLowerCase();
        }
        return textnorm;
    }

    // ---- the getters and setters ---------
    //

    private String[] preTokens = null;
    private String[] postTokens = null;
    private final int DEFAULT_TOKEN_SIZE = 40;

    /**
     * Get some sense of tokens surrounding match. Possibly optimize this by
     * getting token list from SolrTextTagger (which provides the
     * lang-specifics)
     *
     * @param sourceBuffer
     */
    protected void setSurroundingTokens(String sourceBuffer) {
        int[] window = TextUtils.get_text_window(start, end - start, sourceBuffer.length(), DEFAULT_TOKEN_SIZE);

        /*
         * Get right most or left most whole tokens, for now whitespace
         * delimited. TODO: ensure whole tokens are retrieved.
         */
        setPrematchTokens(TextUtils.tokensRight(sourceBuffer.substring(window[0], window[1])));
        setPostmatchTokens(TextUtils.tokensLeft(sourceBuffer.substring(window[2], window[3])));
    }

    /**
     * Common evidence flags -- isCountry, isPerson, isOrganization,
     * abbreviation, and acronym
     */
    public boolean isCountry = false;
    public boolean isContinent = false;
    public boolean isPerson = false;
    public boolean isOrganization = false;
    public boolean isAbbreviation = false;
    public boolean isAcronym = false;

    /**
     * After candidate has been scored and all, the final best place is the
     * geocoding result for the given name in context.
     */
    public Geocoding getGeocoding() {
        getBestPlace();
        return chosen;
    }

    public Place getChosen() {
        return chosen;
    }

    /**
     * Get the most highly ranked Place, or Null if empty list.
     * 
     * @return Place the best choice
     */
    public Place getBestPlace() {
        if (chosen != null) {
            return chosen;
        }

        List<ScoredPlace> tmp = new ArrayList<>();
        tmp.addAll(scoredPlaces.values());
        Collections.sort(tmp);


        chosen = tmp.get(0);
        return chosen;
    }

    /**
     * Get the disambiguation score of the most highly ranked Place, or 0.0 if
     * empty list.
     * 
     * @return score of best place
     */
    public Double getBestPlaceScore() {
        if (chosen != null) {
            return chosen.getScore();
        }
        return 0.0;
    }

    /**
     * @return true = if a Place. Does our confidence indicate that this is
     *         actually a place?
     */
    public boolean isPlace() {
        return (this.getPlaceConfidenceScore() > 0.0);
    }

    public Collection<ScoredPlace> getPlaces() {
        return scoredPlaces.values();
    }

    // add a new place with a default score
    public void addPlace(ScoredPlace place) {
        this.addPlace(place, defaultScore(place));
        this.rules.add("DefaultScore");
    }

    // add a new place with a specific score
    public void addPlace(ScoredPlace place, Double score) {
        place.setScore(score);
        this.scoredPlaces.put(place.getKey(), place);
        this.hierarchicalPaths.add(place.getHierarchicalPath());
    }

    public static final double NAME_WEIGHT = 0.1;
    public static final double FEAT_WEIGHT = 0.2;
    public static final double LOCATION_BIAS_WEIGHT = 0.1;

    /**
     * Given this candidate, how do you score the provided place
     * just based on those place properties (and not on context, document properties,
     * or other evidence)?
     * 
     * This 'should' produce a base score of something between 0 and 1.0, or 0..10.
     * These scores do not necessarily need to stay in that range, as they are all relative.
     * However, as rules fire and compare location data it is better to stay in a known range
     * for sanity sake.
     * 
     * @param g
     * @return
     */
    public double defaultScore(Place g) {
        double sn = scoreName(g);
        double sf = scoreFeature(g);
        double sb = g.getId_bias();

        double baseScore = (NAME_WEIGHT * sn) + (FEAT_WEIGHT * sf) + (LOCATION_BIAS_WEIGHT * sb);
        return 10 * baseScore;
    }

    /**
     * Produce a goodness score of 0..1
     * 
     * Trivial examples of name matching:
     * 
     * <pre>
     *  given some patterns, 'geo' match Text
     * 
     *   case 1. 'Alberta' matches ALBERTA or alberta just fine. 
     *   case 2. 'La' matches LA, however, knowing "LA" is a acronym/abbreviation 
     *       adds to the score of any geo that actually is "LA"
     *   case 3. 'Afghanestan' matches Afghanistan, but decrement because it is not perfectly spelled.
     * 
     * </pre>
     * 
     * @param g
     * @return
     */
    protected double scoreName(Place g) {
        int startingScore = getTextnorm().length();
        int editDist = StringUtils.getLevenshteinDistance(getTextnorm(), g.getNamenorm());
        int score = startingScore - editDist;
        if (isUpper() && (g.isAbbreviation() || TextUtils.isUpper(g.getName()))) {
            ++score;
        }
        return (float) score / startingScore;
    }

    /**
     * A preference for features that are major places or boundaries.
     * 
     * @param g
     * @return
     */
    protected double scoreFeature(Place g) {
        int score = 0;

        // Major Place Rule covers 'A' and 'P' feature types, as such things require more context
        //
        if ("P".equals(g.getFeatureClass())) {
            score += 2;
        } else if ("S".equals(g.getFeatureClass())) {
            score += 1;
        }

        return (float) score / 2;
    }

    // increment the score of an existing place
    public void incrementPlaceScore(Place place, Double score) {
        ScoredPlace currentScore = this.scoredPlaces.get(place.getKey());
        if (currentScore != null) {
            currentScore.incrementScore(score);
        } else {
            // logger.error("Tried to increment a score for a non-existent
            // Place");
        }
    }

    // set the score of an existing place
    public void setPlaceScore(ScoredPlace place, Double score) {
        if (!this.scoredPlaces.containsKey(place.getKey())) {
            // log.error("Tried to increment a score for a non-existent Place");
            return;
        }
        addPlace(place, score);
    }

    public Collection<String> getRules() {
        return rules;
    }

    public boolean hasRule(String rule) {
        return rules.contains(rule);
    }

    public List<Double> getConfidences() {
        return placeConfidences;
    }

    // check if at least one of the Places has the given country code
    public boolean possibleCountry(String cc) {
        for (Place p : scoredPlaces.values()) {
            if (p.getCountryCode() != null && p.getCountryCode().equalsIgnoreCase(cc)) {
                return true;
            }
        }
        return false;
    }

    // check if at least one of the Places has the given admin code
    public boolean possibleAdmin(String adm, String cc) {

        // check the non-null admins first
        for (Place p : scoredPlaces.values()) {
            if (p.getAdmin1() != null && p.getAdmin1().equalsIgnoreCase(adm)) {
                return true;
            }
        }

        // some adm1codes are null, a null admin of the correct country could be
        // possible match
        for (Place p : scoredPlaces.values()) {
            if (p.getAdmin1() == null && p.getCountryCode().equalsIgnoreCase(cc)) {
                return true;
            }
        }

        return false;
    }

    public void addRuleAndConfidence(String rule, Double conf) {
        rules.add(rule);
        placeConfidences.add(conf);
    }

    /**
     * Get the PlaceConfidence score. This is the confidence that this
     * PlaceCandidate represents a named place and not a person,organization or
     * other entity.
     *
     * @return the place confidence score
     */
    public Double getPlaceConfidenceScore() {
        if (placeConfidences.size() == 0) {
            return 0.0;
        }

        // average of placeConfidences
        Double total = 0.0;
        for (Double tmpScore : placeConfidences) {
            total = total + tmpScore;
        }
        Double tmp = total / placeConfidences.size();

        // ensure the final score is within +-1.0
        if (tmp > 1.0) {
            tmp = 1.0;
        }

        if (tmp < -1.0) {
            tmp = -1.0;
        }

        return tmp;
    }

    public void addEvidence(PlaceEvidence evidence) {
        this.evidence.add(evidence);
        if (evidence.getRule() != null) {
            this.rules.add(evidence.getRule());
        }
    }

    public void addEvidence(String rule, double weight, Place ev) {
        addEvidence(new PlaceEvidence(ev, rule, weight));
    }

    // some convenience methods to add evidence
    public void addEvidence(String rule, double weight, String cc, String adm1, String fclass, String fcode,
            LatLon geo) {
        PlaceEvidence ev = new PlaceEvidence();
        ev.setRule(rule);
        ev.setWeight(weight);
        if (cc != null) {
            ev.setCountryCode(cc);
        }
        if (adm1 != null) {
            ev.setAdmin1(adm1);
        }
        if (fclass != null) {
            ev.setFeatureClass(fclass);
        }
        if (fcode != null) {
            ev.setFeatureCode(fcode);
        }
        if (geo != null) {
            ev.setLatLon(geo);
        }
        this.evidence.add(ev);
    }

    public void addCountryEvidence(String rule, double weight, String cc, Place geo) {
        PlaceEvidence ev = new PlaceEvidence();
        ev.setRule(rule);
        ev.setWeight(weight);
        ev.setCountryCode(cc);
        this.evidence.add(ev);

        ev.setEvaluated(true);
        this.incrementPlaceScore(geo, /*1 x */ weight);
    }

    public void addAdmin1Evidence(String rule, double weight, String adm1, String cc) {
        PlaceEvidence ev = new PlaceEvidence();
        ev.setRule(rule);
        ev.setWeight(weight);
        ev.setAdmin1(adm1);
        ev.setCountryCode(cc);
        this.evidence.add(ev);
    }

    public void addFeatureClassEvidence(String rule, double weight, String fclass) {
        PlaceEvidence ev = new PlaceEvidence();
        ev.setRule(rule);
        ev.setWeight(weight);
        ev.setFeatureClass(fclass);
        this.evidence.add(ev);
    }

    public void addFeatureCodeEvidence(String rule, double weight, String fcode) {
        PlaceEvidence ev = new PlaceEvidence();
        ev.setRule(rule);
        ev.setWeight(weight);
        ev.setFeatureCode(fcode);
        this.evidence.add(ev);
    }

    public void addGeocoordEvidence(String rule, double weight, LatLon coord, Place geo, double proximityScore) {
        PlaceEvidence ev = new PlaceEvidence();
        ev.setRule(rule);
        ev.setWeight(weight);
        ev.setLatLon(coord);
        this.evidence.add(ev);
        //
        ev.setEvaluated(true);
        this.incrementPlaceScore(geo, weight * proximityScore);
        // The indirect connection between found coord and closest geo candidate 
        // is assessed here.  The score for geo has already be incremented.
    }

    public List<PlaceEvidence> getEvidence() {
        return this.evidence;
    }

    public boolean hasPlaces() {
        return !this.scoredPlaces.isEmpty();
    }

    // an overide of toString to get a meaningful representation of this PC
    @Override
    public String toString() {
        StringBuilder tmp = new StringBuilder(getText());
        tmp.append("(");
        tmp.append(String.format("(%02.1f/%d)", this.getPlaceConfidenceScore(), this.scoredPlaces.size()));
        tmp.append("\nRules=");
        tmp.append(rules.toString());
        tmp.append("\nEvidence=");
        tmp.append(evidence.toString());

        //this.sort();
        tmp.append("\nPlaces=\n");
        //for (int i = scoredPlaces.size() - 1; i >= 0; --i) {
        for (ScoredPlace p : scoredPlaces.values()) {
            tmp.append("\t");
            tmp.append(p.toString());
            tmp.append(" = ");
            tmp.append(String.format("%04.4f", p.getScore()));
            tmp.append("\n");
        }
        return tmp.toString();
    }

    /**
     * @return the preTokens
     */
    public String[] getPrematchTokens() {
        return preTokens;
    }

    /**
     * @param tok
     *            the preTokens to set
     */
    public void setPrematchTokens(String[] tok) {
        this.preTokens = tok;
    }

    /**
     * @return the postTokens
     */
    public String[] getPostmatchTokens() {
        return postTokens;
    }

    /**
     * @param tok
     *            the postTokens to set
     */
    public void setPostmatchTokens(String[] tok) {
        this.postTokens = tok;
    }

    /**
     * Given a path, 'a.b' ( province b in country a),
     * see if this name is present there.
     * 
     * @param path
     * @return
     */
    public boolean presentInHierarchy(String path) {
        return this.hierarchicalPaths.contains(path);
    }
}
