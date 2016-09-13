package eventstore

import eventstore.EsProjectionsClient._
import org.specs2.execute.EventuallyResults
import play.api.libs.json.Json

import scala.concurrent.duration._
import scala.util.Random

class EsProjectionsClientITest extends AbstractStreamsITest {
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
      val client = new EsProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      val result = client.createProjection(projectionName, ProjectionCode, OneTime)

      result.await_(timeout) should beEqualTo(ProjectionCreated)
    }

    "fail to create the same projection twice" in new TestScope {
      val client = new EsProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      val result = for {
        _ <- client.createProjection(projectionName, ProjectionCode, OneTime)
        secondCreateResult <- client.createProjection(projectionName, ProjectionCode, OneTime)
      } yield secondCreateResult

      result.await_(timeout) should beEqualTo(ProjectionAlreadyExist)
    }

    "return the completed status for a one time projection" in new TestScope {
      val client = new EsProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      client.createProjection(projectionName, ProjectionCode, OneTime)

      EventuallyResults.eventually {
        client.fetchProjectionStatus(projectionName).await_(timeout) should beEqualTo(Completed)
      }
    }

    "return the running status for a continous projection" in new TestScope {
      val client = new EsProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      val result = for {
        _ <- client.createProjection(projectionName, ProjectionCode, Continuous)
        status <- client.fetchProjectionStatus(projectionName)
      } yield status

      result.await_(timeout) should beEqualTo(Running)
    }

    "return the faulted status for a continous projection that doesnt compile" in new TestScope {
      val client = new EsProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      val result = for {
        _ <- client.createProjection(projectionName, "blah", Continuous)
        _ <- client.stopProjection(projectionName)
        status <- client.fetchProjectionStatus(projectionName)
      } yield status

      result.await_(timeout) should beEqualTo(Faulted)
    }

    "return absent when fetching the project status of a non existant projection" in new TestScope {
      val client = new EsProjectionsClient(settings, system)

      val projectionStatus = client.fetchProjectionStatus("notExist").await_(timeout)
      projectionStatus should beEqualTo(Absent)
    }

    "fetch the projection state" in new TestScope {
      val client = new EsProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      client.createProjection(projectionName, ProjectionCode, OneTime)

      EventuallyResults.eventually {
        val projectionState = client.fetchProjectionState(projectionName).await_(timeout)
        projectionState.map(json => (Json.parse(json) \ "count").as[Int]) should beSome(3)
      }
    }

    "return an empty state when the projection doesn't exist" in new TestScope {
      val client = new EsProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      val projectionState = client.fetchProjectionState(projectionName).await_(timeout)

      projectionState shouldEqual None
    }

    "return an empty state when the projection has no state" in new TestScope {
      val projectionName = generateProjectionName
      val client = new EsProjectionsClient(settings, system)

      client.createProjection(projectionName, ProjectionCodeWithNoState, OneTime)

      EventuallyResults.eventually {
        val projectionCompleted = client.fetchProjectionStatus(projectionName).await_(timeout) should beEqualTo(Completed)
        val resultIsEmpty = client.fetchProjectionState(projectionName).await_(timeout) shouldEqual Some("{}")

        projectionCompleted and resultIsEmpty
      }
    }

    "fetch the projection result" in new TestScope {
      val client = new EsProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      client.createProjection(projectionName, ProjectionCode, OneTime)

      EventuallyResults.eventually {
        val projectionResult = client.fetchProjectionResult(projectionName).await_(timeout)
        projectionResult.map(json => (Json.parse(json) \ "count").as[Int]) should beSome(3)
      }
    }

    "return an empty result when the projection doesn't exist" in new TestScope {
      val projectionName = generateProjectionName
      val client = new EsProjectionsClient(settings, system)

      val projectionResult = client.fetchProjectionResult(projectionName).await_(timeout)
      projectionResult shouldEqual None
    }

    "return an empty result when the projection has no result" in new TestScope {
      val projectionName = generateProjectionName
      val client = new EsProjectionsClient(settings, system)

      client.createProjection(projectionName, ProjectionCodeWithNoState, OneTime)

      EventuallyResults.eventually {
        val projectionCompleted = client.fetchProjectionStatus(projectionName).await_(timeout) should beEqualTo(Completed)
        val resultIsEmpty = client.fetchProjectionResult(projectionName).await_(timeout) shouldEqual Some("{}")

        projectionCompleted and resultIsEmpty
      }
    }

    "stop a continous projection and return the stopped status for it" in new TestScope {
      val client = new EsProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      val result = for {
        _ <- client.createProjection(projectionName, ProjectionCode, Continuous)
        _ <- client.stopProjection(projectionName)
        status <- client.fetchProjectionStatus(projectionName)
      } yield status

      result.await_(timeout) should beEqualTo(Stopped)
    }

    "restart a stopped projection" in new TestScope {
      val client = new EsProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      val result = for {
        _ <- client.createProjection(projectionName, ProjectionCode, Continuous)
        _ <- client.stopProjection(projectionName)
        _ <- client.startProjection(projectionName)
        status <- client.fetchProjectionStatus(projectionName)
      } yield status

      result.await_(timeout) should beEqualTo(Running)
    }

    "delete a completed onetime projection" in new TestScope {
      val client = new EsProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      val result = for {
        _ <- client.createProjection(projectionName, ProjectionCode, OneTime)
        _ <- client.waitForProjectionStatus(projectionName, Completed)
        _ <- client.stopProjection(projectionName)
        stopped <- client.waitForProjectionStatus(projectionName, Stopped)
        deleteResult <- client.deleteProjection(projectionName)
      } yield deleteResult

      result.await_(timeout) should beEqualTo(ProjectionDeleted)
    }

    "delete a stopped continuous projection" in new TestScope {
      val client = new EsProjectionsClient(settings, system)
      val projectionName = generateProjectionName

      val result = for {
        _ <- client.createProjection(projectionName, ProjectionCode, Continuous)
        stopped <- client.waitForProjectionStatus(projectionName, Running)
        _ <- client.stopProjection(projectionName)
        stopped <- client.waitForProjectionStatus(projectionName, Stopped)
        deleteResult <- client.deleteProjection(projectionName)
      } yield deleteResult

      result.await_(timeout) should beEqualTo(ProjectionDeleted)
    }

    "fail to delete a running projection" in new TestScope {
      val client = new EsProjectionsClient(settings, system)
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
}
