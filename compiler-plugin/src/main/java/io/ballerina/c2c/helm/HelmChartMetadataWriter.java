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

import io.ballerina.c2c.models.DeploymentModel;
import io.ballerina.c2c.models.DockerModel;
import io.ballerina.c2c.models.JobModel;
import io.ballerina.c2c.models.PodAutoscalerModel;
import io.ballerina.c2c.models.ServiceModel;

import java.util.List;

import static io.ballerina.c2c.DockerGenConstants.REGISTRY_SEPARATOR;
import static io.ballerina.c2c.helm.HelmTemplateUtils.renderQuantityMap;
import static io.ballerina.c2c.helm.HelmTemplateUtils.substitute;
import static io.ballerina.c2c.helm.HelmTemplateUtils.yamlQuote;

/**
 * Writes the non-template root files of a Helm chart: {@code Chart.yaml}, {@code values.yaml},
 * {@code .helmignore} and {@code templates/_helpers.tpl}.
 * <p>
 * {@code values.yaml}'s defaults are derived from the same already-resolved
 * {@code DeploymentModel}/{@code ServiceModel}/{@code PodAutoscalerModel}/{@code DockerModel}
 * instances the other Helm template writers read from, so a {@code helm install} with no
 * {@code --set} overrides reproduces exactly what {@code --cloud=k8s} would have deployed.
 *
 * @since 1.0.0
 */
public final class HelmChartMetadataWriter {

    private HelmChartMetadataWriter() {
    }

    public static String chartYaml(String chartName, String version, String appVersion, String description) {
        String template = """
                apiVersion: v2
                name: @@CHART_NAME@@
                description: @@DESCRIPTION@@
                type: application
                version: @@VERSION@@
                appVersion: @@APP_VERSION@@
                """;
        return substitute(template,
                "@@CHART_NAME@@", chartName,
                "@@DESCRIPTION@@", yamlQuote(description),
                "@@VERSION@@", version,
                "@@APP_VERSION@@", yamlQuote(appVersion));
    }

    public static String helmIgnore() {
        return """
                # Patterns to ignore when building packages.
                .DS_Store
                .git/
                .gitignore
                *.swp
                *.bak
                *.tmp
                *.orig
                *~
                .idea/
                .vscode/
                """;
    }

    public static String helpersTpl(String chartName) {
        String template = """
                {{/*
                Expand the name of the chart.
                */}}
                {{- define "@@CHART@@.name" -}}
                {{- default "@@CHART@@" .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
                {{- end -}}

                {{/*
                Create a default fully qualified app name. Truncated at 63 chars since some
                Kubernetes name fields are limited to that (the DNS naming spec). If the release
                name already contains the chart name, the release name is used as-is.
                */}}
                {{- define "@@CHART@@.fullname" -}}
                {{- if .Values.fullnameOverride -}}
                {{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
                {{- else -}}
                {{- $name := default "@@CHART@@" .Values.nameOverride -}}
                {{- if contains $name .Release.Name -}}
                {{- .Release.Name | trunc 63 | trimSuffix "-" -}}
                {{- else -}}
                {{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
                {{- end -}}
                {{- end -}}
                {{- end -}}

                {{/*
                Create chart name and version as used by the chart label.
                */}}
                {{- define "@@CHART@@.chart" -}}
                {{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
                {{- end -}}

                {{/*
                Common labels, following the Kubernetes recommended label set
                (https://kubernetes.io/docs/concepts/overview/working-with-objects/common-labels/).
                */}}
                {{- define "@@CHART@@.labels" -}}
                app.kubernetes.io/name: {{ include "@@CHART@@.name" . }}
                helm.sh/chart: {{ include "@@CHART@@.chart" . }}
                app.kubernetes.io/instance: {{ .Release.Name }}
                {{- if .Chart.AppVersion }}
                app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
                {{- end }}
                app.kubernetes.io/managed-by: {{ .Release.Service }}
                {{- end -}}

                {{/*
                Selector labels -- kept separate from the common labels above since selector
                labels on an existing Deployment/Job are immutable; only these are used for
                matchLabels/selectors. Includes a plain, release-independent "app" label (not
                just the release-scoped app.kubernetes.io/instance) so an externally-authored
                resource -- a hand-written NetworkPolicy, a ServiceMonitor, another chart -- can
                select this chart's pods by a stable name across releases. Override it the same
                way as the chart name itself, via nameOverride.
                */}}
                {{- define "@@CHART@@.selectorLabels" -}}
                app.kubernetes.io/name: {{ include "@@CHART@@.name" . }}
                app.kubernetes.io/instance: {{ .Release.Name }}
                app: {{ include "@@CHART@@.name" . }}
                {{- end -}}

                {{/*
                Name of the ServiceAccount to use.
                */}}
                {{- define "@@CHART@@.serviceAccountName" -}}
                {{- if .Values.serviceAccount.create -}}
                {{ default (include "@@CHART@@.fullname" .) .Values.serviceAccount.name }}
                {{- else -}}
                {{ default "default" .Values.serviceAccount.name }}
                {{- end -}}
                {{- end -}}
                """;
        return substitute(template, "@@CHART@@", chartName);
    }

    /**
     * Builds {@code values.yaml} for a Deployment/Service-based project (i.e. not a
     * {@code @cloud:Task} scheduled job -- see {@link #jobValuesYaml}).
     */
    public static String deploymentValuesYaml(DeploymentModel deploymentModel, List<ServiceModel> serviceModels,
                                               PodAutoscalerModel podAutoscalerModel, DockerModel dockerModel) {
        boolean autoscalingEnabled = podAutoscalerModel != null;
        String servicePorts = renderServicePorts(serviceModels);
        String serviceType = serviceType(serviceModels);
        String template = """
                # Default values for this chart, generated by the Ballerina Code2Cloud (c2c)
                # compiler plugin (`bal build --cloud=helm`). This file mirrors exactly what
                # `--cloud=k8s` would have deployed -- override any of these per environment with
                # `helm install -f values-prod.yaml ...` or `--set key=value`, without rebuilding
                # the image.
                #
                # If a file referenced under `cloud.config.files`/`cloud.config.secrets` in your
                # Cloud.toml contains `{{ .Values.x }}` placeholders (see templates/configmap.yaml),
                # add the default values those placeholders reference here as well.

                nameOverride: ""
                fullnameOverride: ""

                replicaCount: @@REPLICAS@@

                image:
                  repository: @@IMAGE_REPO@@
                  tag: @@IMAGE_TAG@@
                  pullPolicy: IfNotPresent
                  # Names of existing imagePullSecrets to attach to the pod, e.g. [myregistrykey]
                  pullSecrets: []

                service:
                  type: @@SERVICE_TYPE@@
                  ports:
                @@SERVICE_PORTS@@

                resources:
                  requests:
                @@RESOURCE_REQUESTS@@
                  limits:
                @@RESOURCE_LIMITS@@

                autoscaling:
                  enabled: @@AUTOSCALING_ENABLED@@
                  minReplicas: @@MIN_REPLICAS@@
                  maxReplicas: @@MAX_REPLICAS@@
                  targetCPUUtilizationPercentage: @@TARGET_CPU@@
                  targetMemoryUtilizationPercentage: @@TARGET_MEMORY@@

                # Liveness/readiness probes are not values-driven: they're rendered directly from
                # whatever `cloud.deployment.probes.liveness`/`.readiness` resolved to in
                # Cloud.toml, same as the `k8s`/`openshift` targets -- see templates/deployment.yaml.

                serviceAccount:
                  # Whether a dedicated ServiceAccount should be created for this release.
                  create: true
                  # Name of the ServiceAccount to use. Defaults to the release's full name.
                  name: ""

                podDisruptionBudget:
                  # Whether a PodDisruptionBudget should be created for this release.
                  enabled: true
                  # Maximum pods that may be unavailable at once during a voluntary disruption
                  # (node drain/upgrade). A no-op at the default replicaCount of 1 (100% may
                  # already be unavailable); starts protecting once you scale up.
                  maxUnavailable: 1

                # Off by default: c2c's own base image does not switch to a non-root user, so
                # enforcing runAsNonRoot here would make `helm install` fail out of the box.
                # Turn this on once your image runs as a non-root user.
                podSecurityContext: {}
                securityContext: {}

                nodeSelector: {}
                """;
        return substitute(template,
                "@@REPLICAS@@", String.valueOf(deploymentModel.getReplicas()),
                "@@IMAGE_REPO@@", yamlQuote(imageRepository(deploymentModel.getRegistry(), dockerModel)),
                "@@IMAGE_TAG@@", yamlQuote(dockerModel.getTag()),
                "@@SERVICE_TYPE@@", serviceType,
                "@@SERVICE_PORTS@@", servicePorts,
                "@@RESOURCE_REQUESTS@@", HelmTemplateUtils.indent(
                        renderQuantityMap(deploymentModel.getResourceRequirements().getRequests()), 4),
                "@@RESOURCE_LIMITS@@", HelmTemplateUtils.indent(
                        renderQuantityMap(deploymentModel.getResourceRequirements().getLimits()), 4),
                "@@AUTOSCALING_ENABLED@@", String.valueOf(autoscalingEnabled),
                "@@MIN_REPLICAS@@", String.valueOf(
                        autoscalingEnabled ? podAutoscalerModel.getMinReplicas() : deploymentModel.getReplicas()),
                "@@MAX_REPLICAS@@", String.valueOf(
                        autoscalingEnabled ? podAutoscalerModel.getMaxReplicas() : deploymentModel.getReplicas() + 1),
                "@@TARGET_CPU@@", String.valueOf(autoscalingEnabled ? podAutoscalerModel.getCpuPercentage() : 50),
                "@@TARGET_MEMORY@@", autoscalingEnabled && podAutoscalerModel.getMemoryPercentage() > 0
                        ? String.valueOf(podAutoscalerModel.getMemoryPercentage()) : "null"
        );
    }

    /**
     * Builds {@code values.yaml} for a {@code @cloud:Task} scheduled/one-off Job project.
     */
    public static String jobValuesYaml(JobModel jobModel, DockerModel dockerModel) {
        String template = """
                # Default values for this chart, generated by the Ballerina Code2Cloud (c2c)
                # compiler plugin (`bal build --cloud=helm`). Override per environment with
                # `helm install -f values-prod.yaml ...` or `--set key=value`.

                nameOverride: ""
                fullnameOverride: ""

                image:
                  repository: @@IMAGE_REPO@@
                  tag: @@IMAGE_TAG@@
                  pullPolicy: IfNotPresent
                  pullSecrets: []

                serviceAccount:
                  create: true
                  name: ""

                podSecurityContext: {}
                securityContext: {}

                nodeSelector: {}
                """;
        return substitute(template,
                "@@IMAGE_REPO@@", yamlQuote(imageRepository(jobModel.getRegistry(), dockerModel)),
                "@@IMAGE_TAG@@", yamlQuote(dockerModel.getTag()));
    }

    private static String renderServicePorts(List<ServiceModel> serviceModels) {
        if (serviceModels == null || serviceModels.isEmpty()) {
            return "    []";
        }
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (ServiceModel serviceModel : serviceModels) {
            count++;
            if (!out.isEmpty()) {
                out.append('\n');
            }
            String name = serviceModel.getPortName() != null ? serviceModel.getPortName() : "port-" + count;
            out.append("    - name: ").append(yamlQuote(name)).append('\n')
                    .append("      port: ").append(serviceModel.getPort()).append('\n')
                    .append("      targetPort: ").append(serviceModel.getTargetPort());
        }
        return out.toString();
    }

    private static String serviceType(List<ServiceModel> serviceModels) {
        if (serviceModels == null || serviceModels.isEmpty()) {
            return "ClusterIP";
        }
        return serviceModels.get(0).getServiceType();
    }

    private static String imageRepository(String registry, DockerModel dockerModel) {
        if (registry == null || registry.isBlank()) {
            return dockerModel.getName();
        }
        return registry + REGISTRY_SEPARATOR + dockerModel.getName();
    }
}
