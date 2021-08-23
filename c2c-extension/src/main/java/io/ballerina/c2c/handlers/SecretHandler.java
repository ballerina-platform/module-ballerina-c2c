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
import io.ballerina.c2c.diagnostics.C2CDiagnosticCodes;
import io.ballerina.c2c.diagnostics.NullLocation;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.models.DeploymentModel;
import io.ballerina.c2c.models.SecretModel;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;

import java.io.IOException;
import java.util.Collection;

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
            String secretContent = Serialization.asYaml(secret);
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
        //secret
        int count = 0;
        Collection<SecretModel> secretModels = dataHolder.getSecretModelSet();
        if (secretModels.size() > 0) {
            OUT.println();
        }
        for (SecretModel secretModel : secretModels) {
            count++;
            if (!KubernetesUtils.isBlank(secretModel.getBallerinaConf())) {
                if (secretModel.getData().size() != 1) {
                    Diagnostic diagnostic =
                            C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.ONLY_ONE_BALLERINA_CONFIG_ALLOWED,
                                    new NullLocation());
                    throw new KubernetesPluginException(diagnostic);
                }
                DeploymentModel deploymentModel = dataHolder.getDeploymentModel();
//                deploymentModel.setCommandArgs(" --b7a.config.file=${CONFIG_FILE}");
//                EnvVarValueModel envVarValueModel = new EnvVarValueModel(secretModel.getMountPath() +
//                        BALLERINA_CONF_FILE_NAME);
//                deploymentModel.addEnv("CONFIG_FILE", envVarValueModel);
                dataHolder.setDeploymentModel(deploymentModel);
            }
            generate(secretModel);
            OUT.print("\t@kubernetes:Secret \t\t\t - complete " + count + "/" + secretModels.size() + "\r");
        }

    }

}
