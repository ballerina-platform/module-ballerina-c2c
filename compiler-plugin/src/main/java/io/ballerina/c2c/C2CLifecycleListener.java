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
package io.ballerina.c2c;

import io.ballerina.c2c.tasks.C2CCodeGeneratedTask;
import io.ballerina.c2c.tasks.ChoreoCodeGenTask;
import io.ballerina.projects.plugins.CompilerLifecycleContext;
import io.ballerina.projects.plugins.CompilerLifecycleListener;

/**
 * A {@code CompilerLifecycleListener} implementation that registers a CodeGenerate Listener.
 *
 * @since 1.0.0
 */
public class C2CLifecycleListener extends CompilerLifecycleListener {


    @Override
    public void init(CompilerLifecycleContext compilerLifecycleContext) {
        compilerLifecycleContext.addCodeGenerationCompletedTask(new C2CCodeGeneratedTask());
        compilerLifecycleContext.addCodeGenerationCompletedTask(new ChoreoCodeGenTask());
    }
}
