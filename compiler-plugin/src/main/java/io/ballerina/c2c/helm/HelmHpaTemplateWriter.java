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
 * Writes {@code templates/hpa.yaml}. Unlike the other conditionally-emitted templates, this file
 * is always written, gated internally by {@code {{- if .Values.autoscaling.enabled }} }} -- the
 * idiomatic Helm pattern (matching {@code helm create}'s own default hpa.yaml). This means
 * autoscaling can be toggled per release with {@code --set autoscaling.enabled=false}, which
 * `--cloud=k8s` cannot offer since it decides at build time.
 *
 * @since 1.0.0
 */
public final class HelmHpaTemplateWriter {

    private HelmHpaTemplateWriter() {
    }

    public static String hpaYaml(String chartName) {
        String template = """
                {{- if .Values.autoscaling.enabled }}
                apiVersion: autoscaling/v2
                kind: HorizontalPodAutoscaler
                metadata:
                  name: {{ include "@@CHART@@.fullname" . }}
                  namespace: {{ .Release.Namespace }}
                  labels:
                    {{- include "@@CHART@@.labels" . | nindent 4 }}
                spec:
                  scaleTargetRef:
                    apiVersion: apps/v1
                    kind: Deployment
                    name: {{ include "@@CHART@@.fullname" . }}
                  minReplicas: {{ .Values.autoscaling.minReplicas }}
                  maxReplicas: {{ .Values.autoscaling.maxReplicas }}
                  metrics:
                    - type: Resource
                      resource:
                        name: cpu
                        target:
                          type: Utilization
                          averageUtilization: {{ .Values.autoscaling.targetCPUUtilizationPercentage }}
                    {{- if .Values.autoscaling.targetMemoryUtilizationPercentage }}
                    - type: Resource
                      resource:
                        name: memory
                        target:
                          type: Utilization
                          averageUtilization: {{ .Values.autoscaling.targetMemoryUtilizationPercentage }}
                    {{- end }}
                {{- end }}
                """;
        return HelmTemplateUtils.substitute(template, "@@CHART@@", chartName);
    }
}
