package eventstore
package security

import akka.testkit.TestActorRef
import akka.actor.Status.Failure
import util.{ ActorSpec, PasswordHashAlgorithm }
import tcp.ConnectionActor

// TODO is it a right name?
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

      actor ! Authenticate.withCredentials(user)
      expectMsg(Failure(EventStoreException(EventStoreError.NotAuthenticated)))
    }.pendingUntilFixed

    "delete user" in new UserScope {
      createUser()
      expectAuthenticated()

      actor ! DeleteStream(streamId)
      expectMsg(DeleteStreamCompleted)

      notifyPasswordChanged()

      actor ! Authenticate.withCredentials(user)
      expectMsg(Failure(EventStoreException(EventStoreError.NotAuthenticated)))
    }.pendingUntilFixed
  }

  trait UserScope extends ActorScope {
    val user = {
      val uuid = newUuid.toString
      UserCredentials(uuid, uuid)
    }

    val actor = TestActorRef(ConnectionActor.props())

    val streamId = EventStream(user)

    def expectAuthenticated() {
      actor ! Authenticate.withCredentials(user)
      expectMsg(Authenticated)
    }

    def expectNotAuthenticated() {
      actor ! Authenticate.withCredentials(user)
      expectMsg(Failure(EventStoreException(EventStoreError.NotAuthenticated)))
    }

    def createUser(user: UserCredentials = user) {
      val metadata = """{
                       |  "$acl": {
                       |    "$w": "$admins",
                       |    "$d": "$admins",
                       |    "$mw": "$admins"
                       |  }
                       |}""".stripMargin

      actor ! WriteEvents(streamId, List(userCreated(user)), ExpectedVersion.NoStream)
      expectMsg(WriteEventsCompleted(EventNumber.First))

      actor ! WriteMetadata(streamId, Content.Json(metadata), ExpectedVersion.NoStream)
      expectMsg(WriteEventsCompleted(EventNumber.First))
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

    object WriteMetadata {
      def apply(
        streamId: EventStream.Id,
        data: Content,
        expectedVersion: ExpectedVersion = ExpectedVersion.Any,
        requireMaster: Boolean = true): WriteEvents = {

        // TODO refactor and make a part of EventStream.Id
        require(!streamId.isMeta, s"setting metadata for metastream $streamId is not supported")

        WriteEvents(
          EventStream("$$" + streamId.value), // TODO .map, anyway should be a method of EventStream.scala
          List(EventData(SystemEventType.metadata)),
          expectedVersion = expectedVersion,
          requireMaster = requireMaster)
      }
    }
  }
}

