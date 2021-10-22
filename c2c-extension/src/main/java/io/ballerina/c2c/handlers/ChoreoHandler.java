/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import io.ballerina.c2c.diagnostics.C2CDiagnosticCodes;
import io.ballerina.c2c.diagnostics.NullLocation;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.models.ChoreoModel;
import io.ballerina.c2c.models.DeploymentModel;
import io.ballerina.c2c.models.PortModel;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticFactory;
import io.ballerina.tools.diagnostics.DiagnosticInfo;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import io.fabric8.kubernetes.api.model.ContainerPort;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper handler for creating choreo artifacts.
 */
public class ChoreoHandler extends AbstractArtifactHandler {

    @Override
    public void createArtifacts() throws KubernetesPluginException {
        DeploymentModel deploymentModel = dataHolder.getDeploymentModel();
        List<PortModel> choreoPorts = new ArrayList<>();
        for (ContainerPort containerPort : deploymentModel.getPorts()) {
            int port = containerPort.getContainerPort();
            String protocol = containerPort.getProtocol();
            choreoPorts.add(new PortModel(port, protocol));
        }
        ChoreoModel choreoModel = new ChoreoModel(choreoPorts);
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Path resolve = dataHolder.getChoreoArtifactOutputPath().resolve("choreo.yaml");
        Representer representer = new Representer();
        representer.addClassTag(ChoreoModel.class, Tag.MAP);
        Yaml yaml = new Yaml(representer, options);
        //create required directories
        resolve.toFile().getParentFile().mkdirs();
        try {
            PrintWriter writer = new PrintWriter(resolve.toString());
            yaml.dump(choreoModel, writer);
        } catch (FileNotFoundException e) {
            DiagnosticInfo diagnosticInfo = new DiagnosticInfo(C2CDiagnosticCodes.ARTIFACT_GEN_FAILED.getCode(),
                    e.getMessage(), DiagnosticSeverity.WARNING);
            Diagnostic diagnostic = DiagnosticFactory.createDiagnostic(diagnosticInfo, new NullLocation(), "choreo",
                    "ports");
            throw new KubernetesPluginException(diagnostic);
        }
    }
}
