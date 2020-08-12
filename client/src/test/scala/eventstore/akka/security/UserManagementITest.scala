package eventstore
package akka
package security

import scala.concurrent.duration._
import _root_.akka.testkit.TestActorRef
import _root_.akka.actor.Status.Failure
import eventstore.akka.tcp.ConnectionActor
import eventstore.core.util.PasswordHashAlgorithm

class UserManagementITest extends ActorSpec {

  skipAllIf(testutil.isES20Series) // due to --insecure node setting disables user management

  "admin" should {
    "be authenticated" in new UserScope {
      actor ! Authenticate.withCredentials(UserCredentials.DefaultAdmin)
      expectMsg(Authenticated)

      actor ! Authenticate
      expectMsg(Authenticated)
    }
  }

  "UserManagement" should {
    "create user" in new UserScope {
      expectNotAuthenticated()
      createUser()
      expectAuthenticated()
    }

    "disable user" in new UserScope {
      createUser()
      expectAuthenticated()

      actor ! ReadEvent(streamId)
      val data = expectMsgType[ReadEventCompleted].event.data.data.value.utf8String

      notifyPasswordChanged()

      actor ! WriteEvents(streamId, List(userUpdated(data.replace(""""disabled": false""", """"disabled": true"""))))
      expectMsgType[WriteEventsCompleted]

      expectNotAuthenticated()
    }

    "delete user" in new UserScope {
      createUser()
      expectAuthenticated()

      actor ! DeleteStream(streamId)
      expectMsgType[DeleteStreamCompleted]

      notifyPasswordChanged()

      expectNotAuthenticated()
    }

  }

  trait UserScope extends ActorScope {

    val user = {
      val uuid = randomUuid.toString
      UserCredentials(uuid, uuid)
    }

    val actor = TestActorRef(ConnectionActor.props())

    val streamId = EventStream.Id(user)

    def expectAuthenticated(): Unit = {
      actor ! Authenticate.withCredentials(user)
      expectMsg(Authenticated)
    }

    def expectNotAuthenticated(): Unit = {
      def notAuthenticated = {
        actor ! Authenticate.withCredentials(user)
        expectMsgPF() {
          case Authenticated                         => false
          case Failure(_: NotAuthenticatedException) => true
        }
      }
      awaitCond(notAuthenticated, max = 1.second)
    }

    def createUser(user: UserCredentials = user): Unit = {
      val metadata = """{
                       |  "$acl": {
                       |    "$w": "$admins",
                       |    "$d": "$admins",
                       |    "$mw": "$admins"
                       |  }
                       |}""".stripMargin

      actor ! WriteEvents.StreamMetadata(streamId.metadata, Content.Json(metadata), ExpectedVersion.NoStream)
      expectMsgType[WriteEventsCompleted].numbersRange must beSome(EventNumber.First to EventNumber.First)

      actor ! WriteEvents(streamId, List(userCreated(user)), ExpectedVersion.NoStream)
      expectMsgType[WriteEventsCompleted].numbersRange must beSome(EventNumber.First to EventNumber.First)
    }

    def notifyPasswordChanged(): Unit = {
      actor ! WriteEvents(EventStream.Id("$users-password-notifications"), List(passwordChanged(user.login)))
      expectMsgType[WriteEventsCompleted]
    }

    def userData(login: String, fullName: String, salt: String, hash: String, disabled: Boolean = false) = s"""{
      |  "loginName": "$login",
      |  "fullName": "$fullName",
      |  "salt": "$salt",
      |  "hash": "$hash",
      |  "disabled": $disabled,
      |  "groups": []
      |}""".stripMargin

    def userCreated(user: UserCredentials): EventData = {
      val (hash, salt) = PasswordHashAlgorithm().hash(user.password)
      val login = user.login
      val data = userData(login = login, fullName = login, salt = salt, hash = hash)
      EventData(SystemEventType.userCreated, randomUuid, data = Content.Json(data))
    }

    def userUpdated(data: String): EventData = EventData(SystemEventType.userUpdated, randomUuid, data = Content.Json(data))

    def passwordChanged(login: String): EventData =
      EventData(SystemEventType.passwordChanged, randomUuid, data = Content.Json(s"""{"loginName": "$login"}"""))
  }
}

