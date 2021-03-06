/*
 *
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
 */
package org.opensextant.util;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.StoredField;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.opensextant.data.Place;

/**
 * Utility functions better suited in their own class.
 *
 * @author ubaldino
 *
 */
public class SolrUtil {

    /**
     *
     * @param d solr doc
     * @param f field name
     * @return a list of strings for this field from that document.; Or null if
     *         none found.
     */
    public static List<String> getStrings(SolrDocument d, String f) {
        List<String> vals = new LinkedList<String>();

        Collection<Object> objlist = d.getFieldValues(f);
        if (objlist == null) {
            return null;
        }

        for (Object o : objlist) {
            if (o instanceof StoredField) {
                vals.add(((StoredField) o).stringValue());
            } else if (o instanceof String) {
                vals.add((String) o);
            } else {
                vals.add(o.toString());
            }
        }
        return vals;
    }

    /**
     * Get an integer from a record
     */
    public static int getInteger(SolrDocument d, String f) {
        Object obj = d.getFieldValue(f);
        if (obj == null) {
            return 0;
        }

        if (obj instanceof StoredField) {
            return ((StoredField) obj).numericValue().intValue();
        } else if (obj instanceof Integer) {
            return ((Integer) obj).intValue();
        } else {
            Integer v = Integer.valueOf(obj.toString());
            return v.intValue();
        }
    }

    /**
     * get integer value from input document.
     * @param d doc
     * @param f field name
     * @return int
     */
    public static int getInteger(SolrInputDocument d, String f){
        Object obj = d.getFieldValue(f);
        if (obj == null) {
            return 0;
        }

        if (obj instanceof StoredField) {
            return ((StoredField) obj).numericValue().intValue();
        } else if (obj instanceof Integer) {
            return ((Integer) obj).intValue();
        } else {
            Integer v = Integer.valueOf(obj.toString());
            return v.intValue();
        }
    }

    /**
     * Get a long from a record
     */
    public static long getLong(SolrDocument d, String f) {
        Object obj = d.getFieldValue(f);
        if (obj == null) {
            return 0;
        }

        if (obj instanceof StoredField) {
            return ((StoredField) obj).numericValue().longValue();
        } else if (obj instanceof Long) {
            return ((Long) obj);
        } else {
            return Long.valueOf(obj.toString());
        }
    }

    /**
     * Get a floating point object from a record
     * @return NaN if null
     */
    public static Float getFloat(SolrDocument d, String f) {
        Object obj = d.getFieldValue(f);
        if (obj == null) {
            return Float.NaN;
        }
        if (obj instanceof StoredField) {
            return ((StoredField) obj).numericValue().floatValue();
        } else if (obj instanceof Float) {
            return ((Float) obj);
        } else {
            return Float.valueOf(obj.toString());
        }
    }

    public static Float getFloat(SolrInputDocument d, String f) {
        Object obj = d.getFieldValue(f);
        if (obj == null) {
            return Float.NaN;
        }
        if (obj instanceof StoredField) {
            return ((StoredField) obj).numericValue().floatValue();
        } else if (obj instanceof Float) {
            return ((Float) obj).floatValue();
        } else {
            return Float.valueOf(obj.toString());
        }
    }
    
    /**
     * Get a Date object from a record
     *
     * @throws java.text.ParseException if DateUtil fails to parse date str
     */
    public static Date getDate(SolrDocument d, String f) throws java.text.ParseException {
        if (d == null || f == null) {
            return null;
        }
        Object obj = d.getFieldValue(f);
        if (obj == null) {
            return null;
        }
        if (obj instanceof Date) {
            return (Date) obj;
        } else {
            return TextUtils.parseDate(obj.toString());
        }
    }

    /**
     *
     */
    public static char getChar(SolrDocument solrDoc, String name) {
        String result = getString(solrDoc, name);
        if (result == null) {
            return 0;
        }
        if (result.isEmpty()) {
            return 0;
        }
        return result.charAt(0);
    }

    /**
     * Get a String object from a record on input.
     * 
     * @param solrDoc solr input document
     */
    public static String getString(SolrInputDocument solrDoc, String name) {
        Object result = solrDoc.getFieldValue(name);

        if (result == null || StringUtils.isBlank((String) result)) {
            return null;
        }
        return result.toString();
    }

    /**
     * Get a String object from a record
     * @param solrDoc SolrDocument from index
     */
    public static String getString(SolrDocument solrDoc, String name) {
        Object result = solrDoc.getFirstValue(name);
        if (result == null) {
            return null;
        }
        if (result instanceof StoredField) {
            String val = ((StoredField) result).stringValue();
            if (StringUtils.isBlank(val)) {
                return null;
            }
            return val;
        }
        return result.toString();
    }

    /**
     *
     * Get a double from a record
     */
    public static double getDouble(SolrDocument solrDoc, String name) {
        Object obj = solrDoc.getFirstValue(name);
        if (obj == null) {
            return Double.NaN;
        }
        if (obj instanceof StoredField) {
            return ((StoredField) obj).numericValue().floatValue();
        }        
        if (obj instanceof Number) {
            Number number = (Number) obj;
            return number.doubleValue();
        } else {
            return Double.valueOf(obj.toString());
        }
    }

    /**
     * Parse XY pair stored in Solr Spatial4J record. No validation is done.
     *
     * @return XY double array, [lat, lon]
     */
    public static double[] getCoordinate(SolrDocument solrDoc, String field) {
        String xy = getString(solrDoc, field);
        if (xy == null) {
            throw new IllegalStateException("Blank: " + field + " in " + solrDoc);
        }

        final double[] xyPair = { 0.0, 0.0 };
        String[] lat_lon = xy.split(",", 2);
        xyPair[0] = Double.valueOf(lat_lon[0]);
        xyPair[1] = Double.valueOf(lat_lon[1]);

        return xyPair;
    }

    /**
     * Parse XY pair stored in Solr Spatial4J record. No validation is done.
     *
     * @return XY double array, [lat, lon]
     */
    public static double[] getCoordinate(String xy) {

        final double[] xyPair = { 0.0, 0.0 };
        String[] lat_lon = xy.split(",", 2);
        xyPair[0] = Double.valueOf(lat_lon[0]);
        xyPair[1] = Double.valueOf(lat_lon[1]);

        return xyPair;
    }

    /*
     * ===============================================
     * Higher order routines -- createPlace, populatePlace
     * 
     * 
     * ===============================================
     */

    /**
     * Creates the bare minimum Gazetteer Place record
     * @param gazEntry a solr document of key/value pairs
     * @return Place obj
     */
    public static Place createPlace(SolrDocument gazEntry) {

        Place bean = new Place(getString(gazEntry, "place_id"), getString(gazEntry, "name"));
        populatePlace(gazEntry, bean);
        return bean;
    }

    /**
     * Populate the data card.
     * @param gazEntry solr doc
     * @param bean place obj to populate
     */
    public static void populatePlace(SolrDocument gazEntry, Place bean) {
        String nt = getString(gazEntry, "name_type");
        if (nt != null) {
            if ("code".equals(nt)) {
                bean.setName_type('A');
            } else {
                bean.setName_type(nt.charAt(0));
            }
        }

        bean.setCountryCode(getString(gazEntry, "cc"));

        // Other metadata.
        bean.setAdmin1(getString(gazEntry, "adm1"));
        bean.setAdmin2(getString(gazEntry, "adm2"));
        bean.setFeatureClass(getString(gazEntry, "feat_class"));
        bean.setFeatureCode(getString(gazEntry, "feat_code"));

        // Geo field is specifically Spatial4J lat,lon format.
        // Value should have already been validated as it was stored in index
        double[] xy = getCoordinate(gazEntry, "geo");
        bean.setLatitude(xy[0]);
        bean.setLongitude(xy[1]);

        bean.setName_bias(getDouble(gazEntry, "name_bias"));
        bean.setId_bias(getDouble(gazEntry, "id_bias"));
    }
}
