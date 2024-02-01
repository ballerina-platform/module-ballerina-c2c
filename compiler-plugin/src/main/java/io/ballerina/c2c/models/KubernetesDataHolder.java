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

import io.ballerina.toml.api.Toml;
import lombok.Data;
import org.ballerinalang.model.elements.PackageID;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class to store kubernetes models.
 */
@Data
public class KubernetesDataHolder {
    private DeploymentModel deploymentModel;
    private DockerModel dockerModel;
    private PodAutoscalerModel podAutoscalerModel;
    private List<ServiceModel> serviceModelList;
    private Map<String, Set<SecretModel>> bListenerToSecretMap;
    private Set<SecretModel> secretModelSet;
    private Set<ConfigMapModel> configMapModelSet;
    private JobModel jobModel;
    private Path jarPath;
    private Path k8sArtifactOutputPath;
    private Path dockerArtifactOutputPath;
    private Path choreoArtifactOutputPath;
    private String namespace;
    private Path sourceRoot;
    private PackageID packageID;
    private Toml ballerinaCloud;
    private boolean singleYaml;
    private String outputName;

    KubernetesDataHolder() {
        this.serviceModelList = new ArrayList<>();
        this.bListenerToSecretMap = new HashMap<>();
        this.secretModelSet = new HashSet<>();
        this.configMapModelSet = new HashSet<>();
        this.deploymentModel = new DeploymentModel();
        this.dockerModel = new DockerModel();
        this.ballerinaCloud = null;
        this.singleYaml = true;
    }

    public void addListenerSecret(String listenerName, Set<SecretModel> secretModel) {
        this.bListenerToSecretMap.put(listenerName, secretModel);
    }

    public void addSecrets(Set<SecretModel> secrets) {
        this.secretModelSet.addAll(secrets);
    }

    public void addConfigMaps(Set<ConfigMapModel> configMaps) {
        this.configMapModelSet.addAll(configMaps);
    }

    public void addServiceModel(ServiceModel serviceModel) {
        this.serviceModelList.add(serviceModel);
    }

}
