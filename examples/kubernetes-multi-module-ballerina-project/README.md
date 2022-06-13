##  Multi module Ballerina Project.

- This sample runs two service in two modules.
- The menu related to each module is mounted as a config map   

### How to run:

1. Compile the project. Command to deploy kubernetes artifacts will be printed on build success.
```bash
$> bal build
Compiling source
	fusion/cafe:1.0.0

Running Tests

	cafe.tea
	No tests found

	cafe
	No tests found

Creating bala
	target/bala/fusion-cafe-any-1.0.0.bala

Generating executable

Generating artifacts...

	@kubernetes:Service 			 - complete 1/2
	@kubernetes:Service 			 - complete 2/2
	@kubernetes:ConfigMap 			 - complete 2/2
	@kubernetes:Deployment 			 - complete 1/1
	@kubernetes:HPA 			 - complete 1/1
	@kubernetes:Docker 			 - complete 2/2

	Execute the below command to deploy the Kubernetes artifacts:
	kubectl apply -f /Users/anuruddha/workspace/ballerinax/module-ballerina-c2c/samples/sample7/target/kubernetes/cafe

	Execute the below command to access service via NodePort:
	kubectl expose deployment cafe-deployment --type=NodePort --name=cafe-svc-local

	target/bin/cafe.jar
```

  ### How to write:
This segment shows how a c2c segment is mapped into cloud element. 

1. `Cloud.toml` segment
   ```toml
    [[cloud.config.maps]]
    file="./menus/tea.json" # Path of the external file 
    mount_path="/home/ballerina/menus/tea.json" # Path of the file in the container

    [[cloud.config.maps]]
    file="./menus/coffe.json" # Path of the external file 
    mount_path="/home/ballerina/menus/coffe.json" # Path of the file in the container
   ```

2. Kubernetes YAML file segment
   ```yaml
    apiVersion: "v1"
    kind: "ConfigMap"
    metadata:
      name: "cafe-tea-json"
    data:
      tea.json: "{\n  \"items\": [\n    {\n      \"id\": \"6\",\n      \"name\": \"Ginger\
        \ and Honey\",\n      \"price\": \"150.00\"\n    },\n    {\n      \"id\": \"7\"\
        ,\n      \"name\": \"Lime Tea\",\n      \"price\": \"160.00\"\n    },\n    {\n\
        \      \"id\": \"8\",\n      \"name\": \"Black Tea\",\n      \"price\": \"100.00\"\
        \n    },\n    {\n      \"id\": \"9\",\n      \"name\": \"Earl's gray\",\n    \
        \  \"price\": \"150.00\"\n    },\n    {\n      \"id\": \"10\",\n      \"name\"\
        : \"Ginger & Honey\",\n      \"price\": \"550.00\"\n    }\n  ]\n}\n"
    ---
    apiVersion: "v1"
    kind: "ConfigMap"
    metadata:
      name: "cafe-coffe-json"
    data:
      coffe.json: "{\n  \"items\": [\n    {\n      \"id\": \"1\",\n      \"name\": \"\
        LATTE\",\n      \"price\": \"250.00\"\n    },\n    {\n      \"id\": \"2\",\n \
        \     \"name\": \"Americano\",\n      \"price\": \"260.00\"\n    },\n    {\n \
        \     \"id\": \"3\",\n      \"name\": \"Cappuccino\",\n      \"price\": \"300.00\"\
        \n    },\n    {\n      \"id\": \"4\",\n      \"name\": \"Espresso\",\n      \"\
        price\": \"250.00\"\n    },\n    {\n      \"id\": \"5\",\n      \"name\": \"CAFÉ\
        \ MOCHA\",\n      \"price\": \"550.00\"\n    }\n  ]\n}\n"
    ---
   ```

   A kuberenetes `ConfigMap` componenet is generated with the file content.

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
              .
              .
              .
            volumeMounts:
            - mountPath: "/home/ballerina/menus/tea.json"
              name: "cafe-tea-json-volume"
              readOnly: true
              subPath: "tea.json"
            - mountPath: "/home/ballerina/menus/coffe.json"
              name: "cafe-coffe-json-volume"
              readOnly: true
              subPath: "coffe.json"
          nodeSelector: {}
          volumes:
          - configMap:
              name: "cafe-tea-json"
            name: "cafe-tea-json-volume"
          - configMap:
              name: "cafe-coffe-json"
            name: "cafe-coffe-json-volume"
    ```
	ConfigMap `volumes` are created and are mounted as `volumeMounts` in the container.




