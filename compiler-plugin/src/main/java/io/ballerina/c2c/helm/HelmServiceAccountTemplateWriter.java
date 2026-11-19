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

/**
 * Writes {@code templates/serviceaccount.yaml}. This is new behavior relative to the plain
 * {@code k8s}/{@code openshift} targets, which don't create a dedicated ServiceAccount today --
 * a per-release ServiceAccount (rather than the namespace's {@code default}) is low-risk and
 * broadly recommended Kubernetes practice, so it's on by default for the Helm target
 * ({@code serviceAccount.create: true} in values.yaml) but toggleable per release.
 *
 * @since 1.0.0
 */
public final class HelmServiceAccountTemplateWriter {

    private HelmServiceAccountTemplateWriter() {
    }

    public static String serviceAccountYaml(String chartName) {
        String template = """
                {{- if .Values.serviceAccount.create }}
                apiVersion: v1
                kind: ServiceAccount
                metadata:
                  name: {{ include "@@CHART@@.serviceAccountName" . }}
                  namespace: {{ .Release.Namespace }}
                  labels:
                    {{- include "@@CHART@@.labels" . | nindent 4 }}
                {{- end }}
                """;
        return HelmTemplateUtils.substitute(template, "@@CHART@@", chartName);
    }
}
