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

import io.ballerina.c2c.DockerGenConstants;
import io.ballerina.c2c.KubernetesConstants;
import io.ballerina.c2c.exceptions.DockerGenException;
import io.ballerina.c2c.models.CopyFileModel;
import io.ballerina.c2c.models.DockerModel;
import io.ballerina.c2c.test.utils.DockerTestUtils;
import io.ballerina.c2c.utils.DockerGenerator;
import io.ballerina.c2c.utils.DockerImageName;
import io.ballerina.c2c.utils.NativeDockerGenerator;
import io.ballerina.projects.internal.model.Target;
import org.apache.commons.io.FileUtils;
import org.ballerinalang.model.elements.PackageID;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.wso2.ballerinalang.compiler.util.Name;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Docker generator tests.
 */
public class DockerGeneratorTests {

    private static final Path SOURCE_DIR_PATH = Paths.get("src", "test", "resources");
    private static final Path TEST_SOURCE_DIR_PATH = Paths.get("src", "test", "resources",
            "docker-cloud-test", "docker-gen-files");
    private static final String DOCKER_IMAGE = "anuruddhal/test-gen-image:v1";
    private final PrintStream out = System.out;
    private Path cleaningUpDir;
    private boolean shouldDeleteDockerImage = true;

    @Test(expectedExceptions = DockerGenException.class,
            expectedExceptionsMessageRegExp = "given docker name 'dockerName:latest' is invalid: image name " +
                    "'dockerName' is invalid"
    )
    public void invalidDockerImageNameTest() throws DockerGenException {
        DockerImageName.validate("dockerName:latest");
    }

    @Test
    public void validDockerImageNameTest() throws DockerGenException {
        DockerImageName imageName = DockerImageName.parseName(DOCKER_IMAGE);
        Assert.assertEquals(imageName.getRepository(), "anuruddhal/test-gen-image");
        Assert.assertEquals(imageName.getNameWithoutTag(), "anuruddhal/test-gen-image");
        Assert.assertEquals(imageName.getTag(), "v1");
        Assert.assertEquals(imageName.getFullName(), "anuruddhal/test-gen-image:v1");
        Assert.assertEquals(imageName.getSimpleName(), "test-gen-image");
        Assert.assertEquals(imageName.getUser(), "anuruddhal");
    }

    @Test
    public void buildDockerImageTest() throws DockerGenException, IOException {
        DockerModel dockerModel = new DockerModel();
        dockerModel.setName("test-gen-image");
        dockerModel.setRegistry("anuruddhal");
        dockerModel.setTag("v1");
        dockerModel.setJarFileName("hello.jar");
        dockerModel.setPorts(Collections.singleton(9090));
        dockerModel.setBuildImage(true);
        dockerModel.setService(true);
        DockerGenerator handler = new DockerGenerator(dockerModel);
        Path jarFilePath = SOURCE_DIR_PATH.resolve("docker-test").resolve("hello.jar");
        Set<Path> jarFilePaths = getJarFilePaths();
        PackageID packageID = new PackageID(new Name("wso2"), new Name("bal"), new Name("1.0.0"));
        dockerModel.setPkgId(packageID);
        dockerModel.setDependencyJarPaths(jarFilePaths);
        CopyFileModel configFile = new CopyFileModel();
        configFile.setSource(SOURCE_DIR_PATH.resolve("conf").resolve("Config.toml").toString());
        configFile.setTarget("/home/ballerina/conf/");
        configFile.setBallerinaConf(true);
        CopyFileModel dataFile = new CopyFileModel();
        dataFile.setSource(SOURCE_DIR_PATH.resolve("conf").resolve("data.txt").toString());
        dataFile.setTarget("/home/ballerina/data/");
        dataFile.setBallerinaConf(true);
        Set<CopyFileModel> externalFiles = new HashSet<>();
        externalFiles.add(configFile);
        externalFiles.add(dataFile);
        dockerModel.setCopyFiles(externalFiles);
        Path outputDir = SOURCE_DIR_PATH.resolve("target");
        Files.createDirectories(outputDir);
        handler.createArtifacts(out, "\t@kubernetes:Docker \t\t\t", jarFilePath, outputDir);
        File dockerFile = SOURCE_DIR_PATH.resolve("target").resolve("Dockerfile").toFile();
        Assert.assertTrue(dockerFile.exists());

        String dockerFileContent = new String(Files.readAllBytes(dockerFile.toPath()));
        Assert.assertTrue(dockerFileContent.contains("CMD java -Xdiag -cp \"hello.jar:jars/*\" " +
                "'wso2.bal.1.$_init'"));
        Assert.assertTrue(dockerFileContent.contains("USER ballerina"));
        cleaningUpDir = outputDir.resolve("target");
    }

    @Test(dependsOnMethods = {"buildDockerImageTest"})
    public void validateDockerImage() {
        Assert.assertNotNull(DockerTestUtils.getDockerImage(DOCKER_IMAGE));
        Assert.assertEquals(DockerTestUtils.getExposedPorts(DOCKER_IMAGE).size(), 1);
        Assert.assertEquals(Objects.requireNonNull(DockerTestUtils.getDockerImage(DOCKER_IMAGE).getConfig()
                .getEnv()).length, 8);
        Assert.assertEquals(DockerTestUtils.getCommand(DOCKER_IMAGE).get(2), "java -Xdiag -cp \"hello.jar:jars/*\" " +
                "'wso2.bal.1.$_init'");
    }

    @Test
    public void buildTestDockerImageTest() throws IOException, DockerGenException {
        DockerModel dockerModel = new DockerModel();
        dockerModel.setName("test-gen-image");
        dockerModel.setRegistry("anuruddhal");
        dockerModel.setTag("v1");
        dockerModel.setJarFileName("hello.jar");
        dockerModel.setTest(true);
        DockerGenerator handler = new DockerGenerator(dockerModel);
        Set<Path> jarFilePaths = getTestJarFilePaths();
        PackageID packageID = new PackageID(new Name("wso2"), new Name("bal"), new Name("1.0.0"));
        dockerModel.setPkgId(packageID);
        dockerModel.setDependencyJarPaths(jarFilePaths);
        dockerModel.setTestSuiteJsonPath(TEST_SOURCE_DIR_PATH.resolve("target").resolve("cache")
                .resolve("tests_cache").resolve("test_suit.json"));
        dockerModel.setClassPath("dummy_class_path");
        dockerModel.setJacocoAgentJarPath(TEST_SOURCE_DIR_PATH.resolve("jacocoagent.jar"));
        Target target = new Target(TEST_SOURCE_DIR_PATH.resolve("target"));
        dockerModel.setTarget(target);
        List<Path> configFiles = getConfigPaths();
        dockerModel.setTestConfigPaths(configFiles);
        Path jacocoAgentJarPath = TEST_SOURCE_DIR_PATH.resolve("jacocoagent.jar");
        dockerModel.setJacocoAgentJarPath(jacocoAgentJarPath);
        List<String> cmdArgs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            cmdArgs.add("arg" + i);
        }
        dockerModel.setSourceRoot(TEST_SOURCE_DIR_PATH);
        dockerModel.setTestRunTimeCmdArgs(cmdArgs);
        handler.createTestArtifacts(out, "\t@kubernetes:Docker \t\t\t", TEST_SOURCE_DIR_PATH.resolve("target")
                .resolve("docker"));
        File dockerFile = TEST_SOURCE_DIR_PATH.resolve("target").resolve("docker").resolve("Dockerfile").toFile();
        cleaningUpDir = TEST_SOURCE_DIR_PATH.resolve("target").resolve("docker");
        Assert.assertTrue(dockerFile.exists());
        String dockerFileContent = new String(Files.readAllBytes(dockerFile.toPath()));
        String copyJsonSuite = "COPY test_suit.json /home/ballerina/cache/tests_cache/";
        Assert.assertTrue(dockerFileContent.contains(copyJsonSuite));
        String copyJacocoAgent = "COPY jacocoagent.jar /home/ballerina/jars/";
        Assert.assertTrue(dockerFileContent.contains(copyJacocoAgent));
        String copyTestJar = "COPY hello.jar /home/ballerina/jars/";
        Assert.assertTrue(dockerFileContent.contains(copyTestJar));
        String copyTestConfig1 = "COPY config-files/mod1/" + KubernetesConstants.BALLERINA_CONF_FILE_NAME +
                " /home/ballerina/conf/modules/mod1/tests/";
        Assert.assertTrue(dockerFileContent.contains(copyTestConfig1));
        String copyTestConfig2 = "COPY config-files/mod2/" + KubernetesConstants.BALLERINA_CONF_FILE_NAME +
                " /home/ballerina/conf/modules/mod2/tests/";
        Assert.assertTrue(dockerFileContent.contains(copyTestConfig2));
        String copyTestConfig3 = "COPY config-files/conf/" + KubernetesConstants.BALLERINA_CONF_FILE_NAME +
                " /home/ballerina/conf/tests/";
        Assert.assertTrue(dockerFileContent.contains(copyTestConfig3));
        String dockerCMD = "CMD java -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/home/ballerina -cp \"" +
                "dummy_class_path\" org.ballerinalang.test.runtime.BTestMain \"arg0\" \"arg1\" \"arg2\" \"arg3\" " +
                "\"arg4\" \"arg5\" \"arg6\" \"arg7\" \"arg8\" \"arg9\"";
        Assert.assertTrue(dockerFileContent.contains(dockerCMD));
    }

    @Test
    public void buildNativeTestDockerImageTest() throws IOException {
        shouldDeleteDockerImage = false;    // Do not delete the image after the test Since the generation fails
        DockerModel dockerModel = new DockerModel();
        dockerModel.setName("test-gen-image");
        dockerModel.setRegistry("anuruddhal");
        dockerModel.setTag("v1");
        dockerModel.setJarFileName("hello.jar");
        dockerModel.setFatJarPath(TEST_SOURCE_DIR_PATH.resolve("jars").resolve("hello.jar"));
        dockerModel.setTest(true);
        Set<Path> jarFilePaths = getTestJarFilePaths();
        PackageID packageID = new PackageID(new Name("wso2"), new Name("bal"), new Name("1.0.0"));
        dockerModel.setPkgId(packageID);
        dockerModel.setDependencyJarPaths(jarFilePaths);
        Target target = new Target(TEST_SOURCE_DIR_PATH.resolve("target"));
        dockerModel.setTarget(target);
        List<Path> configFiles = getConfigPaths();
        dockerModel.setTestConfigPaths(configFiles);
        Path jacocoAgentJarPath = TEST_SOURCE_DIR_PATH.resolve("jacocoagent.jar");
        dockerModel.setJacocoAgentJarPath(jacocoAgentJarPath);
        List<String> cmdArgs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            cmdArgs.add("arg" + i);
        }
        dockerModel.setBaseImage(DockerGenConstants.NATIVE_RUNTIME_BASE_IMAGE);
        dockerModel.setSourceRoot(TEST_SOURCE_DIR_PATH);
        dockerModel.setTestRunTimeCmdArgs(cmdArgs);
        NativeDockerGenerator nativeDockerGenerator = new NativeDockerGenerator(dockerModel);
        Path outputDir = TEST_SOURCE_DIR_PATH.resolve("target").resolve("docker");
        Files.createDirectories(outputDir);
        String defaultBuilderCmd = "native-image -jar " +
                dockerModel.getFatJarPath().getFileName().toString() +
                " -H:Name=hello --no-fallback -H:+StaticExecutableWithDynamicLibC";
        dockerModel.setBuilderCmd(defaultBuilderCmd);

        boolean passed = false;
        try {
            nativeDockerGenerator.createTestArtifacts(out, "\t@kubernetes:Docker \t\t\t", outputDir);
        } catch (DockerGenException e) {
            // This is expected
            File dockerFile = TEST_SOURCE_DIR_PATH.resolve("target").resolve("docker").resolve("Dockerfile").toFile();
            cleaningUpDir = TEST_SOURCE_DIR_PATH.resolve("target").resolve("docker");
            Assert.assertTrue(dockerFile.exists());
            String dockerFileContent = new String(Files.readAllBytes(dockerFile.toPath()));
            String copyJar = "COPY hello.jar .";
            Assert.assertTrue(dockerFileContent.contains(copyJar));
            String copyReflectJson = "COPY reflect-config.json .";
            Assert.assertTrue(dockerFileContent.contains(copyReflectJson));
            String nativeImageCMD = "RUN native-image -jar hello.jar -H:Name=hello --no-fallback " +
                    "-H:+StaticExecutableWithDynamicLibC " +
                    "-H:IncludeResources=excludedClasses.txt " +
                    "-H:IncludeResources=cache/tests_cache/test_suit.json " +
                    "-H:ReflectionConfigurationFiles=reflect-config.json";
            Assert.assertTrue(dockerFileContent.contains(nativeImageCMD));
            String copyNativeImage = "COPY --from=build /app/build/hello .";
            Assert.assertTrue(dockerFileContent.contains(copyNativeImage));
            // The args are changed inside the createTestArtifacts method
            String dockerCMD = "CMD [\"./hello\", \"true\", \"cache/tests_cache/test_suit.json\", " +
                    "\"arg2\", \"arg3\", \"arg4\", \"arg5\", " +
                    "\"arg6\", \"arg7\", \"arg8\", \"arg9\"]";
            Assert.assertTrue(dockerFileContent.contains(dockerCMD));
            passed = true;
        }
        Assert.assertTrue(passed);
    }

    private static List<Path> getConfigPaths() {
        Path configFilePath1 = TEST_SOURCE_DIR_PATH.resolve("conf").resolve("modules").resolve("mod1")
                .resolve("tests").resolve(KubernetesConstants.BALLERINA_CONF_FILE_NAME);
        Path configFilePath2 = TEST_SOURCE_DIR_PATH.resolve("conf").resolve("modules").resolve("mod2")
                .resolve("tests").resolve(KubernetesConstants.BALLERINA_CONF_FILE_NAME);
        Path configFilePath3 = TEST_SOURCE_DIR_PATH.resolve("conf").resolve("tests")
                        .resolve(KubernetesConstants.BALLERINA_CONF_FILE_NAME);
        List<Path> configFiles = new ArrayList<>();
        configFiles.add(configFilePath1);
        configFiles.add(configFilePath2);
        configFiles.add(configFilePath3);
        return configFiles;
    }

    private Set<Path> getTestJarFilePaths() throws IOException{
        return Files.list(TEST_SOURCE_DIR_PATH.resolve("jars")).collect(Collectors.toSet());
    }

    private Set<Path> getJarFilePaths() throws IOException {
        return Files.list(SOURCE_DIR_PATH.resolve("docker-test")).collect(Collectors.toSet());
    }

    @AfterMethod
    public void cleanUp() throws IOException {
        FileUtils.deleteDirectory(cleaningUpDir.toFile());
        deleteDockerImage();
    }

    private void deleteDockerImage() {
        if (shouldDeleteDockerImage) {
            DockerTestUtils.deleteDockerImage(DOCKER_IMAGE);
        } else {
            shouldDeleteDockerImage = true; //for the next test
        }
    }

}
