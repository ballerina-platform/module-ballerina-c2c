# Change Log
This file contains all the notable changes done to the Ballerina Cloud package through the releases.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.0.0] - Unreleased

### Improvements

- Support directories to be mounted as Configmaps/Secrets [778](https://github.com/ballerina-platform/module-ballerina-c2c/issues/778)
- Add a `helm` cloud target (`bal build --cloud=helm`) that packages the deployment as an installable, values-driven Helm chart. Config files mounted via `cloud.config.files` are rendered through Helm's `tpl` so `{{ .Values.x }}` placeholders in your own `Config.toml` become live Helm values, overridable per environment without rebuilding the image. Generated resources also carry a plain `app: <name>` selector label (overridable via `nameOverride`) for compatibility with externally-authored selectors, and a `PodDisruptionBudget` (`podDisruptionBudget.enabled`, on by default) protects scaled-up deployments from voluntary disruption.

### Breaking Changes
- Windows containers are not supported anymore. [773](https://github.com/ballerina-platform/module-ballerina-c2c/issues/773)
- SSL configurations are not automatically retrieved from the code anymore. You need to explicitly mark them as secrets in Cloud.toml. [782](https://github.com/ballerina-platform/module-ballerina-c2c/issues/782)
    ```toml
    [[cloud.secret.files]]
    file="resource."
    mount_dir="./resource"
    ```
- Entrypoints are used instead of CMD to run the ballerina application in the dockerfile. [771](https://github.com/ballerina-platform/module-ballerina-c2c/issues/771)
- [[cloud.secret.files]] and [[cloud.config.maps]] changes. [784](https://github.com/ballerina-platform/module-ballerina-c2c/issues/784)
  - `mount_path` is renamed to `mount_dir`. `mount_dir` is the directory where the config maps and secrets are mounted.
  - suffix is added to each config map and secret file to avoid conflicts.
  - subpaths are used underneath to better support multiple files in the same directory.
