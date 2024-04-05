## Mount config map volumes to deployment 

- This sample runs simple ballerina hello world service with config map mounts.
- K8S config maps are intended to hold config information.
- Putting this information in a config map is safer and more flexible than putting it verbatim in a pod definition or in a docker image.

### How to write:
This segment shows how a c2c segment is mapped into cloud element. 

1. `Cloud.toml` segment
```toml
[[cloud.config.maps]]
file="./data/data.txt" # Path of the external file
mount_dir="./data" # Dir of the file in the container

[[cloud.config.files]]
file="./data/Config.toml" # Path of the external file
name="sample5-config-map"

[[cloud.config.secrets]]
file="./mysql-secrets.toml"
name="mysql-secrets"

[[cloud.secret.files]]
file="./resource"
mount_dir="./resource"

```
2. Kubernetes YAML file segment
```yaml
---
apiVersion: "v1"
kind: "ConfigMap"
metadata:
  name: "sample5-config-map"
data:
  Config.toml: "[xlight.hello]\nusers = \"john@ballerina.com,jane@ballerina.com\"\n\
    groups = \"apim,esb\"\n"
---
apiVersion: "v1"
kind: "ConfigMap"
metadata:
  name: "hello-data-txtcfg0"
data:
  data.txt: "Lorem ipsum dolor sit amet."
---
apiVersion: "v1"
kind: "Secret"
metadata:
  name: "mysql-secrets"
data:
  mysql-secrets.toml: "W215c3FsXQpob3N0ID0gImxvY2FsaG9zdCIKdXNlciA9ICJ1c2VyIgo="
---
apiVersion: "v1"
kind: "Secret"
metadata:
  name: "hello-resource-secret0"
data:
  public.crt: "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSURkekNDQWwrZ0F3SUJBZ0lFZlAzZTh6QU5CZ2txaGtpRzl3MEJBUXNGQURCa01Rc3dDUVlEVlFRR0V3SlYKVXpFTE1Ba0dBMVVFQ0JNQ1EwRXhGakFVQmdOVkJBY1REVTF2ZFc1MFlXbHVJRlpwWlhjeERUQUxCZ05WQkFvVApCRmRUVHpJeERUQUxCZ05WQkFzVEJGZFRUekl4RWpBUUJnTlZCQU1UQ1d4dlkyRnNhRzl6ZERBZUZ3MHhOekV3Ck1qUXdOVFEzTlRoYUZ3MHpOekV3TVRrd05UUTNOVGhhTUdReEN6QUpCZ05WQkFZVEFsVlRNUXN3Q1FZRFZRUUkKRXdKRFFURVdNQlFHQTFVRUJ4TU5UVzkxYm5SaGFXNGdWbWxsZHpFTk1Bc0dBMVVFQ2hNRVYxTlBNakVOTUFzRwpBMVVFQ3hNRVYxTlBNakVTTUJBR0ExVUVBeE1KYkc5allXeG9iM04wTUlJQklqQU5CZ2txaGtpRzl3MEJBUUVGCkFBT0NBUThBTUlJQkNnS0NBUUVBZ1Z5aTZmVmlWTGlaS0VudzU5eHpOaTFsY1loNno5ZFpudWcrRjlnS3FGSWcKbWRjUGUrcXRTN2daYzFqWVRqV01DYngxM3NGTGtacU5IZURVYWRwbXRLbzNURGR1T2wxc3FNNmN6M3lYYjZMMwo0ay9sZWg1MG16SVBObWFhWHhkM3ZPUW9LNE9wa2dPMW4zMm1oNit0a3Azc2JIbWZZcURRcmtWSzF0bVlOdFBKCmZmU0NMVCtDdUlobkpVSmNvN04wdW5heCt5U1pONjcvQVgrK3NKcHFBaEFJWkp6clJpNnVlTjNSRkNJeFlEWFMKTXZ4ckVtT2RuNGdPQzBvMUFyOXU1QnA5TjUyc3FxR2JOMXg2ak5LaTNiZlVqMTIySHU1ZStZOUtPbWZiY2hoUQppbDJQODFjSWkzMFZLZ3lEbjVEZVdFdURvWXJlZGs0KzZxQVpyeE13K3dJREFRQUJvekV3THpBT0JnTlZIUThCCkFmOEVCQU1DQmFBd0hRWURWUjBPQkJZRUZObXRyUTM2ajZ0VUdoS3JmVzlxV1dFN0tGek1NQTBHQ1NxR1NJYjMKRFFFQkN3VUFBNElCQVFBdjN5T3dnYnRPdTc2ZUpNbDFCQ2NnVEZnYU1VQlpvVWpLOVVuNkhHaktFZ1l6L1lXUwpaRmxZL3FINXJUMDFEV1FldlVaQjYyNmQ1Wk5kelNCWlJscHN4YmY5SUUvdXJzTkh3SHg5dWE2ZkI3eUhVQ3pDCjFaTXAxbHZCSEFCaTd3Y0ErNW5iVjZ6UTdIRG1CWEZoSmZiZ0gxaVZtQTFLY3ZEZUJQU0ovc2NSR2FzWjVxMlcKM0llbkROcmZQSVVoRDc0dEZpQ2lxTkpPOTFxRC9MTysrKzNYZVp6ZlBoOE5SS2tpUFg3ZEI4V0ozWU5CdVFBdgpnUldUSVNwU1NYTG1xTWIrN01QUVZnZWNzZXBaZGs4Q3drUkx4aDNSS1BKTWppZ21DZ3l2a1Nhb0RNS0FZQzNpCllqZlVUaUo1N1VlcW9TbDBJYU9GSjB3ZlpSRmgrVXl0bERaYQotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0tCg=="
  private.key: "LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUV2UUlCQURBTkJna3Foa2lHOXcwQkFRRUZBQVNDQktjd2dnU2pBZ0VBQW9JQkFRQ0JYS0xwOVdKVXVKa28KU2ZEbjNITTJMV1Z4aUhyUDExbWU2RDRYMkFxb1VpQ1oxdzk3NnExTHVCbHpXTmhPTll3SnZIWGV3VXVSbW8wZAo0TlJwMm1hMHFqZE1OMjQ2WFd5b3pwelBmSmR2b3ZmaVQrVjZIblNiTWc4MlpwcGZGM2U4NUNncmc2bVNBN1dmCmZhYUhyNjJTbmV4c2VaOWlvTkN1UlVyVzJaZzIwOGw5OUlJdFA0SzRpR2NsUWx5anMzUzZkckg3SkprM3J2OEIKZjc2d21tb0NFQWhrbk90R0xxNTQzZEVVSWpGZ05kSXkvR3NTWTUyZmlBNExTalVDdjI3a0duMDNuYXlxb1pzMwpYSHFNMHFMZHQ5U1BYYlllN2w3NWowbzZaOXR5R0ZDS1hZL3pWd2lMZlJVcURJT2ZrTjVZUzRPaGl0NTJUajdxCm9CbXZFekQ3QWdNQkFBRUNnZ0VBWE0vRjR1MjNPdW1tbVExVDFrYUlNcHFuYWFsdDA2akNHQXl3WUJNVXNtY2EKRk1ZRHlmZzVsVlhraktsMXA4Y3JUZUQxQUhqV2F3VGpza2dZbmttZjNvY3hYWEYzbUZCbklVWDdvN0hVUkxnNworUmN4b1Vnd2lSaUZhWlo3c3pYM0pvTGJmenpiY0hOUTM3a2F2Y2NCVld3UXNGTWlVM1RsdytMYkt3SzYvcm93CkxZc1FQeDdnVDR1N2hWaWF0NHZRRFRZY2d5anZ2RkNpZWs0bmRMNk85SzQ5TXhJTVU2NzhVWEI2aWE1aVVldnkKdmdFZmNZa0tRNUVRMzhxUzNad3N1YlB2ajQ2MzNqdkFKUnIvaEpEOFhJTlpDNzRrVFhlVjNCR0gyTGxwUU9FcQprV2tPeXB3WU5qblh0dDFKTzgrSXU2bUVYS1VvaUlCUGZHckozdkRTUVFLQmdRRG1ZUGM3a2ZZYW4vTEhqSlJ2CmlFMkN3YkMyNnlWQTYrQkVQUXY5ejdqQ2hPOVE2Y1ViR3ZNOEVFVk5wQzlubUZvZ2tzbHpKaHo1NUhQODRRWkwKdTNwdFUrRDk2bmNxNnprQnF4QmZSblpHKytEMzYrWFJYSXd6ejNoK2cxTndybDB5ME1GYndsa01tM1pxSmRkNgpwWnoxRlpHZDZ6dlFmdFc4bTdqUFNLSHVzd0tCZ1FDUHY2Y3pGT1pSNmJJK3FDUWRhT1JwZTlKR29BZHVPRCs0CllLbDk2czBlaUFLaGtHaEZDck1kNkdKd1dSa3BOY2Z3QitKOXNNYWhPUmJmdndpWWFuSTU2aDdWaTMwREZQUmIKbTFtOGRMa3I2eis4YnhNeEtKYU1YSUlqeTNVRGFtZ0RyN1FISW5OVWloMmlHdnRCOFFxWjBhb2JzQjJYSXhaZwpxRVNUTWNwWW1RS0JnSFN3U3FuZXJhUWd2Z3o3RkxoRmR0VXpIRG9hY3IwbWZHcXo3UjM3Rjk5WERBeVV5K1NGCnl3dnlSZGdrd0dvZGpoRVBxSC90bnlHbjZHUCs2bnh6a25oTDB4dHBwa0NUOGtUNUM0cm1tc1Fya25DaENMLzUKdTM0R3FVYVRhREViOEZMcnovU1ZSUnVRcHZMdkJleTJkQURqa3VWRkgvL2tMb2lnNjRQNml5TG5Bb0dCQUlsRgpnKzJMNzhZWlhWWG9TMVNxYmpVdFFVaWdXWGd2enVuTHBRL1J3YjkrTXNVR21nd1VnNmZ6MnMxZXlHQktNM3hNCmkwVnNJc0tqT2V6QkNQeEQ2b0RUeWs0eXZsYkxFKzdIRTVLY0JKaWtObUZEMFJnSW9udTNlNitqQTBNWHdleUQKUlcvcXZpZmxIUmRJbk5nRHp4UEUzS1ZFTVgyNnpBdlJwR3JNQ1dkQkFvR0FkUTVTdlgrbUFDM2NLcW9ROVphbApsU3FXb3lqZnpQNUVhVlJHOGR0b0x4YnpuUUdUVHZ0SFhjNjUvTXpuWC9MOXFrV0NTNkViNEhINU0zaEZOWTQ2CkxOSXpHUUx6bkUxb2R3djdINUI4YzAvbTNEcktUeGJoOGJZY3JSMUJXNS9uS1pOTlc3azFPNk9qRW96dkFhaksKSlFkcDNLQlU5UzhDbUJqR3JScEoycXc9Ci0tLS0tRU5EIFBSSVZBVEUgS0VZLS0tLS0K"
---
```
A kubernetes `ConfigMap` componenet is generated with the file content.
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
  template:
    .
    .
    .
    spec:
      containers:
      - env:
        - name: "BAL_CONFIG_FILES"
          value: "/home/ballerina/conf/Config.toml:/home/ballerina/secrets/mysql-secrets.toml:"
        .
        .
        .
        volumeMounts:
          - mountPath: "/home/ballerina/secrets/"
            name: "mysql-secrets-volume"
            readOnly: true
          - mountPath: "/home/ballerina/./resource"
            name: "hello-resource-secret0-volume"
            readOnly: true
          - mountPath: "/home/ballerina/conf/"
            name: "sample5-config-map-volume"
            readOnly: false
          - mountPath: "/home/ballerina/./data/data.txt"
            name: "hello-data-txtcfg0-volume"
            readOnly: true
            subPath: "data.txt"
      volumes:
        - name: "mysql-secrets-volume"
          secret:
            secretName: "mysql-secrets"
        - name: "hello-resource-secret0-volume"
          secret:
            secretName: "hello-resource-secret0"
        - configMap:
            name: "sample5-config-map"
          name: "sample5-config-map-volume"
        - configMap:
            name: "hello-data-txtcfg0"
          name: "hello-data-txtcfg0-volume"
```
	Configmap `volumes` are created and are mounted as `volumeMounts` in the container. A additional step is carried out for the `[[cloud.config.files]]` table element which is used to mount configurations. These configuartions are added to `-env` section of YAML file as `BAL_CONFIG_FILES`.


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

