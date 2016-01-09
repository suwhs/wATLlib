package su.whs.watl.experimental;

import java.util.HashMap;

/*
 * Copyright 2002-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Modified by by igor n. boulliev on 09.01.16.
 * sources:
 *  http://grepcode.com/file_/repo1.maven.org/maven2/commons-lang/commons-lang/2.1/org/apache/commons/lang/Entities.java/?v=source
 */
public class ThirdPartyUtils {
    /**
     * <p>Unescapes the entities in a <code>String</code>.</p>
     *
     * <p>For example, if you have called addEntity(&quot;foo&quot;, 0xA1),
     * unescape(&quot;&amp;foo;&quot;) will return &quot;\u00A1&quot;</p>
     *
     * @param str The <code>String</code> to escape.
     * @return A new escaped <code>String</code>.
     */
    public static String unescape(String str) {
        StringBuffer buf = new StringBuffer(str.length());
        int i;
        for (i = 0; i < str.length(); ++i) {
            char ch = str.charAt(i);
            if (ch == '&') {
                int semi = str.indexOf(';', i + 1);
                if (semi == -1) {
                    buf.append(ch);
                    continue;
                }
                String entityName = str.substring(i + 1, semi);
                int entityValue;
                if (entityName.length() == 0) {
                    entityValue = -1;
                } else if (entityName.charAt(0) == '#') {
                    if (entityName.length() == 1) {
                        entityValue = -1;
                    } else {
                        char charAt1 = entityName.charAt(1);
                        try {
                            if (charAt1 == 'x' || charAt1=='X') {
                                entityValue = Integer.valueOf(entityName.substring(2), 16).intValue();
                            } else {
                                entityValue = Integer.parseInt(entityName.substring(1));
                            }
                        } catch (NumberFormatException ex) {
                            entityValue = -1;
                        }
                    }
                } else {
                    entityValue = entityValue(entityName);
                }
                if (entityValue == -1) {
                    buf.append('&');
                    buf.append(entityName);
                    buf.append(';');
                } else {
                    buf.append((char) (entityValue));
                }
                i = semi;
            } else {
                buf.append(ch);
            }
        }
        return buf.toString();
    }

    public static int entityValue(String name) {
        if (sMap.containsKey(name)) return sMap.get(name);
        return -1;
    }

    private static HashMap<String,Integer> sMap = new HashMap<String, Integer>();
    static {
        sMap.put("quit",34);
        sMap.put("amp",38);
        sMap.put("lt",60);
        sMap.put("gt",62);
    }
}
