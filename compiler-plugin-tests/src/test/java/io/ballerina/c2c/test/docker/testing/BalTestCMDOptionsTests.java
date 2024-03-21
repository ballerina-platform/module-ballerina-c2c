// Copyright (c) 2024 WSO2 LLC. (http://www.wso2.com).
//
// WSO2 LLC. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package io.ballerina.c2c.test.docker.testing;

import io.ballerina.c2c.test.docker.testing.utils.TestUtils;
import io.ballerina.c2c.test.utils.KubernetesTestUtils;
import org.apache.commons.io.FileUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.ballerina.c2c.KubernetesConstants.DOCKER;
import static io.ballerina.c2c.KubernetesConstants.KUBERNETES;

/**
 * Test cases for Docker CMD for ballerina cloud testing.
 */
public class BalTestBasicCasesTest {
    private static final Path SOURCE_DIR_PATH = Paths.get("src", "test", "resources",
            "docker-cloud-test", "project-based-tests");
    private static final Path COMMAND_OUTPUTS = Paths.get("src", "test", "resources",
            "docker-cloud-test", "command-outputs");
    private static final Path DOCKER_TARGET_PATH = SOURCE_DIR_PATH.resolve("target").resolve(DOCKER)
            .resolve("hello");
    private static final Path KUBERNETES_TARGET_PATH = SOURCE_DIR_PATH.resolve("target").resolve(KUBERNETES);
    private static final String DOCKER_IMAGE_JOB = "anuruddhal/cmd:v1";

    private static Path cleaningUpDir = null;

    @Test
    public void testCloudFlagWithNoValue() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-flag-no-value");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, "--cloud", new String[0]);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testCloudFlagWithNoValue.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testCloudFlagWithK8s() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-flag-k8s");
        String firstString = "error [k8s plugin]: k8s cloud build only supported for build\n";
        String endString = " io.ballerina.c2c.tasks.C2CCodeGeneratedTask perform";
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, "--cloud=k8s", new String[0]);
        actualOutcome = TestUtils.replaceVaryingString(firstString, endString, actualOutcome);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testCloudFlagWithK8s.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testCloudFlagWithCodeCoverage() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-flag-code-coverage");
        String firstString = "Building the docker image\n";
        String endString = "\nRunning the generated Docker image";
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--code-coverage"});
        actualOutcome = TestUtils.replaceVaryingString(firstString, endString, actualOutcome);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testCloudFlagWithCodeCoverage.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testCloudFlagWithTestReport() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-flag-test-report");
        String firstString = "Building the docker image\n";
        String endString = "\nRunning the generated Docker image";
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--test-report"});
        actualOutcome = TestUtils.replaceVaryingString(firstString, endString, actualOutcome);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testCloudFlagWithTestReport.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testCloudFlagWithRerunFailed() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-flag-rerun-failed");
        String firstString = "Building the docker image\n";
        String endString = "\nRunning the generated Docker image";
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--rerun-failed"});
        actualOutcome = TestUtils.replaceVaryingString(firstString, endString, actualOutcome);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testCloudFlagWithRerunFailed.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testCloudFlagWithGraalVmForTestsWithoutMocking() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-flag-graalvm");
        String firstString = "Building the native image. This may take a while\n";
        String endString = "\nRunning the generated Docker image";
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--graalvm"});
        cleaningUpDir = projectDir;

        // Assert the availability of the single fat jar
        Path jarPathInTarget = projectDir.resolve("target").resolve("bin")
                .resolve("tests")
                .resolve("cloud_flag_graalvm-testable.jar");
        Assert.assertTrue(Files.exists(jarPathInTarget));
        Path jarPathInDocker = projectDir.resolve("target").resolve("docker")
                .resolve("test")
                .resolve("cloud_flag_graalvm-testable")
                .resolve("cloud_flag_graalvm-testable.jar");
        Assert.assertTrue(Files.exists(jarPathInDocker));

        actualOutcome = TestUtils.replaceVaryingString(firstString, endString, actualOutcome);
        TestUtils.assertOutput("BasicCasesTest-testCloudFlagWithGraalVm.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test //TODO: this test takes a long time to run
    public void testCloudFlagWithGraalVmForTestsWithMocking() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-flag-graalvm-mocking");
        String firstString = "Building the native image. This may take a while\n";
        String endString = "\nRunning the generated Docker image";
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--graalvm"});
        cleaningUpDir = projectDir;

        // Assert the availability of the per module fat jars
        Path jarPathInTarget = projectDir.resolve("target").resolve("bin")
                .resolve("tests")
                .resolve("cloud_flag_graalvm_mocking-testable.jar");
        Assert.assertTrue(Files.exists(jarPathInTarget));
        Path secondJarPathInTarget = projectDir.resolve("target").resolve("bin")
                .resolve("tests")
                .resolve("cloud_flag_graalvm_mocking.mod1-testable.jar");
        Assert.assertTrue(Files.exists(secondJarPathInTarget));
        Path jarPathInDocker = projectDir.resolve("target").resolve("docker")
                .resolve("test")
                .resolve("cloud_flag_graalvm_mocking-testable")
                .resolve("cloud_flag_graalvm_mocking-testable.jar");
        Assert.assertTrue(Files.exists(jarPathInDocker));
        Path secondJarPathInDocker = projectDir.resolve("target").resolve("docker")
                .resolve("test")
                .resolve("cloud_flag_graalvm_mocking.mod1-testable")
                .resolve("cloud_flag_graalvm_mocking.mod1-testable.jar");
        Assert.assertTrue(Files.exists(secondJarPathInDocker));

        actualOutcome = TestUtils.replaceVaryingString(firstString, endString, actualOutcome);
        TestUtils.assertOutput("BasicCasesTest-testCloudFlagWithGraalVmForTestsWithMocking.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testCloudFlagForProjectWithNoTests() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("project-with-no-tests");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[0]);
        Assert.assertFalse(Files.exists(projectDir.resolve("tests")));
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testCloudFlagForProjectWithNoTests.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }
    @Test
    public void testAssertions() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("assertions");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[0]);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testAssertions.txt",
                COMMAND_OUTPUTS, actualOutcome);

    }

    @Test
    public void testAssertDiffError() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("assertions-diff-error");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[0]);
        System.out.println(actualOutcome);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testAssertDiffError.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testAssertionErrorMessage() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("assertions-error-messages");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[0]);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testAssertionErrorMessage.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testAssertBehavioralTypes() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("assertions-behavioral-types");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[0]);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testAssertBehavioralTypes.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testAssertStructuralTypes() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("assertions-structural-types");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[0]);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testAssertStructuralTypes.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testAssertSequenceTypes() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("assertions-sequence-types");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[0]);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testAssertSequenceTypes.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testAnnotationAccess() throws IOException, InterruptedException {
        String endString = " SEVERE {b7a.log.crash} - ";
        String firstString = "We thank you for helping make us better.";
        String endString2 = "********";
        String firstString2 = "unnamed module of loader 'app')";
        Path projectDir = SOURCE_DIR_PATH.resolve("annotation-access");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[0]);
        actualOutcome = actualOutcome + "********";
        actualOutcome = TestUtils.replaceVaryingString(firstString, endString, actualOutcome);
        actualOutcome = TestUtils.replaceVaryingString(firstString2, endString2, actualOutcome);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testAnnotationAccess.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testJavaInterops() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("interops");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[0]);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testJavaInterops.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testRuntimeApi() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("runtime-api-tests");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[0]);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testRuntimeApi.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testBeforeAfter() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("before-after");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[0]);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testBeforeAfter.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testBeforeEachAfterEach() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("before-each-after-each");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[0]);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testBeforeEachAfterEach.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test(dependsOnMethods = "testBeforeAfter")
    public void testDependsOn() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("depends-on");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[0]);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testDependsOn.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test(dependsOnMethods = "testDependsOn")
    public void testAnnotations() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("annotations");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[0]);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testAnnotations.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testIsolatedFunctions() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("isolated-functions");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[0]);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testIsolatedFunctions.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testIntersectionTypes() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("intersection-type-test");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[0]);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testIntersectionTypes.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testAnydataType() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("anydata-type-test");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[0]);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testAnydataType.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testAsyncInvocation() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("async");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[0]);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testAsyncInvocation.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @AfterMethod
    public void cleanUp() throws IOException, IllegalStateException {
        if (cleaningUpDir == null) {
            throw new IllegalStateException("Cleaning up directory is not set");
        }
        FileUtils.deleteDirectory(cleaningUpDir.resolve("target").toFile());
    }
}
