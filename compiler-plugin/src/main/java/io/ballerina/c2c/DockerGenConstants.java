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
    public static final String EXECUTABLE_JAR = ".jar";
    public static final String REGISTRY_SEPARATOR = "/";
    public static final String TAG_SEPARATOR = ":";
    public static final String JRE_SLIM_BASE = "ballerina/jvm-runtime:4.0";
    public static final String NATIVE_BUILDER_IMAGE = "ghcr.io/graalvm/native-image-community:25-ol9";
    public static final String NATIVE_RUNTIME_BASE_IMAGE = "gcr.io/distroless/cc";

    /**
     * Suppresses the terminal-deprecation warning JDK 24+ prints on every {@code sun.misc.Unsafe}
     * memory-access call. Requires JDK 24 or newer; {@code java} fails to start on older releases.
     */
    public static final String SUN_MISC_UNSAFE_MEMORY_ACCESS_FLAG = "--sun-misc-unsafe-memory-access=allow";

    /**
     * Permits the unnamed module to perform restricted native operations without warning.
     * Requires JDK 22 or newer.
     */
    public static final String ENABLE_NATIVE_ACCESS_FLAG = "--enable-native-access=ALL-UNNAMED";

    /**
     * Flags prepended to every generated native-image command. The {@code -J} prefix forwards the
     * option to the JVM running the image generator, which is where the Unsafe warning is emitted
     * while classes are initialized at build time. {@code --enable-native-access} is a native-image
     * option in its own right and applies to the generated image.
     */
    public static final String NATIVE_IMAGE_JDK_FLAGS =
            "-J" + SUN_MISC_UNSAFE_MEMORY_ACCESS_FLAG + " " + ENABLE_NATIVE_ACCESS_FLAG + " ";
    public static final int MAX_BALLERINA_LAYERS = 110;

    public static final String SCHEMA_FILE_NAME = "c2c-schema.json";
    public static final String REFLECT_JSON_FILE = "reflect-config.json";
}
