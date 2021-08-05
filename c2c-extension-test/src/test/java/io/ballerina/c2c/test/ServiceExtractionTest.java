/*
 * Copyright (c) 2020, WSO2 Inc. (http://wso2.com) All Rights Reserved.
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

import io.ballerina.c2c.diagnostics.ClientInfo;
import io.ballerina.c2c.diagnostics.ListenerInfo;
import io.ballerina.c2c.diagnostics.ProjectServiceInfo;
import io.ballerina.c2c.diagnostics.ServiceInfo;
import io.ballerina.projects.directory.BuildProject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Responsible for testing various ways of service definition extraction.
 *
 * @since 2.0.0
 */
public class ServiceExtractionTest {

    @Test
    public void testSimpleHttpNoServiceDecl() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "simple-http-no-service-decl");

        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();

        Assert.assertEquals(serviceList.size(), 1);
        ServiceInfo serviceInfo = serviceList.get(0);
        Assert.assertEquals(serviceInfo.getServicePath().trim(), "/helloWorld");
        ListenerInfo listener = serviceInfo.getListener();
        Assert.assertEquals(listener.getPort(), 9090);
    }

    @Test
    public void testSimpleHttpWithServiceDecl() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "simple-http-with-service-decl");

        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();

        Assert.assertEquals(serviceList.size(), 1);
        ServiceInfo serviceInfo = serviceList.get(0);
        Assert.assertEquals(serviceInfo.getServicePath().trim(), "/helloWorld");
        ListenerInfo listener = serviceInfo.getListener();
        Assert.assertEquals(listener.getPort(), 9090);
    }

    @Test
    public void testSimpleHttpWithSeperateListener() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "seperate-listener");

        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();

        Assert.assertEquals(serviceList.size(), 1);
        ServiceInfo serviceInfo = serviceList.get(0);
        Assert.assertEquals(serviceInfo.getServicePath().trim(), "/helloWorld");
        ListenerInfo listener = serviceInfo.getListener();
        Assert.assertEquals(listener.getPort(), 9090);
    }

    @Test
    public void testSimpleHttpWithSeperateListenerSeperateFiles() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "seperate-listener-seperate-file");

        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();

        Assert.assertEquals(serviceList.size(), 2);
        ServiceInfo helloService = serviceList.get(0);
        Assert.assertTrue(isExpectedService(helloService.getServicePath().trim()));
        ListenerInfo helloListener = helloService.getListener();
        Assert.assertEquals(helloListener.getPort(), 9090);

        ServiceInfo probeService = serviceList.get(1);
        Assert.assertTrue(isExpectedService(probeService.getServicePath().trim()));
        ListenerInfo probeListener = probeService.getListener();
        Assert.assertEquals(probeListener.getPort(), 9090);
    }

    private boolean isExpectedService(String service) {
        return service.equals("/helloWorld") || service.equals("/probe");
    }

    @Test
    public void testSSLSeperateListener() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "ssl-seperate-listener");

        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();

        Assert.assertEquals(serviceList.size(), 1);
        ServiceInfo helloService = serviceList.get(0);
        Assert.assertTrue(isExpectedService(helloService.getServicePath().trim()));
        ListenerInfo helloListener = helloService.getListener();
        Assert.assertEquals(helloListener.getPort(), 9090);
        String keystore = helloListener.getConfig().get().getSecureSocketConfig().get().getCertFile();
        Assert.assertEquals(keystore, "./security/ballerinaKeystore.p12");
    }

    @Test
    public void testClientTrustStore() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "client-truststore");

        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ClientInfo> clientList = projectServiceInfo.getClientList();

        Assert.assertEquals(clientList.size(), 1);
        ClientInfo clientInfo = clientList.get(0);
        Assert.assertEquals(clientInfo.getName(), "nettyEP");
        String keystore = clientInfo.getHttpsConfig().getMutualSSLConfig().get().getPath();
        Assert.assertEquals(keystore, "./security/ballerinaTruststore.p12");
    }

    @Test
    public void testGraphQl() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "graphql");

        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();

        Assert.assertEquals(serviceList.size(), 1);
        ServiceInfo helloService = serviceList.get(0);
        Assert.assertEquals(helloService.getServicePath().trim(), "/graphql");
        ListenerInfo helloListener = helloService.getListener();
        Assert.assertEquals(helloListener.getPort(), 9090);
    }

    @Test
    public void testInlineHttps() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "inline-https");

        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();

        Assert.assertEquals(serviceList.size(), 1);
        ServiceInfo helloService = serviceList.get(0);
        Assert.assertEquals(helloService.getServicePath().trim(), "/probe");
        ListenerInfo helloListener = helloService.getListener();
        Assert.assertEquals(helloListener.getPort(), 9091);

        String keystore = helloListener.getConfig().get().getSecureSocketConfig().get().getCertFile();
        Assert.assertEquals(keystore, "./security/ballerinaKeystore.p12");
        String certPath = helloListener.getConfig().get().getSecureSocketConfig().get().getKeyFile();
        Assert.assertEquals(certPath, "ballerina");
    }

    @Test
    public void testSSLSeperateListenerConfig() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "ssl-seperate-listener-config");

        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();

        Assert.assertEquals(serviceList.size(), 1);
        ServiceInfo helloService = serviceList.get(0);
        Assert.assertEquals(helloService.getServicePath().trim(), "/hello");
        ListenerInfo helloListener = helloService.getListener();
        Assert.assertEquals(helloListener.getPort(), 9095);
        String keystore = helloListener.getConfig().get().getSecureSocketConfig().get().getCertFile();
        Assert.assertEquals(keystore, "./security/ballerinaKeystore.p12");
        String certPath = helloListener.getConfig().get().getMutualSSLConfig().get().getPath();
        Assert.assertEquals(certPath, "/path/to/public.crt");
    }

    @Test
    public void testSSLVariableListenerConfig() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "ssl-variable");

        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();

        Assert.assertEquals(serviceList.size(), 1);
        ServiceInfo helloService = serviceList.get(0);
        Assert.assertEquals(helloService.getServicePath().trim(), "/helloWorld");
        ListenerInfo helloListener = helloService.getListener();
        Assert.assertEquals(helloListener.getPort(), 9090);
        String keystore = helloListener.getConfig().get().getSecureSocketConfig().get().getPath();
        Assert.assertEquals(keystore, "./security/ballerinaKeystore.p12");
    }

    @Test
    public void testOptionalPort() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "valid-variable-port");

        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();
        Assert.assertEquals(serviceList.size(), 1);
    }

    @Test
    public void testUnsupportedListener() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "unsupported-listener");

        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();
        Assert.assertEquals(serviceList.size(), 0);
    }

    @Test
    public void testClientWithoutTrustStore() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "client-no-truststore");
        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ClientInfo> clientList = projectServiceInfo.getClientList();
        Assert.assertEquals(clientList.size(), 0);
    }
}
