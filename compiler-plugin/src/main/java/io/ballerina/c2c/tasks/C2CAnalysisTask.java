/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.c2c.tasks;

import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.models.DeploymentModel;
import io.ballerina.c2c.models.JobModel;
import io.ballerina.c2c.models.KubernetesContext;
import io.ballerina.c2c.models.KubernetesDataHolder;
import io.ballerina.c2c.models.PodAutoscalerModel;
import io.ballerina.c2c.models.ServiceModel;
import io.ballerina.c2c.util.ListenerInfo;
import io.ballerina.c2c.util.ProjectServiceInfo;
import io.ballerina.c2c.util.ScheduledTask;
import io.ballerina.c2c.util.ServiceInfo;
import io.ballerina.c2c.util.Task;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.ballerina.projects.Package;
import io.ballerina.projects.Project;
import io.ballerina.projects.plugins.AnalysisTask;
import io.ballerina.projects.plugins.CompilationAnalysisContext;
import io.ballerina.tools.diagnostics.Diagnostic;

import java.util.ArrayList;
import java.util.List;

import static io.ballerina.c2c.KubernetesConstants.DOCKER_CERT_PATH;
import static io.ballerina.c2c.KubernetesConstants.DOCKER_HOST;
import static io.ballerina.c2c.KubernetesConstants.SVC_POSTFIX;
import static io.ballerina.c2c.utils.KubernetesUtils.getValidName;

/**
 * An {@code AnalysisTask} that is triggered for code to cloud.
 *
 * @since 1.0.0
 */
public class C2CAnalysisTask implements AnalysisTask<CompilationAnalysisContext> {

    @Override
    public void perform(CompilationAnalysisContext compilationAnalysisContext) {
        Package currentPackage = compilationAnalysisContext.currentPackage();
        final Project project = compilationAnalysisContext.currentPackage().project();
        String cloud = project.buildOptions().cloud();
        if (cloud == null || !KubernetesUtils.isValidBuildOption(cloud)) {
            return;
        }
        KubernetesContext.getInstance().setCurrentPackage(KubernetesUtils.getProjectID(currentPackage));
        KubernetesDataHolder dataHolder = KubernetesContext.getInstance().getDataHolder();
        dataHolder.setPackageID(KubernetesUtils.getProjectID(currentPackage));
        List<Diagnostic> c2cDiagnostics = new ArrayList<>();
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(currentPackage.project(), c2cDiagnostics);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();
        try {
            addServices(serviceList);
        } catch (KubernetesPluginException e) {
            compilationAnalysisContext.reportDiagnostic(e.getDiagnostic());
        }

        for (Diagnostic diagnostic : c2cDiagnostics) {
            compilationAnalysisContext.reportDiagnostic(diagnostic);
        }
        addDeployments();
        addHPA();
        addJobs(projectServiceInfo);
    }

    private void addJobs(ProjectServiceInfo projectServiceInfo) {
        if (projectServiceInfo.getTask().isPresent()) {
            Task task = projectServiceInfo.getTask().get();
            JobModel jobModel = new JobModel();
            if (task instanceof ScheduledTask) {
                jobModel.setSchedule(((ScheduledTask) task).getSchedule());
            }

            String dockerHost = System.getenv(DOCKER_HOST);
            if (!KubernetesUtils.isBlank(dockerHost)) {
                jobModel.setDockerHost(dockerHost);
            }
            String dockerCertPath = System.getenv(DOCKER_CERT_PATH);
            if (!KubernetesUtils.isBlank(dockerCertPath)) {
                jobModel.setDockerCertPath(dockerCertPath);
            }
            KubernetesContext.getInstance().getDataHolder().setJobModel(jobModel);
        }
    }

    private void addServices(List<ServiceInfo> serviceList) throws KubernetesPluginException {
        for (ServiceInfo serviceInfo : serviceList) {
            List<ListenerInfo> listeners = serviceInfo.getListeners();
            for (ListenerInfo listener : listeners) {
                ServiceModel serviceModel = new ServiceModel();
                if (KubernetesUtils.isBlank(serviceModel.getName())) {
                    serviceModel.setName(getValidName(serviceInfo.getServicePath() + SVC_POSTFIX));
                }

                int port = listener.getPort();
                if (serviceModel.getPort() == -1) {
                    serviceModel.setPort(port);
                }
                if (serviceModel.getTargetPort() == -1) {
                    serviceModel.setTargetPort(port);
                }

                serviceModel.setProtocol("http");

                KubernetesContext.getInstance().getDataHolder().addServiceModel(serviceModel);
            }
        }
    }

    private void addHPA() {
        PodAutoscalerModel podAutoscalerModel = new PodAutoscalerModel();
        KubernetesContext.getInstance().getDataHolder().setPodAutoscalerModel(podAutoscalerModel);
    }

    private void addDeployments() {
        DeploymentModel deploymentModel = new DeploymentModel();

        String dockerHost = System.getenv(DOCKER_HOST);
        if (!KubernetesUtils.isBlank(dockerHost)) {
            deploymentModel.setDockerHost(dockerHost);
        }
        String dockerCertPath = System.getenv(DOCKER_CERT_PATH);
        if (!KubernetesUtils.isBlank(dockerCertPath)) {
            deploymentModel.setDockerCertPath(dockerCertPath);
        }
        KubernetesContext.getInstance().getDataHolder().setDeploymentModel(deploymentModel);
    }

}
