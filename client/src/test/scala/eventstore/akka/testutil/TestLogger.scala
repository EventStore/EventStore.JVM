package eventstore
package akka
package testutil

import scala.reflect.{ClassTag => CT}
import _root_.akka.event.LoggingAdapter
import TestLogger.Item
import TestLogger.Level

final case class TestLogger private(
  debug: Boolean            = false,
  info: Boolean             = false,
  warning: Boolean          = false,
  error: Boolean            = false,
  itemSink: Item => Unit    = _ => (),
  levelCheck: Level => Unit = _ => ()
) extends LoggingAdapter {

  def isDebugEnabled: Boolean   = { levelCheck(Level.Debug);   debug   }
  def isInfoEnabled: Boolean    = { levelCheck(Level.Info);    info    }
  def isWarningEnabled: Boolean = { levelCheck(Level.Warning); warning }
  def isErrorEnabled: Boolean   = { levelCheck(Level.Error);   error   }

  def notifyDebug(message: String): Unit                   = itemSink(Item.Debug(message))
  def notifyInfo(message: String): Unit                    = itemSink(Item.Info(message))
  def notifyWarning(message: String): Unit                 = itemSink(Item.Warning(message))
  def notifyError(message: String): Unit                   = itemSink(Item.Error(message, None))
  def notifyError(cause: Throwable, message: String): Unit = itemSink(Item.Error(message, Some(cause)))

  def turnOff: TestLogger =
    TestLogger(itemSink = itemSink, levelCheck = levelCheck)
}

object TestLogger {

  sealed trait Level
  object Level {
    case object Debug   extends Level
    case object Info    extends Level
    case object Warning extends Level
    case object Error   extends Level
  }

  sealed abstract class Item(val level: Level) {
    val msg: String
  }

  object Item {
    final case class Debug(msg: String)                        extends Item(Level.Debug)
    final case class Info(msg: String)                         extends Item(Level.Info)
    final case class Warning(msg: String)                      extends Item(Level.Warning)
    final case class Error(msg: String, th: Option[Throwable]) extends Item(Level.Error)
  }

  def debug(onDebug: Item.Debug => Unit, onDebugCheck: => Unit): TestLogger = TestLogger(
    debug      = true,
    itemSink   = only[Item.Debug, Item](onDebug),
    levelCheck = only[Level.Debug.type, Level](_ => onDebugCheck)
  )

  private def only[T: CT, R](cb: T => Unit): R => Unit = { case d: T => cb(d); case _ => () }

}