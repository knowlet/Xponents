/*
 * This software was produced for the U. S. Government
 * under Basic Contract No. W15P7T-13-C-A802, and is
 * subject to the Rights in Noncommercial Computer Software
 * and Noncommercial Computer Software Documentation
 * Clause 252.227-7014 (FEB 2012)
 *
 * Copyright (C) 2016 The MITRE Corporation.
 * Copyright (C) 2016 OpenSextant.org
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
package org.opensextant.mapreduce;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Text;
import org.opensextant.ConfigException;
import org.opensextant.data.Taxon;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.xtax.TaxonMatch;
import org.opensextant.extractors.xtax.TaxonMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.json.JSONObject;

/**
 *  A mapper that takes in a KEY, TEXT value.
 *  The map() routine processes some found text -- this demo assumes input is JSON, and JSON has a "text" field.
 *  Tag the text and write out with the KEY any findings.
 *  k1,{ "type":"taxon", "value":"Paul Bunyan", "offset":455, ...}
 *  k1,{ "type":"taxon", "value":"Mary", "offset":1, ...}
 *  k2,{ "type":"taxon", "value":"Mother Goose", "offset":87, ...}
 *  
 */
public class KeywordTaggerMapper extends AbstractMapper {
    private TaxonMatcher xtax = null;
    private Logger log = LoggerFactory.getLogger(KeywordTaggerMapper.class);

    @Override
    public void cleanup(Context c) {
        if (xtax != null) {
            xtax.shutdown();
        }
    }

    /**
     * Setup.  XTax or PlaceGecoder takes in SOLR path for xponents solr from JVM environment.
     */
    @Override
    public void setup(Context c) throws IOException {
        super.setup(c);

        try {
            xtax = new TaxonMatcher();
        } catch (ConfigException e) {
            // TODO Auto-generated catch block
            throw new IOException("setup.XTax", e);
        }

        log.info("DONE");
    }

    /**
     * 
     */
    @Override
    public void map(BytesWritable key, Text textRecord, Context context)
            throws IOException, InterruptedException {
        ++counter;
        TextInput textObj = null;
        try {
            textObj = prepareInput(null, textRecord);
        } catch (java.lang.NullPointerException npe) {
            log.error("Failed on record {}", textRecord.toString().substring(0, 50));
        }
        if (textObj == null) {
            return;
        }

        textObj.langid = "en";
        Text oid = new Text(textObj.id);

        /* LANG ID = 'ENGLISH', 
         * If this is not true, then you need to add LangID to your metadata or detect it live 
         */

        HashSet<String> dedup = new HashSet<>();

        try {

            /*
             * Testing to see if XTax tagger operates in Hadoop job
             */
            List<TextMatch> matches = xtax.extract(textObj);

            if (matches.isEmpty()) {
                return;
            }

            /* NORMALIZE findings.
             * Reduce all matches, minimizing duplicates, removing whitespace, etc.
             * 
             */
            int filtered = 0, duplicates = 0;
            for (TextMatch tm : matches) {
                if (filterCrap(tm.getText())) {
                    filtered += 1;
                    continue;
                }
                if (dedup.contains(tm.getText())) {
                    duplicates += 1;
                    continue;
                }
                dedup.add(tm.getText());
                JSONObject o = match2JSON(tm);
                Text matchOutput = new Text(o.toString());
                context.write(oid, matchOutput);
            }
            if (log.isTraceEnabled()) {
                log.trace("For key {}, found={}, junk filtered={}, duplicates={}",
                        key.toString(), matches.size(), filtered, duplicates);
            }
        } catch (Exception err) {
            log.error("Error running xtax", err);
        }
    }

    /**
     * Convert a TextMatch (Place, Taxon, Pattern, etc.) and convert to JSON.
     * @param tm
     * @return
     */
    public static final JSONObject match2JSON(TextMatch tm) {
        JSONObject j = prepareOutput(tm);
        if (tm instanceof TaxonMatch) {
            for (Taxon tx : ((TaxonMatch) tm).getTaxons()) {
                j.put("name", tx.name);
                j.put("cat", tx.catalog);
                j.put("type", getTypeLabel(tx));
                break; /* Demo: we only capture the first Taxon Match */
            }
        }
        return j;
    }
    
    protected static String getTypeLabel(final Taxon tx){
        String t= tx.name.toLowerCase();
        if (t.startsWith("org")){
            return "org";
        }
        if (t.startsWith("person")){
            return "person";
        }
            
        return "taxon";
    }

    /**
     * Filtration rules -- what is worth reporting in your output?  You decide.
     * 
     * @param t
     * @return
     */
    private static final boolean filterCrap(final String t) {
        if (t.length() <= 3) {
            return true;
        }
        final char c = t.charAt(0);
        if (c == '@') {
            return true;
        }
        if (t.startsWith("http")) {
            return true;
        }
        return false;
    }
}
