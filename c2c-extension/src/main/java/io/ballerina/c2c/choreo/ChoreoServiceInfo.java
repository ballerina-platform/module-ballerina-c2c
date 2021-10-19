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


/**
 * Represents a Service of a ballerina document.
 *
 * @since 2.0.0
 */
public class ChoreoServiceInfo {
    private ServiceDeclarationNode node;
    private String servicePath;
    private ChoreoListenerInfo listener;

    public ChoreoServiceInfo(ChoreoListenerInfo listener, ServiceDeclarationNode node, String servicePath) {
        this.listener = listener;
        this.node = node;
        this.servicePath = servicePath;
    }

    public ServiceDeclarationNode getNode() {
        return node;
    }

    public String getServicePath() {
        return servicePath;
    }

    public ChoreoListenerInfo getListener() {
        return listener;
    }

    public void setNode(ServiceDeclarationNode node) {
        this.node = node;
    }

    public void setServicePath(String servicePath) {
        this.servicePath = servicePath;
    }

    public void setListener(ChoreoListenerInfo listener) {
        this.listener = listener;
    }

    @Override
    public String toString() {
        return "ServiceInfo{" +
                ", serviceName='" + servicePath + '\'' +
                ", listener=" + listener +
                '}';
    }
}
