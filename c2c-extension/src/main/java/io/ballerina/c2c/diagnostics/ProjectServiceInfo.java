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
package io.ballerina.c2c.diagnostics;

import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Module;
import io.ballerina.projects.Package;
import io.ballerina.projects.Project;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Represents service related data of a Project after parsed from Syntax trees.
 *
 * @since 2.0.0
 */
public class ProjectServiceInfo {

    private List<ServiceInfo> serviceList;
    private List<ListenerInfo> listenerList;
    private Task task = null;

    public ProjectServiceInfo(Project project) {
        this.serviceList = new ArrayList<>();
        this.listenerList = new ArrayList<>();
        Package currentPackage = project.currentPackage();
        Iterable<Module> modules = currentPackage.modules();
        for (Module module : modules) {
            Collection<DocumentId> documentIds = module.documentIds();
            for (DocumentId doc : documentIds) {
                Document document = module.document(doc);
                Node node = document.syntaxTree().rootNode();

                C2CVisitor visitor = new C2CVisitor();
                node.accept(visitor);
                serviceList.addAll(visitor.getServices());
                listenerList.addAll(visitor.getListeners());
                this.task = visitor.getTask();
            }
        }

        //When service use a listener in another bal file
        for (ServiceInfo serviceInfo : serviceList) {
            ListenerInfo listener = serviceInfo.getListener();
            if (listener.getPort() == 0) {
                String name = listener.getName();
                for (ListenerInfo listenerInfo : listenerList) {
                    if (name.equals(listenerInfo.getName())) {
                        listener.setPort(listenerInfo.getPort());
                    }
                }
            }
        }
    }

    public List<ServiceInfo> getServiceList() {
        return serviceList;
    }

    public List<ListenerInfo> getListenerList() {
        return listenerList;
    }

    public Optional<Task> getTask() {
        return Optional.ofNullable(task);
    }
}
