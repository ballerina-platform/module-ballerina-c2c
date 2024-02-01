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

package io.ballerina.c2c.models;

import io.ballerina.c2c.DockerGenConstants;
import io.ballerina.c2c.exceptions.DockerGenException;
import lombok.Getter;
import lombok.Setter;
import org.ballerinalang.model.elements.PackageID;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Docker annotations model class.
 */
@Getter
@Setter
public class DockerModel {
    private final boolean windowsBuild =
            Boolean.parseBoolean(System.getenv(DockerGenConstants.ENABLE_WINDOWS_BUILD));
    private String name;
    private String registry;
    private String tag;
    private boolean buildImage;
    private String baseImage;
    private Set<Integer> ports;
    private boolean enableDebug;
    private int debugPort;
    private boolean isService;
    private String jarFileName;
    private Set<CopyFileModel> externalFiles;
    private String classPath;
    private Path testSuiteJsonPath;
    private Path jacocoAgentJarPath;
    private String commandArg;
    private String cmd;
    private Map<String, String> env;
    private Set<Path> dependencyJarPaths;
    private PackageID pkgId;
    private Path fatJarPath;
    private boolean thinJar = true;
    private String graalvmBuildArgs;
    private boolean isGraalVMBuild = false;
    private String builderBase;
    private String builderCmd;
    private boolean isTest = false;

    public DockerModel() {
        // Initialize with default values except for image name
        this.tag = "latest";
        this.buildImage = true;
        this.baseImage = windowsBuild ? DockerGenConstants.JRE_WINDOWS_BASE_IMAGE :
                DockerGenConstants.JRE_SLIM_BASE;
        this.enableDebug = false;
        this.debugPort = 5005;
        this.externalFiles = new HashSet<>();
        this.commandArg = "";
        this.env = new HashMap<>();
        this.dependencyJarPaths = new TreeSet<>();
        this.builderBase = DockerGenConstants.NATIVE_BUILDER_IMAGE;
        this.builderCmd = "";
    }

    public void addDependencyJarPaths(Set<Path> paths) {
        this.dependencyJarPaths.addAll(paths);
    }

    public Set<Path> getDependencyJarPaths() {
        return this.dependencyJarPaths.stream().sorted().collect(Collectors.toSet());
    }

    public Set<CopyFileModel> getCopyFiles() {
        return externalFiles;
    }

    public void setCopyFiles(Set<CopyFileModel> externalFiles) throws DockerGenException {
        this.externalFiles = externalFiles;
        for (CopyFileModel externalFile : externalFiles) {
            if (!externalFile.isBallerinaConf()) {
                continue;
            }

            if (Files.isDirectory(Paths.get(externalFile.getSource()))) {
                throw new DockerGenException("invalid config file given: " + externalFile.getSource());
            }
            this.env.put("BALCONFIGFILE", externalFile.getTarget());
        }
    }

    public void addCommandArg(String commandArg) {
        this.commandArg += commandArg;
    }

    public void setIsTest(boolean isTest) {
        this.isTest = isTest;
    }

    public boolean getIsTest() {
        return this.isTest;
    }

    public String getCmd() {
        if (this.cmd == null) {
            return null;
        }

        return this.cmd;
    }

}
