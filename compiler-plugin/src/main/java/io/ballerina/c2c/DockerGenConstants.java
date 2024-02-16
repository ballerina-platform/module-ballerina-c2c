/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package io.ballerina.c2c;

/**
 * Constants used in docker annotation processor.
 */
public class DockerGenConstants {
    public static final String ENABLE_DEBUG_LOGS = "BAL_DOCKER_DEBUG";
    public static final String ENABLE_WINDOWS_BUILD = "BAL_DOCKER_WINDOWS";
    public static final String EXECUTABLE_JAR = ".jar";
    public static final String REGISTRY_SEPARATOR = "/";
    public static final String TAG_SEPARATOR = ":";
    public static final String JRE_SLIM_BASE = "ballerina/jvm-runtime:2.0";
    public static final String JRE_WINDOWS_BASE_IMAGE = "openjdk:17-windowsservercore";
    public static final String NATIVE_BUILDER_IMAGE = "ghcr.io/graalvm/native-image-community:17-ol8";
    public static final String NATIVE_RUNTIME_BASE_IMAGE = "gcr.io/distroless/base";
    public static final int MAX_BALLERINA_LAYERS = 110;

    public static final String SCHEMA_FILE_NAME = "c2c-schema.json";
    public static final String WINDOWS_SEPARATOR = "\\";
}
