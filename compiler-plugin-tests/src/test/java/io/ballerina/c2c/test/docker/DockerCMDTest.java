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

package io.ballerina.c2c.test.docker;

import com.github.dockerjava.api.command.InspectImageResponse;
import io.ballerina.c2c.exceptions.DockerGenException;
import io.ballerina.c2c.models.DockerModel;
import io.ballerina.c2c.test.utils.DockerTestUtils;
import io.ballerina.c2c.utils.DockerGenerator;
import org.apache.commons.io.FileUtils;
import org.ballerinalang.model.elements.PackageID;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import org.wso2.ballerinalang.compiler.util.Name;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static io.ballerina.c2c.test.utils.DockerTestUtils.getDockerImage;

/**
 * Docker CMD tests.
 */
public class DockerCMDTest {

    private static final Path SOURCE_DIR_PATH = Paths.get("src", "test", "resources");
    private static final String DOCKER_IMAGE = "anuruddhal/test-cmd-image:v1";
    private final PrintStream out = System.out;

    @Test
    public void buildDockerImageTest() throws DockerGenException, IOException {
        DockerModel dockerModel = new DockerModel();
        dockerModel.setName("test-cmd-image");
        dockerModel.setRegistry("anuruddhal");
        dockerModel.setTag("v1");
        dockerModel.setJarFileName("hello.jar");
        dockerModel.setPorts(Collections.singleton(9090));
        dockerModel.setBuildImage(true);
        dockerModel.setService(false);
        DockerGenerator handler = new DockerGenerator(dockerModel);
        Path jarFilePath = SOURCE_DIR_PATH.resolve("docker-test").resolve("hello.jar");
        Set<Path> jarFilePaths = getJarFilePaths();
        PackageID packageID = new PackageID(new Name("wso2"), new Name("bal"), new Name("1.0.0"));
        dockerModel.setPkgId(packageID);
        dockerModel.setDependencyJarPaths(jarFilePaths);
        dockerModel.setEntryPoint("ENTRYPOINT java -XX:+ExitOnOutOfMemoryError -Xdiag -cp '${APP}:jars/*' '$_init' " +
                "--b7a.http.accesslog.console=true");
        Path outputDir = SOURCE_DIR_PATH.resolve("target");
        Files.createDirectories(outputDir);
        handler.createArtifacts(out, "\t@kubernetes:Docker \t\t\t", jarFilePath, outputDir);
        File dockerFile = SOURCE_DIR_PATH.resolve("target").resolve("Dockerfile").toFile();
        Assert.assertTrue(dockerFile.exists());
        String dockerFileContent = new String(Files.readAllBytes(dockerFile.toPath()));
        Assert.assertTrue(dockerFileContent.contains("COPY hello.jar /home/ballerina/jars/"));
        Assert.assertTrue(dockerFileContent.contains("USER ballerina"));

        Assert.assertNotNull(getDockerImage(DOCKER_IMAGE));
        InspectImageResponse imageInspect = getDockerImage(DOCKER_IMAGE);
        Assert.assertEquals(Arrays.toString(Objects.requireNonNull(imageInspect.getConfig()).getEntrypoint()),
                "[/bin/sh, -c, java -XX:+ExitOnOutOfMemoryError -Xdiag -cp 'hello.jar:jars/*' " +
                        "'$_init' --b7a.http.accesslog.console=true]");
    }

    private Set<Path> getJarFilePaths() throws IOException {
        return Files.list(SOURCE_DIR_PATH.resolve("docker-test")).collect(Collectors.toSet());
    }

    @AfterClass
    public void cleanUp() throws IOException {
        FileUtils.deleteDirectory(SOURCE_DIR_PATH.resolve("target").toFile());
        DockerTestUtils.deleteDockerImage(DOCKER_IMAGE);
    }

}
