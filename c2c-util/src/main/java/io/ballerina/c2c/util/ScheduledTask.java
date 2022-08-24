/*
 *  Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package io.ballerina.c2c.util;

/**
 * Represents a Scheduled Task config of code to cloud annotation.
 *
 * @since 2.0.0
 */
public class ScheduledTask extends Task {
    private final String minutes;
    private final String hours;
    private final String dayOfMonth;
    private final String monthOfYear;
    private final String daysOfWeek;

    public ScheduledTask(String minutes, String hours, String dayOfMonth, String monthOfYear, String daysOfWeek) {
        this.minutes = minutes;
        this.hours = hours;
        this.dayOfMonth = dayOfMonth;
        this.monthOfYear = monthOfYear;
        this.daysOfWeek = daysOfWeek;
    }

    public String getSchedule () {
        return minutes + " " + hours + " " + dayOfMonth + " "
                + monthOfYear + " " + daysOfWeek;
    }
}
