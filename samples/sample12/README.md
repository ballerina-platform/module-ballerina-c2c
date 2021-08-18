## Sample12: Mount Secret volumes to deployment 

- This sample runs simple ballerina hello world service with secret mounts.

### How to run:

1. Compile the ballerina module. Command to deploy kubernetes artifacts will be printed on build success.
```bash
$> bal build 
Compiling source
	hello/hello:0.0.1

Generating executable

Generating artifacts...

	@kubernetes:Secret 			 - complete 2/2
	@kubernetes:Service 			 - complete 1/1
	@kubernetes:Deployment 			 - complete 1/1
	@kubernetes:HPA 			 - complete 1/1
	@kubernetes:Docker 			 - complete 2/2

	Execute the below command to deploy the Kubernetes artifacts:
	kubectl apply -f /Users/anuruddha/workspace/ballerinax/module-ballerina-c2c/samples/sample12/target/kubernetes/hello

	Execute the below command to access service via NodePort:
	kubectl expose deployment hello-deployment --type=NodePort --name=hello-svc-local

	target/bin/hello.jar
```

2. .jar, Dockerfile, Docker image and Kubernetes artifacts will be generated:


3. Verify the docker image is created:
```bash
$> docker images
REPOSITORY                      TAG                 IMAGE ID            CREATED             SIZE
anuruddhal/hello-api            sample12            8c9d8521ec84        52 seconds ago      215MB
```

4. Run kubectl command to deploy artifacts (Use the command printed on screen in step 1):
```bash
$> kubectl apply -f /Users/anuruddha/workspace/ballerinax/module-ballerina-c2c/samples/sample12/target/kubernetes/hello   
secret/helloworldep-secure-socket created
secret/hello-data-txt created
service/hello-svc created
deployment.apps/hello-deployment created
horizontalpodautoscaler.autoscaling/hello-hpa created
```

5. Verify kubernetes deployment,service, hpa is deployed:
```bash
$> kubectl get pods
NAME                                        READY   STATUS    RESTARTS   AGE
hello-hello-0-0-deployment-595f466b-5hz64   1/1     Running   0          2s

$> kubectl get svc
NAME               TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)    AGE
helloworldep-svc   ClusterIP   10.97.222.219   <none>        9090/TCP   17s

$> kubectl get secrets
NAME                         TYPE       DATA      AGE
hello-data-txt               Opaque     1      4m46s
helloworldep-secure-socket   Opaque     2      4m46s

```

7. Execute the below command to access service via NodePort:
```bash
$> kubectl expose deployment hello-deployment --type=NodePort --name=hello-svc-local
service/hello-svc-local exposed
```

8. Execute the below command to find the NodePort to access the service.
```bash
$> kubctl get svc
NAME                        TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)          AGE
hello-svc-local   NodePort    10.104.241.227   <none>        9090:31812/TCP   16s
helloworldep-svc             ClusterIP   10.99.134.22     <none>        9090/TCP         5m56s
```

9. Access the service using NodePort (Replace the NodePort(31812) with the output of the above command):
```bash
$> curl https://127.0.0.1:<31812>/helloWorld/data -k
Data: Lorem ipsum dolor sit amet.
```

7. Undeploy sample:
```bash
$> kubectl delete -f ./target/kubernetes/hello
$> kubectl delete svc hello-svc-local
$> docker rmi anuruddhal/hello-api:sample12
```
