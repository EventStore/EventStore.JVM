package eventstore

import akka.stream.ThrottleMode.Shaping
import akka.stream.scaladsl.Source
import eventstore.ProjectionsClient.ProjectionCreationResult._
import eventstore.ProjectionsClient.ProjectionDeleteResult._
import eventstore.ProjectionsClient.ProjectionMode._
import eventstore.ProjectionsClient.ProjectionStatus._
import eventstore.ProjectionsClient._
import org.specs2.execute.EventuallyResults
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

class ProjectionsClientITest extends AbstractStreamsITest {
  sequential

  val timeout = 20.seconds
  val ProjectionCode =
    """ fromStream("$users").
      |   when({
      |     $init : function(state,event){
      |       return { count : 0 }
      |     },
      |     $any : function(state,event){
      |       state.count += 1
      |     }
      |   })
    """.stripMargin
  val ProjectionCodeWithNoState =
    """ fromStream("$users").
      |   when({
      |     $any : function(state,event){
      |       return;
      |     }
      |   })
    """.stripMargin

  "the projections client" should {
    "be able to create a projection" in new TestScope {
      val client = new ProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      val result = client.createProjection(projectionName, ProjectionCode, OneTime)

      result.await_(timeout) should beEqualTo(ProjectionCreated)
    }

    "fail to create the same projection twice" in new TestScope {
      val client = new ProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      val result = for {
        _ <- client.createProjection(projectionName, ProjectionCode, OneTime)
        secondCreateResult <- client.createProjection(projectionName, ProjectionCode, OneTime)
      } yield secondCreateResult

      result.await_(timeout) should beEqualTo(ProjectionAlreadyExist)
    }

    "return the completed status for a one time projection" in new TestScope {
      val client = new ProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      client.createProjection(projectionName, ProjectionCode, OneTime)

      EventuallyResults.eventually {
        val projectionDetails = client.fetchProjectionDetails(projectionName).await_(timeout)
        projectionDetails.map(_.status) should beSome(Completed)
      }
    }

    "return the running status for a continuous projection" in new TestScope {
      val client = new ProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      val result = for {
        _ <- client.createProjection(projectionName, ProjectionCode, Continuous)
        details <- client.fetchProjectionDetails(projectionName)
      } yield details

      val projectionDetails = result.await_(timeout)
      projectionDetails.map(_.status) should beSome(Running)
    }

    "return the faulted status for a continuous projection that doesnt compile" in new TestScope {
      val client = new ProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      val result = for {
        _ <- client.createProjection(projectionName, "blah", Continuous)
        _ <- client.stopProjection(projectionName)
        details <- client.fetchProjectionDetails(projectionName)
      } yield details

      val projectionDetails = result.await_(timeout)
      projectionDetails.map(_.status) should beSome(Faulted)
    }

    "return None when fetching the project details of a non existant projection" in new TestScope {
      val client = new ProjectionsClient(settings, system)

      val projectionDetails = client.fetchProjectionDetails("notExist").await_(timeout)
      projectionDetails should beNone
    }

    "fetch the projection state" in new TestScope {
      val client = new ProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      client.createProjection(projectionName, ProjectionCode, OneTime)

      EventuallyResults.eventually {
        val projectionState = client.fetchProjectionState(projectionName).await_(timeout)
        projectionState.map(json => (Json.parse(json) \ "count").as[Int]) should beSome(2)
      }
    }

    "return an empty state when the projection doesn't exist" in new TestScope {
      val client = new ProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      val projectionState = client.fetchProjectionState(projectionName).await_(timeout)
      projectionState shouldEqual None
    }

    "return an empty state when the projection has no state" in new TestScope {
      val projectionName = generateProjectionName
      val client = new ProjectionsClient(settings, system)

      client.createProjection(projectionName, ProjectionCodeWithNoState, OneTime)

      EventuallyResults.eventually {
        val projectionCompleted = client.fetchProjectionDetails(projectionName).await_(timeout).map(_.status) should beSome(Completed)
        val resultIsEmpty = client.fetchProjectionState(projectionName).await_(timeout) shouldEqual Some("{}")

        projectionCompleted and resultIsEmpty
      }
    }

    "fetch the projection result" in new TestScope {
      val client = new ProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      client.createProjection(projectionName, ProjectionCode, OneTime)

      EventuallyResults.eventually {
        val projectionResult = client.fetchProjectionResult(projectionName).await_(timeout)
        projectionResult.map(json => (Json.parse(json) \ "count").as[Int]) should beSome(2)
      }
    }

    "return an empty result when the projection doesn't exist" in new TestScope {
      val projectionName = generateProjectionName
      val client = new ProjectionsClient(settings, system)

      val projectionResult = client.fetchProjectionResult(projectionName).await_(timeout)
      projectionResult shouldEqual None
    }

    "return an empty result when the projection has no result" in new TestScope {
      val projectionName = generateProjectionName
      val client = new ProjectionsClient(settings, system)

      client.createProjection(projectionName, ProjectionCodeWithNoState, OneTime)

      EventuallyResults.eventually {
        val projectionCompleted = client.fetchProjectionDetails(projectionName).await_(timeout).map(_.status) should beSome(Completed)
        val resultIsEmpty = client.fetchProjectionResult(projectionName).await_(timeout) shouldEqual Some("{}")

        projectionCompleted and resultIsEmpty
      }
    }

    "stop a continuous projection and return the stopped status for it" in new TestScope {
      val client = new ProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      val result = for {
        _ <- client.createProjection(projectionName, ProjectionCode, Continuous)
        _ <- client.stopProjection(projectionName)
        status <- client.fetchProjectionDetails(projectionName)
      } yield status

      val projectionDetails = result.await_(timeout)
      projectionDetails.map(_.status) should beSome(Stopped)
    }

    "restart a stopped projection" in new TestScope {
      val client = new ProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      val result = for {
        _ <- client.createProjection(projectionName, ProjectionCode, Continuous)
        _ <- client.stopProjection(projectionName)
        _ <- client.startProjection(projectionName)
        status <- client.fetchProjectionDetails(projectionName)
      } yield status

      val projectionDetails = result.await_(timeout)
      projectionDetails.map(_.status) should beSome(Running)
    }

    "delete a completed onetime projection" in new TestScope {
      val client = new ProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      val result = for {
        _ <- client.createProjection(projectionName, ProjectionCode, OneTime)
        _ <- waitForProjectionStatus(client, projectionName, Completed)
        _ <- client.stopProjection(projectionName)
        stopped <- waitForProjectionStatus(client, projectionName, Stopped)
        deleteResult <- client.deleteProjection(projectionName)
      } yield deleteResult

      result.await_(timeout) should beEqualTo(ProjectionDeleted)
    }

    "delete a stopped continuous projection" in new TestScope {
      val client = new ProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      val result = for {
        _ <- client.createProjection(projectionName, ProjectionCode, Continuous)
        stopped <- waitForProjectionStatus(client, projectionName, Running)
        _ <- client.stopProjection(projectionName)
        stopped <- waitForProjectionStatus(client, projectionName, Stopped)
        deleteResult <- client.deleteProjection(projectionName)
      } yield deleteResult

      result.await_(timeout) should beEqualTo(ProjectionDeleted)
    }

    "fail to delete a running projection" in new TestScope {
      val client = new ProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      val result = for {
        _ <- client.createProjection(projectionName, ProjectionCode, OneTime)
        deleteResult <- client.deleteProjection(projectionName)
      } yield deleteResult

      result.await_(timeout) should beLike[ProjectionDeleteResult] {
        case UnableToDeleteProjection(_) => ok
      }

    }
  }

  private def generateProjectionName: String = {
    val random = new Random()
    val nameLength = 10
    "test_" + random.alphanumeric.take(nameLength).foldLeft("") {
      _ + _
    }
  }

  def waitForProjectionStatus(client: ProjectionsClient, name: String, expectedStatus: ProjectionStatus, waitDuration: FiniteDuration = 10.seconds): Future[Boolean] = {
    val retryCount = 10
    val retryDelay = waitDuration / retryCount.toLong

    Source(1 to retryCount)
      .throttle(1, retryDelay, 1, Shaping)
      .mapAsync(1)(_ => client.fetchProjectionDetails(name))
      .takeWhile(_ != expectedStatus)
      .runFold(0) { case (sum, _) => sum + 1 }
      .map(failedCount => failedCount != retryCount)
  }
}
