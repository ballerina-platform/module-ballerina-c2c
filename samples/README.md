## Ballerina Kubernetes samples


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
  

## Try kubernetes annotation samples:

1. [Sample1: Kubernetes Hello World](sample1/)
1. [Sample2: Kubernetes Hello World with livenessProbe and hostname mapping](sample2/)
1. [Sample3: Ballerina program with multiple services running in multiple ports](sample3/)
1. [Sample4: Kubernetes Hello World Secured](sample4/)
1. [Sample5: Ballerina service with http and https endpoint](sample5/)
1. [Sample6: Kubernetes Hello World in Google Cloud Environment](sample6/)
1. [Sample7: Mount secret volumes to deployment](sample7/)
1. [Sample8: Mount config map volumes to deployment](sample8/)
1. [Sample9: Mount PersistentVolumeClaim to deployment](sample9/)
1. [Sample10: Ballerina module with kubernetes annotations](sample10/)
1. [Sample11: Kubernetes Hello World with Ballerina Function](sample11/)
1. [Sample12: Copy External files to Docker Image](sample12/) 
1. [Sample13: Ballerina modules with dependencies](sample13/) 
1. [Sample14: Deploy Ballerina service in a namespace](sample14/)
1. [Sample15: Resource quotas for namespaces](sample15/) 
1. [Sample16: Istio Gateway and Virtual Service generation](sample16/)  (Remove nginx setting up)
1. [Sample17: OpenShift Build Configs and Routes](sample17/)  (Remove nginx setting up)