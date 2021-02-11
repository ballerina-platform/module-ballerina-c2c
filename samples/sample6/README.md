## Sample6: Readiness and Liveness Probes.

- This sample runs hello world service with liveness and readiness probes.
```toml
[cloud.deployment.probes.readiness]
port=9090
path="/helloWorld/readyz"

[cloud.deployment.probes.liveness]
port=9090
path="/helloWorld/healthz"
```

### How to run:

1. Compile the project. Command to deploy kubernetes artifacts will be printed on build success.
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
	kubectl apply -f /Users/anuruddha/workspace/ballerinax/module-ballerina-c2c/samples/sample6/target/kubernetes/hello

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

4. Verify the docker image is created:
```bash
$> docker images
REPOSITORY                                            TAG                                              IMAGE ID            CREATED             SIZE
hello-api                                             sample6                                          3ef0ab894d4c        7 minutes ago      215MB
```


5. Run kubectl command to deploy artifacts (Use the command printed on screen in step 2):
```bash
$> kubectl apply -f target/kubernetes/hello/
service/helloworld-svc created
deployment.apps/hello-hello-0-0-deployment created
horizontalpodautoscaler.autoscaling/hello-hello-0-0-hpa created
```

6. Verify kubernetes deployment, service, hpa deployed. Note that the pod is taking 30 seconds be ready.
```bash
$> kubectl get pods
NAME                                          READY   STATUS    RESTARTS   AGE
hello-hello-0-0-deployment-66d5b57b95-kwbzs   1/1     Running   0          117s
```

7. Execute the below command to access service via NodePort:
```bash
$> kubectl expose deployment hello-hello-0-0-deployment --type=NodePort --name=hello-hello-0-0-svc-local
service/hello-hello-0-0-svc-local exposed
```

8. Execute the below command to find the NodePort to access the service.
```bash
$> kubctl get svc
NAME                        TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)          AGE
hello-hello-0-0-svc-local   NodePort    10.104.241.227   <none>        9090:31812/TCP   16s
helloworld-svc              ClusterIP   10.99.134.22     <none>        9090/TCP         5m56s
```

9. Access the service using NodePort (Replace the NodePort(31812) with the output of the above command):
```bash
$> curl http://localhost:31812/helloWorld/sayHello
Hello, World from service helloWorld !
```

8. Undeploy sample:
```bash
$> kubectl delete -f ./target/kubernetes/hello
$> kubectl delete svc hello-hello-0-0-svc-local
$> docker rmi anuruddhal/hello-api:sample6
```
