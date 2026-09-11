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

import io.ballerina.c2c.models.SecretModel;

import java.util.Map;
import java.util.TreeMap;

import static io.ballerina.c2c.helm.HelmTemplateUtils.yamlQuote;

/**
 * Writes one {@code templates/secret-<name>.yaml} per {@code SecretModel}.
 * <p>
 * Deliberately <b>not</b> values-driven, unlike {@link HelmConfigMapTemplateWriter}: once a
 * value flows through plain {@code values.yaml}/{@code --set}, it lands in Helm's release
 * history (an in-cluster Secret, base64 -- not encrypted at rest by default) and often ends up
 * committed to git as part of an environment's values file, a real regression versus today's
 * one-shot, non-persisted Secret generation. The data here is rendered exactly as
 * {@code handlers/SecretHandler} already renders it for the plain {@code k8s} target (the
 * {@code SecretModel}'s data values are already base64-encoded).
 *
 * @since 1.0.0
 */
public final class HelmSecretTemplateWriter {

    private HelmSecretTemplateWriter() {
    }

    public static String secretYaml(String chartName, SecretModel secretModel) {
        StringBuilder data = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(secretModel.getData()).entrySet()) {
            data.append("  ").append(yamlQuote(entry.getKey())).append(": ").append(entry.getValue()).append('\n');
        }
        String template = """
                apiVersion: v1
                kind: Secret
                metadata:
                  name: @@NAME@@
                  namespace: {{ .Release.Namespace }}
                  labels:
                    {{- include "@@CHART@@.labels" . | nindent 4 }}
                data:
                @@DATA@@""";
        return HelmTemplateUtils.substitute(template,
                "@@NAME@@", yamlQuote(secretModel.getName()),
                "@@CHART@@", chartName,
                "@@DATA@@", data.toString());
    }
}
