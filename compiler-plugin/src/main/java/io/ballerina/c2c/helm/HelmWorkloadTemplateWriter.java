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

import io.ballerina.c2c.models.ConfigMapModel;
import io.ballerina.c2c.models.DeploymentModel;
import io.ballerina.c2c.models.JobModel;
import io.ballerina.c2c.models.PersistentVolumeClaimModel;
import io.ballerina.c2c.models.SecretModel;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.fabric8.kubernetes.api.model.ConfigMapKeySelector;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.ballerina.c2c.helm.HelmTemplateUtils.indent;
import static io.ballerina.c2c.helm.HelmTemplateUtils.renderStringMap;
import static io.ballerina.c2c.helm.HelmTemplateUtils.yamlQuote;

/**
 * Writes the Helm chart's workload template ({@code templates/deployment.yaml} or
 * {@code templates/job.yaml}/{@code templates/cronjob.yaml}), following the same field-for-field
 * shape {@code handlers/DeploymentHandler} and {@code handlers/JobHandler} already build for the
 * plain {@code k8s}/{@code openshift} targets -- so that a {@code helm install} with default
 * values reproduces exactly what {@code --cloud=k8s} would have deployed.
 * <p>
 * Only the fields already exposed as Helm values in {@code values.yaml}
 * ({@link HelmChartMetadataWriter}) are read from {@code .Values} here (replicas, image,
 * resources, container ports, security contexts, pull secrets, node selector); everything else
 * (env vars, probes, volumes/volumeMounts, labels/annotations) is rendered statically from the
 * already-resolved model, exactly as the plain {@code k8s} handlers do.
 *
 * @since 1.0.0
 */
public final class HelmWorkloadTemplateWriter {

    private HelmWorkloadTemplateWriter() {
    }

    public static String deploymentYaml(String chartName, DeploymentModel deploymentModel) {
        StringBuilder out = new StringBuilder();
        out.append("apiVersion: apps/v1\n")
                .append("kind: Deployment\n")
                .append("metadata:\n")
                .append("  name: {{ include \"").append(chartName).append(".fullname\" . }}\n")
                .append("  namespace: {{ .Release.Namespace }}\n")
                .append("  labels:\n")
                .append("    {{- include \"").append(chartName).append(".labels\" . | nindent 4 }}\n");
        appendStaticMapBlock(out, "  annotations:", deploymentModel.getAnnotations(), 4);

        out.append("spec:\n")
                .append("  replicas: {{ .Values.replicaCount }}\n")
                .append("  selector:\n")
                .append("    matchLabels:\n")
                .append("      {{- include \"").append(chartName).append(".selectorLabels\" . | nindent 6 }}\n")
                .append("  template:\n")
                .append("    metadata:\n")
                .append("      labels:\n")
                .append("        {{- include \"").append(chartName)
                .append(".selectorLabels\" . | nindent 8 }}\n")
                .append(renderPodAnnotationsBlock(deploymentModel));

        out.append("    spec:\n")
                .append("      serviceAccountName: {{ include \"").append(chartName)
                .append(".serviceAccountName\" . }}\n")
                .append("      {{- with .Values.podSecurityContext }}\n")
                .append("      securityContext:\n")
                .append("        {{- toYaml . | nindent 8 }}\n")
                .append("      {{- end }}\n")
                .append("      {{- with .Values.image.pullSecrets }}\n")
                .append("      imagePullSecrets:\n")
                .append("        {{- range . }}\n")
                .append("        - name: {{ . }}\n")
                .append("        {{- end }}\n")
                .append("      {{- end }}\n")
                .append("      containers:\n")
                .append("        - name: {{ include \"").append(chartName).append(".name\" . }}\n")
                .append("          image: \"{{ .Values.image.repository }}:{{ .Values.image.tag }}\"\n")
                .append("          imagePullPolicy: {{ .Values.image.pullPolicy }}\n")
                .append("          {{- with .Values.securityContext }}\n")
                .append("          securityContext:\n")
                .append("            {{- toYaml . | nindent 12 }}\n")
                .append("          {{- end }}\n")
                .append("          {{- with .Values.service.ports }}\n")
                .append("          ports:\n")
                .append("            {{- range . }}\n")
                .append("            - name: {{ .name }}\n")
                .append("              containerPort: {{ .targetPort }}\n")
                .append("              protocol: TCP\n")
                .append("            {{- end }}\n")
                .append("          {{- end }}\n")
                .append(renderEnvBlock(deploymentModel.getEnvVars(), 10))
                .append(renderProbeBlock("livenessProbe", deploymentModel.getLivenessProbe(), 10))
                .append(renderProbeBlock("readinessProbe", deploymentModel.getReadinessProbe(), 10))
                .append("          resources:\n")
                .append("            {{- toYaml .Values.resources | nindent 12 }}\n")
                .append("          lifecycle:\n")
                .append("            preStop:\n")
                .append("              exec:\n")
                .append("                command: [\"sleep\", \"15\"]\n")
                .append(renderVolumeMountsBlock(deploymentModel, 10))
                .append("      {{- with .Values.nodeSelector }}\n")
                .append("      nodeSelector:\n")
                .append("        {{- toYaml . | nindent 8 }}\n")
                .append("      {{- end }}\n")
                .append(renderVolumesBlock(deploymentModel.getSecretModels(), deploymentModel.getConfigMapModels(),
                        deploymentModel.getVolumeClaimModels(), 6));
        return out.toString();
    }

    /**
     * @param secretModels    secrets to mount -- for a Job/CronJob these live on the shared
     *                        {@code KubernetesDataHolder}, not on {@code JobModel} itself (see
     *                        {@code handlers/JobHandler#populateVolumeMounts})
     * @param configMapModels configmaps to mount, same caveat as {@code secretModels}
     */
    public static String jobYaml(String chartName, JobModel jobModel, Set<SecretModel> secretModels,
                                  Set<ConfigMapModel> configMapModels) {
        boolean scheduled = !KubernetesUtils.isBlank(jobModel.getSchedule());
        StringBuilder out = new StringBuilder();
        out.append("apiVersion: batch/v1\n")
                .append("kind: ").append(scheduled ? "CronJob" : "Job").append('\n')
                .append("metadata:\n")
                .append("  name: {{ include \"").append(chartName).append(".fullname\" . }}\n")
                .append("  namespace: {{ .Release.Namespace }}\n")
                .append("  labels:\n")
                .append("    {{- include \"").append(chartName).append(".labels\" . | nindent 4 }}\n")
                .append("spec:\n");
        int specIndent = 2;
        if (scheduled) {
            out.append("  schedule: ").append(yamlQuote(jobModel.getSchedule())).append('\n')
                    .append("  jobTemplate:\n")
                    .append("    spec:\n");
            specIndent = 6;
        }
        String pad = " ".repeat(specIndent);
        List<VolumeMount> volumeMounts = new ArrayList<>();
        volumeMounts.addAll(KubernetesUtils.generateSecretVolumeMounts(secretModels));
        volumeMounts.addAll(KubernetesUtils.generateConfigMapVolumeMounts(configMapModels));
        out.append(pad).append("template:\n")
                .append(pad).append("  spec:\n")
                .append(pad).append("    restartPolicy: ").append(jobModel.getRestartPolicy()).append('\n')
                .append(pad).append("    serviceAccountName: {{ include \"").append(chartName)
                .append(".serviceAccountName\" . }}\n")
                .append(pad).append("    {{- with .Values.image.pullSecrets }}\n")
                .append(pad).append("    imagePullSecrets:\n")
                .append(pad).append("      {{- range . }}\n")
                .append(pad).append("      - name: {{ . }}\n")
                .append(pad).append("      {{- end }}\n")
                .append(pad).append("    {{- end }}\n")
                .append(pad).append("    containers:\n")
                .append(pad).append("      - name: {{ include \"").append(chartName).append(".name\" . }}\n")
                .append(pad).append("        image: \"{{ .Values.image.repository }}:{{ .Values.image.tag }}\"\n")
                .append(pad).append("        imagePullPolicy: {{ .Values.image.pullPolicy }}\n")
                .append(renderEnvBlock(jobModel.getEnvVars(), specIndent + 8))
                .append(renderVolumeMounts(volumeMounts, pad + "        "))
                .append(renderVolumesBlock(secretModels, configMapModels, Set.of(), specIndent + 4));
        return out.toString();
    }

    private static void appendStaticMapBlock(StringBuilder out, String key, Map<String, String> map,
                                              int childIndent) {
        if (map == null || map.isEmpty()) {
            return;
        }
        out.append(key).append('\n').append(indent(renderStringMap(map), childIndent)).append('\n');
    }

    /**
     * Renders the pod template's {@code annotations:} block: any static annotations the user
     * configured, plus a {@code checksum/config} hash of every mounted ConfigMap/Secret template.
     * <p>
     * Without this, a {@code helm upgrade} that only changes a values-driven config value (see
     * {@link HelmConfigMapTemplateWriter}) updates the ConfigMap object but never the Deployment
     * spec itself, so Kubernetes has no reason to recreate the pod -- the already-running
     * container keeps its stale mounted Config.toml (Ballerina reads it once at startup) even
     * though `kubectl get configmap` shows the new value. Hashing the rendered config templates
     * into a pod annotation forces a new rollout whenever their content actually changes.
     */
    private static String renderPodAnnotationsBlock(DeploymentModel deploymentModel) {
        List<String> checksumSources = new ArrayList<>();
        for (ConfigMapModel configMapModel : deploymentModel.getConfigMapModels()) {
            checksumSources.add("(include (print $.Template.BasePath \"/configmap-"
                    + configMapModel.getName() + ".yaml\") .)");
        }
        for (SecretModel secretModel : deploymentModel.getSecretModels()) {
            checksumSources.add("(include (print $.Template.BasePath \"/secret-"
                    + secretModel.getName() + ".yaml\") .)");
        }
        Map<String, String> podAnnotations = deploymentModel.getPodAnnotations();
        if (checksumSources.isEmpty() && (podAnnotations == null || podAnnotations.isEmpty())) {
            return "";
        }
        StringBuilder out = new StringBuilder("      annotations:\n");
        if (podAnnotations != null && !podAnnotations.isEmpty()) {
            out.append(indent(renderStringMap(podAnnotations), 8)).append('\n');
        }
        if (!checksumSources.isEmpty()) {
            out.append("        checksum/config: {{ print ")
                    .append(String.join(" ", checksumSources))
                    .append(" | sha256sum }}\n");
        }
        return out.toString();
    }

    private static String renderEnvBlock(List<EnvVar> envVars, int indentSpaces) {
        String pad = " ".repeat(indentSpaces);
        if (envVars == null || envVars.isEmpty()) {
            return pad + "env: []\n";
        }
        StringBuilder out = new StringBuilder(pad).append("env:\n");
        for (EnvVar envVar : envVars) {
            out.append(pad).append("- name: ").append(yamlQuote(envVar.getName())).append('\n');
            if (envVar.getValue() != null) {
                out.append(pad).append("  value: ").append(yamlQuote(envVar.getValue())).append('\n');
            } else if (envVar.getValueFrom() != null && envVar.getValueFrom().getConfigMapKeyRef() != null) {
                ConfigMapKeySelector ref = envVar.getValueFrom().getConfigMapKeyRef();
                out.append(pad).append("  valueFrom:\n")
                        .append(pad).append("    configMapKeyRef:\n")
                        .append(pad).append("      name: ").append(yamlQuote(ref.getName())).append('\n')
                        .append(pad).append("      key: ").append(yamlQuote(ref.getKey())).append('\n');
            } else if (envVar.getValueFrom() != null && envVar.getValueFrom().getSecretKeyRef() != null) {
                SecretKeySelector ref = envVar.getValueFrom().getSecretKeyRef();
                out.append(pad).append("  valueFrom:\n")
                        .append(pad).append("    secretKeyRef:\n")
                        .append(pad).append("      name: ").append(yamlQuote(ref.getName())).append('\n')
                        .append(pad).append("      key: ").append(yamlQuote(ref.getKey())).append('\n');
            }
        }
        return out.toString();
    }

    private static String renderProbeBlock(String key, Probe probe, int indentSpaces) {
        if (probe == null) {
            return "";
        }
        String pad = " ".repeat(indentSpaces);
        StringBuilder out = new StringBuilder(pad).append(key).append(":\n");
        if (probe.getHttpGet() != null) {
            out.append(pad).append("  httpGet:\n")
                    .append(pad).append("    path: ").append(yamlQuote(probe.getHttpGet().getPath())).append('\n')
                    .append(pad).append("    port: ").append(probe.getHttpGet().getPort().getIntVal()).append('\n');
        } else if (probe.getTcpSocket() != null) {
            out.append(pad).append("  tcpSocket:\n")
                    .append(pad).append("    port: ").append(probe.getTcpSocket().getPort().getIntVal())
                    .append('\n');
        }
        if (probe.getInitialDelaySeconds() != null) {
            out.append(pad).append("  initialDelaySeconds: ").append(probe.getInitialDelaySeconds()).append('\n');
        }
        if (probe.getPeriodSeconds() != null) {
            out.append(pad).append("  periodSeconds: ").append(probe.getPeriodSeconds()).append('\n');
        }
        return out.toString();
    }

    private static String renderVolumeMountsBlock(DeploymentModel deploymentModel, int indentSpaces) {
        String pad = " ".repeat(indentSpaces);
        List<VolumeMount> volumeMounts = new ArrayList<>();
        volumeMounts.addAll(KubernetesUtils.generateSecretVolumeMounts(deploymentModel.getSecretModels()));
        volumeMounts.addAll(KubernetesUtils.generateConfigMapVolumeMounts(deploymentModel.getConfigMapModels()));
        for (PersistentVolumeClaimModel pvc : deploymentModel.getVolumeClaimModels()) {
            volumeMounts.add(new VolumeMountBuilder()
                    .withMountPath(pvc.getMountPath())
                    .withName(pvc.getName() + "-volume")
                    .withReadOnly(pvc.isReadOnly())
                    .build());
        }
        return renderVolumeMounts(volumeMounts, pad);
    }

    private static String renderVolumeMounts(List<VolumeMount> volumeMounts, String pad) {
        if (volumeMounts.isEmpty()) {
            return pad + "volumeMounts: []\n";
        }
        StringBuilder out = new StringBuilder(pad).append("volumeMounts:\n");
        for (VolumeMount volumeMount : volumeMounts) {
            out.append(pad).append("- name: ").append(yamlQuote(volumeMount.getName())).append('\n')
                    .append(pad).append("  mountPath: ").append(yamlQuote(volumeMount.getMountPath())).append('\n')
                    .append(pad).append("  readOnly: ").append(Boolean.TRUE.equals(volumeMount.getReadOnly()))
                    .append('\n');
            if (volumeMount.getSubPath() != null) {
                out.append(pad).append("  subPath: ").append(yamlQuote(volumeMount.getSubPath())).append('\n');
            }
        }
        return out.toString();
    }

    static String renderVolumesBlock(Set<SecretModel> secretModels, Set<ConfigMapModel> configMapModels,
                                      Set<PersistentVolumeClaimModel> volumeClaimModels, int indentSpaces) {
        String pad = " ".repeat(indentSpaces);
        boolean empty = secretModels.isEmpty() && configMapModels.isEmpty() && volumeClaimModels.isEmpty();
        if (empty) {
            return pad + "volumes: []\n";
        }
        StringBuilder out = new StringBuilder(pad).append("volumes:\n");
        for (SecretModel secretModel : secretModels) {
            out.append(pad).append("- name: ").append(yamlQuote(secretModel.getName() + "-volume")).append('\n')
                    .append(pad).append("  secret:\n")
                    .append(pad).append("    secretName: ").append(yamlQuote(secretModel.getName())).append('\n');
            if (secretModel.getDefaultMode() > 0) {
                out.append(pad).append("    defaultMode: ").append(secretModel.getDefaultMode()).append('\n');
            }
        }
        for (ConfigMapModel configMapModel : configMapModels) {
            out.append(pad).append("- name: ").append(yamlQuote(configMapModel.getName() + "-volume")).append('\n')
                    .append(pad).append("  configMap:\n")
                    .append(pad).append("    name: ").append(yamlQuote(configMapModel.getName())).append('\n');
            if (configMapModel.getDefaultMode() > 0) {
                out.append(pad).append("    defaultMode: ").append(configMapModel.getDefaultMode()).append('\n');
            }
        }
        for (PersistentVolumeClaimModel pvc : volumeClaimModels) {
            out.append(pad).append("- name: ").append(yamlQuote(pvc.getName() + "-volume")).append('\n')
                    .append(pad).append("  persistentVolumeClaim:\n")
                    .append(pad).append("    claimName: ").append(yamlQuote(pvc.getName())).append('\n');
        }
        return out.toString();
    }
}
