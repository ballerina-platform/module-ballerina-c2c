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
package org.ballerina.c2c.processors;

import org.ballerina.c2c.exceptions.KubernetesPluginException;
import org.ballerina.c2c.models.DeploymentModel;
import org.ballerina.c2c.models.KubernetesContext;
import org.ballerina.c2c.utils.KubernetesUtils;
import org.ballerinalang.model.tree.AnnotationAttachmentNode;
import org.ballerinalang.model.tree.FunctionNode;
import org.ballerinalang.model.tree.ServiceNode;
import org.ballerinalang.model.tree.SimpleVariableNode;

import static org.ballerina.c2c.KubernetesConstants.DOCKER_CERT_PATH;
import static org.ballerina.c2c.KubernetesConstants.DOCKER_HOST;
import static org.ballerina.c2c.KubernetesConstants.MAIN_FUNCTION_NAME;

/**
 * Deployment Annotation processor.
 */
public class DeploymentAnnotationProcessor extends AbstractAnnotationProcessor {

    @Override
    public void processAnnotation(ServiceNode entityName, AnnotationAttachmentNode attachmentNode) {
        processDeployment();
    }

    @Override
    public void processAnnotation(SimpleVariableNode variableNode, AnnotationAttachmentNode attachmentNode) {
        processDeployment();
    }

    @Override
    public void processAnnotation(FunctionNode functionNode, AnnotationAttachmentNode attachmentNode) throws
            KubernetesPluginException {
        if (!MAIN_FUNCTION_NAME.equals(functionNode.getName().getValue())) {
            throw new KubernetesPluginException("@kubernetes:Deployment{} annotation cannot be attached to a non " +
                    "main function.");
        }

        processDeployment();
    }

    private void processDeployment() {
        DeploymentModel deploymentModel = new DeploymentModel();

        String dockerHost = System.getenv(DOCKER_HOST);
        if (!KubernetesUtils.isBlank(dockerHost)) {
            deploymentModel.setDockerHost(dockerHost);
        }
        String dockerCertPath = System.getenv(DOCKER_CERT_PATH);
        if (!KubernetesUtils.isBlank(dockerCertPath)) {
            deploymentModel.setDockerCertPath(dockerCertPath);
        }
        KubernetesContext.getInstance().getDataHolder().setDeploymentModel(deploymentModel);
    }
}
