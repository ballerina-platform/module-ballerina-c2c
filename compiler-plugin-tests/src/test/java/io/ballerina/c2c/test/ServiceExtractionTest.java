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

import io.ballerina.c2c.util.ListenerInfo;
import io.ballerina.c2c.util.ProjectServiceInfo;
import io.ballerina.c2c.util.ServiceInfo;
import io.ballerina.projects.DiagnosticResult;
import io.ballerina.projects.directory.BuildProject;
import io.ballerina.tools.diagnostics.Diagnostic;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
        ListenerInfo listener = serviceInfo.getListeners().get(0);
        Assert.assertEquals(listener.getPort(), 9090);
    }

    @Test
    public void testVariableHttpServiceListenerDecl() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "listener-variable");

        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();

        Assert.assertEquals(serviceList.size(), 1);
    }

    @Test
    public void testExposeOnListenerDeclaration() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "expose-on-listener");

        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();

        Assert.assertEquals(serviceList.size(), 2);
        ServiceInfo serviceInfo = serviceList.get(0);
        ListenerInfo listener = serviceInfo.getListeners().get(0);
        Assert.assertEquals(listener.getPort(), 8080);

        ServiceInfo serviceInfo1 = serviceList.get(1);
        ListenerInfo listener1 = serviceInfo1.getListeners().get(0);
        Assert.assertEquals(listener1.getPort(), 9090);
    }

    @Test
    public void testExposeIntOrHttpListener() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "expose-int-or-http");

        BuildProject project = BuildProject.load(projectPath);
        List<Diagnostic> diagnostics = new ArrayList<>();
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project, diagnostics);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();

        Assert.assertEquals(serviceList.size(), 3);
        ServiceInfo serviceInfo = serviceList.get(0);
        ListenerInfo listener = serviceInfo.getListeners().get(0);
        Assert.assertEquals(listener.getPort(), 8080);

        ServiceInfo serviceInfo1 = serviceList.get(1);
        ListenerInfo listener1 = serviceInfo1.getListeners().get(0);
        Assert.assertEquals(listener1.getPort(), 9090);

        ServiceInfo serviceInfo2 = serviceList.get(2);
        ListenerInfo listener2 = serviceInfo2.getListeners().get(0);
        Assert.assertEquals(listener2.getPort(), 9091);

        Assert.assertEquals(diagnostics.size(), 2);
        Assert.assertEquals(diagnostics.get(0).message(),
                "failed to retrieve port. defaultable ports are not supported");
        Assert.assertEquals(diagnostics.get(1).message(),
                "failed to retrieve port. defaultable ports are not supported");
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
        ListenerInfo listener = serviceInfo.getListeners().get(0);
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
        ListenerInfo listener = serviceInfo.getListeners().get(0);
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
        ListenerInfo helloListener = helloService.getListeners().get(0);
        Assert.assertEquals(helloListener.getPort(), 9090);

        ServiceInfo probeService = serviceList.get(1);
        Assert.assertTrue(isExpectedService(probeService.getServicePath().trim()));
        ListenerInfo probeListener = probeService.getListeners().get(0);
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
        ListenerInfo helloListener = helloService.getListeners().get(0);
        Assert.assertEquals(helloListener.getPort(), 9090);
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
        ListenerInfo helloListener = helloService.getListeners().get(0);
        Assert.assertEquals(helloListener.getPort(), 9090);
    }

//    @Test
//    public void testWebSocket() {
//        Path projectPath = Paths.get("src", "test", "resources", "service", "websocket");
//
//        BuildProject project = BuildProject.load(projectPath);
//        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
//        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();
//
//        Assert.assertEquals(serviceList.size(), 1);
//        ServiceInfo helloService = serviceList.get(0);
//        Assert.assertEquals(helloService.getServicePath().trim(), "/chat");
//        ListenerInfo helloListener = helloService.getListeners().get(0);
//        Assert.assertEquals(helloListener.getPort(), 9090);
//    }

    @Test
    public void testWebSub() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "websub");

        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();

        Assert.assertEquals(serviceList.size(), 1);
        ServiceInfo helloService = serviceList.get(0);
        ListenerInfo helloListener = helloService.getListeners().get(0);
        Assert.assertEquals(helloListener.getPort(), 9090);
    }

    @Test
    public void testWebSubHub() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "websubhub");

        BuildProject project = BuildProject.load(projectPath);
        DiagnosticResult diagnosticResult = project.currentPackage().runCodeGenAndModifyPlugins();
        Assert.assertEquals(diagnosticResult.errorCount(), 0);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();

        Assert.assertEquals(serviceList.size(), 1);
        ServiceInfo helloService = serviceList.get(0);
        ListenerInfo helloListener = helloService.getListeners().get(0);
        Assert.assertEquals(helloListener.getPort(), 9090);
    }

    @Test
    public void testWebSocketSsl() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "websocket-sec");

        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();

        Assert.assertEquals(serviceList.size(), 1);
        ServiceInfo helloService = serviceList.get(0);
        Assert.assertEquals(helloService.getServicePath().trim(), "/foo");
        ListenerInfo helloListener = helloService.getListeners().get(0);
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
        ListenerInfo helloListener = helloService.getListeners().get(0);
        Assert.assertEquals(helloListener.getPort(), 9091);
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
        ListenerInfo helloListener = helloService.getListeners().get(0);
        Assert.assertEquals(helloListener.getPort(), 9095);
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
        ListenerInfo helloListener = helloService.getListeners().get(0);
        Assert.assertEquals(helloListener.getPort(), 9090);
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
    public void testDefaultConfigValue() {
        Path projectPath = Paths.get("src", "test", "resources", "diagnostics", "configurable-default-port-warning");
        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();
        Assert.assertEquals(serviceList.size(), 1);
        Assert.assertEquals(serviceList.get(0).getServicePath(), "/helloWorld");
        Assert.assertEquals(serviceList.get(0).getListeners().get(0).getPort(), 9090);
    }

    @Test
    public void testMultiListener() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "multi-listener");
        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();
        Assert.assertEquals(serviceList.size(), 1);
        Assert.assertEquals(serviceList.get(0).getServicePath(), "/");
        Assert.assertEquals(serviceList.get(0).getListeners().get(0).getPort(), 9090);
        Assert.assertEquals(serviceList.get(0).getListeners().get(1).getPort(), 9091);
    }

    @Test
    public void testMultiListenerExpose() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "multi-listener-expose");
        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();
        Assert.assertEquals(serviceList.size(), 1);
        Assert.assertEquals(serviceList.get(0).getServicePath(), "");
        Assert.assertEquals(serviceList.get(0).getListeners().get(0).getPort(), 9091);
        Assert.assertEquals(serviceList.get(0).getListeners().get(1).getPort(), 9090);
    }

    @Test
    public void testNamedArgListener() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "named-param-port");
        BuildProject project = BuildProject.load(projectPath);
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();
        Assert.assertEquals(serviceList.size(), 1);
        Assert.assertEquals(serviceList.get(0).getListeners().get(0).getPort(), 8290);
    }
}
