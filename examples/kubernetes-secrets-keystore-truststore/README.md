## Mount Keystore, Truststore

- This sample runs simple ballerina hello world service with keystore and truststore.

### How to write:
This segment shows how a c2c segment is mapped into cloud element.  
1. ```Ballerina file``` segment
```bal
listener http:Listener helloWorldEP = new(9090, {
	secureSocket: {
		key: {
			path: "./resource/ballerinaKeystore.p12",
			password: "ballerina"
		},
		mutualSsl: {
			verifyClient: http:REQUIRE,
			cert: {
				path: "./resource1/ballerinaKeystore.p12",
				password: "ballerina"
			}
		}
	}
});

```
   Above `secureSocket`,`mutualSsl` components can be mapped to the Kubernetes `Secret` componenet without explicitly being mentioned in `Cloud.toml`. This behavoiur is applicable to other SSL related stuffs.
   
2. Kubernetes YAML file segment
```yaml
apiVersion: "v1"
kind: "Secret"
metadata:
  name: "helloworldep-secure-socket"
data:
  ballerinaKeystore.p12: "MIIKHgIBAzCCCdgGCSq......"
---
apiVersion: "v1"
kind: "Secret"
metadata:
  name: "helloworldep-mutual-ssl"
data:
  ballerinaKeystore.p12: "MIIKHgIBAzCCCdgG......."

```
A kubernetes `Secret` component is created with the content of SSL stuffs.

```yaml
apiVersion: "apps/v1"
kind: "Deployment"
metadata:
  .
  .
  .
spec:
    .
    .
    .
    spec:
      containers:
      - image: "anuruddhal/hello-api:sample9"
        .
        .
        .
        volumeMounts:
        - mountPath: "/home/ballerina/./resource"
          name: "helloworldep-secure-socket-volume"
          readOnly: true
        - mountPath: "/home/ballerina/./resource1"
          name: "helloworldep-mutual-ssl-volume"
          readOnly: true
      nodeSelector: {}
      volumes:
      - name: "helloworldep-secure-socket-volume"
        secret:
          secretName: "helloworldep-secure-socket"
      - name: "helloworldep-mutual-ssl-volume"
        secret:
          secretName: "helloworldep-mutual-ssl"
```

   `volume` and `volumeMount` are used to mount the secret to the container.

   

### How to run:

1. Compile the ballerina module. Command to deploy kubernetes artifacts will be printed on build success.
```bash
$> bal build
Compiling source
        hello/hello:0.0.1

Generating executable

Generating artifacts...

        @kubernetes:Service                      - complete 1/1
        @kubernetes:Deployment                   - complete 1/1
        @kubernetes:HPA                          - complete 1/1
        @kubernetes:Secret                       - complete 2/2
        @kubernetes:Docker                       - complete 2/2 

        Execute the below command to deploy the Kubernetes artifacts: 
        kubectl apply -f /Users/luheerathan/luhee/Ballerina-Project-Files/Test/c2c-test/examples/kubernetes-secrets-keystore-truststore/target/kubernetes/hello

        Execute the below command to access service via NodePort: 
        kubectl expose deployment hello-deployment --type=NodePort --name=hello-svc-local

        target/bin/hello.jar
```
