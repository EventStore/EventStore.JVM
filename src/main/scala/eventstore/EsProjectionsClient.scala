package eventstore

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ Authorization, BasicHttpCredentials }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.ThrottleMode.Shaping
import akka.stream.scaladsl.{ Flow, Sink, Source }
import eventstore.EsProjectionsClient.{ Continuous, OneTime, ProjectionMode, Transient }
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

/**
 * The API miss documentation so I used the C# client as a starting point
 * See : https://github.com/EventStore/EventStore/blob/release-v3.9.0/src/EventStore.ClientAPI/Projections/ProjectionsClient.cs
 */
protected[this] trait EventStoreProjectionsUrls {
  def createProjectionUrl(name: String, mode: ProjectionMode = Continuous, allowEmit: Boolean = true): String = {
    val emit = if (allowEmit) "emit=1&checkpoints=yes" else "emit=0"
    val projectionModeStr = mode match {
      case OneTime    => "onetime"
      case Transient  => "transient"
      case Continuous => "continuous"
    }
    s"/projections/$projectionModeStr?name=$name&type=JS&$emit"
  }

  def projectionBaseUrl(name: String): String = s"/projection/$name"

  def fetchProjectionStateUrl(name: String, partition: Option[String]): String =
    s"${projectionBaseUrl(name)}/state" + partition.fold("")(partition => s"?partition=$partition")

  def fetchProjectionResultUrl(name: String, partition: Option[String]): String =
    s"${projectionBaseUrl(name)}/result" + partition.fold("")(partition => s"?partition=$partition")

  def projectionCommandUrl(name: String, command: String): String =
    s"${projectionBaseUrl(name)}/command/$command"
}

object EsProjectionsClient {
  sealed trait ProjectionMode
  object Continuous extends ProjectionMode
  object OneTime extends ProjectionMode
  object Transient extends ProjectionMode

  sealed trait ProjectionStatus
  object Running extends ProjectionStatus
  object Faulted extends ProjectionStatus
  object Completed extends ProjectionStatus
  object Stopped extends ProjectionStatus
  object Absent extends ProjectionStatus
  final case class Other(status: String) extends ProjectionStatus

  sealed trait ProjectionCreationResult
  object ProjectionCreated extends ProjectionCreationResult
  object ProjectionAlreadyExist extends ProjectionCreationResult

  sealed trait ProjectionDeleteResult
  object ProjectionDeleted extends ProjectionDeleteResult
  case class UnableToDeleteProjection(reason: String) extends ProjectionDeleteResult

}

/**
 * A client allowing to create, get the status and delete an existing projection.
 */
class EsProjectionsClient(settings: Settings = Settings.Default, system: ActorSystem)(implicit materializer: Materializer) extends EventStoreProjectionsUrls {

  import materializer.executionContext
  import EsProjectionsClient._

  private val connection: Flow[HttpRequest, Try[HttpResponse], NotUsed] = {
    val uri = Uri(settings.http.url)
    val httpExt = Http(system)

    val connectionPool = if (settings.http.protocol == "http") {
      httpExt.cachedHostConnectionPool[Unit](uri.authority.host.address(), uri.authority.port)
    } else {
      httpExt.cachedHostConnectionPoolHttps[Unit](uri.authority.host.address(), uri.authority.port)
    }

    Flow[HttpRequest]
      .map(req => (req, ()))
      .via(connectionPool)
      .map { case (resp, _) => resp }
  }

  private val authorization: Option[Authorization] = settings.defaultCredentials.map(credentials => Authorization(BasicHttpCredentials(credentials.login, credentials.password)))

  private val defaultHeaders = authorization.toList

  /**
   * Create the projection with the specified name and code
   * @param name the name of the projection to create
   * @param javascriptCode the javascript code for the projection
   * @param mode the projection's mode (Either OneTime, Continuous or Transient)
   * @param allowEmit
   * @return
   */
  def createProjection(name: String, javascriptCode: String, mode: ProjectionMode = Continuous, allowEmit: Boolean = true): Future[ProjectionCreationResult] = {
    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = Uri(createProjectionUrl(name, mode, allowEmit)),
      headers = defaultHeaders,
      entity = HttpEntity(ContentTypes.`application/json`, javascriptCode))

    singleRequestWithErrorHandling(request)
      .map {
        case response if response.status == StatusCodes.Created => ProjectionCreated
        case response if response.status == StatusCodes.Conflict => ProjectionAlreadyExist
        case response => throw new ProjectionException(s"Received unexpected reponse $response")
      }
      .runWith(Sink.head)
  }

  /**
   * Fetch the status for the specified projection.
   * @param name the name of the projection
   * @return Absent if the projection doesn't exist. Otherwise either Running/Completed/Stopped/Faulted or Other.
   */
  def fetchProjectionStatus(name: String): Future[ProjectionStatus] = {
    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = Uri(projectionBaseUrl(name)),
      headers = defaultHeaders)

    singleRequestWithErrorHandling(request)
      .mapAsync(1) {
        case response if response.status == StatusCodes.NotFound => Future.successful(Absent)
        case response => Unmarshal(response).to[String].map { rawJson =>
          val json = Json.parse(rawJson)
          (json \ "status").as[String] match {
            case status if status.startsWith("Running")   => Running
            case status if status.startsWith("Completed") => Completed
            case status if status.startsWith("Stopped")   => Stopped
            case status if status.startsWith("Faulted")   => Faulted
            case other                                    => Other(other)
          }
        }
      }
      .runWith(Sink.head)
  }

  private[this] def fetchProjectionData(request: HttpRequest): Future[Option[String]] = {
    singleRequestWithErrorHandling(request)
      .mapAsync(1) {
        case response if response.status == StatusCodes.NotFound => Future.successful(None)
        case response => Unmarshal(response).to[String].map(Some(_))
      }
      .runWith(Sink.head)
  }

  /**
   * Fetch the projection's state
   * @param name the name of the projection
   * @param partition the name of the partition
   * @return a String that should be either empty or a valid json object with the current state.
   */
  def fetchProjectionState(name: String, partition: Option[String] = None): Future[Option[String]] = {
    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = Uri(fetchProjectionStateUrl(name, partition)),
      headers = defaultHeaders)

    fetchProjectionData(request)
  }

  /**
   * Fetch the projection's result.
   * It only works for OneTime projections as Continuous one dont provide a result.
   * @param name the name of the projection
   * @param partition the name of the partition
   * @return a String that should be either empty or a valid json object with the projection's result.
   */
  def fetchProjectionResult(name: String, partition: Option[String] = None): Future[Option[String]] = {
    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = Uri(fetchProjectionResultUrl(name, partition)),
      headers = defaultHeaders)

    fetchProjectionData(request)
  }

  private[this] def executeCommand(name: String, command: String): Future[Unit] = {
    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = Uri(projectionCommandUrl(name, command)),
      headers = defaultHeaders,
      entity = HttpEntity(ContentTypes.`application/json`, "{}"))

    singleRequestWithErrorHandling(request)
      .map {
        case response if response.status == StatusCodes.NotFound => ()
        case response if response.status == StatusCodes.OK => ()
        case response => throw new ProjectionException(s"Received unexpected reponse $response")
      }
      .runWith(Sink.head)
  }

  /**
   * Blocks for at most #waitDuration for the projection #name to change to the desired status.
   * @param name the name of the projection to wait on
   * @param expectedStatus the desired status
   * @param waitDuration the maximum wait duration
   * @return true is the expectedStatus was reached, false otherwise
   */
  def waitForProjectionStatus(name: String, expectedStatus: ProjectionStatus, waitDuration: FiniteDuration = 10.seconds): Future[Boolean] = {
    val retryCount = 10
    val retryDelay = waitDuration / retryCount

    Source(1 to retryCount)
      .throttle(1, retryDelay, 1, Shaping)
      .mapAsync(1)(_ => fetchProjectionStatus(name))
      .takeWhile(_ != expectedStatus)
      .runFold(0) { case (sum, _) => sum + 1 }
      .map(failedCount => failedCount != retryCount)
  }

  /**
   * Start the projection with the specified name.
   * Note that when eventstore responds to the command. It only acknowledges it.
   * To know when it is started, you should use #waitForProjectionStatus
   * @param name the name of the projection to start
   * @return a future completed when the request is completed.
   *
   */
  def startProjection(name: String): Future[Unit] = executeCommand(name, "enable")

  /**
   * Stop the projection with the specified name.
   * Note that when eventstore responds to the command. It only acknowledges it.
   * To know when it is stopped, you should use #waitForProjectionStatus
   * @param name the name of the projection to stop
   * @return a future completed when the request is completed.
   *
   */
  def stopProjection(name: String): Future[Unit] = executeCommand(name, "disable")

  /**
   * Try to delete the projection with the specified name.
   * To delete a projection. It must be stopped first (see #stopProjection)
   * @param name the name of the projection to stop
   * @return a future telling whether the action was done (@ProjectionDeleted) or if it was not able to do so (@UnableToDeleteProjection)
   */
  def deleteProjection(name: String): Future[ProjectionDeleteResult] = {
    val request = HttpRequest(
      method = HttpMethods.DELETE,
      uri = Uri(projectionBaseUrl(name)),
      headers = defaultHeaders)

    singleRequestWithErrorHandling(request)
      .mapAsync(1) {
        case response if response.status == StatusCodes.InternalServerError => Unmarshal(response.entity).to[String].map(UnableToDeleteProjection)
        case response if response.status == StatusCodes.OK => Future.successful(ProjectionDeleted)
        case response => Future.failed(new ProjectionException(s"Received unexpected reponse $response"))
      }
      .runWith(Sink.head)
  }

  private[this] def singleRequestWithErrorHandling(request: HttpRequest): Source[HttpResponse, NotUsed] = {
    Source
      .single(request)
      .via(connection)
      .map { responseTry =>
        val response = responseTry.recover {
          case ex => throw new ProjectionException(s"Failed to query eventstore on ${request.uri}", ex)
        }.get
        if (response.status == StatusCodes.Unauthorized)
          throw new CannotEstablishConnectionException("Invalid credentials")
        else
          response
      }
  }
}
