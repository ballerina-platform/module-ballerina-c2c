/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import io.ballerina.c2c.exceptions.DockerGenException;
import io.ballerina.c2c.models.CopyFileModel;
import io.ballerina.c2c.models.DockerModel;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.ballerina.c2c.DockerGenConstants.EXECUTABLE_JAR;
import static io.ballerina.c2c.KubernetesConstants.LINE_SEPARATOR;
import static io.ballerina.c2c.utils.DockerGenUtils.copyFileOrDirectory;
import static io.ballerina.c2c.utils.DockerGenUtils.isBlank;

/**
 * Contains the implementation for native docker image generation.
 *
 * @since 2.3.0
 */
public class NativeDockerGenerator extends DockerGenerator {

    public NativeDockerGenerator(DockerModel dockerModel) {

        super(dockerModel);
    }

    @Override
    public void createArtifacts(PrintStream outStream, String logAppender, Path jarFilePath, Path outputDir)
            throws DockerGenException {

        String dockerContent = generateMultiStageDockerfile();
        try {
            DockerGenUtils.writeToFile(dockerContent, outputDir.resolve("Dockerfile"));
            Path jarLocation =
                    outputDir.resolve(DockerGenUtils.extractJarName(this.dockerModel.getFatJarPath()) + EXECUTABLE_JAR);
            copyFileOrDirectory(this.dockerModel.getFatJarPath(), jarLocation);
            for (CopyFileModel copyFileModel : this.dockerModel.getCopyFiles()) {
                // Copy external files to docker folder
                Path target = outputDir.resolve(Paths.get(copyFileModel.getSource()).getFileName());
                Path sourcePath = Paths.get(copyFileModel.getSource());
                if (!sourcePath.isAbsolute()) {
                    sourcePath = sourcePath.toAbsolutePath();
                }
                copyFileOrDirectory(sourcePath, target);

            }
            //check image build is enabled.
            if (this.dockerModel.isBuildImage()) {
                outStream.println("Building the native image. This may take a while\n");
                buildImage(outputDir);
                outStream.println();
            }
        } catch (IOException e) {
            throw new DockerGenException("unable to write content to " + outputDir);
        }
    }

    private String generateMultiStageDockerfile() {

        String fatJarFileName = this.dockerModel.getFatJarPath().getFileName().toString();
        String executableName = fatJarFileName.replaceFirst(".jar", "");
        StringBuilder dockerfileContent =
                new StringBuilder().append("# Auto Generated Dockerfile").append(LINE_SEPARATOR).append("FROM ")
                        .append(dockerModel.getBuilderBase()).append(" as build").append(LINE_SEPARATOR)
                        .append(LINE_SEPARATOR).append("WORKDIR /app/build").append(LINE_SEPARATOR)
                        .append(LINE_SEPARATOR).append("COPY ").append(fatJarFileName).append(" .")
                        .append(LINE_SEPARATOR).append(LINE_SEPARATOR)
                        .append("RUN ").append(this.dockerModel.getBuilderCmd())
                        .append(LINE_SEPARATOR).append(LINE_SEPARATOR).append("FROM ")
                        .append(dockerModel.getBaseImage()).append(LINE_SEPARATOR)
                        .append(LINE_SEPARATOR);

        appendUser(dockerfileContent);

        appendCommonCommands(dockerfileContent);

        dockerfileContent.append("COPY --from=build /app/build/").append(executableName).append(" .")
                .append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        if (isBlank(this.dockerModel.getCmd())) {
            dockerfileContent.append("CMD [\"./").append(executableName).append("\"]").append(LINE_SEPARATOR);
        } else {
            dockerfileContent.append(this.dockerModel.getCmd());
        }
        if (!isBlank(this.dockerModel.getCommandArg())) {
            dockerfileContent.append(this.dockerModel.getCommandArg());
        }
        dockerfileContent.append(LINE_SEPARATOR);

        return dockerfileContent.toString();
    }

    @Override
    protected void appendUser(StringBuilder dockerfileContent) {
        dockerfileContent.append("WORKDIR ").append("/home/ballerina").append(LINE_SEPARATOR);
    }
}
