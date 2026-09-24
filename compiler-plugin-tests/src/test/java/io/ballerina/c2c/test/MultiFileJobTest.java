/*
 * Copyright (c) 2026, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package io.ballerina.c2c.test;

import io.ballerina.c2c.KubernetesConstants;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.test.utils.KubernetesTestUtils;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.ballerina.c2c.KubernetesConstants.DOCKER;
import static io.ballerina.c2c.KubernetesConstants.KUBERNETES;

/**
 * Regression test for a package whose {@code main()} carries {@code @cloud:Task} but is not the
 * alphabetically-last `.bal` file in its module. {@code ProjectServiceInfo} used to overwrite the
 * detected task with whatever the last-visited file found (nothing, for a plain helper file),
 * silently turning the CronJob into a Deployment with no diagnostic at all.
 */
public class MultiFileJobTest {

    private static final Path SOURCE_DIR_PATH = Paths.get("src", "test", "resources", "job-multi-file");
    private static final Path DOCKER_TARGET_PATH = SOURCE_DIR_PATH.resolve("target").resolve(DOCKER)
            .resolve("hello_multi_file_job");
    private static final Path KUBERNETES_TARGET_PATH = SOURCE_DIR_PATH.resolve("target").resolve(KUBERNETES)
            .resolve("hello_multi_file_job");
    private static final String DOCKER_IMAGE_JOB = "anuruddhal/hello-multi-file-job:v4";

    @Test
    public void testTaskDetectionNotClobberedByLaterFile() throws IOException, InterruptedException {
        Assert.assertEquals(KubernetesTestUtils.compileBallerinaProject(SOURCE_DIR_PATH), 0);

        File jobYAML = KUBERNETES_TARGET_PATH.resolve("hello_multi_file_job.yaml").toFile();
        Assert.assertTrue(jobYAML.exists());
        CronJob job = KubernetesTestUtils.loadYaml(jobYAML);
        Assert.assertEquals(job.getSpec().getSchedule(), "* * * * *");
        Assert.assertEquals(job.getSpec().getJobTemplate().getSpec().getTemplate().getSpec()
                .getRestartPolicy(), KubernetesConstants.RestartPolicy.OnFailure.name());
        Assert.assertEquals(job.getSpec().getJobTemplate().getSpec().getTemplate().getSpec()
                .getContainers().get(0).getImage(), DOCKER_IMAGE_JOB);
    }

    @AfterClass
    public void cleanUp() throws KubernetesPluginException {
        KubernetesUtils.deleteDirectory(KUBERNETES_TARGET_PATH);
        KubernetesUtils.deleteDirectory(DOCKER_TARGET_PATH);
        KubernetesTestUtils.deleteDockerImage(DOCKER_IMAGE_JOB);
    }
}
