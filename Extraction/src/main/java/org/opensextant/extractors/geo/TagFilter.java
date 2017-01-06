package org.opensextant.extractors.geo;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensextant.ConfigException;
import org.opensextant.extraction.MatchFilter;
import org.opensextant.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.supercsv.io.CsvMapReader;
import org.supercsv.prefs.CsvPreference;
/*
 * We can filter out trivial place name matches that we know to be close to
 * false positives 100% of the time. E.g,. "way", "back", "north" You might
 * consider two different stop filters, Is "North" different than "north"?
 * This first pass filter should really filter out only text we know to be
 * false positives regardless of case.
 * 
 * Filter out unwanted tags via GazetteerETL data model or in Solr index. If
 * you believe certain items will always be filtered then set name_bias >
 * 0.0
 */

public class TagFilter extends MatchFilter {
    /**
     * This may need to be turned off for processing lower-case or dirty
     * data.
     */
    boolean filter_stopwords = true;
    boolean filter_on_case = true;
    Set<String> stopTerms = null;
    Logger log = LoggerFactory.getLogger(TagFilter.class);

    /*
     * Select languages for experimentation.
     */
    private Map<String, Set<String>> langStopFilters = new HashMap<>();

    private Set<String> generalLangId = new HashSet<>();

    /**
     * NOTE:  This expects the files are all available. This fails if resource files are missing.
     * 
     * @throws ConfigException if any file has a problem. 
     */
    public TagFilter() throws IOException, ConfigException {
        super();
        stopTerms = new HashSet<>();
        String[] defaultNonPlaceFilters = {
                "/filters/non-placenames.csv", // GENERAL
                "/filters/non-placenames,spa.csv", // SPANISH 
                "/filters/non-placenames,acronym.csv" // ACRONYMS
        };
        for (String f : defaultNonPlaceFilters) {
            stopTerms.addAll(loadExclusions(GazetteerMatcher.class.getResourceAsStream(f)));
        }
        generalLangId.add(TextUtils.englishLang);
        generalLangId.add(TextUtils.spanishLang);

        String[] langSet = { "ja", "th", "tr", "id", "ar" };
        loadLanguageStopwords(langSet);
    }

    /**
     * Load default Lucene stop words to aid in language specific filtration.
     * @param langids
     * @throws IOException
     * @throws ConfigException
     */
    private void loadLanguageStopwords(String[] langids) throws IOException, ConfigException {

        for (String lg : langids) {
            String url = String.format("/org/apache/lucene/analysis/%s/stopwords.txt", lg);
            URL obj = URL.class.getResource(url);
            if (obj == null) {
                throw new IOException("No such stop filter file " + url);
            }
            loadStopSet(obj, lg);
        }

        /*
         * More optional lists.
         */
        // KOREAN
        String url = "/filters/carrot2-stopwords.ko";
        String lg = "ko";
        URL obj = URL.class.getResource(url);
        if (obj != null) {
            loadStopSet(obj, lg);
        }
        // CHINESE
        url = "/filters/carrot2-stopwords.zh";
        lg = "zh";
        obj = URL.class.getResource(url);
        if (obj != null) {
            loadStopSet(obj, lg);
        }

        // VIETNAMESE
        url = "/filters/vietnamese-stopwords.txt";
        lg = "vn";
        obj = URL.class.getResource(url);
        if (obj != null) {
            loadStopSet(obj, lg);
        }

    }

    private void loadStopSet(URL url, String langid) throws IOException, ConfigException {
        try (InputStream strm = url.openStream()) {
            HashSet<String> stopTerms = new HashSet<>();
            for (String line : IOUtils.readLines(strm, Charset.forName("UTF-8"))) {
                if (line.trim().startsWith("#")) {
                    continue;
                }
                stopTerms.add(line.trim().toLowerCase());
            }
            if (stopTerms.isEmpty()) {
                throw new ConfigException("No terms found in stop filter file " + url.toString());
            }
            langStopFilters.put(langid, stopTerms);
        }
    }

    public void enableStopwordFilter(boolean b) {
        filter_stopwords = b;
    }

    public void enableCaseSensitive(boolean b) {
        filter_on_case = b;
    }

    @Override
    public boolean filterOut(String t) {
        if (filter_on_case && StringUtils.isAllLowerCase(t)) {
            return true;
        }

        if (filter_stopwords) {
            if (stopTerms.contains(t.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Experimental.
     * 
     * Using proper Language ID (ISO 2-char for now), determine if the 
     * given term, t, is a stop term in that language.
     * 
     * @param t
     * @param langId
     * @return
     */
    public boolean filterOut(PlaceCandidate t, String langId) {
        /*
         * Consider no given language ID -- only short, non-ASCII terms should be filtered out 
         * against all stop filters; Otherwise there is some performance issues.
         */
        if (langId == null) {
            if (t.isASCII()) {
                return false; /* Not filtering out short crap, right now. */
            } else if (t.getLength() < 4) {
                return assessAllFilters(t.getTextnorm());
            }
        }
        /*
         * IGNORE languages already filtered out by the general filter above.
         */
        if (generalLangId.contains(langId)) {
            return false;
        }

        /* EXPERIMENTAL.
         * 
         * But if langID is given, we first consider if text in document
         * is possibly a Proper name of sort...
         * UPPERCASENAME -- possibly stop?
         * Upper Case Name -- pass; not stop
         * not upper case name -- possibly stop.
         */
        char c = t.getText().charAt(0);
        if (Character.isUpperCase(c) && !t.isUpper()) {
            // Proper Name, possibly. Not stopping.
            return false;
        }

        boolean cjk = TextUtils.isCJK(langId);

        if (cjk && filterOutCJK(t)) {
            return true;
        }

        /* 
         * Consider language specific stop filters.
         * NOTE: LangID should not be 'CJK' or group.  langStopFilters keys stop terms by LangID
         */
        if (langStopFilters.containsKey(langId)) {
            Set<String> terms = langStopFilters.get(langId);
            return terms.contains(t.getTextnorm());
        }
        return false;
    }

    /**
     * Experimental. Hack.
     * 
     * Due to bi-gram shingling with CJK languages - Chinese, Japanese, Korean - 
     * the matcher really over-matches, e.g.  For really short matches, let's rule out obvious bad matches.
     * <pre>
     * ... に た ...  input text matched
     *  にた          gazetteer place name. 
     * </pre>
     * TOOD: make use of better tokenizer/matcher in SolrTextTagger configuration for CJK 
     * @param t
     * @return
     */
    private boolean filterOutCJK(PlaceCandidate t) {
        if (t.getLength() < 5 && TextUtils.count_ws(t.getText()) > 0) {
            return true;
        }
        return false;
    }

    /**
     * Run a term (already lowercased) against all stop filters.
     * @param textnorm
     * @return
     */
    private boolean assessAllFilters(String textnorm) {
        for (Set<String> terms : langStopFilters.values()) {
            if (terms.contains(textnorm)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Exclusions have two columns in a CSV file. 'exclusion', 'category'
     *
     * "#" in exclusion column implies a comment.
     * Call is responsible for getting I/O stream.
     *  
     * @param filestream
     *            URL/file with exclusion terms
     * @return set of filter terms
     * @throws ConfigException
     *             if filter is not found
     */
    public static Set<String> loadExclusions(InputStream filestream) throws ConfigException {
        /*
         * Load the exclusion names -- these are terms that are gazeteer
         * entries, e.g., gazetteer.name = <exclusion term>, that will be marked
         * as search_only = true.
         */
        try (Reader termsIO = new InputStreamReader(filestream)) {
            CsvMapReader termreader = new CsvMapReader(termsIO, CsvPreference.EXCEL_PREFERENCE);
            String[] columns = termreader.getHeader(true);
            Map<String, String> terms = null;
            HashSet<String> stopTerms = new HashSet<String>();
            while ((terms = termreader.read(columns)) != null) {
                String term = terms.get("exclusion");
                if (StringUtils.isBlank(term) || term.startsWith("#")) {
                    continue;
                }
                stopTerms.add(term.toLowerCase().trim());
            }
            termreader.close();
            return stopTerms;
        } catch (Exception err) {
            throw new ConfigException("Could not load exclusions.", err);
        }
    }

}
