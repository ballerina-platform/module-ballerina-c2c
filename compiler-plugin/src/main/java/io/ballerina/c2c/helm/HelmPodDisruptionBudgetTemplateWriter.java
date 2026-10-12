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
 * Writes {@code templates/poddisruptionbudget.yaml}, Deployment/Service path only -- a
 * PodDisruptionBudget protects a long-running, continuously-replicated workload against
 * voluntary disruption (node drains/upgrades), which doesn't apply to Job/CronJob pods.
 * <p>
 * Unlike the other conditionally-emitted templates, this file is always written, gated
 * internally by {@code {{- if .Values.podDisruptionBudget.enabled }} }} -- same idiom as
 * {@link HelmHpaTemplateWriter}, so it can be toggled per release with
 * {@code --set podDisruptionBudget.enabled=false} without a rebuild.
 * <p>
 * Uses {@code maxUnavailable} rather than {@code minAvailable} (the field real-world reference
 * charts -- WSO2's own {@code helm-mi}/{@code helm-apim} among them -- commonly use):
 * {@code DeploymentModel} defaults {@code replicaCount} to 1, and {@code minAvailable: 1} with a
 * single replica would block 100% of pods from ever being evicted, silently blocking
 * {@code kubectl drain}/node maintenance for the common single-replica case. {@code
 * maxUnavailable: 1} is a no-op at replicaCount 1 (same as no PDB at all) and only starts doing
 * real work once a release is scaled up, so enabling this by default is safe regardless of
 * replica count.
 *
 * @since 1.0.0
 */
public final class HelmPodDisruptionBudgetTemplateWriter {

    private HelmPodDisruptionBudgetTemplateWriter() {
    }

    public static String podDisruptionBudgetYaml(String chartName) {
        String template = """
                {{- if .Values.podDisruptionBudget.enabled }}
                apiVersion: policy/v1
                kind: PodDisruptionBudget
                metadata:
                  name: {{ include "@@CHART@@.fullname" . }}
                  namespace: {{ .Release.Namespace }}
                  labels:
                    {{- include "@@CHART@@.labels" . | nindent 4 }}
                spec:
                  maxUnavailable: {{ .Values.podDisruptionBudget.maxUnavailable }}
                  selector:
                    matchLabels:
                      {{- include "@@CHART@@.selectorLabels" . | nindent 6 }}
                {{- end }}
                """;
        return HelmTemplateUtils.substitute(template, "@@CHART@@", chartName);
    }
}
