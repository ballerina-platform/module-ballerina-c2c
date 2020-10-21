# Auto Generated Dockerfile
FROM ballerina/jre11:v1

LABEL maintainer="dev@ballerina.io"

WORKDIR /home/ballerina
COPY ballerina_job.jar /home/ballerina/jars/ 
COPY ballerina-java-0.9.0.jar /home/ballerina/jars/ 
COPY ballerina-rt-2.0.0-Preview5-SNAPSHOT.jar /home/ballerina/jars/ 
COPY io-native-0.5.1-SNAPSHOT.jar /home/ballerina/jars/ 
COPY ballerina-io-0.5.1.jar /home/ballerina/jars/ 
COPY ballerina-c2c-1.0.0.jar /home/ballerina/jars/ 
RUN addgroup troupe \
    && adduser -S -s /bin/bash -g 'ballerina' -G troupe -D ballerina \
    && apk add --update --no-cache bash \
    && chown -R ballerina:troupe /usr/bin/java \
    && rm -rf /var/cache/apk/*

COPY ballerina_job.jar /home/ballerina


USER ballerina

CMD java -Xdiag -cp "ballerina_job.jar:jars/*" '$_init'
