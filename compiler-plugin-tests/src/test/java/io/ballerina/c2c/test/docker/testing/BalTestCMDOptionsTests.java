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
    private static Path cleaningUpDir = null;

    @Test
    public void testCloudFlagWithNoValue() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-flag-no-value");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, "--cloud", new String[0]);
        cleaningUpDir = projectDir;
        Assert.assertFalse(projectDir.resolve("target").toFile().exists());
        Assert.assertTrue(actualOutcome.contains("ballerina: flag '--cloud' (<cloud>) needs an argument"));
        Assert.assertTrue(actualOutcome.contains("Run 'bal help' for usage."));
    }

    @Test
    public void testCloudFlagWithK8s() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-flag-k8s");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                "--cloud=k8s", new String[0]);
        cleaningUpDir = projectDir;
        Assert.assertFalse(projectDir.resolve("target").resolve("docker").toFile().exists());
        Assert.assertTrue(actualOutcome.contains("error [k8s plugin]: k8s cloud build only supported for build"));
        Assert.assertTrue(actualOutcome.contains("SEVERE: k8s cloud build only supported for build"));
    }

    @Test
    public void testCloudFlagWithCodeCoverage() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-flag-code-coverage");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--code-coverage"});
        cleaningUpDir = projectDir;
        Assert.assertTrue(actualOutcome.contains("WARNING: Code coverage generation is not supported with " +
                "Ballerina cloud test"));
        Assert.assertTrue(actualOutcome.contains("Running Tests"));
        Assert.assertFalse(actualOutcome.contains("Running Tests with Coverage"));
        Assert.assertTrue(actualOutcome.contains("[pass] anotherSmallTest"));
        Assert.assertTrue(actualOutcome.contains("[pass] anotherSmallTest2"));
        Assert.assertTrue(actualOutcome.contains("[pass] smallTest"));
    }

    @Test
    public void testCloudFlagWithTestReport() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-flag-test-report");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--test-report"});
        cleaningUpDir = projectDir;
        Assert.assertTrue(actualOutcome.contains("WARNING: Test report generation is not supported with " +
                "Ballerina cloud test"));
        Assert.assertTrue(actualOutcome.contains("[pass] anotherSmallTest"));
        Assert.assertTrue(actualOutcome.contains("[pass] anotherSmallTest2"));
        Assert.assertTrue(actualOutcome.contains("[pass] smallTest"));
    }

    @Test
    public void testCloudFlagWithRerunFailed() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-flag-rerun-failed");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--rerun-failed"});
        cleaningUpDir = projectDir;
        Assert.assertTrue(actualOutcome.contains("WARNING: Rerun failed tests is not supported with " +
                "Ballerina cloud test"));
        Assert.assertTrue(actualOutcome.contains("[pass] anotherSmallTest"));
        Assert.assertTrue(actualOutcome.contains("[pass] anotherSmallTest2"));
        Assert.assertTrue(actualOutcome.contains("[pass] smallTest"));
    }

    @Test
    public void testCloudFlagWithGraalVmForTestsWithoutMocking() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-flag-graalvm");
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

        Assert.assertTrue(actualOutcome.contains("Building the native image. This may take a while"));
        Assert.assertTrue(actualOutcome.contains("[pass] assertThis"));
    }

    @Test //TODO: this test takes a long time to run
    public void testCloudFlagWithGraalVmForTestsWithMocking() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-flag-graalvm-mocking");
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
        Assert.assertEquals(actualOutcome.split("Building the native image. This may take a while").length, 3);
        Assert.assertTrue(actualOutcome.contains("[pass] mockTest"));
        Assert.assertTrue(actualOutcome.contains("[pass] testMockingInModule"));
    }

    @Test
    public void testCloudFlagForProjectWithNoTests() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("project-with-no-tests");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[0]);
        Assert.assertFalse(Files.exists(projectDir.resolve("tests")));
        cleaningUpDir = projectDir;
        Assert.assertTrue(actualOutcome.contains("No tests found"));
        Assert.assertFalse(projectDir.resolve("target").resolve("docker").toFile().exists());
    }

    @Test
    public void testCloudFlagWithListGroupsForProjectWithNoGroups() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("project-with-no-groups");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--list-groups"});
        cleaningUpDir = projectDir;
        Assert.assertTrue(actualOutcome.contains("There are no groups available!"));
    }

    @Test
    public void testCloudFlagWithListGroupsForProjectWithGroups() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("project-with-groups");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--list-groups"});
        cleaningUpDir = projectDir;
        Assert.assertTrue(actualOutcome.contains("Following groups are available :"));
        Assert.assertTrue(actualOutcome.contains("[g1, g2, g3]"));
    }

    @Test
    public void testCloudFlagWithSpecificGroups() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("project-with-groups");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--groups", "g1,g2"});
        cleaningUpDir = projectDir;
        Assert.assertTrue(actualOutcome.contains("[pass] testFunction1"));
        Assert.assertTrue(actualOutcome.contains("[pass] testFunction2"));
        Assert.assertTrue(actualOutcome.contains("[pass] testFunction3"));
        Assert.assertTrue(actualOutcome.contains("3 passing"));
    }

    @Test
    public void testCloudFlagWithDisabledGroups() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("project-with-groups");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--disable-groups", "g1,g3"});
        cleaningUpDir = projectDir;
        Assert.assertTrue(actualOutcome.contains("[pass] testFunction3"));
        Assert.assertFalse(actualOutcome.contains("[pass] testFunction1"));
        Assert.assertFalse(actualOutcome.contains("[pass] testFunction2"));
    }

    @Test
    public void testCloudFlagWithSpecificTestFunctions() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-project-with-modules");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--tests", "testMain,testMod"});
        cleaningUpDir = projectDir;
        String regexMod2 = "cloud_project_with_modules\\.mod2\\s+No tests found";
        Assert.assertTrue(actualOutcome.matches("(?s).*" + regexMod2 + ".*"));
        String regexCloudProject = "cloud_project_with_modules\\s+\\[pass\\] testMain\\s+1" +
                " passing\\s+0 failing\\s+0 skipped";
        Assert.assertTrue(actualOutcome.matches("(?s).*" + regexCloudProject + ".*"));
        String regexMod1 = "cloud_project_with_modules\\.mod1\\s+\\[pass\\] testMod\\s+1" +
                " passing\\s+0 failing\\s+0 skipped";
        Assert.assertTrue(actualOutcome.matches("(?s).*" + regexMod1 + ".*"));
    }

    @Test
    public void testCloudFlagWithRootPackageTests() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-project-with-modules");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--tests", "cloud_project_with_modules:testMain"});
        cleaningUpDir = projectDir;
        String regexMod2 = "cloud_project_with_modules\\.mod2\\s+No tests found";
        Assert.assertTrue(actualOutcome.matches("(?s).*" + regexMod2 + ".*"));
        String regexCloudProject = "cloud_project_with_modules\\s+\\[pass\\] testMain\\s+1 passing\\s+" +
                "0 failing\\s+0 skipped";
        Assert.assertTrue(actualOutcome.matches("(?s).*" + regexCloudProject + ".*"));
        String regexMod1 = "cloud_project_with_modules\\.mod1\\s+No tests found";
        Assert.assertTrue(actualOutcome.matches("(?s).*" + regexMod1 + ".*"));
    }

    @Test
    public void testCloudFlagWithExecutionOfOnlyRootPackageTests() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-project-with-modules");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--tests", "cloud_project_with_modules:*"});
        cleaningUpDir = projectDir;
        String regexMod2 = "cloud_project_with_modules\\.mod2\\s+No tests found";
        Assert.assertTrue(actualOutcome.matches("(?s).*" + regexMod2 + ".*"));
        String regexCloudProject = "cloud_project_with_modules\\s+\\[pass\\] testMain\\s+\\[pass\\] testMainTwo\\s+" +
                "2 passing\\s+0 failing\\s+0 skipped";
        Assert.assertTrue(actualOutcome.matches("(?s).*" + regexCloudProject + ".*"));
        String regexMod1 = "cloud_project_with_modules\\.mod1\\s+No tests found";
        Assert.assertTrue(actualOutcome.matches("(?s).*" + regexMod1 + ".*"));
    }

    @Test
    public void testCloudFlagWithExecutionOfModuleTests() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-project-with-modules");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--tests", "cloud_project_with_modules.mod1:*"});
        cleaningUpDir = projectDir;
        String regexMod2 = "cloud_project_with_modules\\.mod2\\s+No tests found";
        Assert.assertTrue(actualOutcome.matches("(?s).*" + regexMod2 + ".*"));
        String regexCloudProject = "cloud_project_with_modules\\s+No tests found";
        Assert.assertTrue(actualOutcome.matches("(?s).*" + regexCloudProject + ".*"));
        String regexMod1 = "cloud_project_with_modules\\.mod1\\s+\\[pass] testMod\\s+\\[pass] testModTwo\\s+" +
                "2 passing\\s+0 failing\\s+0 skipped";
        Assert.assertTrue(actualOutcome.matches("(?s).*" + regexMod1 + ".*"));
    }

    @Test
    public void testCloudFlagWithExecutionOfMultipleSpecifiedTests() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("cloud-project-with-modules");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--tests", "cloud_project_with_modules.mod2:testMod2Two,cloud_project_with_modules:*"});
        cleaningUpDir = projectDir;
        String regexMod2 = "cloud_project_with_modules\\.mod2\\s+\\[pass\\] testMod2Two\\s+1 passing\\s+" +
                "0 failing\\s+0 skipped";
        Assert.assertTrue(actualOutcome.matches("(?s).*" + regexMod2 + ".*"));
        String regexCloudProject = "cloud_project_with_modules\\s+\\[pass\\] testMain\\s+\\[pass\\] testMainTwo\\s+" +
                "2 passing\\s+0 failing\\s+0 skipped";
        Assert.assertTrue(actualOutcome.matches("(?s).*" + regexCloudProject + ".*"));
        String regexMod1 = "cloud_project_with_modules\\.mod1\\s+No tests found";
        Assert.assertTrue(actualOutcome.matches("(?s).*" + regexMod1 + ".*"));
    }

    @Test
    public void testCloudFlagWithSpecificTestBalFile() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.getParent().resolve("single-file-tests")
                .resolve("single-test-file-execution");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{projectDir.resolve("single-test-file-execution.bal").toAbsolutePath().toString()});
        cleaningUpDir = projectDir;
        String regexPattern = "\\s*single-test-file-execution\\.bal\\s+\\[pass\\] testFunc\\s+\\[pass\\] " +
                "testFunc2\\s+2 passing\\s+0 failing\\s+0 skipped\\s*";
        Assert.assertTrue(actualOutcome.matches("(?s).*" + regexPattern + ".*"));
        //Check for the existence of the target directory (It shouldn't exist)
        Assert.assertFalse(Files.exists(projectDir.resolve("target")));
    }

    @Test
    public void testCloudFlagWithConfigurableValues() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("test-with-config-but-val-not-provided");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"-CmyVal=2"});
        cleaningUpDir = projectDir;
        Assert.assertTrue(actualOutcome.contains("[pass] failingTest"));
    }

    @Test
    public void testCloudFlagWithoutProvidingConfigurableValues() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("test-with-config-but-val-not-provided");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[0]);
        cleaningUpDir = projectDir;
        String regexPattern = "error: value not provided for required configurable variable 'myVal'\\s+at " +
                "cloud_tests/test_with_config_but_val_not_provided:0\\.0\\.0\\(tests/tes\\.bal:3\\)";
        Assert.assertTrue(actualOutcome.matches("(?s).*" + regexPattern + ".*"));
        Assert.assertTrue(actualOutcome.contains("error [k8s plugin]: Error running the docker image: " +
                "test_with_config_but_val_not_provided-testable:latest"));
    }

    @Test
    public void testCloudFlagWithConfigFiles() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("tests-with-config");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[0]);
        cleaningUpDir = projectDir;
        Assert.assertTrue(actualOutcome.contains("[pass] testName"));
    }

    @Test
    public void testCloudFlagWithConfigurableValuesToOverrideConfig() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("tests-with-config");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"-Cname=Goku"});
        cleaningUpDir = projectDir;
        // Cannot use the TestUtils.assertOutput method here as the test fails
        Assert.assertTrue(actualOutcome.contains("0 passing"));
        Assert.assertTrue(actualOutcome.contains("1 failing"));
        Assert.assertTrue(actualOutcome.contains("+Ballerina"));
        Assert.assertTrue(actualOutcome.contains("-Goku"));
    }

    @Test
    public void testCloudFlagWithDataProviderMap() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("data-provider-map");
        //TODO: check the argument
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--tests", "fruitsDataProviderTest#'banana'"});
        cleaningUpDir = projectDir;
        Assert.assertTrue(actualOutcome.contains("[pass] fruitsDataProviderTest#banana"));
        Assert.assertTrue(actualOutcome.contains("1 passing"));
    }

    @Test
    public void testCloudFlagWithDataProviderArray() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("data-provider-array");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir,
                new String[]{"--tests", "stringDataProviderTest#1"});
        cleaningUpDir = projectDir;
        Assert.assertTrue(actualOutcome.contains("[pass] stringDataProviderTest#1"));
        Assert.assertTrue(actualOutcome.contains("1 passing"));
    }

    @Test
    public void testCloudFlagForProjectsWithResourceFiles() throws IOException, InterruptedException {
        Path projectDir = SOURCE_DIR_PATH.resolve("project-with-resources");
        String actualOutcome = KubernetesTestUtils.compileBallerinaProjectTests(projectDir, new String[0]);
        cleaningUpDir = projectDir;
        String regexPattern1 = "\\s*project_with_resources_for_tests\\s+\\[pass] testReadingResourceCsv\\s+" +
                "\\[pass] testReadingResourceTxt\\s*";
        Assert.assertTrue(actualOutcome.matches("(?s).*" + regexPattern1 + ".*"));
        String regexPattern2 = "\\s*project_with_resources_for_tests\\.mod1\\s+\\[pass] testModuleTestResourceFile\\s*";
        Assert.assertTrue(actualOutcome.matches("(?s).*" + regexPattern2 + ".*"));
    }

    @AfterMethod
    public void cleanUp() throws IOException, IllegalStateException {
        if (cleaningUpDir == null) {
            throw new IllegalStateException("Cleaning up directory is not set");
        }
        FileUtils.deleteDirectory(cleaningUpDir.resolve("target").toFile());
    }
}
