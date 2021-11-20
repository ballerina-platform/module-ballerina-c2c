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

package io.ballerina.c2c.handlers;

import io.ballerina.c2c.KubernetesConstants;
import io.ballerina.c2c.diagnostics.NullLocation;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.models.ConfigMapModel;
import io.ballerina.c2c.models.DeploymentModel;
import io.ballerina.c2c.util.C2CDiagnosticCodes;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;

import java.io.IOException;
import java.util.Collection;

/**
 * Generates kubernetes Config Map.
 */
public class ConfigMapHandler extends AbstractArtifactHandler {

    private void generate(ConfigMapModel configMapModel) throws KubernetesPluginException {
        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName(configMapModel.getName())
                .withNamespace(dataHolder.getNamespace())
                .endMetadata()
                .withData(configMapModel.getData())
                .build();
        try {
            String configMapContent = Serialization.asYaml(configMap);
            String outputFileName = KubernetesConstants.CONFIG_MAP_FILE_POSTFIX + KubernetesConstants.YAML;
            if (dataHolder.isSingleYaml()) {
                outputFileName = configMap.getMetadata().getName() + KubernetesConstants.YAML;
            }
            KubernetesUtils.writeToFile(configMapContent, outputFileName);
        } catch (IOException e) {
            Diagnostic diagnostic = C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.ARTIFACT_GEN_FAILED,
                    new NullLocation(), "config map" , configMapModel.getName());
            throw new KubernetesPluginException(diagnostic);
        }
    }

    @Override
    public void createArtifacts() throws KubernetesPluginException {
        //Config Map
        int count = 0;
        Collection<ConfigMapModel> configMapModels = dataHolder.getConfigMapModelSet();
        if (configMapModels.size() > 0) {
            OUT.println();
        }
        for (ConfigMapModel configMapModel : configMapModels) {
            count++;
            if (configMapModel.isBallerinaConf()) {
                DeploymentModel deploymentModel = dataHolder.getDeploymentModel();
                EnvVar ballerinaConfEnv = new EnvVarBuilder()
                        .withName("BAL_CONFIG_FILES")
                        .withValue(getBALConfigFiles(configMapModel))
                        .build();
                deploymentModel.addEnv(ballerinaConfEnv);
                dataHolder.setDeploymentModel(deploymentModel);
            }
            generate(configMapModel);
            OUT.print("\t@kubernetes:ConfigMap \t\t\t - complete " + count + "/" + configMapModels.size() + "\r");
        }
    }

    private String getBALConfigFiles(ConfigMapModel configMapModel) {
        StringBuilder configPaths = new StringBuilder();
        for (String key : configMapModel.getData().keySet()) {
            configPaths.append(configMapModel.getMountPath()).append(key).append(":");
        }
        return configPaths.toString();
    }
}
