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

package io.ballerina.c2c.models;

/**
 * Model class to hold ports of the choreo yaml structure.
 *
 * @since 2.0.0
 */
public class PortModel {
    private int containerPort;
    private String protocol;

    public PortModel() {
    }

    public PortModel(int containerPort, String protocol) {
        this.containerPort = containerPort;
        this.protocol = protocol;
    }

    public void setContainerPort(int containerPort) {
        this.containerPort = containerPort;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public int getContainerPort() {
        return containerPort;
    }

    public String getProtocol() {
        return protocol;
    }
}
