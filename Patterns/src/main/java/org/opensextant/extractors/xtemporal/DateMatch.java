/**
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
 *
 * **************************************************************************
 * NOTICE This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 */
package org.opensextant.extractors.xtemporal;

import java.util.Date;
import org.opensextant.extraction.TextMatch;

/**
 *
 * @author ubaldino
 */
public class DateMatch extends TextMatch {

    /**
     * Just the coordinate text normalized
     */
    public Date datenorm = null;
    /**
     *
     */
    public String datenorm_text = null;

    /**
     *
     */
    public DateMatch() {
        // populate attrs as needed;
        type = "datetime";
        producer = "XTemp";
    }

    /**
     * A simplistic way to capture resolution of the date/time reference.
     */
    public enum TimeResolution {

        NONE(-1, "U"), YEAR(1, "Y"), MONTH(2, "M"), WEEK(3, "W"), DAY(4, "D"), HOUR(5, "H"), MINUTE(6, "m"), SECOND(7, "s");
        public int level = -1;
        public String code = null;

        TimeResolution(int l, String c) {
            level = l;
            code = c;
        }
    };

    // Enum representing YEAR, MON, WEEK, DAY, HR, MIN
    //
    public TimeResolution resolution = TimeResolution.NONE;

    /** Flag caller can use to classify if a date match is distant */
    public boolean isDistantPast = false;
    /** Flag caller can use to classify if date is future relative to a given date, by default TODAY*/
    public boolean isFuture = false;

}