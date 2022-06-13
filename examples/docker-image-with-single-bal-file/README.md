## Dockerfile Generation With Single Ballerina  File

- This sample shows how to generate a Dockerfile with a  simple ballerina file runnig a service.   

### How to run:

1. Compile the project. Command to deploy kubernetes artifacts will be printed on build success.
   
	**Note that a `--cloud=docker` commandline option is utilised to generate a Dockerfile.**
```bash
$> bal build --cloud=docker service.bal 

Compiling source
        service.bal

Generating executable

Generating artifacts...

        @kubernetes:Docker                       - complete 2/2 

        Execute the below command to run the generated Docker image: 
        docker run -d -p 9096:9096 service:latest

        service.jar
```

2. service.jar, Dockerfile, Docker image artifacts will be generated: 
```bash
├── service.jar                                    
├── docker                                             
    └── Dockerfile                                                           
```

3. Verify the docker image is created:
```bash
$> docker images
REPOSITORY                         TAG       IMAGE ID       CREATED             SIZE
service                            latest    55727282caf8   6 minutes ago       222MB
```
4. Run docker images
```bash
docker run -d -p 9096:9096 service:latest
```
