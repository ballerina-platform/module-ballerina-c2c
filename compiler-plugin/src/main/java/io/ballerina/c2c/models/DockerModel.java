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
import io.ballerina.projects.Project;
import io.ballerina.projects.internal.model.Target;
import lombok.Getter;
import lombok.Setter;
import org.ballerinalang.model.elements.PackageID;
import org.ballerinalang.test.runtime.entity.TestSuite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

    private TestSpecificProps testSpecificProps;

    private static class TestSpecificProps {
        private List<String> testRunTimeCmdArgs = null;
        private String classPath;
        private Path testSuiteJsonPath;
        private Path jacocoAgentJarPath;
        private Map<String, TestSuite> testSuiteMap;
        private Target target;
        private List<Path> testConfigPaths;
        private List<String> getTestRunTimeCmdArgs() {
            return testRunTimeCmdArgs;
        }

        private void setTestRunTimeCmdArgs(List<String> runTimeArgs) {
            this.testRunTimeCmdArgs = runTimeArgs;
        }

        private String getClassPath() {
            return classPath;
        }

        private void setClassPath(String classPath) {
            this.classPath = classPath;
        }

        private Path getTestSuiteJsonPath() {
            return testSuiteJsonPath;
        }

        private void setTestSuiteJsonPath(Path testSuiteJsonPath) {
            this.testSuiteJsonPath = testSuiteJsonPath;
        }

        private Path getJacocoAgentJarPath() {
            return jacocoAgentJarPath;
        }

        private void setJacocoAgentJarPath(Path jacocoAgentJarPath) {
            this.jacocoAgentJarPath = jacocoAgentJarPath;
        }

        private Map<String, TestSuite> getTestSuiteMap() {
            return testSuiteMap;
        }

        private void setTestSuiteMap(Map<String, TestSuite> testSuiteMap) {
            this.testSuiteMap = testSuiteMap;
        }

        private Target getTarget() {
            return this.target;
        }

        private void setTarget(Target target) {
            this.target = target;
        }

        private List<Path> getTestConfigPaths() {
            return testConfigPaths;
        }

        private void setTestConfigPaths(List<Path> testConfigPaths) {
            this.testConfigPaths = testConfigPaths;
        }
    }

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
        this.testSpecificProps = null;
        this.isTest = false;
    }

    public void setTestRunTimeCmdArgs(List<String> cmdArgsList) {
        if (this.testSpecificProps.getTestRunTimeCmdArgs() == null) {
            this.testSpecificProps.setTestRunTimeCmdArgs(cmdArgsList);
        }
    }

    public List<String> getTestRunTimeCmdArgs() {
        return this.testSpecificProps.getTestRunTimeCmdArgs();
    }

    public void setTest(boolean isTest) {
        this.isTest = isTest;

        if (isTest) {
            this.testSpecificProps = new TestSpecificProps();
        }
    }

    public void setClassPath(String classPath) {
        this.testSpecificProps.setClassPath(classPath);
    }

    public String getClassPath() {
        return this.testSpecificProps.getClassPath();
    }

    public void setTestSuiteJsonPath(Path testSuiteJsonPath) {
        this.testSpecificProps.setTestSuiteJsonPath(testSuiteJsonPath);
    }

    public Path getTestSuiteJsonPath() {
        return this.testSpecificProps.getTestSuiteJsonPath();
    }

    public void setJacocoAgentJarPath(Path jacocoAgentJarPath) {
        this.testSpecificProps.setJacocoAgentJarPath(jacocoAgentJarPath);
    }

    public Path getJacocoAgentJarPath() {
        return this.testSpecificProps.getJacocoAgentJarPath();
    }

    public void setTestSuiteMap(Map<String, TestSuite> testSuiteMap) {
        this.testSpecificProps.setTestSuiteMap(testSuiteMap);
    }

    public Map<String, TestSuite> getTestSuiteMap() {
        return this.testSpecificProps.getTestSuiteMap();
    }

    public void setTarget(Target target) {
        this.testSpecificProps.setTarget(target);
    }

    public Target getTarget() {
        return this.testSpecificProps.getTarget();
    }

    public void setTestConfigPaths(List<Path> testConfigPaths) {
        this.testSpecificProps.setTestConfigPaths(testConfigPaths);
    }

    public List<Path> getTestConfigPaths() {
        return this.testSpecificProps.getTestConfigPaths();
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

    public String getCmd() {
        if (this.cmd == null) {
            return null;
        }

        String configFile = "";
        for (CopyFileModel externalFile : externalFiles) {
            if (!externalFile.isBallerinaConf()) {
                continue;
            }
            configFile = externalFile.getTarget();
        }

        return this.cmd
                .replace("${APP}", this.jarFileName)
                .replace("${CONFIG_FILE}", configFile);
    }

}
