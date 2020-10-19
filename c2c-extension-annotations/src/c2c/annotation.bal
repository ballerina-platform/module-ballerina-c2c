// Copyright (c) 2020 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
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


public type DeploymentConfiguration record {|
 |};

# @kubernetes:Deployment annotation to configure deplyoment yaml.
public const annotation DeploymentConfiguration Deployment on source service, source function, source listener;

# Kubernetes service configuration.
public type ServiceConfiguration record {|
|};

# @kubernetes:Service annotation to configure service yaml.
public const annotation ServiceConfiguration Service on source listener, source service;

# Kubernetes Horizontal Pod Autoscaler configuration
public type PodAutoscalerConfig record {|
|};

# @kubernetes:HPA annotation to configure horizontal pod autoscaler yaml.
public const annotation PodAutoscalerConfig HPA on source service, source function;

# Secret volume mount configurations for kubernetes.
public type SecretMount record {|
|};

# @kubernetes:Secret annotation to configure secrets.
public const annotation SecretMount Secret on source service, source function;

# ConfigMap volume mount configurations for kubernetes.
public type ConfigMapMount record {|
|};

# @kubernetes:ConfigMap annotation to configure config maps.
public const annotation ConfigMapMount ConfigMap on source service, source function;

# Persistent Volume Claims configurations for kubernetes.
public type PersistentVolumeClaims record {|
|};

# @kubernetes:PersistentVolumeClaim annotation to configure Persistent Volume Claims.
public const annotation PersistentVolumeClaims PersistentVolumeClaim on source service, source function;

# Resource Quota configuration for kubernetes.
public type ResourceQuotas record {|
|};

# @kubernetes:ResourcesQuotas annotation to configure Resource Quotas.
public const annotation ResourceQuotas ResourceQuota on source service, source function;

public type ScheduleConfig record {|
     string minutes;
     string hours;
     string dayOfMonth;
     string monthOfYear;
     string daysOfWeek;
|};

public type TaskConfig record{|
    ScheduleConfig schedule?;
|};

# @c2c:Task annotation to configure cron job.
public const annotation TaskConfig Task on source function;
