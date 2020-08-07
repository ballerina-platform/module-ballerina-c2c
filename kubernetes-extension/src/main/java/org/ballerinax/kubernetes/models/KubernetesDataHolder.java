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

package org.ballerinax.kubernetes.models;

import com.moandjiezana.toml.Toml;
import lombok.Data;
import org.ballerinalang.model.elements.PackageID;
import org.ballerinax.docker.generator.models.DockerModel;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Class to store kubernetes models.
 */
@Data
public class KubernetesDataHolder {
    private boolean canProcess;
    private DeploymentModel deploymentModel;
    private DockerModel dockerModel;
    private PodAutoscalerModel podAutoscalerModel;
    private Map<String, ServiceModel> bListenerToK8sServiceMap;
    private Map<String, Set<SecretModel>> bListenerToSecretMap;
    private Set<SecretModel> secretModelSet;
    private Set<ConfigMapModel> configMapModelSet;
    private Set<PersistentVolumeClaimModel> volumeClaimModelSet;
    private Set<ResourceQuotaModel> resourceQuotaModels;
    private JobModel jobModel;
    private Path uberJarPath;
    private Path k8sArtifactOutputPath;
    private Path dockerArtifactOutputPath;
    private String namespace;
    private Path sourceRoot;
    private PackageID packageID;
    private Toml ballerinaCloud;

    KubernetesDataHolder(Path sourceRoot) {
        this.sourceRoot = sourceRoot;
        this.bListenerToK8sServiceMap = new HashMap<>();
        this.bListenerToSecretMap = new HashMap<>();
        this.secretModelSet = new HashSet<>();
        this.configMapModelSet = new HashSet<>();
        this.volumeClaimModelSet = new HashSet<>();
        this.deploymentModel = new DeploymentModel();
        this.resourceQuotaModels = new HashSet<>();
        this.dockerModel = new DockerModel();
        this.ballerinaCloud = null;
    }

    public void addSecrets(Set<SecretModel> secrets) {
        this.secretModelSet.addAll(secrets);
    }

    public void addConfigMaps(Set<ConfigMapModel> configMaps) {
        this.configMapModelSet.addAll(configMaps);
    }

    public void addPersistentVolumeClaims(Set<PersistentVolumeClaimModel> persistentVolumeClaims) {
        this.volumeClaimModelSet.addAll(persistentVolumeClaims);
    }

    public void addBListenerToK8sServiceMap(String listenerName, ServiceModel serviceModel) {
        this.bListenerToK8sServiceMap.put(listenerName, serviceModel);
    }

}
