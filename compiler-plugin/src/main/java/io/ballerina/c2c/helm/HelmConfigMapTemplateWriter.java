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

import io.ballerina.c2c.KubernetesConstants;
import io.ballerina.c2c.models.ConfigMapModel;

import java.util.Map;

import static io.ballerina.c2c.helm.HelmTemplateUtils.yamlQuote;

/**
 * Writes one {@code templates/configmap-<name>.yaml} per {@code ConfigMapModel} -- this is the
 * "values-driven config" path: rather than inlining each mounted file's content as a YAML
 * literal (what {@code handlers/ConfigMapHandler} does for the plain {@code k8s}/{@code
 * openshift} targets), the file's content is copied into the chart under {@code files/} and
 * rendered through Helm's {@code tpl} function against {@code .Files.Get}. Whatever
 * {@code {{ .Values.x }}} expressions a user already writes into the file they point
 * {@code cloud.config.files} at (typically their own {@code Config.toml}) become live Helm
 * values -- no introspection of Ballerina {@code configurable} declarations is needed; the file
 * decides what it exposes.
 * <p>
 * Resource names are deliberately <b>not</b> release-scoped (no {@code include "chart.fullname"})
 * -- they stay exactly {@code configMapModel.getName()}, the same literal name
 * {@code handlers/DeploymentHandler} already wired into the pod's volume reference
 * ({@code secretName}/{@code configMap.name}) for the plain {@code k8s} target. Re-templating
 * the name here without also changing that volume reference would make the two diverge.
 *
 * @since 1.0.0
 */
public final class HelmConfigMapTemplateWriter {

    private HelmConfigMapTemplateWriter() {
    }

    /**
     * The path (relative to the chart root) a mounted file's content is copied to, and the same
     * path referenced by {@code .Files.Get} in the generated template -- kept in one place so
     * the two stay in sync.
     */
    public static String filesPath(ConfigMapModel configMapModel, String fileName) {
        return KubernetesConstants.HELM_FILES_DIR + "/" + configMapModel.getName() + "/" + fileName;
    }

    public static String configMapYaml(String chartName, ConfigMapModel configMapModel) {
        StringBuilder data = new StringBuilder();
        for (Map.Entry<String, String> entry : configMapModel.getData().entrySet()) {
            data.append("  ").append(yamlQuote(entry.getKey())).append(": |\n")
                    .append("    {{- tpl (.Files.Get ").append(yamlQuote(filesPath(configMapModel, entry.getKey())))
                    .append(") . | nindent 4 }}\n");
        }
        String template = """
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: @@NAME@@
                  namespace: {{ .Release.Namespace }}
                  labels:
                    {{- include "@@CHART@@.labels" . | nindent 4 }}
                data:
                @@DATA@@""";
        return HelmTemplateUtils.substitute(template,
                "@@NAME@@", yamlQuote(configMapModel.getName()),
                "@@CHART@@", chartName,
                "@@DATA@@", data.toString());
    }
}
