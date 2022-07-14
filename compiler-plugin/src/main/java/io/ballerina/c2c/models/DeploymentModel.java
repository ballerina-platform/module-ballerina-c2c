/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package io.ballerina.c2c.models;

import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentStrategy;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.ballerina.c2c.DockerGenConstants.OPENJDK_11_JRE_SLIM_BASE;

/**
 * Kubernetes deployment annotations model class.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DeploymentModel extends KubernetesModel {
    private Map<String, String> podAnnotations;
    private int replicas;
    private Probe livenessProbe;
    private Probe readinessProbe;
    private String namespace;
    private String image;
    private boolean buildImage;
    private String baseImage;
    private String dockerHost;
    private String dockerCertPath;
    private List<ContainerPort> ports;
    private PodAutoscalerModel podAutoscalerModel;
    private Set<SecretModel> secretModels;
    private Set<ConfigMapModel> configMapModels;
    private Set<PersistentVolumeClaimModel> volumeClaimModels;
    private Set<String> imagePullSecrets;
    private String commandArgs;
    private String registry;
    private DeploymentStrategy strategy;
    private Map<String, String> nodeSelector;
    private String dockerConfigPath;
    private ResourceRequirements resourceRequirements;
    private String internalDomainName;

    public DeploymentModel() {
        // Initialize with default values.
        this.replicas = 1;
        this.envVars = new ArrayList<>();
        this.buildImage = true;
        this.baseImage = OPENJDK_11_JRE_SLIM_BASE;
        this.labels = new LinkedHashMap<>();
        this.nodeSelector = new LinkedHashMap<>();
        this.ports = new ArrayList<>();
        this.secretModels = new HashSet<>();
        this.configMapModels = new HashSet<>();
        this.volumeClaimModels = new HashSet<>();
        this.imagePullSecrets = new HashSet<>();
        this.commandArgs = "";
        this.registry = "";
        Map<String, Quantity> limit = new HashMap<>();
        limit.put("cpu", new Quantity("500m"));
        limit.put("memory", new Quantity("256Mi"));
        Map<String, Quantity> resource = new HashMap<>();
        resource.put("cpu", new Quantity("200m"));
        resource.put("memory", new Quantity("100Mi"));
        this.resourceRequirements = new ResourceRequirementsBuilder()
                .withLimits(limit)
                .withRequests(resource)
                .build();
    }

    public Map<String, String> getPodAnnotations() {
        return podAnnotations;
    }

    public void setLivenessProbe(Probe livenessProbe) {
        this.livenessProbe = livenessProbe;
    }

    public void setReadinessProbe(Probe readinessProbe) {
        this.readinessProbe = readinessProbe;
    }

    public void addPort(ContainerPort port) {
        this.ports.add(port);
    }

}
