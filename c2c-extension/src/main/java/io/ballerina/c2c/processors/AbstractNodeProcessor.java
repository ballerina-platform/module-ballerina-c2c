/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 *
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.c2c.processors;

import io.ballerina.c2c.exceptions.KubernetesPluginException;
import org.ballerinalang.model.tree.AnnotationAttachmentNode;
import org.ballerinalang.model.tree.FunctionNode;
import org.ballerinalang.model.tree.ServiceNode;
import org.ballerinalang.model.tree.SimpleVariableNode;

/**
 * Abstract Node processor class.
 */
public abstract class AbstractNodeProcessor implements NodeProcessor {

    @Override
    public void processNode(ServiceNode serviceNode) throws KubernetesPluginException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void processNode(SimpleVariableNode variableNode) throws KubernetesPluginException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void processNode(FunctionNode functionNode) throws KubernetesPluginException {
        throw new UnsupportedOperationException();
    }

    public void processNode(FunctionNode functionNode, AnnotationAttachmentNode attachmentNode)
            throws KubernetesPluginException {
        throw new UnsupportedOperationException();
    }
}
