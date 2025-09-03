## Kubernetes YAML Generation With A Sample Ballerina Module

- This sample runs hello world service as a module.   

### How to run:

1. Compile the project. Command to deploy kubernetes artifacts will be printed on build success. 
**Note that the build options are provided in the `Ballerina.toml` file. `cloud` option is set to `k8s` here to generate a kubernetes YAML file.**

```toml
[package]
org = "hello"
name = "hello"
version = "0.0.1"

[build-options]
cloud="k8s"
```

```bash
$> bal build
Compiling source
	hello/hello:0.0.1

Creating balas
	target/bala/hello-2020r2-any-0.0.1.bala

Running Tests

	hello/hello:0.0.1
	No tests found


Generating executables
	target/bin/hello.jar

Generating artifacts...

	@kubernetes:Service 			 - complete 1/1
	@kubernetes:Deployment 			 - complete 1/1
	@kubernetes:HPA 			 - complete 1/1
	@kubernetes:Docker 			 - complete 2/2

	Execute the below command to deploy the Kubernetes artifacts:
	kubectl apply -f /Users/anuruddha/workspace/ballerinax/module-ballerina-c2c/samples/sample2/target/kubernetes/hello

	Execute the below command to access service via NodePort:
	kubectl expose deployment hello-hello-0-0-deployment --type=NodePort --name=hello-hello-0-0-svc-local
```

2. hello.jar, Dockerfile, docker image and kubernetes artifacts will be generated: 
```bash
$> tree target
.
├── Cloud.toml
├── Ballerina.lock
├── Ballerina.toml
├── README.md
├── src
│   └── hello
│       └── hello_api.bal
└── target
    ├── docker
    │   └── hello
    │       ├── Dockerfile
    └── kubernetes
        └── hello
            └── hello-hello-0.0.1.yaml       
```

3. Verify the docker image is created:
```bash
$> docker images
REPOSITORY                                                       TAG                                              IMAGE ID            CREATED             SIZE
anuruddhal/hello-api                                             sample2                                          3ef0ab894d4c        27 minutes ago      215MB
```


4. Run kubectl command to deploy artifacts (Use the command printed on screen in step 2):
```bash
$> kubectl apply -f target/kubernetes/hello/
service/helloworld-svc created
deployment.apps/hello-hello-0-0-deployment created
horizontalpodautoscaler.autoscaling/hello-hello-0-0-hpa created
```

5. Verify kubernetes deployment, service, secrets and ingress is deployed:
```bash
$> kubectl get pods
NAME                                          READY   STATUS    RESTARTS   AGE
hello-hello-0-0-deployment-66d5b57b95-kwbzs   1/1     Running   0          117s
```

6. Execute the below command to access service via NodePort:
```bash
$> kubectl expose deployment hello-hello-0-0-deployment --type=NodePort --name=hello-hello-0-0-svc-local
service/hello-hello-0-0-svc-local exposed
```

7. Execute the below command to find the NodePort to access the service.
```bash
$> kubctl get svc
NAME                        TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)          AGE
hello-hello-0-0-svc-local   NodePort    10.104.241.227   <none>        9090:31812/TCP   16s
helloworld-svc              ClusterIP   10.99.134.22     <none>        9090/TCP         5m56s
```

8. Access the service using NodePort (Replace the NodePort(31812) with the output of the above command):
```bash
$> curl http://localhost:31812/helloWorld/sayHello
Hello, World from service helloWorld !
```

9. Undeploy sample:
```bash
$> kubectl delete -f ./target/kubernetes/hello
$> kubectl delete svc hello-hello-0-0-svc-local
$> docker rmi anuruddhal/hello-api:sample2
```
