## Mount config map volumes to deployment 

- This sample runs simple ballerina hello world service with config map mounts.
- K8S config maps are intended to hold config information.
- Putting this information in a config map is safer and more flexible than putting it verbatim in a pod definition or in a docker image.

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

	@kubernetes:Service 		    - complete 1/1
	@kubernetes:Secret 			    - complete 1/1
	@kubernetes:ConfigMap 			- complete 2/2
	@kubernetes:Deployment 			- complete 1/1
	@kubernetes:HPA 			 - complete 1/1
	@kubernetes:Docker 			 - complete 2/2

	Execute the below command to deploy the Kubernetes artifacts:
	kubectl apply -f /Users/anuruddha/workspace/ballerinax/module-ballerina-c2c/samples/sample5/target/kubernetes/hello

	Execute the below command to access service via NodePort:
	kubectl expose deployment hello-hello-0-0-deployment --type=NodePort --name=hello-hello-0-0-svc-local
```
### How to write:
This segment shows how a c2c segment is mapped into cloud element. 

1. `Cloud.toml` segment
   ```toml
      [[cloud.config.maps]]
		file="./conf/data.txt" # Path of the external file
		mount_path="/home/ballerina/data" # Path of the file in the container 

		[[cloud.config.files]]
		file="./conf/Config.toml" # Path of the external file
		name="sample5-config-map" 

   ```
2. Kubernetes YAML file segment
   ```yaml
       apiVersion: "v1"
		kind: "ConfigMap"
		metadata:
		name: "sample5-config-map"
		data:
		Config.toml: "[hello.hello]\nusers = \"john@ballerina.com,jane@ballerina.com\"\n\
			groups = \"apim,esb\"\n"
		---
		apiVersion: "v1"
		kind: "ConfigMap"
		metadata:
		name: "hello-data-txt"
		data:
		data.txt: "Lorem ipsum dolor sit amet."
   ```
   A kubernetes `ConfigMap` componenet is generated with the file content.
   ```yaml
       apiVersion: "apps/v1"
		kind: "Deployment"
		.
		.
		.
		.
		template:
			.
			.
			.
			.
			containers:
			- env:
				- name: "BAL_CONFIG_FILES"
				  value: "/home/ballerina/conf/Config.toml:"
				.
				.
				.
				.
				volumeMounts:
							.
							.
							.
				- mountPath: "/home/ballerina/conf/"
				name: "sample5-config-map-volume"
				readOnly: false
				- mountPath: "/home/ballerina/data"
				name: "hello-data-txt-volume"
				readOnly: true
				nodeSelector: {}
				volumes:
						.
						.
						.
				- configMap:
					name: "sample5-config-map"
					name: "sample5-config-map-volume"
				- configMap:
					name: "hello-data-txt"
					name: "hello-data-txt-volume"
   ```
	Configmap `volumes` are created and are mounted as `volumeMounts` in the container. A additional step is carried out for the `[[cloud.config.files]]` table element which is used to mount configurations. These configuartions are added to `-env` section of YAML file as `BAL_CONFIG_FILES`.

1. ```Ballerina file``` segment
   ```bal
		listener http:Listener helloWorldEP = new(9090, {
			secureSocket: {
				key: {
					certFile: "./resource/public.crt",
					keyFile: "./resource/private.key"
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
	public.crt: "LS0tLS1CRUdJTiBDRVJ........"
	private.key: "LS0tLS1CRUdJTiBQUk........"

	```
	A kubernetes `Secret` component is created with the content of SSL stuffs.

   ```yaml
	apiVersion: "apps/v1"
	kind: "Deployment"
		.
		.
		.
	name: "hello-deployment"
	spec:
		.
		.
		.
	template:
		.
		.
		.
		containers:
		- env:
			.
			.
			.
			volumeMounts:
			- mountPath: "/home/ballerina/./resource"
			name: "helloworldep-secure-socket-volume"
			readOnly: true
			.
			.
			.
		nodeSelector: {}
		volumes:
		- name: "helloworldep-secure-socket-volume"
			secret:
			secretName: "helloworldep-secure-socket"
		.
		.
		.  
	```

   `volume` and `volumeMount` are used to mount the secret to the container.


