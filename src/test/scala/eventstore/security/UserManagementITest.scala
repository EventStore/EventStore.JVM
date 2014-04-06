package eventstore
package security

import akka.testkit.TestActorRef
import akka.actor.Status.Failure
import util.{ ActorSpec, PasswordHashAlgorithm }
import tcp.ConnectionActor
import scala.concurrent.duration._

class UserManagementITest extends ActorSpec {

  "admin" should {
    "be authenticated" in new UserScope {
      actor ! Authenticate.withCredentials(UserCredentials.defaultAdmin)
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
      expectMsg(DeleteStreamCompleted)

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

    val streamId = EventStream(user)

    def expectAuthenticated() {
      actor ! Authenticate.withCredentials(user)
      expectMsg(Authenticated)
    }

    def expectNotAuthenticated() {
      def notAuthenticated = {
        actor ! Authenticate.withCredentials(user)
        expectMsgPF() {
          case Authenticated                                     => false
          case Failure(EsException(EsError.NotAuthenticated, _)) => true
        }
      }
      awaitCond(notAuthenticated, max = 1.second)
    }

    def createUser(user: UserCredentials = user) {
      val metadata = """{
                       |  "$acl": {
                       |    "$w": "$admins",
                       |    "$d": "$admins",
                       |    "$mw": "$admins"
                       |  }
                       |}""".stripMargin

      actor ! WriteEvents.Metadata(streamId, Content.Json(metadata), ExpectedVersion.NoStream)
      expectMsg(WriteEventsCompleted(Some(EventNumber.First to EventNumber.First)))

      actor ! WriteEvents(streamId, List(userCreated(user)), ExpectedVersion.NoStream)
      expectMsg(WriteEventsCompleted(Some(EventNumber.First to EventNumber.First)))
    }

    def notifyPasswordChanged() {
      actor ! WriteEvents(EventStream("$users-password-notifications"), List(passwordChanged(user.login)))
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
      EventData(SystemEventType.userCreated, data = Content.Json(data))
    }

    def userUpdated(data: String): EventData = EventData(SystemEventType.userUpdated, data = Content.Json(data))

    def passwordChanged(login: String): EventData =
      EventData(SystemEventType.passwordChanged, data = Content.Json(s"""{"loginName": "$login"}"""))
  }
}

