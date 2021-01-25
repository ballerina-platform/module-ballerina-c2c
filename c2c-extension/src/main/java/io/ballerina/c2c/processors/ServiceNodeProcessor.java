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

package io.ballerina.c2c.processors;

import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.models.KubernetesContext;
import io.ballerina.c2c.models.KubernetesDataHolder;
import io.ballerina.c2c.models.SecretModel;
import io.ballerina.c2c.models.ServiceModel;
import io.ballerina.c2c.utils.KubernetesUtils;
import org.apache.commons.codec.binary.Base64;
import org.ballerinalang.model.tree.ServiceNode;
import org.ballerinalang.model.tree.SimpleVariableNode;
import org.wso2.ballerinalang.compiler.tree.BLangIdentifier;
import org.wso2.ballerinalang.compiler.tree.BLangService;
import org.wso2.ballerinalang.compiler.tree.BLangSimpleVariable;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangNamedArgsExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTypeInit;
import org.wso2.ballerinalang.compiler.tree.types.BLangUserDefinedType;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.ballerina.c2c.KubernetesConstants.BALLERINA_HOME;
import static io.ballerina.c2c.KubernetesConstants.LISTENER_PATH_VARIABLE;
import static io.ballerina.c2c.KubernetesConstants.SVC_POSTFIX;
import static io.ballerina.c2c.utils.KubernetesUtils.convertRecordFields;
import static io.ballerina.c2c.utils.KubernetesUtils.getValidName;

/**
 * Service annotation processor.
 */
public class ServiceNodeProcessor extends AbstractNodeProcessor {

    final KubernetesDataHolder dataHolder = KubernetesContext.getInstance().getDataHolder();

    @Override
    public void processNode(ServiceNode serviceNode) throws KubernetesPluginException {
        BLangService bService = (BLangService) serviceNode;
        ServiceModel serviceModel = new ServiceModel();
        if (KubernetesUtils.isBlank(serviceModel.getName())) {
            serviceModel.setName(getValidName(serviceNode.getName().getValue()) + SVC_POSTFIX);
        }

        if (bService.getAttachedExprs().get(0) instanceof BLangTypeInit) {
            BLangTypeInit bListener = (BLangTypeInit) bService.getAttachedExprs().get(0);
            validatePorts(serviceModel, bListener);
            dataHolder.addServiceModel(serviceModel);
        }
    }

    private void validatePorts(ServiceModel serviceModel, BLangTypeInit bListener) throws KubernetesPluginException {
        // If port is not empty, then listener port is used for the k8s svc target port while
        // service annotation port is used for k8s port.
        // If port is empty, then listener port is used for both port and target port of the k8s
        // svc.
        if (serviceModel.getPort() == -1) {
            serviceModel.setPort(extractPort(bListener));
        }

        if (serviceModel.getTargetPort() == -1) {
            serviceModel.setTargetPort(extractPort(bListener));
        }
        setServiceProtocol(serviceModel, bListener);
    }

    @Override
    public void processNode(SimpleVariableNode variableNode) throws KubernetesPluginException {
        ServiceModel serviceModel = new ServiceModel();
        if (KubernetesUtils.isBlank(serviceModel.getName())) {
            serviceModel.setName(getValidName(variableNode.getName().getValue()) + SVC_POSTFIX);
        }

        BLangTypeInit bListener = (BLangTypeInit) ((BLangSimpleVariable) variableNode).expr;
        if (bListener.argsExpr.size() == 2) {
            if (bListener.argsExpr.get(1) instanceof BLangRecordLiteral) {
                BLangRecordLiteral recordLiteral = (BLangRecordLiteral) bListener.argsExpr.get(1);
                List<BLangRecordLiteral.BLangRecordKeyValueField> listenerConfig =
                        convertRecordFields(recordLiteral.getFields());
                processListener(variableNode.getName().getValue(), listenerConfig);
            } else if (bListener.argsExpr.get(1) instanceof BLangNamedArgsExpression) {
                // expression is in config = {} format.
                BLangRecordLiteral recordFields =
                        (BLangRecordLiteral) ((BLangNamedArgsExpression) bListener.argsExpr.get(1)).expr;
                List<BLangRecordLiteral.BLangRecordKeyValueField> listenerConfig = convertRecordFields(
                        recordFields.getFields());
                processListener(variableNode.getName().getValue(), listenerConfig);
            }
        }
        validatePorts(serviceModel, bListener);
        dataHolder.addServiceModel(serviceModel);
    }

    private void processListener(String listenerName, List<BLangRecordLiteral.BLangRecordKeyValueField> listenerConfig)
            throws KubernetesPluginException {
        for (BLangRecordLiteral.BLangRecordKeyValueField keyValue : listenerConfig) {
            String key = keyValue.getKey().toString();
            if ("secureSocket".equals(key)) {
                List<BLangRecordLiteral.BLangRecordKeyValueField> sslKeyValues =
                        convertRecordFields(((BLangRecordLiteral) keyValue.valueExpr).getFields());
                Set<SecretModel> secretModels = processSecureSocketAnnotation(listenerName, sslKeyValues);
                KubernetesContext.getInstance().getDataHolder().addListenerSecret(listenerName, secretModels);
                KubernetesContext.getInstance().getDataHolder().addSecrets(secretModels);
            }
        }
    }

    /**
     * Extract key-store/trust-store file location from listener.
     *
     * @param listenerName          Listener name
     * @param secureSocketKeyValues secureSocket annotation struct
     * @return List of @{@link SecretModel} objects
     */
    private Set<SecretModel> processSecureSocketAnnotation(String listenerName, List<BLangRecordLiteral
            .BLangRecordKeyValueField> secureSocketKeyValues) throws KubernetesPluginException {
        Set<SecretModel> secrets = new HashSet<>();
        String keyStoreFile = null;
        String trustStoreFile = null;
        for (BLangRecordLiteral.BLangRecordKeyValueField keyValue : secureSocketKeyValues) {
            //extract file paths.
            String key = keyValue.getKey().toString();
            if ("keyStore".equals(key)) {
                keyStoreFile = extractFilePath(keyValue);
            } else if ("trustStore".equals(key)) {
                trustStoreFile = extractFilePath(keyValue);
            }
        }
        if (keyStoreFile != null && trustStoreFile != null) {
            if (getMountPath(keyStoreFile).equals(getMountPath(trustStoreFile))) {
                // trust-store and key-store mount to same path
                String keyStoreContent = readSecretFile(keyStoreFile);
                String trustStoreContent = readSecretFile(trustStoreFile);
                SecretModel secretModel = new SecretModel();
                secretModel.setName(getValidName(listenerName) + "-secure-socket");
                secretModel.setMountPath(getMountPath(keyStoreFile));
                Map<String, String> dataMap = new HashMap<>();
                dataMap.put(String.valueOf(Paths.get(keyStoreFile).getFileName()), keyStoreContent);
                dataMap.put(String.valueOf(Paths.get(trustStoreFile).getFileName()), trustStoreContent);
                secretModel.setData(dataMap);
                secrets.add(secretModel);
                return secrets;
            }
        }
        if (keyStoreFile != null) {
            String keyStoreContent = readSecretFile(keyStoreFile);
            SecretModel secretModel = new SecretModel();
            secretModel.setName(getValidName(listenerName) + "-keystore");
            secretModel.setMountPath(getMountPath(keyStoreFile));
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put(String.valueOf(Paths.get(keyStoreFile).getFileName()), keyStoreContent);
            secretModel.setData(dataMap);
            secrets.add(secretModel);
        }
        if (trustStoreFile != null) {
            String trustStoreContent = readSecretFile(trustStoreFile);
            SecretModel secretModel = new SecretModel();
            secretModel.setName(getValidName(listenerName) + "-truststore");
            secretModel.setMountPath(getMountPath(trustStoreFile));
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put(String.valueOf(Paths.get(trustStoreFile).getFileName()), trustStoreContent);
            secretModel.setData(dataMap);
            secrets.add(secretModel);
        }
        return secrets;
    }

    private String readSecretFile(String filePath) throws KubernetesPluginException {
        if (filePath.contains("${ballerina.home}")) {
            // Resolve variable locally before reading file.
            String ballerinaHome = System.getProperty("ballerina.home");
            filePath = filePath.replace("${ballerina.home}", ballerinaHome);
        }
        Path dataFilePath = Paths.get(filePath);
        return Base64.encodeBase64String(KubernetesUtils.readFileContent(dataFilePath));
    }

    private String getMountPath(String mountPath) throws KubernetesPluginException {
        Path parentPath = Paths.get(mountPath).getParent();
        if (parentPath != null && ".".equals(parentPath.toString())) {
            // Mounts to the same path overriding the source file.
            throw new KubernetesPluginException("Invalid path: " + mountPath + ". " +
                    "Providing relative path in the same level as source file is not supported with code2cloud." +
                    "Please create a subfolder and provide the relative path. " +
                    "eg: './security/ballerinaKeystore.p12'");
        }
        if (!Paths.get(mountPath).isAbsolute()) {
            mountPath = BALLERINA_HOME + File.separator + mountPath;
        }
        return String.valueOf(Paths.get(mountPath).getParent());
    }

    private String extractFilePath(BLangRecordLiteral.BLangRecordKeyValueField keyValue) {
        List<BLangRecordLiteral.BLangRecordKeyValueField> keyStoreConfigs =
                convertRecordFields(((BLangRecordLiteral) keyValue.valueExpr).getFields());
        for (BLangRecordLiteral.BLangRecordKeyValueField keyStoreConfig : keyStoreConfigs) {
            String configKey = keyStoreConfig.getKey().toString();
            if (LISTENER_PATH_VARIABLE.equals(configKey)) {
                return keyStoreConfig.getValue().toString();
            }
        }
        return null;
    }

    private int extractPort(BLangTypeInit bListener) throws KubernetesPluginException {
        try {
            if ("int".equals(bListener.argsExpr.get(0).expectedType.toString())) {
                // Listener with port as the first argument. eg:- http listener
                return Integer.parseInt(bListener.argsExpr.get(0).toString());
            } else {
                // other listeners with port as an attribute
                for (BLangRecordLiteral.BLangRecordKeyValueField arg :
                        convertRecordFields(((BLangRecordLiteral) bListener.argsExpr.get(0)).getFields())) {
                    if ("port".equals(arg.getKey().toString())) {
                        return Integer.parseInt(arg.getValue().toString());
                    }
                }
            }
            throw new KubernetesPluginException("unable extract port from the listener " +
                    bListener.argsExpr.get(0).toString());
        } catch (NumberFormatException e) {
            throw new KubernetesPluginException("unable to parse port/targetPort for the service: " +
                    bListener.argsExpr.get(0).toString());
        }
    }

    private void setServiceProtocol(ServiceModel serviceModel, BLangTypeInit bListener) {
        if (null != bListener.userDefinedType) {
            BLangUserDefinedType userDefinedType = (BLangUserDefinedType) bListener.userDefinedType;
            serviceModel.setProtocol(userDefinedType.getPackageAlias().getValue());
        } else {
            BLangIdentifier packageAlias =
                    ((BLangUserDefinedType) ((BLangSimpleVariable) bListener.parent).typeNode).getPackageAlias();
            serviceModel.setProtocol(packageAlias.getValue());
        }
        if ("http".equals(serviceModel.getProtocol())) {
            // Add http config
            if (bListener.argsExpr.size() == 2) {
                if (bListener.argsExpr.get(1) instanceof BLangRecordLiteral) {
                    BLangRecordLiteral bConfigRecordLiteral = (BLangRecordLiteral) bListener.argsExpr.get(1);
                    List<BLangRecordLiteral.BLangRecordKeyValueField> listenerConfig =
                            convertRecordFields(bConfigRecordLiteral.getFields());
                    serviceModel.setProtocol(isHTTPS(listenerConfig) ? "https" : "http");
                }
            }
        }
    }

    private boolean isHTTPS(List<BLangRecordLiteral.BLangRecordKeyValueField> listenerConfig) {
        for (BLangRecordLiteral.BLangRecordKeyValueField keyValue : listenerConfig) {
            String key = keyValue.getKey().toString();
            if ("secureSocket".equals(key)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Enum for Service configurations.
     */
    public enum ServiceConfiguration {
        name,
        annotations,
        serviceType,
    }
}
