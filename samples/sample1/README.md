## Sample1: Ballerina single file

- This sample runs a simple ballerina file with a service.   

### How to run:

1. Compile the project. Command to deploy kubernetes artifacts will be printed on build success.
```bash
$> bal build --cloud=k8s hello_world.bal 

Compiling source
	hello_world.bal

Generating executables
	hello_world.jar

Generating artifacts...

	@kubernetes:Service 			 - complete 1/1
	@kubernetes:Deployment 			 - complete 1/1
	@kubernetes:HPA 			 - complete 1/1
	@kubernetes:Docker 			 - complete 2/2

	Execute the below command to deploy the Kubernetes artifacts:
	kubectl apply -f /Users/anuruddha/workspace/ballerinax/module-ballerina-c2c/samples/sample1/kubernetes

	Execute the below command to access service via NodePort:
	kubectl expose deployment hello-world-deployment --type=NodePort --name=hello-world-svc-local
```

2. hello_world.jar, Dockerfile, Docker image and Kubernetes artifacts will be generated: 
```bash
├── hello_world.jar                                    
├── docker                                             
    └── Dockerfile                                                                              
└── kubernetes
    └── hello_world.yaml        
```

4. Verify the docker image is created:
```bash
$> docker images
REPOSITORY                                             TAG                                              IMAGE ID            CREATED             SIZE
hello_world                                            latest                                           96d3dddb6cb3        2 minutes ago       137MB
```

5. Run kubectl command to deploy artifacts (Use the command printed on screen in step 2):
```bash
$> kubectl apply -f /Users/anuruddha/workspace/ballerinax/module-ballerina-c2c/samples/sample1/kubernetes
service/helloworld-svc created
deployment.apps/hello-world-deployment created
horizontalpodautoscaler.autoscaling/hello-world-hpa created

$> kubectl expose deployment hello-world-deployment --type=NodePort --name=hello-world-svc-local
service/hello-world-svc-local exposed
```

6. Verify kubernetes deployment, service, secrets and ingress is deployed:
```bash
$> kubectl get pods
NAME                                     READY   STATUS    RESTARTS   AGE
hello-world-deployment-c85fc7f7f-494pz   1/1     Running   0          38s


$> kubectl get svc
NAME                    TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)          AGE
hello-world-svc-local   NodePort    10.103.172.221   <none>        9090:32029/TCP   80s
helloworld-svc          ClusterIP   10.101.114.196   <none>        9090/TCP         90s
kubernetes              ClusterIP   10.96.0.1        <none>        443/TCP          55d
```

7. Access the hello-world-svc-local with curl command (Replace the port `32029` with NodePort):

```bash
$> curl http://localhost:32029/helloWorld/sayHello
Hello, World from service helloWorld !
```

8. Undeploy sample:
```bash
$> kubectl delete -f kubernetes
$> kubectl delete svc hello-world-svc-local
```
