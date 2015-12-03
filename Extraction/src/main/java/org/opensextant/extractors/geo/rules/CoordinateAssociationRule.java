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

import java.util.ArrayList;
import java.util.List;

import org.opensextant.data.Geocoding;
import org.opensextant.data.Place;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.xcoord.GeocoordMatch;
import org.opensextant.util.GeodeticUtility;

import com.spatial4j.core.io.GeohashUtils;

public class CoordinateAssociationRule extends GeocodeRule {

    /**
     * Default threshold distance between a coordinate and a candidate location
     */
    public static int DEFAULT_THRESHOLD_METERS = 10000;
    /** Threshold for geohash prefix similarity */
    public static int DEFAULT_THRESHOLD_DIGITS = 5;

    public final static int GEOHASH = 0;
    public final static int HAVERSINE = 1;
    private int associationScheme = HAVERSINE;

    private List<Geocoding> coordinates = new ArrayList<>();

    public CoordinateAssociationRule() {
        weight = 5;
    }

    public CoordinateAssociationRule(int distScheme) {
        associationScheme = distScheme;
    }

    public void setCoordinates(List<Geocoding> geos) {
        coordinates = geos;
    }

    public void addCoordinate(Geocoding geo) {
        coordinates.add(geo);
    }

    public void addCoordinates(List<TextMatch> found) {
        for (TextMatch g : found) {
            if (g instanceof GeocoordMatch) {
                this.addCoordinate((GeocoordMatch) g);
                if (this.coordObserver != null) {
                    this.coordObserver.locationInScope((GeocoordMatch) g);
                }
            }
        }
    }

    @Override
    public void reset() {
        if (coordinates != null) {
            coordinates.clear();
        }
    }

    /**
     * If a particular geo is close to a candidate name/location, then add to
     * the candidate's score for that location.
     */
    @Override
    public void evaluate(PlaceCandidate name, Place geo) {
        if (coordinates == null || coordinates.isEmpty()) {
            // Perfectly allowable to be null.
            return;
        }
        if (name.isCountry) {
            return;
        }

        switch (associationScheme) {

        case HAVERSINE:
            // if geo <=> coordinates is within threshold
            // score up geo appropriately
            for (Geocoding ll : coordinates) {
                long meters = GeodeticUtility.distanceMeters(ll, geo);

                // is within
                if (meters < DEFAULT_THRESHOLD_METERS) {
                    double proximityScore = (float) (DEFAULT_THRESHOLD_METERS - meters) / DEFAULT_THRESHOLD_METERS;
                    name.addGeocoordEvidence("Coordinate", weight, ll, geo, proximityScore);                    
                }
            }
            break;

        default:
        case GEOHASH:

            // if geohash geo matches one ore more coordinates,
            // score up geo appropriately.
            String geo_gh = GeohashUtils.encodeLatLon(geo.getLatitude(), geo.getLongitude());
            String grid = geo_gh.substring(0, DEFAULT_THRESHOLD_DIGITS);

            for (Geocoding ll : coordinates) {

                String gh = GeohashUtils.encodeLatLon(ll.getLatitude(), ll.getLongitude());

                // is within
                if (gh.startsWith(grid)) {
                    name.addGeocoordEvidence("Geohash", weight, ll, geo, 1.0);
                }
            }
            break;
        }

    }
}
