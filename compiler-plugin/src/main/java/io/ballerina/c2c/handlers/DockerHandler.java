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

import io.ballerina.c2c.diagnostics.NullLocation;
import io.ballerina.c2c.exceptions.DockerGenException;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.util.C2CDiagnosticCodes;
import io.ballerina.c2c.utils.DockerGenerator;
import io.ballerina.c2c.utils.NativeDockerGenerator;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticFactory;
import io.ballerina.tools.diagnostics.DiagnosticInfo;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;

/**
 * Wrapper handler for creating docker artifacts.
 */
public class DockerHandler extends AbstractArtifactHandler {
    private boolean isNative;

    public DockerHandler(boolean isNative) {
        this.isNative = isNative;
    }

    @Override
    public void createArtifacts() throws KubernetesPluginException {
        try {
            DockerGenerator dockerArtifactHandler;
            //
            if (isNative) {
                dockerArtifactHandler = new NativeDockerGenerator(dataHolder.getDockerModel());
            } else {
                dockerArtifactHandler = new DockerGenerator(dataHolder.getDockerModel());
            }

            if (dataHolder.getDockerModel().isTest()) {
                dockerArtifactHandler.createTestArtifacts(OUT,
                        "\t@kubernetes:Docker \t\t\t",
                        dataHolder.getDockerArtifactOutputPath());
            } else {
                dockerArtifactHandler.createArtifacts(OUT,
                        "\t@kubernetes:Docker \t\t\t",
                        dataHolder.getJarPath(),
                        dataHolder.getDockerArtifactOutputPath());
            }

        } catch (DockerGenException e) {
            DiagnosticInfo diagnosticInfo = new DiagnosticInfo(C2CDiagnosticCodes.DOCKER_FAILED.getCode(),
                    e.getMessage(), DiagnosticSeverity.WARNING);
            Diagnostic diagnostic = DiagnosticFactory.createDiagnostic(diagnosticInfo, new NullLocation());
            throw new KubernetesPluginException(diagnostic, true);
        }
    }
}
