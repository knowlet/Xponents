/**
 * Copyright 2013 The MITRE Corporation.
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
 ** **************************************************
 * NOTICE
 *
 *
 * This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2009-2013 The MITRE Corporation. All Rights Reserved.
 * *************************************************
 */
package org.opensextant.extractors.test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.opensextant.extractors.flexpat.PatternTestCase;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.flexpat.TextMatchResult;
import org.opensextant.extractors.poli.PatternsOfLife;
import org.opensextant.extraction.NormalizationException;
import org.opensextant.util.FileUtility;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.prefs.CsvPreference;

/**
 *
 * @author Marc C. Ubaldino, MITRE <ubaldino at mitre dot org>
 */
public class TestPoLiReporter {

    private Logger log = LoggerFactory.getLogger(TestPoLiReporter.class);
    private PatternsOfLife poli;
    protected final static String[] header = { "result_id", "status", "message", "pattern", "matchtext", "offset" };
    protected static final CellProcessor[] poliResultsSpec = new CellProcessor[] {
            // Given test data is required:
            new NotNull(), new NotNull(), new NotNull(),
            // test results fields -- if result exists
            //
            new Optional(), new Optional(), new Optional() };

    public TestPoLiReporter(PatternsOfLife _poli) {
        this.poli = _poli;
    }

    private CsvMapWriter report = null;

    /**
     *
     */
    protected void createResultsFile(String file) throws IOException {
        report = open(file);
        report.writeHeader(header);
    }

    /**
     *
     */
    public void closeReport() throws IOException {
        if (report != null) {
            report.flush();
            report.close();
        }
    }

    /**
     * Create a typical CSV writer -- Excel compliant
     *
     * @param file
     * @return
     * @throws IOException
     */
    public CsvMapWriter open(String file) throws IOException {

        FileUtility.makeDirectory(new File(file).getParentFile());
        OutputStreamWriter iowriter = FileUtility.getOutputStream(file, "UTF-8");
        CsvMapWriter R = new CsvMapWriter(iowriter, CsvPreference.STANDARD_PREFERENCE);

        return R;
    }

    /**
     * If test case is null, then we're likely testing patterns against a random
     * block of text If match is null then we likely have a test case that
     * yields no hits.
     *
     */
    protected Map<String, Object> createResultRow(PatternTestCase t, TextMatch m) {

        Map<String, Object> row = new HashMap<String, Object>();

        if (t != null) {
            row.put(header[0], t.id);
        }

        if (m != null) {
            String msg = "";
            if (m.is_submatch) {
                msg = "Submatch";
            } else if (m.is_duplicate) {
                msg = "Dup";
            } else if (m.is_overlap) {
                msg = "Overlap";
            }
            row.put(header[1], "PASS");
            row.put(header[2], msg);

            row.put(header[3], m.pattern_id);
            row.put(header[4], m.getText());
            row.put(header[5], m.start);
        } else {
            if (!t.true_positive) {
                row.put(header[1], "PASS"); // true negative
            } else {
                row.put(header[1], "FAIL"); // false negative                
            }
            row.put(header[2], "test='" + t.text + "', nothing found");

            row.put(header[3], "");
            row.put(header[4], "");
            row.put(header[5], "");

        }

        return row;
    }

    /**
     * System tests
     */
    public void test() throws IOException {

        createResultsFile("results/test_System.csv");

        // List<TextMatch> allResults = new ArrayList<>();
        log.info("TESTING ALL SYSTEM PATTERNS");
        for (PatternTestCase test : this.poli.getPatternManager().testcases) {
            log.info("TEST " + test.id);
            TextMatchResult results = this.poli.extract_patterns(test.text, test.id, test.family);
            if (results.evaluated && !results.matches.isEmpty()) {
                try {
                    for (TextMatch m : results.matches) {
                        // log.debug("TEST " + test.id + " FOUND: " +
                        // m.toString());
                        Map<String, Object> row = createResultRow(test, m);
                        report.write(row, header, poliResultsSpec);
                    }
                } catch (IOException ioerr) {
                    log.error("Failed to write result for " + test.id, ioerr);
                }
            } else {
                Map<String, Object> row = createResultRow(test, null);
                report.write(row, header, poliResultsSpec);

                log.info("TEST " + test.id + " STATUS: FAILED");
            }
        }

        closeReport();
    }

    /**
     * Run patterns over a list of files
     */
    public void testUserFiles(String listfile) {
    }

    /**
     * Run patterns over a single file using a pre-configured PoLi. Use -c
     * config -u file test
     */
    public void testUserFile(String f) throws IOException, NormalizationException {
        // poli.configure(new File(f));
        String fname = FilenameUtils.getBaseName(f);

        createResultsFile("results/test_" + fname + ".csv");

        // List<TextMatch> allResults = new ArrayList<>();
        log.info("TESTING FILE: " + f);
        for (PatternTestCase test : poli.getPatternManager().testcases) {
            log.info("TEST " + test.id);
            TextMatchResult results = poli.extract_patterns(test.text, test.id, test.family);
            if (results.evaluated && !results.matches.isEmpty()) {
                try {
                    for (TextMatch m : results.matches) {
                        // log.debug("TEST " + test.id + " FOUND: " +
                        // m.toString());
                        Map<String, Object> row = createResultRow(test, m);
                        report.write(row, header, poliResultsSpec);
                    }
                } catch (IOException ioerr) {
                    log.error("Failed to write result for " + test.id, ioerr);
                }
            } else {
                log.info("TEST " + test.id + " STATUS: FAILED");
            }
        }

        String inputText = FileUtils.readFileToString(new File(f));

        poli.enableAll();
        String fileID = "FILE:" + fname;
        PatternTestCase fileTestCase = new PatternTestCase(fileID, "all", "(file text)");
        TextMatchResult results = poli.extract_patterns(inputText, fileID, null);
        if (results.evaluated && !results.matches.isEmpty()) {
            try {
                for (TextMatch m : results.matches) {
                    // log.debug("TEST " + test.id + " FOUND: " +
                    // m.toString());
                    Map<String, Object> row = createResultRow(fileTestCase, m);
                    report.write(row, header, poliResultsSpec);
                }
            } catch (IOException ioerr) {
                log.error("Failed to write result for " + fileID, ioerr);
            }
        } else {
            log.info("FILE TEST " + fileID + " STATUS: FAILED");
        }

        closeReport();
    }

    /**
     * Random testing
     * TOOD: move the testing off to test package.  Such things involve re-compiling and therefore core lib is 
     * recompiled to do adhoc testing.
     */
    public void adhoc() {
        // this.poli.disableAll();
        // this.poli.getPatternManager().enable_patterns("PHONE");
        // this.test();
    }
}
