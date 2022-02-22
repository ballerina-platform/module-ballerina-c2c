// Copyright (c) 2021 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import ballerina/websubhub;

# An in-memory WebSub Hub implementation.
service /JuApTOXq19 on new websubhub:Listener(9090) {

    # Registers a `topic` in the hub.
    #
    # + message - Details related to the topic-registration
    # + return - `websubhub:TopicRegistrationSuccess` if topic registration is successful, `websubhub:TopicRegistrationError`
    #           if topic registration failed
    remote function onRegisterTopic(readonly & websubhub:TopicRegistration message)
                                returns websubhub:TopicRegistrationSuccess|websubhub:TopicRegistrationError {
        return websubhub:TOPIC_REGISTRATION_SUCCESS;
    }

    # Deregisters a `topic` in the hub.
    # 
    # + message - Details related to the topic-deregistration
    # + return - `websubhub:TopicDeregistrationSuccess` if topic deregistration is successful, `websubhub:TopicDeregistrationError`
    #            if topic deregistration failed
    remote function onDeregisterTopic(readonly & websubhub:TopicDeregistration message) returns websubhub:TopicDeregistrationSuccess|websubhub:TopicDeregistrationError {
        return websubhub:TOPIC_DEREGISTRATION_SUCCESS;
    }

    # Publishes content to the hub.
    # 
    # + message - Details of the published content
    # + return - `websubhub:Acknowledgement` if publish content is successful, `websubhub:UpdateMessageError`
    #            if publish content failed
    remote function onUpdateMessage(readonly & websubhub:UpdateMessage message) returns websubhub:Acknowledgement|websubhub:UpdateMessageError {
        return websubhub:ACKNOWLEDGEMENT;
    }

    # Validates a incomming subscription request.
    # 
    # + message - Details of the subscription
    # + return - `websubhub:SubscriptionDeniedError` if the subscription is denied by the hub or else `()`
    remote function onSubscriptionValidation(readonly & websubhub:Subscription message) returns websubhub:SubscriptionDeniedError? {
        return websubhub:SUBSCRIPTION_DENIED_ERROR;
    }

    # Processes a verified subscription request.
    # 
    # + message - Details of the subscription
    # + return - `error` if there is any unexpected error or else `()`
    remote function onSubscriptionIntentVerified(readonly & websubhub:VerifiedSubscription message) returns error? {
    }

    # Validates a incomming unsubscription request.
    # 
    # + message - Details of the unsubscription
    # + return - `websubhub:UnsubscriptionDeniedError` if the unsubscription is denied by the hub or else `()`
    remote function onUnsubscriptionValidation(readonly & websubhub:Unsubscription message) returns websubhub:UnsubscriptionDeniedError? {
        return websubhub:UNSUBSCRIPTION_DENIED_ERROR;
    }

    # Processes a verified unsubscription request.
    # 
    # + message - Details of the unsubscription
    # + return - `error` if there is any unexpected error or else `()`
    remote function onUnsubscriptionIntentVerified(readonly & websubhub:VerifiedUnsubscription message) returns error? {
    }    
}
