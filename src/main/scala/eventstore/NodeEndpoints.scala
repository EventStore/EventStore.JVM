package eventstore

import java.net.{ InetSocketAddress, InetAddress }

//sealed trait NodeEndpoints

// TODO
case class NodeEndpoints(endpoint: InetSocketAddress, secureEndpoint: Option[InetSocketAddress] = None /*TODO*/ )

/*
object NodeEndpoints {
  def apply(endpoint: Option[InetAddress], secureEndpoint: Option[InetAddress]): NodeEndpoints = {
    //    throw new ArgumentException("Both endpoints are null.");
    (endpoint, secureEndpoint) match {
      case (None, None)       => ???
      case (Some(_), Some(_)) => ???
      case (None, Some(_))    => ???
      case (Some(_), None)    => ???
    }
  }
}*/
