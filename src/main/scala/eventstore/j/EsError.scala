package eventstore
package j

import eventstore.{ EsError => S }
import java.net.InetSocketAddress

object EsError {
  def eventNotFound = S.EventNotFound

  def streamNotFound = S.StreamNotFound

  def prepareTimeout = S.PrepareTimeout

  def commitTimeout = S.CommitTimeout

  def forwardTimeout = S.ForwardTimeout

  def wrongExpectedVersion = S.WrongExpectedVersion

  def streamDeleted = S.StreamDeleted

  def invalidTransaction = S.InvalidTransaction

  def accessDenied = S.AccessDenied

  def notAuthenticated = S.NotAuthenticated

  def error = S.Error

  def connectionLost = S.ConnectionLost

  def badRequest = S.BadRequest

  def noHandled(reason: S.NotHandled.Reason) = S.NotHandled(reason)

  object NotHandled {
    def notReady = S.NotHandled.NotReady

    def tooBusy = S.NotHandled.TooBusy

    def notMaster(
      tcpAddress: InetSocketAddress,
      httpAddress: InetSocketAddress,
      tcpSecureAddress: InetSocketAddress) = S.NotHandled.NotMaster(S.NotHandled.MasterInfo(
      tcpAddress = tcpAddress,
      httpAddress = httpAddress,
      tcpSecureAddress = Option(tcpSecureAddress)))
  }
}
