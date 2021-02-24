## Sample8: Copy Files to Docker image 

- This sample runs simple ballerina hello world service with data.txt file copied to Docker Image

### How to run:

1. Compile the ballerina module. Command to deploy kubernetes artifacts will be printed on build success.
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

	@kubernetes:Docker 			 - complete 2/2

    Execute the below command to run the generated docker image:
    docker run -d -p 9090:9090 anuruddhal/hello-api:sample8

    target/bin/hello-0.0.1.jar
```

2. Verify the docker image is created:
```bash
$> docker images
REPOSITORY                      TAG                 IMAGE ID            CREATED             SIZE
anuruddhal/hello-api            sample8           8c9d8521ec84        52 seconds ago      215MB
```

3. Run Docker command to start container (Use the command printed on screen in step 1):
```bash
$> docker run -d -p 9090:9090 anuruddhal/hello-api:sample8
   8440e38b73301668568ac6f9bf4f16667e239a83490f715066902eb9e9c65ff3
```

4. Verify Docker container is running:
```bash
$> docker ps
CONTAINER ID   IMAGE                          COMMAND                  CREATED         STATUS         PORTS                    NAMES
8440e38b7330   anuruddhal/hello-api:sample8   "/bin/sh -c 'java -X…"   4 seconds ago   Up 3 seconds   0.0.0.0:9090->9090/tcp   charming_hellman
```


5. Access the service.
```bash
$> curl https://127.0.0.1:9090/helloWorld/data -k
Data: Lorem ipsum dolor sit amet.
```

7. Undeploy sample:
```bash
$> docker kill 8440e38b7330
```
