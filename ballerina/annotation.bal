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

# Cron Job Schedule Configuration.
#
# + hours - Hours
# + monthOfYear - Month of the Year
# + dayOfMonth - Day of Month
# + minutes - Minutes
# + daysOfWeek - Days of Week
public type ScheduleConfig record {|
     string minutes;
     string hours;
     string dayOfMonth;
     string monthOfYear;
     string daysOfWeek;
|};

# Task Configuration.
#
# + schedule - Task execution schedule
public type TaskConfig record{|
    ScheduleConfig schedule?;
|};

# @cloud:Task annotation to configure cron job.
public const annotation TaskConfig Task on source function;

# @cloud:Expose annotation marks a port that has to be exposed in a custom listener initializer.
# ```ballerina
# public function init(@cloud:Expose int port) {}
# ```
public const annotation Expose on parameter;
