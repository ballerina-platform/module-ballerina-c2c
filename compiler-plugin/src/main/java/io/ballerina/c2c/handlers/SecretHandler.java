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
import io.ballerina.c2c.models.DeploymentModel;
import io.ballerina.c2c.models.SecretModel;
import io.ballerina.c2c.util.C2CDiagnosticCodes;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Generates kubernetes secret.
 */
public class SecretHandler extends AbstractArtifactHandler {

    private void generate(SecretModel secretModel) throws KubernetesPluginException {
        Secret secret = new SecretBuilder()
                .withNewMetadata()
                .withNamespace(dataHolder.getNamespace())
                .withName(secretModel.getName())
                .endMetadata()
                .withData(secretModel.getData())
                .build();
        try {
            String secretContent = KubernetesUtils.asYaml(secret);
            String outputFileName = KubernetesConstants.SECRET_FILE_POSTFIX + KubernetesConstants.YAML;
            if (dataHolder.isSingleYaml()) {
                outputFileName = secret.getMetadata().getName() + KubernetesConstants.YAML;
            }
            KubernetesUtils.writeToFile(secretContent, outputFileName);
        } catch (IOException e) {
            Diagnostic diagnostic = C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.ARTIFACT_GEN_FAILED,
                    new NullLocation(), "secret", secretModel.getName());
            throw new KubernetesPluginException(diagnostic);
        }
    }

    @Override
    public void createArtifacts() throws KubernetesPluginException {
        Collection<SecretModel> secretModels = dataHolder.getSecretModelSet();
        StringBuilder configTomlEnv = new StringBuilder();
        for (SecretModel secretModel : secretModels) {
            if (secretModel.isBallerinaConf()) {
                DeploymentModel deploymentModel = dataHolder.getDeploymentModel();
                configTomlEnv.append(getBALConfigFiles(secretModel));
                dataHolder.setDeploymentModel(deploymentModel);
            }
            generate(secretModel);
        }

        if (configTomlEnv.length() > 0) {
            DeploymentModel deploymentModel = dataHolder.getDeploymentModel();
            List<EnvVar> envVars = deploymentModel.getEnvVars();
            if (isBalConfigFilesEnvExist(envVars)) {
                for (EnvVar envVar : envVars) {
                    if (envVar.getName().equals("BAL_CONFIG_FILES")) {
                        String value = envVar.getValue() + configTomlEnv;
                        envVar.setValue(value);
                    }
                }
            } else {
                EnvVar ballerinaConfEnv = new EnvVarBuilder()
                        .withName("BAL_CONFIG_FILES")
                        .withValue(configTomlEnv.toString())
                        .build();
                deploymentModel.addEnv(ballerinaConfEnv);
            }
            dataHolder.setDeploymentModel(deploymentModel);
        }
        OUT.println("\t@kubernetes:Secret");
    }

    private String getBALConfigFiles(SecretModel secretModel) {
        StringBuilder configPaths = new StringBuilder();
        for (String key : secretModel.getData().keySet()) {
            configPaths.append(secretModel.getMountPath()).append(key).append(":");
        }
        return configPaths.toString();
    }
    
    private boolean isBalConfigFilesEnvExist(List<EnvVar> envVars) {
        for (EnvVar envVar : envVars) {
            if (envVar.getName().equals("BAL_CONFIG_FILES")) {
                return true;
            }
        }
        return false;
    }
}
