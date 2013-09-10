/**
 * Copyright 2009-2013 The MITRE Corporation.
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
package org.opensextant.extraction;

import java.io.IOException;
import java.util.Map;

import org.apache.solr.client.solrj.StreamingResponseCallback;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.opensextant.util.SolrProxy;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.SolrTaggerRequest;

/**
 * 
 * Connects to a Solr sever via HTTP and tags place names in document. The
 * <code>SOLR_HOME</code> environment variable must be set to the location of
 * the Solr server.
 * <p />
 * This class is not thread-safe. It could be made to be with little effort.
 * 
 * @author David Smiley - dsmiley@mitre.org
 * @author Marc Ubaldino - ubaldino@mitre.org
 */
public abstract class SolrMatcherSupport {

    protected Logger log = LoggerFactory.getLogger(getClass());

    /*
     */
    protected static String requestHandler = "/tag";
    // protected String coreName = null;
    private SolrProxy solr = null;

    // updated after each call to getText();
    protected int tagNamesTime = 0;
    protected int getNamesTime = 0;
    protected int totalTime = 0;

    /**
     * Close solr resources.
     */
    public void shutdown() {
        if (solr != null) {
            solr.close();
        }
    }

    /**
     * Be explicit about the solr core to use for tagging
     */
    public abstract String getCoreName();

    /**
     * Return the Solr Parameters for the tagger op.
     * 
     * @return
     */
    public abstract SolrParams getMatcherParameters();

    /**
     * Caller must implement their domain objects, POJOs... this callback
     * handler only hashes them.
     * 
     * @param doc
     * @return
     */
    public abstract Object createTag(SolrDocument doc);

    /**
     */
    protected final void initialize() throws IOException {

        // NOTE: This is set via opensextant.apps.Config or by some other means
        // But it is required to intialize. "gazetteer" is the core name of
        // interest.
        // Being explicit here about the core name allows integrator to field
        // multiple cores
        // in the same gazetteer.
        //
        String configSolrHome = System.getProperty("solr.solr.home");
        if (configSolrHome != null) {
            solr = new SolrProxy(configSolrHome, getCoreName());
        } else {
            solr = new SolrProxy(System.getProperty("solr.url"));
            // e.g. http://localhost:8983/solr/gazetteer/
        }
    }

    /**
     * Emphemeral metric for the current tagText() call. Caller must get these
     * numbers immediately after call.
     * 
     * @return time to tag
     */
    public int getTaggingNamesTime() {
        return tagNamesTime;
    }

    /**
     * @return time to get reference records.
     */
    public int getRetrievingNamesTime() {
        return getNamesTime;
    }

    /**
     * @return time to get gazetteer records.
     */
    public int getTotalTime() {
        return totalTime;
    }

    /**
     * Solr call: tag input buffer, returning all candiate reference data that
     * matched during tagging.
     * 
     * @param buffer
     * @param docid
     * @param refDataMap
     *            - a map of reference data in solr, It will store caller's
     *            domain objects. e.g., rec.id => domain(rec)
     * @return
     * @throws ExtractionException
     */
    protected QueryResponse tagTextCallSolrTagger(String buffer, String docid,
            final Map<Integer, Object> refDataMap) throws ExtractionException {
        SolrTaggerRequest tagRequest = new SolrTaggerRequest(getMatcherParameters(), buffer);
        tagRequest.setPath(requestHandler);
        // Stream the response to avoid serialization and to save memory by
        // only keeping one SolrDocument materialized at a time
        tagRequest.setStreamingResponseCallback(new StreamingResponseCallback() {
            @Override
            public void streamDocListInfo(long numFound, long start, Float maxScore) {
            }

            // Future optimization: it would be nice if Solr could give us the
            // doc id without giving us a SolrDocument, allowing us to
            // conditionally get it. It would save disk IO & speed, at the
            // expense of putting ids into memory.
            @Override
            public void streamSolrDocument(final SolrDocument solrDoc) {
                Integer id = (Integer) solrDoc.getFirstValue("id");
                // create a domain object for the given tag;
                // this callback handler caches such domain obj in simple k/v
                // map.
                Object domainObj = createTag(solrDoc);
                if (domainObj != null) {
                    refDataMap.put(id, domainObj);
                }
            }
        });

        QueryResponse response;
        try {
            response = tagRequest.process(solr.getInternalSolrServer());
        } catch (Exception err) {
            throw new ExtractionException("Failed to tag document=" + docid, err);
        }

        // see https://issues.apache.org/jira/browse/SOLR-5154
        SolrDocumentList docList = response.getResults();
        if (docList == null) {
            // SolrTextTagger v1.x
            docList = (SolrDocumentList) response.getResponse().get("matchingDocs");
        }
        if (docList != null) {
            // log.debug("Not streaming docs from Solr (not supported)");
            StreamingResponseCallback callback = tagRequest.getStreamingResponseCallback();
            callback.streamDocListInfo(docList.getNumFound(), docList.getStart(),
                    docList.getMaxScore());
            for (SolrDocument solrDoc : docList) {
                /**
                 * This appears to be an empty list; what is this explicit
                 * callback loop for?
                 */
                callback.streamSolrDocument(solrDoc);
            }
        }

        return response;
    }

}
