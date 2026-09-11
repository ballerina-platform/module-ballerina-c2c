## Helm Chart Generation With A Sample Ballerina Project

This sample runs a hello world service and packages it as an installable Helm chart. The
service's greeting is a Ballerina `configurable` value, delivered through a `Config.toml` that
c2c mounts as a ConfigMap -- and because that `Config.toml` contains a `{{ .Values.greeting }}`
placeholder, it becomes a live Helm value: you can override it with `helm install`/`helm upgrade
--set greeting=...` for any environment, without rebuilding the image.

### How it works

The build option is set to `helm` in `Ballerina.toml`:

```toml
[build-options]
cloud="helm"
```

`hello_api.bal` imports `ballerina/cloud` purely so the c2c compiler plugin attaches to the
build (`import ballerina/cloud as _;` -- the `_` alias means nothing from the module is actually
referenced):

```ballerina
import ballerina/cloud as _;
import ballerina/http;

configurable string greeting = "Hello, World";

service /helloWorld on new http:Listener(9090) {
    resource function get sayHello() returns string {
        return greeting + " from service helloWorld ! \n";
    }
}
```

`Cloud.toml` points at a local `Config.toml` to mount as a ConfigMap:

```toml
[[cloud.config.files]]
file="./conf/Config.toml"
name="hello-config-map"
```

`conf/Config.toml` is a normal Ballerina configuration file, except the value we want
environment teams to be able to override is written as a Helm template expression:

```toml
[hello.hello]
greeting = "{{ .Values.greeting | default "Hello, World" }}"
```

### Build it

```bash
$ bal build
...
Execute the below command to install the Helm chart:
	helm install <release-name> /path/to/kubernetes-helm-with-ballerina-project/target/helm/hello
```

This produces a standard Helm chart under `target/helm/hello/`:

```bash
$ tree target/helm/hello
target/helm/hello
├── Chart.yaml
├── .helmignore
├── values.yaml
├── files
│   └── hello-config-map
│       └── Config.toml
└── templates
    ├── _helpers.tpl
    ├── configmap-hello-config-map.yaml
    ├── deployment.yaml
    ├── hpa.yaml
    ├── poddisruptionbudget.yaml
    ├── service.yaml
    └── serviceaccount.yaml
```

The Dockerfile and Docker image are generated exactly as they would be for `--cloud=k8s`; check
`target/docker/hello/`.

### Install it

```bash
$ helm install demo target/helm/hello
NAME: demo
STATUS: deployed
```

```bash
$ kubectl get pods
NAME                          READY   STATUS    RESTARTS   AGE
demo-hello-69557fbb75-ld49v   1/1     Running   0          9s

$ kubectl port-forward svc/demo-hello 9090:9090 &
$ curl http://localhost:9090/helloWorld/sayHello
Hello, World from service helloWorld !
```

### Override the greeting per environment

Because `conf/Config.toml`'s `greeting` is a Helm template expression, no image rebuild is
needed to change it -- `helm upgrade` re-renders the ConfigMap and (thanks to a
`checksum/config` pod annotation that hashes the rendered config templates) automatically rolls
the Deployment so the new value takes effect:

```bash
$ helm upgrade demo target/helm/hello --set greeting="Bonjour"
Release "demo" has been upgraded.

$ kubectl rollout status deploy/demo-hello
deployment "demo-hello" successfully rolled out

$ curl http://localhost:9090/helloWorld/sayHello
Bonjour from service helloWorld !
```

The same override can be supplied via `helm install -f values-prod.yaml ...` with a
`greeting: "Bonjour"` entry, so each environment can keep its own values file instead of
touching `Config.toml`.

### Other values worth knowing about

`values.yaml` mirrors what `--cloud=k8s` would have deployed by default -- `replicaCount`,
`image.repository`/`image.tag`, `resources`, and `autoscaling.*` (resolved from
`cloud.deployment.*` in `Cloud.toml`, same as the plain Kubernetes target) are all live Helm
values too:

```bash
$ helm upgrade demo target/helm/hello --set replicaCount=3 --set autoscaling.enabled=false
```

`podSecurityContext`/`securityContext` are present in `values.yaml` but empty by default, since
c2c's own base image does not run as a non-root user out of the box -- set them once your image
does.

Every generated resource that selects pods (Deployment, Service, HPA, PodDisruptionBudget) also
carries a plain `app: hello` label alongside the standard `app.kubernetes.io/*` ones -- override
the name it uses with `nameOverride`, so an externally-authored resource (a hand-written
NetworkPolicy, a ServiceMonitor, another chart) can select this chart's pods by a stable name
that doesn't change with the release name.

A `PodDisruptionBudget` (`podDisruptionBudget.enabled: true`, `maxUnavailable: 1` by default) is
created so a node drain/upgrade can't take down more than one pod at a time once you've scaled
past a single replica -- at the default `replicaCount: 1` it's a no-op (100% may already be
unavailable), so it's safe to leave on regardless of scale:

```bash
$ helm upgrade demo target/helm/hello --set podDisruptionBudget.enabled=false
```

### Uninstall

```bash
$ helm uninstall demo
$ docker rmi anuruddhal/hello-api:helm-sample
```
