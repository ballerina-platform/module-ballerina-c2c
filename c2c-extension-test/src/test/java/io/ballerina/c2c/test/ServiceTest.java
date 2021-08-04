/*
 * Copyright (c) 2021, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ballerina.c2c.test;

import io.ballerina.c2c.test.utils.KubernetesTestUtils;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static io.ballerina.c2c.KubernetesConstants.KUBERNETES;

/**
 * Service Generation Tests.
 */
public class ServiceTest {

    @Test
    public void testInternalDomainName() throws IOException, InterruptedException {
        Path projectPath = Paths.get("src", "test", "resources", "service", "internal-domain");
        Assert.assertEquals(KubernetesTestUtils.compileBallerinaProject(projectPath)
                , 0);
        File artifactYaml = projectPath.resolve("target").resolve(KUBERNETES).resolve("internal").resolve("internal" +
                ".yaml").toFile();
        Assert.assertTrue(artifactYaml.exists());
        KubernetesClient client = new DefaultKubernetesClient();
        List<HasMetadata> k8sItems = client.load(new FileInputStream(artifactYaml)).get();
        for (HasMetadata data : k8sItems) {
            if ("Service".equals(data.getKind())) {
                Service service = (Service) data;
                Assert.assertEquals(service.getMetadata().getName(), "hello-svc");
                return;
            }
        }
    }


    @Test
    public void testClientTrustStore() throws IOException, InterruptedException {
        Path projectPath = Paths.get("src", "test", "resources", "service", "client-truststore-modulelevel");
        Assert.assertEquals(KubernetesTestUtils.compileBallerinaProject(projectPath)
                , 0);
        File artifactYaml = projectPath.resolve("target").resolve(KUBERNETES).resolve("clientconfig").resolve(
                "clientconfig.yaml").toFile();
        Assert.assertTrue(artifactYaml.exists());
        KubernetesClient client = new DefaultKubernetesClient();
        List<HasMetadata> k8sItems = client.load(new FileInputStream(artifactYaml)).get();
        for (HasMetadata data : k8sItems) {
            if ("Secret".equals(data.getKind())) {
                Secret secret = (Secret) data;
                Assert.assertEquals(secret.getData().size(), 2);
                Assert.assertEquals(secret.getMetadata().getName(), "securedep-secure-socket");
                return;
            }
        }
    }

    @Test
    public void testInvalidInternalDomainName() throws IOException, InterruptedException {
        Path projectPath = Paths.get("src", "test", "resources", "service", "invalid-internal-domain");
        Assert.assertNotEquals(KubernetesTestUtils.compileBallerinaProject(projectPath), 0);
    }
}
