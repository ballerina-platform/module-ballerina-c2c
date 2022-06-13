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
  

