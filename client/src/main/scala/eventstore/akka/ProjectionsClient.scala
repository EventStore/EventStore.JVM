package eventstore
package akka

import scala.concurrent.Future
import _root_.akka.actor.ActorSystem
import sttp.client3._
import sttp.client3.circe._
import sttp.model.{MediaType, StatusCode, Uri}
import ProjectionsClient.ProjectionCreationResult._
import ProjectionsClient.ProjectionDeleteResult._
import ProjectionsClient.ProjectionMode
import ProjectionsClient.ProjectionMode._

/**
 * The API miss documentation so I used the C# client as a starting point
 * See : https://github.com/EventStore/EventStore/blob/release-v3.9.0/src/EventStore.ClientAPI/Projections/ProjectionsClient.cs
 */
protected[this] trait ProjectionsUrls {

  def baseUri: Uri

  protected def createProjectionUrl(
    name: String,
    mode: ProjectionMode,
    allowEmit: Boolean
  ): Uri = {
    val emit = if (allowEmit) "emit=1&checkpoints=yes" else "emit=0"
    val projectionModeStr = mode match {
      case OneTime    => "onetime"
      case Transient  => "transient"
      case Continuous => "continuous"
    }
    baseUri.withWholePath(s"/projections/$projectionModeStr?name=$name&type=JS&$emit")
  }

  protected def projectionBaseUrl(name: String): Uri =
    baseUri.withWholePath(s"/projection/$name")

  protected def fetchProjectionStateUrl(name: String, partition: Option[String]): Uri =
    projectionBaseUrl(name).addPath(s"state${partition.fold("")(p => s"partition=$p")}")

  protected def fetchProjectionResultUrl(name: String, partition: Option[String]): Uri =
    projectionBaseUrl(name).addPath(s"result${partition.fold("")(p => s"partition=$p")}")

  protected def projectionCommandUrl(name: String, command: String): Uri =
    projectionBaseUrl(name).addPath(s"command/$command")
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

  import ProjectionsClient._
  import system.dispatcher

  private val hs = settings.http

  val baseUri: Uri = uri"${hs.protocol}://${hs.host}:${hs.port}${hs.prefix}"

  private val sttp = Http.mkSttpFutureBackend(hs.useTls, system)
  private val br = settings.defaultCredentials.fold(basicRequest)(c => basicRequest.auth.basic(c.login, c.password))

  /**
   * Create the projection with the specified name and code
   * @param name the name of the projection to create
   * @param javascript the javascript code for the projection
   * @param mode the projection's mode (Either OneTime, Continuous or Transient)
   * @param allowEmit indicates if the projection is allowed to emit new events.
   * @return
   */
  def createProjection(
    name: String,
    javascript: String,
    mode: ProjectionMode = Continuous,
    allowEmit: Boolean = true
  ): Future[ProjectionCreationResult] = {

    val uri = createProjectionUrl(name, mode, allowEmit)
    val res = withErrorHandling(br.post(uri).body(javascript).contentType(MediaType.ApplicationJson).send(sttp), uri)

    res.map(_.code).flatMap {
      case StatusCode.Created => Future.successful(ProjectionCreated)
      case StatusCode.Conflict => Future.successful(ProjectionAlreadyExist)
      case status => Future.failed(ProjectionException(s"Received unexpected response status $status"))
    }
  }

  /**
   * Fetch the details for the specified projection.
   * @param name the name of the projection
   * @return the Projection details if it exist. None otherwise
   */
  def fetchProjectionDetails(name: String): Future[Option[ProjectionDetails]] = {

    val uri = projectionBaseUrl(name)
    val res = withErrorHandling(br.get(uri).response(asJson[ProjectionDetails]).send(sttp), uri)

    res.flatMap { r =>
      r.body match {
       case Left(_) if r.code == StatusCode.NotFound => Future.successful(None)
       case Left(v) => Future.failed(v)
       case Right(v) => Future.successful(Some(v))
      }
    }

  }

  private[this] def fetchProjectionData(uri: Uri): Future[Option[String]] =
    withErrorHandling(br.get(uri).response(asStringAlways).send(sttp), uri).flatMap { r =>
      if(r.code == StatusCode.NotFound) Future.successful(None) else Future.successful(Some(r.body))
    }


  /**
   * Fetch the projection's state
   * @param name the name of the projection
   * @param partition the name of the partition
   * @return a String that should be either empty or a valid json object with the current state.
   */
  def fetchProjectionState(name: String, partition: Option[String] = None): Future[Option[String]] =
    fetchProjectionData(fetchProjectionStateUrl(name, partition))

  /**
   * Fetch the projection's result.
   * It only works for OneTime projections as Continuous one dont provide a result.
   * @param name the name of the projection
   * @param partition the name of the partition
   * @return a String that should be either empty or a valid json object with the projection's result.
   */
  def fetchProjectionResult(name: String, partition: Option[String] = None): Future[Option[String]] =
    fetchProjectionData(fetchProjectionResultUrl(name, partition))

  private[this] def executeCommand(name: String, command: String): Future[Unit] = {

    val uri = projectionCommandUrl(name, command)
    val res = withErrorHandling(br.post(uri).body("{}").contentType(MediaType.ApplicationJson).send(sttp), uri)

    res.map(_.code).flatMap {
      case StatusCode.NotFound | StatusCode.Ok => Future.successful(())
      case status => Future.failed(ProjectionException(s"Received unexpected reponse status : $status"))
    }
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

    val uri = projectionBaseUrl(name)
    val res = withErrorHandling(br.delete(uri).response(asStringAlways).send(sttp), uri)

    res.flatMap {
      case response if response.code == StatusCode.InternalServerError =>
        Future.successful(UnableToDeleteProjection(response.body))
      case response if response.code == StatusCode.Ok =>
        Future.successful(ProjectionDeleted)
      case response =>
        Future.failed(ProjectionException(s"Received unexpected reponse $response"))
    }

  }

  def withErrorHandling[T](response: Future[Response[T]], uri:Uri): Future[Response[T]] = {
    response.flatMap(r =>
      if(r.code == StatusCode.Unauthorized) Future.failed(AccessDeniedException("Invalid credentials "))
      else Future.successful(r)
    ).recoverWith {
      case ex => Future.failed(ProjectionException(s"Failed to query eventstore on $uri", ex))
    }
  }
}
