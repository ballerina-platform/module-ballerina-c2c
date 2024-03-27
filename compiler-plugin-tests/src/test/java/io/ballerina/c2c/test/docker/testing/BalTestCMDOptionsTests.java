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

/**
 * Test cases for Docker CMD for ballerina cloud testing.
 */
public class BalTestCMDOptionsTests {
    private static final Path SOURCE_DIR_PATH = Paths.get("src", "test", "resources",
            "docker-cloud-test", "project-based-tests");
    private static final Path COMMAND_OUTPUTS = Paths.get("src", "test", "resources",
            "docker-cloud-test", "command-outputs");
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
    public void testCloudFlagWithListGroupsForProjectWithNoGroups() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("project-with-no-groups");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[]{"--list-groups"});
        String firstString = "Building the docker image\n";
        String endString = "\nRunning the generated Docker image";
        actualOutcome = TestUtils.replaceVaryingString(firstString, endString, actualOutcome);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testCloudFlagWithListGroupsForProjectWithNoGroups.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testCloudFlagWithListGroupsForProjectWithGroups() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("project-with-groups");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[]{"--list-groups"});
        String firstString = "Building the docker image\n";
        String endString = "\nRunning the generated Docker image";
        actualOutcome = TestUtils.replaceVaryingString(firstString, endString, actualOutcome);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testCloudFlagWithListGroupsForProjectWithGroups.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testCloudFlagWithSpecificGroups() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("project-with-groups");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[]{"--groups", "g1,g2"});
        String firstString = "Building the docker image\n";
        String endString = "\nRunning the generated Docker image";
        actualOutcome = TestUtils.replaceVaryingString(firstString, endString, actualOutcome);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testCloudFlagWithSpecificGroups.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testCloudFlagWithDisabledGroups() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("project-with-groups");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[]{"--disable-groups", "g1,g3"});
        String firstString = "Building the docker image\n";
        String endString = "\nRunning the generated Docker image";
        actualOutcome = TestUtils.replaceVaryingString(firstString, endString, actualOutcome);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("BasicCasesTest-testCloudFlagWithDisabledGroups.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testCloudFlagWithSpecificTestFunctions() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-project-with-modules");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--tests", "testMain,testMod"});
        String firstString = "Building the docker image\n";
        String endString = "\nRunning the generated Docker image";
        actualOutcome = TestUtils.replaceVaryingString(firstString, endString, actualOutcome);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("SelectedFunctionTest-testCloudFlagWithSingleFunctionExecution.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testCloudFlagWithRootPackageTests() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-project-with-modules");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--tests", "cloud_project_with_modules:testMain"});
        String firstString = "Building the docker image\n";
        String endString = "\nRunning the generated Docker image";
        actualOutcome = TestUtils.replaceVaryingString(firstString, endString, actualOutcome);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("SelectedFunctionTest-testCloudFlagWithRootPackageTests.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testCloudFlagWithExecutionOfOnlyRootPackageTests() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-project-with-modules");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--tests", "cloud_project_with_modules:*"});
        String firstString = "Building the docker image\n";
        String endString = "\nRunning the generated Docker image";
        actualOutcome = TestUtils.replaceVaryingString(firstString, endString, actualOutcome);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("SelectedFunctionTest-testCloudFlagWithExecutionOfOnlyRootPackageTests.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testCloudFlagWithExecutionOfModuleTests() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-project-with-modules");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--tests", "cloud_project_with_modules.mod1:*"});
        String firstString = "Building the docker image\n";
        String endString = "\nRunning the generated Docker image";
        actualOutcome = TestUtils.replaceVaryingString(firstString, endString, actualOutcome);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("SelectedFunctionTest-testCloudFlagWithExecutionOfModuleTests.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testCloudFlagWithExecutionOfMultipleSpecifiedTests() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-project-with-modules");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--tests", "cloud_project_with_modules.mod2:testMod2Two,cloud_project_with_modules:*"});
        String firstString = "Building the docker image\n";
        String endString = "\nRunning the generated Docker image";
        actualOutcome = TestUtils.replaceVaryingString(firstString, endString, actualOutcome);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("SelectedFunctionTest-testCloudFlagWithExecutionOfMultipleSpecifiedTests.txt",
                COMMAND_OUTPUTS, actualOutcome);
    }

    @Test
    public void testCloudFlagWithSpecificTestBalFile() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.getParent().resolve("single-file-tests")
                .resolve("single-test-file-execution");
        String firstString = "Building the docker image\n";
        String endString = "\nRunning the generated Docker image";
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{projectDir.resolve("single-test-file-execution.bal").toAbsolutePath().toString()});
        actualOutcome = TestUtils.replaceVaryingString(firstString, endString, actualOutcome);
        cleaningUpDir = projectDir;
        TestUtils.assertOutput("SelectedFunctionTest-testCloudFlagWithSpecificTestBalFile.txt",
                COMMAND_OUTPUTS, actualOutcome);
        //Check for the existence of the target directory (It shouldn't exist)
        Assert.assertFalse(Files.exists(projectDir.resolve("target")));
    }

    @AfterMethod
    public void cleanUp() throws IOException, IllegalStateException {
        if (cleaningUpDir == null) {
            throw new IllegalStateException("Cleaning up directory is not set");
        }
        FileUtils.deleteDirectory(cleaningUpDir.resolve("target").toFile());
    }
}
