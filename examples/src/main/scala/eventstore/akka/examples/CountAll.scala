package eventstore
package akka
package examples

import _root_.akka.actor._
import scala.concurrent.duration._
import eventstore.akka.tcp.ConnectionActor

object CountAll extends App {
  val system = ActorSystem()
  val connection = system.actorOf(ConnectionActor.props(), "connection")
  val countAll = system.actorOf(Props[CountAll](), "count-all")
  system.actorOf(SubscriptionActor.props(connection, countAll, None, None, Settings.Default), "subscription")
}

class CountAll extends Actor with ActorLogging {
  context.setReceiveTimeout(1.second)

  def receive = count(0)

  def count(n: Long, printed: Boolean = false): Receive = {
    case _: IndexedEvent       => context become count(n + 1)
    case LiveProcessingStarted => log.info("live processing started")
    case ReceiveTimeout if !printed =>
      log.info("count {}", n)
      context become count(n, printed = true)
  }
}