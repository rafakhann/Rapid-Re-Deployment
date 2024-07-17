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
import scala.sys.exit
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
    logger.info("\n1)dev\n2)ops\n3)prepod\n4)prod\n5)qa\n6)stage\nSelect anyone Environment From above List :")
    val listOfEnv = Array("dev","ops","prepod","prod","qa","stage")
    val selectEnv1 = readLine().trim
    val selectEnv:Int = selectEnv1.toInt
    // Get the context for the given environment
    val env:String = listOfEnv(selectEnv-1)
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
      val namespacesWithIndex = filteredNamespaces.zipWithIndex.map { case (namespace, index) => (namespace, index + 1) }
      val namespaceString = namespacesWithIndex.map { case (namespace, index) =>
        s"$index) ${namespace.getMetadata.getName}"
      }.mkString("\n")
      val selectNamespace = s"$namespaceString\nSelect anyone namespace from above list:"
      logger.info(s"Available Namespaces for $env environment:\n"+selectNamespace)
      val indexInput = readLine().trim  // Trim whitespace from the input
      // Convert the trimmed input to an integer
      val namespaceIndex: Int = indexInput.toInt
      val selectedNamespace = namespacesWithIndex.find(_._2 == namespaceIndex).map(_._1.getMetadata.getName)
      selectedNamespace match {
        case Some(namespace) =>
          // Initialize the AppsV1Api
          val apiInstance = new AppsV1Api()
          // Get deployments in the selected namespace
          val deploymentList: V1DeploymentList = apiInstance.listNamespacedDeployment(
            namespace, null, null, null, null, null, null, null, null, null, false
          )
          // Zip the deployments with indices starting from 1 and print the
          val deploymentsWithIndex = deploymentList.getItems.asScala.zipWithIndex.map { case (deployment, index) => (deployment, index + 1) }
          val deploymentsString = deploymentsWithIndex.map{ case (deployment, index) =>
            s"$index) ${deployment.getMetadata.getName}"
          }.mkString("\n")
          val selectDeployment = s"$deploymentsString\nSelect anyone Deployment from above list:"
          logger.info(s"Deployments in the namespace $namespace:\n"+selectDeployment)
          val deployindexInput = readLine().trim
          val deploymentIndex: Int = deployindexInput.toInt
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
                    var version = parts(1)
                    logger.info(s"Container Latest Image Version: $version")
                    logger.info(s"Which Version do you want to select?\n1)Latest\n2)Specific")
                    val selectVersion: Int =readLine().toInt
                    selectVersion match {
                      case 2 =>
                        logger.info("Please enter Specific Version.\n")
                        val specificVersion = readLine()
                        version = specificVersion
                      case 1 =>
                        version
                      case _ =>
                        logger.info("You entered wrong choice.")
                        exit(0)
                    }
                    // Fetch build ID from Azure DevOps
                    logger.info("Enter your Personal Access Token:")
                    val personalAccessToken: String = readLine()
                    val buildId = fetchBuildIdFromAzure(version,personalAccessToken)
                    logger.info(s"Build ID: $buildId")
                    // Rerun the deploy stage
                    val rerunResult = rerunDeployStage(buildId,personalAccessToken)
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
  def fetchBuildIdFromAzure(version: String,personalAccessToken:String): String = {
    val organization = "https://dev.azure.com/cnhi"
    val project = "GeoSpatialStorage"

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
      val errorMessage = s"Failed to fetch build ID: ${response.statusCode()}. Response body: ${response.body()}"
      logger.error(errorMessage)
      exit() // Exit the system with a non-zero status code
    }
  }
  def rerunDeployStage(buildId: String,personalAccessToken:String): String = {
    val organization = "https://dev.azure.com/cnhi"
    val project = "GeoSpatialStorage"
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
 
 