package eventstore.tcp

import akka.NotUsed
import akka.stream.BidiShape
import akka.stream.scaladsl.{ BidiFlow, Broadcast, Flow, GraphDSL, Merge }
import eventstore.{ In, Out }

object BidiReply {
  def apply(pf: PartialFunction[In, Out]): BidiFlow[PackIn, PackIn, PackOut, PackOut, NotUsed] = BidiFlow.fromGraph {
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder add Broadcast[PackIn](2)
      val merge = builder add Merge[PackOut](2)
      val filter = builder add Flow[PackIn].filter(_.message.toOption exists pf.isDefinedAt)
      val filterNot = builder add Flow[PackIn].filterNot(_.message.toOption exists pf.isDefinedAt)
      val reqToRes = builder add Flow[PackIn].map(req => PackOut(pf(req.message.get), req.correlationId))

      broadcast.out(0) ~> filterNot
      broadcast.out(1) ~> filter ~> reqToRes ~> merge.in(0)

      BidiShape(broadcast.in, filterNot.outlet, merge.in(1), merge.out)
    }
  }
}