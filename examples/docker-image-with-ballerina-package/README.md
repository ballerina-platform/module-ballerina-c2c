## Dockerfile Generation With A Sample Ballerina Module.

- This sample shows how to generate a Dockerfile with a single Ballerina file running a service.   

### How to run:

1. Compile the project. Command to deploy kubernetes artifacts will be printed on build success. 
**Note that the build options are provided in the `Ballerina.toml` file. `cloud` option is set to `docker` here to generate a Dockerfile.**

```toml
[package]
org = "hello"
name= "hello"
version = "0.0.1"

[build-options]
observabilityIncluded = true
cloud = "docker"
```

```bash
$> bal build
Compiling source
        hello/hello:0.0.1

Generating executable

Generating artifacts...

        @kubernetes:Docker                       - complete 2/2 

        Execute the below command to run the generated Docker image: 
        docker run -d -p 9095:9095 -p 9096:9096 xlight05/hello:v1.0.0

        target/bin/hello.jar
```

2. hello.jar, Dockerfile, docker image artifacts will be generated: 
```bash
$> tree target 
target
├── bin
│   └── hello.jar
└── docker
    └── hello
        └── Dockerfile
```

3. Verify the docker image is created:
```bash
$> docker images
REPOSITORY                         TAG       IMAGE ID       CREATED          SIZE
xlight05/hello                     v1.0.0    031613c25829   36 minutes ago   222MB
```


4. Run docker images
```bash
docker run -d -p 9095:9095 -p 9096:9096 xlight05/hello:v1.0.0  
```