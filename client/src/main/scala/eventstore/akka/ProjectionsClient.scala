package eventstore
package akka

import scala.util.Try
import scala.concurrent.Future
import _root_.akka.NotUsed
import _root_.akka.actor.ActorSystem
import _root_.akka.http.scaladsl.Http
import _root_.akka.http.scaladsl.model._
import _root_.akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import _root_.akka.http.scaladsl.unmarshalling.Unmarshal
import _root_.akka.stream.scaladsl.{Flow, Sink, Source}
import ProjectionsClient.ProjectionCreationResult._
import ProjectionsClient.ProjectionDeleteResult._
import ProjectionsClient.ProjectionMode
import ProjectionsClient.ProjectionMode._

/**
 * The API miss documentation so I used the C# client as a starting point
 * See : https://github.com/EventStore/EventStore/blob/release-v3.9.0/src/EventStore.ClientAPI/Projections/ProjectionsClient.cs
 */
protected[this] trait ProjectionsUrls {
  protected def createProjectionUrl(name: String, mode: ProjectionMode = Continuous, allowEmit: Boolean = true): String = {
    val emit = if (allowEmit) "emit=1&checkpoints=yes" else "emit=0"
    val projectionModeStr = mode match {
      case OneTime    => "onetime"
      case Transient  => "transient"
      case Continuous => "continuous"
    }
    s"/projections/$projectionModeStr?name=$name&type=JS&$emit"
  }

  protected def projectionBaseUrl(name: String): String = s"/projection/$name"

  protected def fetchProjectionStateUrl(name: String, partition: Option[String]): String =
    s"${projectionBaseUrl(name)}/state" + partition.fold("")(partition => s"?partition=$partition")

  protected def fetchProjectionResultUrl(name: String, partition: Option[String]): String =
    s"${projectionBaseUrl(name)}/result" + partition.fold("")(partition => s"?partition=$partition")

  protected def projectionCommandUrl(name: String, command: String): String =
    s"${projectionBaseUrl(name)}/command/$command"
}

object ProjectionsClient {

  sealed trait ProjectionMode
  object ProjectionMode {
    case object Continuous extends ProjectionMode
    case object OneTime extends ProjectionMode
    case object Transient extends ProjectionMode

    def apply(modeString: String): ProjectionMode = modeString.toLowerCase match {
      case "onetime"    => OneTime
      case "transient"  => Transient
      case "continuous" => Continuous
      case other        => throw new IllegalArgumentException(s"Expected ProjectionMode tp be one of OneTime|Transient|Continuous but was $other")
    }

  }

  sealed trait ProjectionStatus
  object ProjectionStatus {
    case object Running extends ProjectionStatus
    case object Faulted extends ProjectionStatus
    case object Completed extends ProjectionStatus
    case object Stopped extends ProjectionStatus
    final case class Other(status: String) extends ProjectionStatus

    def apply(statusString: String): ProjectionStatus = statusString match {
      case status if status.startsWith("Running")   => Running
      case status if status.startsWith("Completed") => Completed
      case status if status.startsWith("Stopped")   => Stopped
      case status if status.startsWith("Faulted")   => Faulted
      case other                                    => Other(other)
    }
  }

  sealed trait ProjectionCreationResult
  object ProjectionCreationResult {
    case object ProjectionCreated extends ProjectionCreationResult
    case object ProjectionAlreadyExist extends ProjectionCreationResult
  }

  sealed trait ProjectionDeleteResult
  object ProjectionDeleteResult {
    case object ProjectionDeleted extends ProjectionDeleteResult
    final case class UnableToDeleteProjection(reason: String) extends ProjectionDeleteResult
  }

@SerialVersionUID(1L)
final case class ProjectionException(message: String, cause: Option[Throwable]) extends EsException(message, cause)
object ProjectionException {
  def apply(msg: String): ProjectionException                = ProjectionException(msg, None)
  def apply(msg: String, th: Throwable): ProjectionException = ProjectionException(msg, Option(th))
}

}

/**
 * A client allowing to create, get the status and delete an existing projection.
 */
class ProjectionsClient(settings: Settings = Settings.Default, system: ActorSystem) extends ProjectionsUrls {

  private implicit val as = system

  import system.dispatcher
  import ProjectionsClient._

  private val connection: Flow[HttpRequest, Try[HttpResponse], NotUsed] = {
    val httpExt = Http(system)
    val uri = settings.http.uri

    val connectionPool = if (uri.scheme == "http") {
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
   * @param javascript the javascript code for the projection
   * @param mode the projection's mode (Either OneTime, Continuous or Transient)
   * @param allowEmit indicates if the projection is allowed to emit new events.
   * @return
   */
  def createProjection(name: String, javascript: String, mode: ProjectionMode = Continuous, allowEmit: Boolean = true): Future[ProjectionCreationResult] = {
    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = Uri(createProjectionUrl(name, mode, allowEmit)),
      headers = defaultHeaders,
      entity = HttpEntity(ContentTypes.`application/json`, javascript)
    )

    singleRequestWithErrorHandling(request)
      .map { response =>
        response.entity.discardBytes()
        response.status
      }
      .map {
        case StatusCodes.Created  => ProjectionCreated
        case StatusCodes.Conflict => ProjectionAlreadyExist
        case status               => throw ProjectionException(s"Received unexpected response status $status")
      }
      .runWith(Sink.head)
  }

  /**
   * Fetch the details for the specified projection.
   * @param name the name of the projection
   * @return the Projection details if it exist. None otherwise
   */
  def fetchProjectionDetails(name: String): Future[Option[ProjectionDetails]] = {
    import _root_.akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = Uri(projectionBaseUrl(name)),
      headers = defaultHeaders
    )

    singleRequestWithErrorHandling(request)
      .mapAsync(1) {
        case response if response.status == StatusCodes.NotFound =>
          response.entity.discardBytes()
          Future.successful(None)
        case response =>
          Unmarshal(response)
            .to[ProjectionDetails]
            .map(Some.apply)
      }
      .runWith(Sink.head)
  }

  private[this] def fetchProjectionData(request: HttpRequest): Future[Option[String]] = {
    singleRequestWithErrorHandling(request)
      .mapAsync(1) {
        case response if response.status == StatusCodes.NotFound =>
          response.entity.discardBytes()
          Future.successful(None)
        case response =>
          Unmarshal(response).to[String].map(Some(_))
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
      headers = defaultHeaders
    )

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
      headers = defaultHeaders
    )

    fetchProjectionData(request)
  }

  private[this] def executeCommand(name: String, command: String): Future[Unit] = {
    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = Uri(projectionCommandUrl(name, command)),
      headers = defaultHeaders,
      entity = HttpEntity(ContentTypes.`application/json`, "{}")
    )

    singleRequestWithErrorHandling(request)
      .map { response =>
        response.entity.discardBytes()
        response.status
      }
      .map {
        case StatusCodes.NotFound => ()
        case StatusCodes.OK       => ()
        case status               => throw ProjectionException(s"Received unexpected reponse status : $status")
      }
      .runWith(Sink.head)
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
      headers = defaultHeaders
    )

    singleRequestWithErrorHandling(request)
      .mapAsync(1) {
        case response if response.status == StatusCodes.InternalServerError =>
          response.entity.discardBytes()
          Unmarshal(response.entity).to[String].map(UnableToDeleteProjection)
        case response if response.status == StatusCodes.OK =>
          response.entity.discardBytes()
          Future.successful(ProjectionDeleted)
        case response =>
          response.entity.discardBytes()
          Future.failed(ProjectionException(s"Received unexpected reponse $response"))
      }
      .runWith(Sink.head)
  }

  private[this] def singleRequestWithErrorHandling(request: HttpRequest): Source[HttpResponse, NotUsed] = {
    Source
      .single(request)
      .via(connection)
      .map { responseTry =>
        val response = responseTry.recover {
          case ex => throw ProjectionException(s"Failed to query eventstore on ${request.uri}", ex)
        }.get
        if (response.status == StatusCodes.Unauthorized) {
          response.entity.discardBytes()
          throw new AccessDeniedException("Invalid credentials ")
        } else {
          response
        }
      }
  }
}
