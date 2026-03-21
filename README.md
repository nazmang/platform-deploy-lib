# Platform Deploy Library

A Jenkins shared library for deploying Kubernetes workloads (Helm charts, raw manifests, Kustomize) to multiple environments (e.g. cloud, on-prem). Supports environment-specific config, optional steps per environment, and change-based or full deploy.

## Features

- **Multi-environment**: Deploy to several clusters in parallel or to a single target (cloud, on-prem, etc.).
- **Declarative pipeline**: Each project is driven by a `deploy.yaml` in its directory.
- **Dependencies**: Optional `depends` list in `deploy.yaml` to deploy dependent projects first (with cycle detection).
- **Step types**: Helm, manifest (kubectl apply), Kustomize, wait (e.g. for CRDs), and shell (run arbitrary commands).
- **Environment-specific config**: Different Helm values or settings per environment.
- **Conditional steps**: Run steps only in selected environments via `onlyEnvs`.
- **Change detection**: Deploy only projects whose files changed (when not using `FORCE_DEPLOY` or `PROJECT`).

## Requirements

- Jenkins with Pipeline support
- Jenkins plugins:
  - Pipeline, Pipeline: Groovy, (optional) Pipeline: Multibranch
  - **[Helm Tool Plugin](https://github.com/nazmang/helm-tool-plugin)** — used by the `helm` step to install Helm charts (Global Tool for Helm binary + pipeline `helm` step)
- A **deploy** container (or agent) with `kubectl` and `helm` available (or use the Helm installation provided by the Helm Tool Plugin)
- Kubernetes kubeconfig credentials stored in Jenkins (e.g. `kubeconfig-hetzner`, `kubeconfig-onprem`)
- For change detection: pipeline must have access to `currentBuild.changeSets` (e.g. polling or SCM trigger)

## Installation

1. Install the [Helm Tool Plugin](https://github.com/nazmang/helm-tool-plugin) (when using the `helm` step): **Manage Jenkins → Plugins**, then in **Global Tool Configuration** add a Helm installation (e.g. install from URL or set `HELM_HOME`).
2. Clone or add this repository as a Jenkins **Shared Library** (e.g. named `platform-deploy-lib`).
3. Configure the library in Jenkins: **Manage Jenkins → Configure System → Global Pipeline Libraries**:
   - Name: `platform-deploy-lib`
   - **Default version:** set a branch or tag (e.g. `main`) so the library can be loaded; otherwise you must specify a version in the Jenkinsfile (see below).
   - Load implicitly: optional (you can also use `@Library('platform-deploy-lib@main') _` in the Jenkinsfile)
4. Ensure your Jenkinsfile runs inside a context where the **deploy** container and credentials are available (see [ClusterManager](#cluster-configuration) and example below).

## Repository layout

Your repo is expected to contain one or more **projects**, each in its own directory. Each project has a `deploy.yaml` and the files it references (values, manifests, etc.):

```
repository-root/
├── Jenkinsfile
├── project-a/
│   ├── deploy.yaml
│   ├── values-common.yaml
│   ├── environments/
│   │   ├── cloud/
│   │   │   └── values.yaml
│   │   └── onprem/
│   │       └── values.yaml
│   └── manifest.yaml
└── project-b/
    ├── deploy.yaml
    └── ...
```

- **deploy.yaml**: Required. Defines the list of `steps` and their config (see below).
- All paths in `deploy.yaml` (e.g. `file`, `values`, `overlay`) are **relative to the project directory**.

---

## deploy.yaml reference

Top-level keys:

| Key       | Required | Description |
|--------|----------|-------------|
| `steps` | Yes     | List of step objects. Executed in order. |
| `depends` | No    | List of project directory names to deploy **before** this project. Dependencies are deployed first (with their own dependencies resolved recursively); each project is deployed only once per cluster per run. Circular dependencies cause the pipeline to fail. |

### Step structure

Each step is an object with:

| Key            | Required | Description |
|----------------|----------|-------------|
| `type`         | Yes      | One of: `helm`, `manifest`, `kustomize`, `wait`, `decrypt-sops`, `shell`. |
| `config`       | Conditional | Single config used for all environments. Use when the step is the same everywhere. |
| `environments` | Conditional | Map of environment name → `{ config: { ... } }`. Use when config differs per environment (e.g. different Helm values per cluster). Exactly one of `config` or `environments` must be provided for the environments where the step runs. |
| `onlyEnvs`     | No       | List of environment names. If set, the step runs **only** for those environments; otherwise it runs for the current environment (subject to having a valid `config`). |

- If the step has **`environments`**, the config for the current cluster is `environments[<cluster>].config`. The cluster name comes from the pipeline (e.g. `cloud`, `onprem`).
- If the step has **`config`** (no `environments`), that config is used for every cluster.
- If **`onlyEnvs`** is set and the current cluster is not in the list, the step is skipped.

---

## Step types

### helm

Deploys a Helm chart using the [Helm Tool Plugin](https://github.com/nazmang/helm-tool-plugin) for Jenkins (Global Tool + pipeline `helm` step). The plugin handles repository setup and `helm install` with the options you pass from `deploy.yaml`. The library maps your config to the plugin’s **chartPath**, **repositories**, **releaseName**, **valuesFile** (single file), **additionalArgs** (namespace, version, and multiple `-f` when you list more than one values file), and optional **helmInstallation**.

**Config (under `config` or `environments.<env>.config`):**

| Key               | Required | Description |
|-------------------|----------|-------------|
| `repoName`        | Yes      | Helm repo name. |
| `repoUrl`         | Yes      | Helm repo URL. |
| `chartName`       | Yes      | Chart name (e.g. `repoName/chart-name` or `repo/chart`). |
| `releaseName`     | Yes      | Helm release name. |
| `namespace`       | Yes      | Target namespace. |
| `version`         | No       | Chart version. |
| `values`          | No       | List of values files (paths relative to project dir). One file is passed as the plugin’s `valuesFile`; multiple are passed as `-f` in additional arguments. |
| `helmInstallation`| No       | Name of the Helm Global Tool installation to use (e.g. `helm-3.14`). Omit to use the plugin default. |

**Example (environment-specific):**

```yaml
- type: helm
  environments:
    cloud:
      config:
        repoName: metallb
        repoUrl: https://metallb.github.io/metallb
        chartName: metallb/metallb
        version: 0.14.8
        releaseName: metallb
        namespace: metallb-system
        values:
          - values-common.yaml
          - environments/cloud/values.yaml
    onprem:
      config:
        repoName: metallb
        repoUrl: https://metallb.github.io/metallb
        chartName: metallb/metallb
        version: 0.14.8
        releaseName: metallb
        namespace: metallb-system
        values:
          - values-common.yaml
          - environments/onprem/values.yaml
```

---

### manifest

Applies a Kubernetes manifest with `kubectl apply -f`.

**Config:**

| Key         | Required | Description |
|-------------|----------|-------------|
| `file`      | Yes      | Path to YAML manifest (relative to project dir). |
| `namespace` | No       | If set, passed as `-n <namespace>` to `kubectl apply`. |

**Example:**

```yaml
- type: manifest
  config:
    file: metallb-config.yaml
    namespace: metallb-system
```

**Example (only in one environment):**

```yaml
- type: manifest
  onlyEnvs:
    - cloud
  config:
    file: something.yaml
```

---

### kustomize

Applies a Kustomize overlay with `kubectl apply -k`.

**Config:**

| Key       | Required | Description |
|-----------|----------|-------------|
| `overlay` | Yes      | Path to overlay directory (relative to project dir). |

**Example:**

```yaml
- type: kustomize
  config:
    overlay: overlays/production
```

---

### wait

Waits for a resource to be ready (e.g. CRD established) before continuing.

**Config:**

| Key        | Required | Description |
|------------|----------|-------------|
| `kind`     | Yes      | Resource kind (e.g. `CustomResourceDefinition`). |
| `name`     | Yes      | Resource name (e.g. CRD name like `ipaddresspools.metallb.io`). |
| `timeout`  | No       | Timeout in seconds (default: 120). |
| `namespace`| No       | For non-CRD resources, namespace for `kubectl wait`. |

- For **CustomResourceDefinition**, the library runs:  
  `kubectl wait --for=condition=established crd/<name> --timeout=<timeout>s`
- For other kinds, it uses:  
  `kubectl wait --for=condition=available <kind>/<name> --timeout=<timeout>s` (and `-n <namespace>` if set).

**Example:**

```yaml
- type: wait
  config:
    kind: CustomResourceDefinition
    name: ipaddresspools.metallb.io
    timeout: 60
```

---

### decrypt-sops

Decrypts one or more files in place using [SOPS](https://github.com/getsops/sops) (`sops --decrypt --in-place`). Place this step **before** `helm`, `manifest`, or `kustomize` steps that consume the cleartext files. The Jenkins agent must have `sops` on `PATH` and credentials configured for your backend (e.g. KMS, age, Vault).

**Config:**

| Key          | Required | Description |
|--------------|----------|-------------|
| `files`      | Yes      | List of file paths relative to the project directory. Each file is decrypted in place. |
| `extraArgs`  | No       | Extra arguments passed to `sops` after `--decrypt --in-place` (string or list of strings). |

**Example:**

```yaml
- type: decrypt-sops
  config:
    files:
      - environments/cloud/values.secret.yaml
      - secrets/app.yaml
```

---

### shell

Runs one or more shell commands in the project directory (same as other steps). Use for pre/post hooks, custom scripts, or any command that does not fit helm/manifest/kustomize/wait/decrypt-sops.

**Config:**

| Key         | Required | Description |
|-------------|----------|-------------|
| `command`   | Conditional | Single command string. Use when you have one command. |
| `commands`  | Conditional | List of command strings. Each is run in order; the first failure fails the step. Exactly one of `command` or `commands` must be provided. |

**Example (single command):**

```yaml
- type: shell
  config:
    command: "./scripts/pre-deploy.sh"
```

**Example (multiple commands):**

```yaml
- type: shell
  config:
    commands:
      - "echo Building assets..."
      - "./scripts/build.sh"
      - "kubectl get nodes"
```

---

## Pipeline parameters

The library expects these parameters (define them in your Jenkins job or Jenkinsfile):

| Parameter       | Description |
|-----------------|-------------|
| `CLUSTER`       | Target cluster name (e.g. `cloud`, `onprem`). Used when deploying to a single cluster. When `DEPLOY_TARGET=all`, the library uses the current parallel branch name instead. |
| `DEPLOY_TARGET` | Set to `"all"` to deploy to all configured clusters in parallel. Otherwise deployment uses `CLUSTER` only. |
| `PROJECT`       | Optional. If set, only this project (directory name) is deployed. Can also be set at runtime via `env.PROJECT_SELECTED` (e.g. from an input step). |
| `FORCE_DEPLOY`  | Optional. If set, all projects (all directories with a `deploy.yaml`) are deployed, ignoring change detection. |

**Project selection (in order):**

1. If `env.PROJECT_SELECTED` or `params.PROJECT` is non-empty → deploy only that project.
2. Else if `params.FORCE_DEPLOY` is set → deploy all projects.
3. Else → deploy only projects with changes (from `currentBuild.changeSets`).

**Autofill PROJECT from repo:** After checkout, you can show a dropdown of project dirs (those with `deploy.yaml`) using the library step `platformProjectChoices()`. It returns a list of directory names. Use it in an `input` step and set `env.PROJECT_SELECTED` to the result; `platformDeploy()` will use it. See `Jenkinsfile.example` for a full pipeline with `AUTOFILL_PROJECT` and the input stage.

---

## Cluster configuration

Clusters are defined in `ClusterManager.groovy`:

- **cloud**: credential `kubeconfig-hetzner`, critical (pipeline fails if this cluster fails).
- **onprem**: credential `kubeconfig-onprem`, non-critical (failure is logged and skipped).

The pipeline runs each target in a **deploy** container and injects the corresponding kubeconfig via `KUBECONFIG`. The library copies the credential into the workspace and sets `KUBECONFIG` to that path inside the container so that `kubectl` and `helm` (and any plugin that runs them) use the credential’s identity instead of the pod’s in-cluster ServiceAccount (e.g. `system:serviceaccount:jenkins:default`). To add or change clusters, edit the `clusterMap` in `src/com/nazmang/platform/ClusterManager.groovy` and ensure the credential IDs exist in Jenkins.

**Pod ServiceAccount (same-cluster deploys):** If you deploy to the same cluster where Jenkins runs, you can avoid “forbidden” errors by giving the deploy pod a dedicated ServiceAccount with RBAC. In your `podTemplate`, set `serviceAccount: 'jenkins'` (see `Jenkinsfile.example`). Create a ServiceAccount `jenkins` in the Jenkins namespace and grant it the roles needed to create resources in target namespaces (e.g. `nfs-provisioner`). Then the pod runs as `system:serviceaccount:<namespace>:jenkins` and `kubectl`/`helm` use that identity when no `KUBECONFIG` is set or when the kubeconfig uses the in-cluster config. For **different** clusters you still need a kubeconfig credential.

---

## Example Jenkinsfile

Specify the library **version** (branch or tag) when loading — either in the Jenkinsfile or as the library’s default in Jenkins. Example with branch `main`:

```groovy
@Library('platform-deploy-lib@main') _

pipeline {
    agent none
    parameters {
        choice(name: 'DEPLOY_TARGET', choices: ['single', 'all'], description: 'Deploy to one cluster or all')
        choice(name: 'CLUSTER', choices: ['cloud', 'onprem'], description: 'Target cluster (when DEPLOY_TARGET=single)')
        string(name: 'PROJECT', defaultValue: '', description: 'Optional: deploy only this project (directory name)')
        booleanParam(name: 'FORCE_DEPLOY', defaultValue: false, description: 'Deploy all projects, ignore changes')
    }
    stages {
        stage('Deploy') {
            steps {
                platformDeploy()
            }
        }
    }
}
```

- Use `@Library('platform-deploy-lib@main')` for branch `main`, or `@Library('platform-deploy-lib@v1.0.0')` for a tag. If a **Default version** is set in the library configuration, `@Library('platform-deploy-lib')` (no version) will work.
- If you see **"No version specified for library platform-deploy-lib"**, add a version: `@Library('platform-deploy-lib@main')` or set **Default version** in **Manage Jenkins → Global Pipeline Libraries** for this library.

If your pipeline runs on an agent that already has a `deploy` container and credentials, no extra stage is needed. Otherwise, ensure the job runs in a context where `ClusterManager` can use `container('deploy')` and `withCredentials` as in the library.

---

## Full deploy.yaml example

**With dependencies** (e.g. `project-b` deploys after `project-a`):

```yaml
# project-b/deploy.yaml
depends:
  - project-a
steps:
  - type: helm
    ...
```

**Full example with steps:**

```yaml
steps:
  - type: helm
    environments:
      cloud:
        config:
          repoName: metallb
          repoUrl: https://metallb.github.io/metallb
          chartName: metallb/metallb
          version: 0.14.8
          releaseName: metallb
          namespace: metallb-system
          values:
            - values-common.yaml
            - environments/cloud/values.yaml
      onprem:
        config:
          repoName: metallb
          repoUrl: https://metallb.github.io/metallb
          chartName: metallb/metallb
          version: 0.14.8
          releaseName: metallb
          namespace: metallb-system
          values:
            - values-common.yaml
            - environments/onprem/values.yaml

  - type: wait
    config:
      kind: CustomResourceDefinition
      name: ipaddresspools.metallb.io
      timeout: 60

  - type: manifest
    config:
      file: metallb-config.yaml
      namespace: metallb-system

  - type: manifest
    onlyEnvs:
      - cloud
    config:
      file: cloud-only.yaml
```

---

## Library layout

```
platform-deploy-lib/
├── README.md
├── src/com/nazmang/platform/
│   ├── ClusterManager.groovy   # Multi-cluster parallel execution, credentials
│   ├── ProjectLoader.groovy    # Load deploy.yaml, discover project dirs (findProjectDirsWithDeployYaml)
│   └── ChangeDetector.groovy  # Changed projects from SCM change sets
└── vars/
    ├── platformDeploy.groovy   # Entry point: project selection + executeOnTargets
    ├── platformProjectChoices.groovy  # Returns list of project dirs with deploy.yaml (for input/autofill)
    ├── deployProject.groovy   # Load spec, run steps (helm/manifest/kustomize/wait/decrypt-sops/shell)
    ├── helmDeploy.groovy
    ├── manifestDeploy.groovy
    ├── kustomizeDeploy.groovy
    ├── waitDeploy.groovy
    ├── decryptSopsDeploy.groovy
    └── shellDeploy.groovy
```
