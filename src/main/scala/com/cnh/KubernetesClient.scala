package com.cnh


import io.kubernetes.client.openapi.apis.{AppsV1Api, CoreV1Api}
import io.kubernetes.client.openapi.models.{V1Deployment, V1DeploymentList, V1NamespaceList}
import io.kubernetes.client.openapi.{ApiClient, Configuration}
import io.kubernetes.client.util.{Config, KubeConfig}
import org.slf4j.LoggerFactory
import play.api.libs.json._

import java.io.{FileReader, IOException}
import java.net.URI
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest}
import java.util.Base64
import scala.io.StdIn.readLine
import scala.jdk.CollectionConverters._

object KubernetesClient {

  private val logger = LoggerFactory.getLogger(this.getClass)

  def loadKubeConfig(path: String): KubeConfig = {
    val reader = new FileReader(path)
    KubeConfig.loadKubeConfig(reader)
  }

  def createApiClient(context: String, kubeConfig: KubeConfig): ApiClient = {
    kubeConfig.setContext(context)
    val client = Config.fromConfig(kubeConfig)
    Configuration.setDefaultApiClient(client)
    client
  }

  def main(args: Array[String]): Unit = {
    val kubeConfigPath = System.getProperty("user.home") + "/.kube/config"
    val kubeConfig = loadKubeConfig(kubeConfigPath)

    // Define contexts and corresponding cluster names
    val clusterContexts = Map(
      "dev" -> "eugssaksd011",
      "qa" -> "eugssaksd011",
      "stage" -> "eugssaksi011",
      "preprod" -> "eugssaksi011",
      "prod" -> "eusspaks010",
      "ops" -> "eussmonpaks010"
    )

    // Take user input for environment
    logger.info("Enter the environment (dev, qa, stage, preprod, prod, ops): ")
    val env = readLine().trim.toLowerCase

    // Get the context for the given environment
    clusterContexts.get(env) match {
      case Some(context) =>
        val client = createApiClient(context, kubeConfig)
        logger.info(s"Switched to context for $env environment.")
      case None =>
        logger.error(s"Invalid environment: $env")
        return
    }

    try {
      // Initialize the CoreV1Api
      val coreApiInstance = new CoreV1Api()

      // Get the list of namespaces
      val namespaceList: V1NamespaceList = coreApiInstance.listNamespace(null, null, null, null, null, null, null, null, null, false)

      // Filter namespaces based on the selected environment
      val filteredNamespaces = namespaceList.getItems.asScala.filter { namespace =>
        namespace.getMetadata.getName.contains(s"-$env")
      }

      // Zip the filtered namespaces with indices starting from 1 and print them
      logger.info(s"Available Namespaces for $env environment:")
      val namespacesWithIndex = filteredNamespaces.zipWithIndex.map { case (namespace, index) => (namespace, index + 1) }
      namespacesWithIndex.foreach { case (namespace, index) =>
        logger.info(s"$index: ${namespace.getMetadata.getName}")
      }

      // Get the user to select a namespace by index
      logger.info("Enter the index of the namespace from the above list:")
      val namespaceIndex: Int = readLine().toInt

      val selectedNamespace = namespacesWithIndex.find(_._2 == namespaceIndex).map(_._1.getMetadata.getName)
      selectedNamespace match {
        case Some(namespace) =>
          // Initialize the AppsV1Api
          val apiInstance = new AppsV1Api()

          // Get deployments in the selected namespace
          val deploymentList: V1DeploymentList = apiInstance.listNamespacedDeployment(
            namespace, null, null, null, null, null, null, null, null, null, false
          )

          // Zip the deployments with indices starting from 1 and print them
          logger.info(s"Deployments in the namespace $namespace:")
          val deploymentsWithIndex = deploymentList.getItems.asScala.zipWithIndex.map { case (deployment, index) => (deployment, index + 1) }
          deploymentsWithIndex.foreach { case (deployment, index) =>
            logger.info(s"$index: ${deployment.getMetadata.getName}")
          }

          // Get the user to select a deployment by index
          logger.info("Enter the index of the deployment from the above list:")
          val deploymentIndex: Int = readLine().toInt

          val selectedDeployment = deploymentsWithIndex.find(_._2 == deploymentIndex).map(_._1.getMetadata.getName)
          selectedDeployment match {
            case Some(deploymentName) =>
              val deployment: V1Deployment = apiInstance.readNamespacedDeployment(deploymentName, namespace, null)

              // Parse the JSON to get the build version (image name)
              if (deployment != null) {
                deployment.getSpec.getTemplate.getSpec.getContainers.asScala.foreach { container =>
                  val image = container.getImage
                  // Split the image string by colon to get the version part
                  val parts = image.split(":")
                  if (parts.length > 1) {
                    val version = parts(1)
                    logger.info(s"Container Image Version: $version")
                    // Fetch build ID from Azure DevOps
                    val buildId = fetchBuildIdFromAzure("20240627.1")
                    logger.info(s"Build ID: $buildId")
                    // Rerun the deploy stage
                    val rerunResult = rerunDeployStage(buildId)
                    logger.info(s"Rerun Deploy Stage Result: $rerunResult")
                  } else {
                    logger.warn("No version found in image tag.")
                  }
                }
              } else {
                logger.error("Deployment not found")
              }
            case None =>
              logger.error("Invalid deployment index")
          }
        case None =>
          logger.error("Invalid namespace index")
      }
    } catch {
      case ex: IOException =>
        logger.error(s"Error loading kubeconfig: ${ex.getMessage}")
    }
  }

  def fetchBuildIdFromAzure(version: String): String = {
    val organization = "https://dev.azure.com/cnhi"
    val project = "GeoSpatialStorage"
    logger.info("Enter your Personal Access Token:")
    val personalAccessToken: String = readLine()

    val url = s"$organization/$project/_apis/build/builds?api-version=6.0&queryOrder=finishTimeDescending&buildNumber=$version"

    val client = HttpClient.newHttpClient()
    val request = HttpRequest.newBuilder()
      .uri(new URI(url))
      .header("Authorization", s"Basic ${Base64.getEncoder.encodeToString(s":$personalAccessToken".getBytes)}")
      .header("Content-Type", "application/json")
      .build()

    val response = client.send(request, BodyHandlers.ofString())

    if (response.statusCode() == 200) {
      val json = Json.parse(response.body())
      (json \ "value").as[JsArray].value.headOption.flatMap(obj => (obj \ "id").asOpt[Int]).map(_.toString).getOrElse("Build ID not found")
    } else {
      s"Failed to fetch build ID: ${response.statusCode()}. Response body: ${response.body()}"
    }
  }

  def rerunDeployStage(buildId: String): String = {
    val organization = "https://dev.azure.com/cnhi"
    val project = "GeoSpatialStorage"
    logger.info("Enter your Personal Access Token:")
    val personalAccessToken: String = readLine()

    val url = s"$organization/$project/_apis/build/builds/$buildId/stages/Deploy?api-version=6.0-preview.1"

    val requestBody = Json.obj(
      "state" -> "retry",
      "forceRetryAllJobs" -> false,
      "retryDependencies" -> false
    ).toString()

    val client = HttpClient.newHttpClient()
    val request = HttpRequest.newBuilder()
      .uri(new URI(url))
      .header("Authorization", s"Basic ${Base64.getEncoder.encodeToString(s":$personalAccessToken".getBytes)}")
      .header("Content-Type", "application/json")
      .method("PATCH", BodyPublishers.ofString(requestBody))
      .build()

    val response = client.send(request, BodyHandlers.ofString())

    if (response.statusCode() == 200 || response.statusCode() == 204) {
      "Deploy stage rerun successfully"
    } else {
      s"Failed to rerun deploy stage: ${response.statusCode()}. Response body: ${response.body()}"
    }
  }
}

