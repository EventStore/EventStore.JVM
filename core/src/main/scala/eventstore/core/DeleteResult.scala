package eventstore
package core

/**
 * Result type returned after deleting a stream.
 *
 * @param logPosition The position of the write in the log
 */
@SerialVersionUID(1L) final case class DeleteResult(logPosition: Position)