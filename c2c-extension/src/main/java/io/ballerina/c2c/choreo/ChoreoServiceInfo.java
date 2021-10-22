/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.c2c.choreo;

import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a Service of a ballerina document.
 *
 * @since 2.0.0
 */
@Getter
@Setter
public class ChoreoServiceInfo {
    private ServiceDeclarationNode node;
    private String servicePath;
    private ChoreoListenerInfo listener;

    public ChoreoServiceInfo(ChoreoListenerInfo listener, ServiceDeclarationNode node, String servicePath) {
        this.listener = listener;
        this.node = node;
        this.servicePath = servicePath;
    }
}
