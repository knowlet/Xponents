package org.opensextant.xlayer.server.xgeo;

import java.util.List;

import org.json.JSONException;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.Extractor;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.extractors.xtax.TaxonMatch;
import org.opensextant.extractors.xtemporal.XTemporal;
import org.opensextant.output.Transforms;
import org.opensextant.processing.Parameters;
import org.opensextant.processing.RuntimeTools;
import org.opensextant.xlayer.server.TaggerResource;
import org.restlet.data.CharacterSet;
import org.restlet.data.Form;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;

import jodd.json.JsonObject;

/**
 * A RESTFul application of PlaceGeocoder
 */
public class XponentsGeotagger extends TaggerResource {

    /**
     * Restlet resource that pulls its configuration from Context.
     */
    public XponentsGeotagger() {
        super();
        log = getContext().getCurrentLogger();
    }

    /**
     * get Xponents Exxtractor object from global attributes.
     */
    public Extractor getExtractor(String xid) {
        Object X = this.getApplication().getContext().getAttributes().get(xid);
        if (xid.equals("xgeo")) {
            PlaceGeocoder xgeo = (PlaceGeocoder) X;
            if (xgeo == null) {
                info("Misconfigured, no context-level geocoder pipeline initialized");
                return null;
            }
            return xgeo;
        } else if (xid.equals("xtemp")) {
            XTemporal xt = (XTemporal) X;
            if (xt == null) {
                info("Misconfigured, no context-level date/time pipeline initialized");
                return null;
            }
            return xt;
        }
        error("No such extractor " + xid, null);
        return null;
    }

    /**
     * Contract: docid optional; 'text' | 'doc-list' required. command: cmd=ping sends back a simple
     * response
     * 
     * text = UTF-8 encoded text docid = user's provided document ID doc-list = An array of text
     * 
     * cmd=ping = report status.
     * 
     * Where json-array contains { docs=[ {docid='A', text='...'}, {docid='B', text='...',...] } The
     * entire array must be parsable in memory as a single, traversible JSON object. We make no
     * assumption about one-JSON object per line or anything about line-endings as separators.
     * 
     *
     * @param params JSON parameters per REST API: docid, text, lang, features, options, and preferred_*
     * @return the representation
     * @throws JSONException the JSON exception
     */
    @Post("application/json;charset=utf-8")
    public Representation processForm(JsonRepresentation params) throws JSONException {
        org.json.JSONObject json = params.getJsonObject();
        String input = json.optString("text", null);
        String docid = json.optString("docid", null);

        if (input != null) {
            String lang = json.optString("lang", null);
            TextInput item = new TextInput(docid, input);
            item.langid = lang;

            Parameters job = fromRequest(json);
            return process(item, job);
        }

        return status("FAIL", "Invalid API use text+docid pair or doc-list was not found");
    }

    /**
     * HTTP GET -- vanilla. Do not use in production, unless you have really small data packages. This
     * is useful for testing. Partial contract:
     * 
     * miscellany: 'cmd' = 'ping' |... other commands. processing: 'docid' = ?, 'text' = ?
     * 
     * @param params JSON parameters. see process()
     * @return the representation
     */
    @Get
    public Representation processGet(Representation params) {
        Form inputs = getRequest().getResourceRef().getQueryAsForm();
        String input = inputs.getFirstValue("text");
        String docid = inputs.getFirstValue("docid");
        String lang = inputs.getFirstValue("lang");
        TextInput item = new TextInput(docid, input);
        item.langid = lang;

        Parameters job = fromRequest(inputs);
        return process(item, job);
    }

    /**
     * Process the text for the given document.
     *
     * @param input     the input
     * @param jobParams the job params
     * @return the representation
     */
    public Representation process(TextInput input, Parameters jobParams) {

        if (input == null || input.buffer == null) {
            return status("FAIL", "No text");
        }
        debug("Processing plain text doc");

        try {
            if (prodMode) {
                PlaceGeocoder xgeo = (PlaceGeocoder) getExtractor("xgeo");
                List<TextMatch> matches = xgeo.extract(input, jobParams);

                if (jobParams.tag_patterns) {
                    XTemporal xt = (XTemporal) getExtractor("xtemp");
                    matches.addAll(xt.extract(input));
                }
                if (isDebug()) {
                    debug(String.format("CURRENT MEM USAGE(K)=", RuntimeTools.reportMemory()));
                }
                /*
                 * formulate matches as JSON output.
                 */
                filter(matches, jobParams);
                return format(matches, jobParams);
            }

        } catch (Exception processingErr) {
            error("Failure on doc " + input.id, processingErr);
            return status("FAIL", processingErr.getMessage());
        }

        return status("TEST", "nothing done in test with doc=" + input.id);
    }

    /**
     * Format matches as JSON
     * 
     * @param matches   items to format
     * @param jobParams parameters
     * @return formatted json
     * @throws JSONException on format error
     */
    private Representation format(List<TextMatch> matches, Parameters jobParams) throws JSONException {

        JsonObject j = Transforms.toJSON(matches, jobParams);
        Representation result = new JsonRepresentation(j.toString());
        result.setCharacterSet(CharacterSet.UTF_8);

        return result;
    }

    /**
     * @param params         parameters
     * @param variousMatches matches to filter
     */
    public void filter(List<TextMatch> variousMatches, Parameters params) {
        // Determine what looks useful. Filter out things not worth
        // saving at all in data store.
        //simpleFilter.filter(variousMatches);

        /* HACK.  Prefer not to change the "filter" state based on output/visibility parameters.
         * consider output options; filter out returns based on requested outputs.
         */
        if (!params.output_taxons) {
            for (TextMatch m : variousMatches) {
                if (m.isFilteredOut()) {
                    continue;
                } else if (m instanceof TaxonMatch) {
                    m.setFilteredOut(true);
                }
            }
        }
    }

}
