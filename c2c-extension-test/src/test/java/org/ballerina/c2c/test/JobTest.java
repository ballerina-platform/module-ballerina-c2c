/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.ballerina.c2c.test;

import com.github.dockerjava.api.command.InspectImageResponse;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.batch.Job;
import org.ballerina.c2c.KubernetesConstants;
import org.ballerina.c2c.exceptions.KubernetesPluginException;
import org.ballerina.c2c.test.utils.KubernetesTestUtils;
import org.ballerina.c2c.utils.KubernetesUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.ballerina.c2c.KubernetesConstants.DOCKER;
import static org.ballerina.c2c.KubernetesConstants.KUBERNETES;
import static org.ballerina.c2c.test.utils.KubernetesTestUtils.getDockerImage;

/**
 * Test cases for job resources.
 */
public class JobTest {
    private static final Path SOURCE_DIR_PATH = Paths.get("src", "test", "resources", "job");
    private static final Path DOCKER_TARGET_PATH = SOURCE_DIR_PATH.resolve(DOCKER);
    private static final Path KUBERNETES_TARGET_PATH = SOURCE_DIR_PATH.resolve(KUBERNETES);
    private static final String DOCKER_IMAGE_JOB = "ballerina_job:latest";

    @Test
    public void testKubernetesJobGeneration() throws IOException, InterruptedException {
        Assert.assertEquals(KubernetesTestUtils.compileBallerinaFile(SOURCE_DIR_PATH, "ballerina_job.bal"), 0);

        File dockerFile = DOCKER_TARGET_PATH.resolve("Dockerfile").toFile();
        Assert.assertTrue(dockerFile.exists());
        InspectImageResponse imageInspect = getDockerImage(DOCKER_IMAGE_JOB);
        Assert.assertNotNull(imageInspect.getConfig());

        File jobYAML = KUBERNETES_TARGET_PATH.resolve("ballerina_job.yaml").toFile();
        Job job = KubernetesTestUtils.loadYaml(jobYAML);
        Assert.assertEquals(job.getMetadata().getName(), "ballerina-job-job");
        Assert.assertEquals(job.getSpec().getTemplate().getSpec().getContainers().size(), 1);

        Container container = job.getSpec().getTemplate().getSpec().getContainers().get(0);
        Assert.assertEquals(container.getImage(), DOCKER_IMAGE_JOB);
        Assert.assertEquals(container.getImagePullPolicy(), KubernetesConstants.ImagePullPolicy.IfNotPresent.name());
        Assert.assertEquals(job.getSpec().getTemplate().getSpec()
                .getRestartPolicy(), KubernetesConstants.RestartPolicy.OnFailure.name());
    }

    @AfterClass
    public void cleanUp() throws KubernetesPluginException {
        KubernetesUtils.deleteDirectory(KUBERNETES_TARGET_PATH);
        KubernetesUtils.deleteDirectory(DOCKER_TARGET_PATH);
        KubernetesTestUtils.deleteDockerImage(DOCKER_IMAGE_JOB);
    }
}
