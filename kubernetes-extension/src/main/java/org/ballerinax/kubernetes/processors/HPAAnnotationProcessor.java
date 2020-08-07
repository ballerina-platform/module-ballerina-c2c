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

package org.ballerinax.kubernetes.processors;

import org.ballerinalang.model.tree.AnnotationAttachmentNode;
import org.ballerinalang.model.tree.FunctionNode;
import org.ballerinalang.model.tree.ServiceNode;
import org.ballerinax.kubernetes.exceptions.KubernetesPluginException;
import org.ballerinax.kubernetes.models.KubernetesContext;
import org.ballerinax.kubernetes.models.PodAutoscalerModel;

import static org.ballerinax.kubernetes.KubernetesConstants.MAIN_FUNCTION_NAME;

/**
 * HPA annotation processor.
 */
public class HPAAnnotationProcessor extends AbstractAnnotationProcessor {

    @Override
    public void processAnnotation(ServiceNode serviceNode, AnnotationAttachmentNode attachmentNode) throws
            KubernetesPluginException {
        processHPA();
    }

    @Override
    public void processAnnotation(FunctionNode functionNode, AnnotationAttachmentNode attachmentNode) throws
            KubernetesPluginException {
        if (!MAIN_FUNCTION_NAME.equals(functionNode.getName().getValue())) {
            throw new KubernetesPluginException("@kubernetes:HPA{} annotation cannot be attached to a non main " +
                    "function.");
        }
        processHPA();
    }

    private void processHPA() {
        PodAutoscalerModel podAutoscalerModel = new PodAutoscalerModel();
        KubernetesContext.getInstance().getDataHolder().setPodAutoscalerModel(podAutoscalerModel);
    }

}
