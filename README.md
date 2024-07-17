This Scala application, Rapid Re Deployment, interacts with Kubernetes clusters to manage deployments, fetch build information from Azure DevOps, and rerun the deployment stage.

## Prerequisites

- Scala
- sbt (Scala Build Tool)
- Kubernetes client configuration file (`~/.kube/config`)
- Azure DevOps Personal Access Token (PAT)

## How to generate Personal Access Token (PAT):

To create a Personal Access Token, login to Azure DevOps in this organization. On the right top corner click on the user icon.

Select "Personal access tokens"

Then Click on "New Token". You will be asked to provide a name for the token, the expiration date, Organization Access, and the scope you want to apply, either all scopes or specify access for Work items, code (git repository), Build, Release, test and packaging.

Defining scope is important for your application; it defines how the application associated with the token will interact with Azure DevOps Services. Unless you are testing the API, never choose full access, review your needs and select the appropriate scopes.

After pushing the "Create" button, the token is displayed. Make sure to save the token securely, there is no way to retrieve it later.

## Usage

Follow the prompts to select the environment, namespace, and deployment. The application will fetch and display the container image version, retrieve the corresponding build ID from Azure DevOps, and rerun the deploy stage.

## Features

- **Environment Selection**: Choose from predefined environments (`dev`, `qa`, `stage`, `preprod`, `prod`, `ops`).
- **Namespace Filtering**: Lists namespaces filtered by the selected environment.
- **Deployment Management**: Lists deployments in the selected namespace and allows interaction with specific deployments.
- **Azure DevOps Integration**: Fetches build IDs and reruns deployment stages using Azure DevOps REST API.

## Configuration

Ensure you have the correct Kubernetes configuration file (`~/.kube/config`) with contexts for each environment. The predefined environments and their corresponding clusters are:

- `dev` -> `eugssaksd011`
- `qa` -> `eugssaksd011`
- `stage` -> `eugssaksi011`
- `preprod` -> `eugssaksi011`
- `prod` -> `eusspaks010`
- `ops` -> `eussmonpaks010`

## Logging

The application uses `SLF4J` for logging. Logs are printed to the console, providing information about the current operation and any errors encountered.
