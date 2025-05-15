/*
 * Copyright (c) 2025, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
import io.ballerina.c2c.util.C2CDiagnosticCodes;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigBuilder;

import java.io.IOException;

/**
 * Generates kubernetes deployment from annotations.
 */
public class BuildConfigHandler extends AbstractArtifactHandler {


    @Override
    public void createArtifacts() throws KubernetesPluginException {
        BuildConfig buildConfig = new BuildConfigBuilder()
                .withApiVersion("build.openshift.io/v1")
                .withKind("BuildConfig")
                .withNewMetadata()
                .withName("dockerfile-binary-build")
                .endMetadata()
                .withNewSpec()
                .withNewSource()
                .withType("Binary")
                .endSource()
                .withNewStrategy()
                .withType("Docker")
                .withNewDockerStrategy()
                .withNoCache(false)
                .endDockerStrategy()
                .endStrategy()
                .withNewOutput()
                .withNewTo()
                .withKind("ImageStreamTag")
                .withName("dockerfile-app:latest")
                .endTo()
                .endOutput()
                .endSpec()
                .build();
        String outputFileName = KubernetesConstants.BUILD_CONFIG_FILE_POSTFIX + KubernetesConstants.YAML;
        try {
            String buildConfigYAML = KubernetesUtils.asYaml(buildConfig);
            if (dataHolder.isSingleYaml()) {
                outputFileName = buildConfig.getMetadata().getName() + KubernetesConstants.YAML;
            }
            OUT.println("\t@openshift:BuildConfig");
            KubernetesUtils.writeToFile(dataHolder.getOpenshiftArtifactOutputPath(), buildConfigYAML, outputFileName);
        } catch (IOException e) {
            Diagnostic diagnostic = C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.ARTIFACT_GEN_FAILED,
                    new NullLocation(), "buildConfig", outputFileName);
            throw new KubernetesPluginException(diagnostic);
        }
    }

}

