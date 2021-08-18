## Ballerina Code2Cloud samples

### Prerequisites
 1. Install a recent version of Docker for Mac/Windows and [enable Kubernetes](https://docs.docker.com/docker-for-mac/#kubernetes) OR
    [Minikube](https://kubernetes.io/docs/tasks/tools/install-minikube/) is installed and running.
 2. Mini-kube users should configure env vars with valid values: 
    ```bash
        export DOCKER_HOST="tcp://192.168.99.100:2376" 
        export DOCKER_CERT_PATH="/Users/anuruddha/.minikube/certs"
    ```
 2. Docker for windows users should enable remote access to the API.
 (If DOCKER_HOST and DOCKER_CERT_PATH are exported as environment variables, priority will be given to environment variables.)
 ![alt tag](./images/docker_for_windows.png)
  

## Try Code2Cloud samples:

1. [Sample1: Kubernetes Hello World](sample1/)
2. [Sample2: Hello World with module](sample2/)
3. [Sample3: Scaling Sample](sample3/)
4. [Sample4: Sample with CronJob](sample4/)
5. [Sample5: Mount config map volumes to deployment](sample5/)
6. [Sample6: Readiness and Liveness Probes](sample6/)
7. [Sample7: Multi module Ballerina project](sample7/)
8. [Sample8: Copy Files to Docker image](sample8)
9. [Sample9: Sample with keystore trust-store](sample9)
10. [Sample10: Sample with listener](sample10)
11. [Sample11: Sample with volume mounts](sample11)
12. [Sample12: Sample with secret mounts](sample12)