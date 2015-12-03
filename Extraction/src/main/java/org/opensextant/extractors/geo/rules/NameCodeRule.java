/**
 * Copyright 2014 The MITRE Corporation.
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
 */

package org.opensextant.extractors.geo.rules;

import java.util.List;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceEvidence;

/**
 * A rule that associates a CODE with a NAME, when the pattern
 *
 * "NAME, CODE" appears within N characters of each other.
 * 
 * If CODE.adm1 == NAME.adm1 and CODE is an ADM1 boundary, then flag this is significant.
 * 
 * 
 *
 * @author ubaldino
 *
 */
public class NameCodeRule extends GeocodeRule {

    private static int MAX_CHAR_DIST = 5;

    public final static String NAME_ADMCODE_RULE = "AdminCode";
    public final static String NAME_ADMNAME_RULE = "AdminName";

    public NameCodeRule() {
        NAME = "AdminCodeOrName";
        weight = 4;
    }

    /**
     * Requirement: List of place candidate is a linked list.
     */
    @Override
    public void evaluate(final List<PlaceCandidate> names) {
        for (int x = 0; x < names.size() - 1; ++x) {
            PlaceCandidate name = names.get(x);
            PlaceCandidate code = names.get(x + 1); /* code or name of admin area*/

            if (name.isFilteredOut() || code.isFilteredOut()) {
                continue;
            }

            /*
             * Test if SOMENAME, CODE is the case. a1.....a2.b1.., where b1 > a2
             * > a1, but distance is minimal from end of name to start of code.
             *
             */
            if ((code.start - name.end) > MAX_CHAR_DIST) {
                continue;
            }

            /*
             * Not supporting lowercase codes/abbreviations.  'la', 'is', 'un', etc.
             */
            if (code.isLower()) {
                continue;
            }

            /*
             * by this point a place name tag should be marked as a name or
             * code/abbrev. Match the abbreviation with a geographic location
             * that is a state, county, district, etc.
             */

            log.debug("{} name, code: {} in {}?", NAME, name.getText(), code.getText());
            for (Place geo : code.getPlaces()) {
                if (!geo.isAdministrative()) {
                    continue;
                }
                // Provinces, states, districts, etc. Only. 
                //
                // Make sure you can match an province name or code with the gazetteer entries found:
                //   Boston, Ma.  ==== for 'Ma', resolve to an abbreviation for Massachusetts
                //                     Ignore places called 'Ma'
                // 
                // Place ('Ma') == will have gazetteer metadata indicating if this is a valid abbreviated code for a place. 
                // PlaceCandidate('Ma.') will have textual metadata from given text indicating if it is a code, MA, or abbrev. 'Ma.'
                // 
                // These two situations must match here.   We ignore geo locations that do not fit this profile.
                // 
                boolean lexicalMatch = ((code.isAbbreviation && geo.isAbbreviation()) ||
                        (!code.isAbbreviation && !geo.isAbbreviation()));
                // 
                if (!lexicalMatch) {
                    continue;
                }

                String adm1 = geo.getHierarchicalPath();
                if (adm1 == null) {
                    log.info("ADM1 hierarchical path should not be null");
                    continue;
                }

                if (name.presentInHierarchy(adm1)) {
                    // Associate the CODE to the NAME that precedes it.
                    // 
                    PlaceEvidence ev = new PlaceEvidence();
                    ev.setCountryCode(geo.getCountryCode());
                    ev.setAdmin1(geo.getAdmin1());
                    ev.setEvaluated(true); // Shunt. Evaluate this rule here.

                    if (geo.isAbbreviation() && (code.isAbbreviation || code.isAcronym)) {
                        ev.setRule(NAME_ADMCODE_RULE);
                        ev.setWeight(weight);

                    } else {
                        ev.setRule(NAME_ADMNAME_RULE);
                        ev.setWeight(weight + 1);
                    }
                    name.addEvidence(ev);

                    if (boundaryObserver != null) {
                        boundaryObserver.boundaryLevel1InScope(geo);
                    }

                    //
                    // 
                    for (Place nameGeo : name.getPlaces()) {
                        if (adm1.equals(nameGeo.getHierarchicalPath())) {
                            name.incrementPlaceScore(nameGeo, ev.getWeight());
                            break;
                        }
                    }

                    break;
                }
            }
        }
    }

    /**
     * No-op.
     */
    @Override
    public void evaluate(PlaceCandidate name, Place geo) {
        // no-op

    }

}
