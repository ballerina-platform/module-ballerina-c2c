/*
 * Copyright (c) 2026, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.ballerina.c2c.helm;

import io.fabric8.kubernetes.api.model.Quantity;

import java.util.Map;
import java.util.TreeMap;

/**
 * Shared string/YAML rendering helpers used when hand-assembling Helm chart template text.
 * <p>
 * Helm chart templates are Go-template text, not structured Kubernetes objects: a value such as
 * {@code replicas: {{ .Values.replicaCount }}} cannot be produced by the typed fabric8 model
 * builders {@code handlers/*Handler} use for the plain {@code k8s}/{@code openshift} targets
 * (an {@code Integer} field cannot hold a template expression). So every Helm template in this
 * package is assembled as text, and these helpers keep that text assembly consistent and safe
 * (correct YAML quoting/indentation) across all of them.
 *
 * @since 1.0.0
 */
public final class HelmTemplateUtils {

    private HelmTemplateUtils() {
    }

    /**
     * Fills a template by literal token replacement (never {@code String.format}/{@code
     * .formatted}): every Helm template here embeds {@code \n} deliberately (YAML/Go-template
     * content must always use it, regardless of platform), and format-string methods flag that
     * as a bug. Plain {@link String#replace} sidesteps the check entirely since it isn't a
     * format method.
     *
     * @param template            the template text, containing {@code @@TOKEN@@}-style markers
     * @param tokensAndValues     alternating {@code token, value, token, value, ...} pairs
     */
    public static String substitute(String template, String... tokensAndValues) {
        String result = template;
        for (int i = 0; i + 1 < tokensAndValues.length; i += 2) {
            result = result.replace(tokensAndValues[i], tokensAndValues[i + 1]);
        }
        return result;
    }

    /**
     * Quotes a scalar value so it is always safe to embed literally in hand-written YAML,
     * regardless of its content (colons, braces, leading special characters, etc.).
     */
    public static String yamlQuote(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * Indents every line of {@code text} by {@code spaces} spaces. Used to splice a
     * multi-line, already-rendered YAML block under a parent key.
     */
    public static String indent(String text, int spaces) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String prefix = " ".repeat(spaces);
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(prefix).append(line);
        }
        return out.toString();
    }

    /**
     * Renders a string-keyed map as a YAML mapping block (one {@code key: "value"} per line,
     * sorted for deterministic output), or {@code {}} if empty.
     */
    public static String renderStringMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(map).entrySet()) {
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(entry.getKey()).append(": ").append(yamlQuote(entry.getValue()));
        }
        return out.toString();
    }

    /**
     * Renders a fabric8 {@link Quantity} map (e.g. resource requests/limits) as a YAML mapping
     * block (cpu/memory unquoted-but-safe scalars), or {@code {}} if empty.
     */
    public static String renderQuantityMap(Map<String, Quantity> quantities) {
        if (quantities == null || quantities.isEmpty()) {
            return "{}";
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Quantity> entry : new TreeMap<>(quantities).entrySet()) {
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(entry.getKey()).append(": ").append(yamlQuote(entry.getValue().getAmount()
                    + emptyIfNull(entry.getValue().getFormat())));
        }
        return out.toString();
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
