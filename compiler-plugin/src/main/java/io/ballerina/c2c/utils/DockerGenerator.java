/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package io.ballerina.c2c.utils;

import io.ballerina.c2c.DockerGenConstants;
import io.ballerina.c2c.KubernetesConstants;
import io.ballerina.c2c.exceptions.DockerGenException;
import io.ballerina.c2c.models.CopyFileModel;
import io.ballerina.c2c.models.DockerModel;
import io.ballerina.c2c.models.KubernetesContext;
import io.ballerina.cli.utils.TestUtils;
import io.ballerina.projects.JarResolver;
import io.ballerina.toml.api.Toml;
import org.ballerinalang.model.elements.PackageID;
import org.ballerinalang.test.runtime.util.TesterinaConstants;
import org.ballerinalang.test.runtime.util.TesterinaConstants.RunTimeArgs;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static io.ballerina.c2c.DockerGenConstants.EXECUTABLE_JAR;
import static io.ballerina.c2c.DockerGenConstants.REGISTRY_SEPARATOR;
import static io.ballerina.c2c.DockerGenConstants.TAG_SEPARATOR;
import static io.ballerina.c2c.DockerGenConstants.WINDOWS_SEPARATOR;
import static io.ballerina.c2c.utils.DockerGenUtils.addConfigTomls;
import static io.ballerina.c2c.utils.DockerGenUtils.copyTestConfigFiles;
import static io.ballerina.c2c.utils.DockerGenUtils.isWindowsBuild;
import static io.ballerina.c2c.utils.DockerGenUtils.getWorkDir;
import static io.ballerina.c2c.utils.DockerGenUtils.getTestSuiteJsonCopiedDir;
import static io.ballerina.c2c.KubernetesConstants.LINE_SEPARATOR;
import static io.ballerina.c2c.utils.DockerGenUtils.copyFileOrDirectory;
import static io.ballerina.c2c.utils.DockerGenUtils.isBlank;
import static io.ballerina.c2c.utils.DockerGenUtils.printDebug;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.MODULE_INIT_CLASS_NAME;

/**
 * Generates Docker artifacts from annotations.
 */
public class DockerGenerator {

    protected final DockerModel dockerModel;

    public DockerGenerator(DockerModel dockerModel) {
        String registry = dockerModel.getRegistry();
        String imageName = dockerModel.getName();
        imageName = !isBlank(registry) ? registry + REGISTRY_SEPARATOR + imageName + TAG_SEPARATOR
                + dockerModel.getTag() :
                imageName + TAG_SEPARATOR + dockerModel.getTag();
        dockerModel.setName(imageName);

        this.dockerModel = dockerModel;
    }

    public void createArtifacts(PrintStream outStream, String logAppender, Path jarFilePath, Path outputDir)
            throws DockerGenException {
        try {
            if (!this.dockerModel.isThinJar()) {
                DockerGenUtils.writeToFile(generateDockerfile(), outputDir.resolve("Dockerfile"));
                copyFileOrDirectory(this.dockerModel.getFatJarPath(),
                        outputDir.resolve(this.dockerModel.getFatJarPath().getFileName()));
            } else {
                String dockerContent;
                if (!isWindowsBuild()) {
                    dockerContent = generateDockerfile();
                } else {
                    dockerContent = generateThinJarWindowsDockerfile();
                }
                copyNativeJars(outputDir);
                DockerGenUtils.writeToFile(dockerContent, outputDir.resolve("Dockerfile"));
                Path jarLocation = outputDir.resolve(DockerGenUtils.extractJarName(jarFilePath) + EXECUTABLE_JAR);
                copyFileOrDirectory(jarFilePath, jarLocation);
            }
            copyExternalFiles(outputDir);
            //check image build is enabled.
            if (this.dockerModel.isBuildImage()) {
                outStream.println("\nBuilding the docker image\n");
                buildImage(outputDir);
                outStream.println();
            }
        } catch (IOException e) {
            throw new DockerGenException("unable to write content to " + outputDir);
        }
    }

    public void createTestArtifacts(PrintStream outStream, String logAppender, Path outputDir)
            throws  DockerGenException {

        try {
            if (this.dockerModel.isThinJar()) {
                String dockerContent;
                if (!isWindowsBuild()) {
                    dockerContent = generateTestDockerFile(this.dockerModel.getTestSuiteJsonPath(),
                            this.dockerModel.getJacocoAgentJarPath());
                } else {
                    dockerContent = generateTestThinJarWindowsDockerfile(
                            this.dockerModel.getTestSuiteJsonPath(), this.dockerModel.getJacocoAgentJarPath()
                    );
                }
                copyNativeJars(outputDir);
                //copy the test suite json (to the  root directory (/home/ballerina/))
                copyFileOrDirectory(this.dockerModel.getTestSuiteJsonPath(), outputDir);

                //copy the jacoco agent jar
                copyFileOrDirectory(this.dockerModel.getJacocoAgentJarPath(), outputDir);
                DockerGenUtils.writeToFile(dockerContent, outputDir.resolve("Dockerfile"));
            } else {
                DockerGenUtils.writeToFile(generateTestDockerFile(this.dockerModel.getTestSuiteJsonPath(),
                        this.dockerModel.getJacocoAgentJarPath()), outputDir.resolve("Dockerfile"));
                copyFileOrDirectory(this.dockerModel.getFatJarPath(),
                        outputDir.resolve(this.dockerModel.getFatJarPath().getFileName()));
            }

            copyTestConfigFiles(outputDir, this.dockerModel);
            copyExternalFiles(outputDir);

            //check image build is enabled.
            if (this.dockerModel.isBuildImage()) {
                outStream.println("\nBuilding the docker image\n");
                buildImage(outputDir);
                outStream.println();
            }
        } catch (IOException e) {
            throw new DockerGenException("unable to write content to " + outputDir);
        }
    }

    private void copyExternalFiles(Path outputDir) throws DockerGenException {
        for (CopyFileModel copyFileModel : this.dockerModel.getCopyFiles()) {
            // Copy external files to docker folder
            Path target = outputDir.resolve(Paths.get(copyFileModel.getSource()).getFileName());
            Path sourcePath = Paths.get(copyFileModel.getSource());
            if (!sourcePath.isAbsolute()) {
                sourcePath = sourcePath.toAbsolutePath();
            }
            copyFileOrDirectory(sourcePath, target);
        }
    }

    private void copyNativeJars(Path outputDir) throws DockerGenException {
        for (Path jarPath : this.dockerModel.getDependencyJarPaths()) {
            // Copy jar files
            Path target = outputDir.resolve(jarPath.getFileName());
            Path sourcePath = jarPath;
            if (!sourcePath.isAbsolute()) {
                sourcePath = sourcePath.toAbsolutePath();
            }
            copyFileOrDirectory(sourcePath, target);
        }
    }

    /**
     * Create docker image.
     *
     * @param dockerDir dockerfile directory
     */
    public void buildImage(Path dockerDir) throws DockerGenException {
        // validate docker image name
        DockerImageName.validate(this.dockerModel.getName());

        printDebug("building docker image `" + this.dockerModel.getName() + "` from directory `" + dockerDir + "`.");
        ProcessBuilder pb = new ProcessBuilder("docker", "build", "--no-cache", "--force-rm", "-t",
                this.dockerModel.getName(), dockerDir.toFile().toString());
        pb.inheritIO();

        try {
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new DockerGenException("docker build failed. refer to the build log");
            }

        } catch (IOException | InterruptedException | RuntimeException e) {
            throw new DockerGenException(getErrorMessage(e.getMessage()));
        }
    }

    private String getErrorMessage(String message) {
        switch (message) {
            case "Cannot run program \"docker\": error=2, No such file or directory":
            case "Cannot run program \"docker\": CreateProcess error=2, The system cannot find the file specified":
                return "command not found: docker";
            default:
                return message;
        }
    }

    /**
     * Generate Dockerfile content according to selected jar type.
     *
     * @return Dockerfile content as a string
     */
    private String generateDockerfile() {
        StringBuilder dockerfileContent = new StringBuilder();
        addInitialDockerContent(dockerfileContent);
        if (this.dockerModel.isThinJar()) {
            // Append Jar copy instructions without observability jar and executable jar
            this.dockerModel.getDependencyJarPaths()
                    .stream()
                    .map(Path::getFileName)
                    .filter(path -> !(path.toString().endsWith("-observability-symbols.jar") ||
                            path.toString().endsWith(dockerModel.getJarFileName())))
                    .collect(Collectors.toCollection(TreeSet::new))
                    .forEach(path -> {
                                dockerfileContent.append("COPY ")
                                        .append(path)
                                        .append(" ").append(getWorkDir())
                                        .append("/jars/ ").append(LINE_SEPARATOR);
                                //TODO: Remove once https://github.com/moby/moby/issues/37965 is fixed.
                                boolean isCiBuild = "true".equals(System.getenv().get("CI_BUILD"));
                                if (isCiBuild) {
                                    dockerfileContent.append("RUN true ").append(LINE_SEPARATOR);
                                }
                            }
                            );
            // Append Jar copy for observability jar and executable jar
            this.dockerModel.getDependencyJarPaths().forEach(path -> {
                        if (path.toString().endsWith("observability-symbols.jar") ||
                                path.toString().endsWith(dockerModel.getJarFileName())) {
                            dockerfileContent.append("COPY ")
                                    .append(path.getFileName())
                                    .append(" ").append(getWorkDir())
                                    .append("/jars/ ").append(LINE_SEPARATOR);
                        }
                    }
                                                            );
        } else {
            dockerfileContent.append("COPY ")
                    .append(this.dockerModel.getFatJarPath().getFileName())
                    .append(" ").append(getWorkDir())
                    .append("/jars/ ").append(LINE_SEPARATOR);
        }
        appendUser(dockerfileContent);
        dockerfileContent.append("WORKDIR ").append(getWorkDir()).append(LINE_SEPARATOR);
        appendCommonCommands(dockerfileContent);
        if (isBlank(this.dockerModel.getCmd())) {
            PackageID packageID = this.dockerModel.getPkgId();
            String mainClass = JarResolver.getQualifiedClassName(packageID.orgName.value, packageID.name.value,
                    packageID.version.value, MODULE_INIT_CLASS_NAME);
            mainClass = "'" + mainClass + "'";
            if (this.dockerModel.isEnableDebug()) {
                dockerfileContent.append("CMD java -Xdiag -agentlib:jdwp=transport=dt_socket,server=y,suspend=n," +
                        "address='*:")
                        .append(this.dockerModel.getDebugPort()).append("' -cp \"")
                        .append(this.dockerModel.getJarFileName()).append(":jars/*\" ").append(mainClass);
            } else {
                dockerfileContent.append("CMD java -Xdiag -cp \"").append(this.dockerModel.getJarFileName())
                        .append(":jars/*\" ").append(mainClass);
            }
        } else {
            dockerfileContent.append(this.dockerModel.getCmd());
        }
        if (!isBlank(this.dockerModel.getCommandArg())) {
            dockerfileContent.append(this.dockerModel.getCommandArg());
        }
        dockerfileContent.append(LINE_SEPARATOR);

        return dockerfileContent.toString();
    }

    private String generateTestDockerFile(Path testSuiteJsonPath, Path jacocoAgentJarPath) {
        StringBuilder testDockerFileContent = new StringBuilder();
        addInitialDockerContent(testDockerFileContent);

        //copy test suite json and all the dependencies
        if (this.dockerModel.isThinJar()) {
            //copy the test suite json
            testDockerFileContent.append("COPY ")
                    .append(testSuiteJsonPath.getFileName().toString())
                    .append(" ").append(getTestSuiteJsonCopiedDir()).append("/ ").append(LINE_SEPARATOR);
                    //to the  root directory (/home/ballerina/)

            new TreeSet<>(this.dockerModel.getDependencyJarPaths())
                    .stream()
                    .map(Path::getFileName)
                    .forEach(path -> {
                        testDockerFileContent.append("COPY ")
                                        .append(path)
                                        .append(" ").append(getWorkDir())
                                        .append("/jars/ ").append(LINE_SEPARATOR);
                                //TODO: Remove once https://github.com/moby/moby/issues/37965 is fixed.
                                boolean isCiBuild = "true".equals(System.getenv().get("CI_BUILD"));
                                if (isCiBuild) {
                                    testDockerFileContent.append("RUN true ").append(LINE_SEPARATOR);
                                }
                            }
                    );

            //copy the jacoco agent jar path
            testDockerFileContent.append("COPY ")
                    .append(jacocoAgentJarPath.getFileName().toString())
                    .append(" ").append(getWorkDir())
                    .append("/jars/ ").append(LINE_SEPARATOR);
        } else {
            if (!isWindowsBuild()) {
                testDockerFileContent.append("COPY ")
                        .append(this.dockerModel.getFatJarPath().getFileName())
                        .append(" ").append(getWorkDir())
                        .append("/jars/ ").append(LINE_SEPARATOR);
            } else {
                testDockerFileContent.append("COPY ")
                        .append(this.dockerModel.getFatJarPath().getFileName())
                        .append(" ").append(getWorkDir())
                        .append("jars")
                        .append(WINDOWS_SEPARATOR)
                        .append(" ").append(LINE_SEPARATOR);
            }
        }

        addConfigTomls(testDockerFileContent, this.dockerModel, Paths.get(getWorkDir()));

        appendUser(testDockerFileContent);
        testDockerFileContent.append("WORKDIR ").append(getWorkDir()).append(LINE_SEPARATOR);
        appendCommonCommands(testDockerFileContent);

        if (!isBlank(this.dockerModel.getCmd())) {
            testDockerFileContent.append(this.dockerModel.getCmd());
        } else {
            addDockerTestCMD(testDockerFileContent);
        }

        if (!isBlank(this.dockerModel.getCommandArg())) {
            testDockerFileContent.append(this.dockerModel.getCommandArg()).append(" ");
        } else {
            addDockerTestCMDArgs(testDockerFileContent);
        }
        testDockerFileContent.append(LINE_SEPARATOR);

        return testDockerFileContent.toString();
    }

    private void addDockerTestCMDArgs(StringBuilder testDockerFileContent) {
        if (this.dockerModel.getTestRunTimeCmdArgs() != null) {
            List<String> testRunTimeCmdArgs = this.dockerModel.getTestRunTimeCmdArgs();

            if (!this.dockerModel.isThinJar()) {
                testRunTimeCmdArgs.set(RunTimeArgs.IS_FAT_JAR_EXECUTION, "true");

                if (isWindowsBuild()) {
                    testRunTimeCmdArgs.set(RunTimeArgs.TEST_SUITE_JSON_PATH,
                            TestUtils.getJsonFilePathInFatJar(WINDOWS_SEPARATOR));
                } else {
                    testRunTimeCmdArgs.set(RunTimeArgs.TEST_SUITE_JSON_PATH,
                            TestUtils.getJsonFilePathInFatJar("/"));
                }
            }

            testRunTimeCmdArgs.forEach(arg -> testDockerFileContent.append("\"").append(arg).append("\" "));

            //add config toml values by traversing each config toml file
//            if (this.dockerModel.getTestConfigPaths() != null) {
//                this.dockerModel.getTestConfigPaths().forEach(testConfigPath -> {
//                    try {
//                        Toml toml = Toml.read(testConfigPath);
//                        //get all the keys
//                        toml.toMap().forEach((key, value) -> {
//                            String appendingArg = "-C" + key + "=" + value;
//
//                            if (!testRunTimeCmdArgs.contains(appendingArg)) {
//                                // give higher priority to the args passed via command line
//                                testDockerFileContent.append(appendingArg).append(" ");
//                            }
//                        });
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    }
//                });
//            }
        }
    }

    private void addDockerTestCMD(StringBuilder testDockerFileContent) {
        testDockerFileContent.append("CMD ");
        TestUtils.getInitialCmdArgs("java", getWorkDir()).stream().forEach(arg -> {
            testDockerFileContent.append(arg).append(" ");
        });

        if (this.dockerModel.isEnableDebug()) {
            testDockerFileContent.append("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:")
                    .append(this.dockerModel.getDebugPort()).append(" ");
        }

        //we are not adding jacoco agent jar path

        if (!isBlank(this.dockerModel.getClassPath())) {
            if (this.dockerModel.isThinJar()) {
                testDockerFileContent.append("-cp \"").append(this.dockerModel.getClassPath()).append("\" ");
            }
        }

        if (this.dockerModel.isThinJar()) {
            testDockerFileContent.append(TesterinaConstants.TESTERINA_LAUNCHER_CLASS_NAME).append(" ");
        } else {
            //-jar <jar-file>
            if (!isWindowsBuild()) {
                testDockerFileContent.append("-jar ")
                        .append(getWorkDir())
                        .append("/jars/")
                        .append(this.dockerModel.getFatJarPath().getFileName())
                        .append(" ");
            } else {
                testDockerFileContent.append("-jar ")
                        .append(getWorkDir())
                        .append("jars")
                        .append(WINDOWS_SEPARATOR)
                        .append(this.dockerModel.getFatJarPath().getFileName())
                        .append(" ");
            }
        }
    }

    protected void appendUser(StringBuilder dockerfileContent) {
        if (this.dockerModel.getBaseImage().equals(DockerGenConstants.JRE_SLIM_BASE)) {
            dockerfileContent.append("RUN addgroup troupe \\").append(LINE_SEPARATOR);
            dockerfileContent.append("    && adduser -S -s /bin/bash -g 'ballerina' -G troupe -D ballerina \\")
                    .append(LINE_SEPARATOR);
            dockerfileContent.append("    && apk add --update --no-cache bash \\").append(LINE_SEPARATOR);
            dockerfileContent.append("    && rm -rf /var/cache/apk/*").append(LINE_SEPARATOR);
            dockerfileContent.append(LINE_SEPARATOR);
        }
    }

    private void addInitialDockerContent(StringBuilder dockerfileContent) {
        dockerfileContent.append("# Auto Generated Dockerfile").append(LINE_SEPARATOR);
        dockerfileContent.append("FROM ").append(this.dockerModel.getBaseImage()).append(LINE_SEPARATOR);
        dockerfileContent.append(LINE_SEPARATOR);
        dockerfileContent.append("LABEL maintainer=\"dev@ballerina.io\"").append(LINE_SEPARATOR);
    }

    private String generateThinJarWindowsDockerfile() {
        StringBuilder dockerfileContent = new StringBuilder();
        addInitialDockerContent(dockerfileContent);

        dockerfileContent.append(LINE_SEPARATOR);
        dockerfileContent.append("WORKDIR ").append(getWorkDir()).append(LINE_SEPARATOR);

        for (Path path : this.dockerModel.getDependencyJarPaths()) {
            dockerfileContent.append("COPY ").append(path.getFileName()).append(getWorkDir())
                    .append("jars").append(WINDOWS_SEPARATOR);
            dockerfileContent.append(LINE_SEPARATOR);
        }
        dockerfileContent.append(LINE_SEPARATOR);
        appendCommonCommands(dockerfileContent);
        if (isBlank(this.dockerModel.getCmd())) {
            PackageID packageID = this.dockerModel.getPkgId();
            String mainClass = JarResolver.getQualifiedClassName(packageID.orgName.value, packageID.name.value,
                    packageID.version.value, MODULE_INIT_CLASS_NAME);
            mainClass = "'" + mainClass + "'";
            if (this.dockerModel.isEnableDebug()) {
                dockerfileContent.append("CMD java -Xdiag -agentlib:jdwp=transport=dt_socket,server=y,suspend=n," +
                        "address='*:").append(this.dockerModel.getDebugPort()).append("' -cp \"")
                        .append(this.dockerModel.getJarFileName()).append(":jars/*\" ").append(mainClass);
            } else {
                dockerfileContent.append("CMD java -Xdiag -cp \"").append(this.dockerModel.getJarFileName())
                        .append(":jars/*\" ").append(mainClass);
            }
        } else {
            dockerfileContent.append(this.dockerModel.getCmd());
        }
        dockerfileContent.append(LINE_SEPARATOR);
        if (!isBlank(this.dockerModel.getCommandArg())) {
            dockerfileContent.append(this.dockerModel.getCommandArg());
        }
        dockerfileContent.append(LINE_SEPARATOR);

        return dockerfileContent.toString();
    }

    private String generateTestThinJarWindowsDockerfile(Path testSuiteJsonPath, Path jacocoAgentJarPath) {
        StringBuilder dockerfileContent = new StringBuilder();
        addInitialDockerContent(dockerfileContent);

        dockerfileContent.append(LINE_SEPARATOR);
        dockerfileContent.append("WORKDIR ").append(getWorkDir()).append(LINE_SEPARATOR);

        for (Path path : this.dockerModel.getDependencyJarPaths()) {
            dockerfileContent.append("COPY ").append(path.getFileName()).append(getWorkDir())
                    .append("jars").append(WINDOWS_SEPARATOR);
            dockerfileContent.append(LINE_SEPARATOR);
        }

        //copy the test suite json
        dockerfileContent.append("COPY ")
                .append(testSuiteJsonPath.getFileName().toString())
                .append(" ").append(getTestSuiteJsonCopiedDir()).append(WINDOWS_SEPARATOR).append(LINE_SEPARATOR);

        addConfigTomls(dockerfileContent, this.dockerModel, Paths.get(getWorkDir()));
        appendCommonCommands(dockerfileContent);

        if (isBlank(this.dockerModel.getCmd())) {
            addDockerTestCMD(dockerfileContent);
        } else {
            dockerfileContent.append(this.dockerModel.getCmd());
        }

        if (!isBlank(this.dockerModel.getCommandArg())) {
            dockerfileContent.append(this.dockerModel.getCommandArg());
        } else {
            addDockerTestCMDArgs(dockerfileContent);
        }
        dockerfileContent.append(LINE_SEPARATOR);

        return dockerfileContent.toString();
    }

    protected void appendCommonCommands(StringBuilder dockerfileContent) {
        this.dockerModel.getEnv().forEach((key, value) -> dockerfileContent.append("ENV ").
                append(key).append("=").append(value).append(LINE_SEPARATOR));

        this.dockerModel.getCopyFiles().forEach(file -> {
            // Extract the source filename relative to docker folder.
            String sourceFileName = String.valueOf(Paths.get(file.getSource()).getFileName());
            dockerfileContent.append("COPY ")
                    .append(sourceFileName)
                    .append(" ")
                    .append(file.getTarget())
                    .append(LINE_SEPARATOR);
        });

        dockerfileContent.append(LINE_SEPARATOR);

        if (this.dockerModel.isService() && this.dockerModel.getPorts().size() > 0) {
            dockerfileContent.append("EXPOSE ");
            this.dockerModel.getPorts().forEach(port -> dockerfileContent.append(" ").append(port));
        }
        dockerfileContent.append(LINE_SEPARATOR);
        if (this.dockerModel.getBaseImage().equals(DockerGenConstants.JRE_SLIM_BASE)) {
            dockerfileContent.append("USER ballerina").append(LINE_SEPARATOR);
            dockerfileContent.append(LINE_SEPARATOR);
        }
    }
}
