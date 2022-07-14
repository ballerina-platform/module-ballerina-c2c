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
package io.ballerina.c2c.models;

import io.fabric8.kubernetes.api.model.EnvVar;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * Kubernetes Model class.
 */
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Data
public abstract class KubernetesModel {
    private String version;
    @EqualsAndHashCode.Include
    protected String name;
    protected Map<String, String> labels;
    protected Map<String, String> annotations;
    protected List<EnvVar> envVars;

    public void addLabel(String key, String value) {
        this.labels.put(key, value);
    }

    public void addEnv(EnvVar envVar) {
        envVars.add(envVar);
    }
}
