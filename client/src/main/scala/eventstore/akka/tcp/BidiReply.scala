package eventstore
package akka
package tcp

import _root_.akka.NotUsed
import _root_.akka.stream.BidiShape
import _root_.akka.stream.scaladsl.{ BidiFlow, Broadcast, Flow, GraphDSL, Merge }

private[eventstore] object BidiReply {
  def apply[I, O](pf: PartialFunction[I, O]): BidiFlow[I, I, O, O, NotUsed] = BidiFlow.fromGraph {
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder add Broadcast[I](2)
      val merge = builder add Merge[O](2)
      val filter = builder add Flow[I].filter(pf.isDefinedAt)
      val filterNot = builder add Flow[I].filterNot(pf.isDefinedAt)
      val reqToRes = builder add Flow[I].map(pf)

      broadcast.out(0) ~> filterNot
      broadcast.out(1) ~> filter ~> reqToRes ~> merge.in(0)

      BidiShape(broadcast.in, filterNot.outlet, merge.in(1), merge.out)
    }
  }
}