package eventstore
package core
package tcp

// TODO(AHJ): Restore this into package object after 7.1.0

private[eventstore] trait TypeAliases {

  type Bytes      = Array[Byte]
  type Flags      = Byte
  type Flag       = Byte
  type MarkerByte = Byte
}