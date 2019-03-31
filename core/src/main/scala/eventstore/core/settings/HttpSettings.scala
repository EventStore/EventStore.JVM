package eventstore
package core
package settings

import com.typesafe.config.Config

/**
 * Contains settings relating to a connection to Event Store HTTP server.
 *
 * @param protocol Scheme, http or https.
 * @param host Hostname or address.
 * @param port Port number used by Event Store
 * @param prefix Prefix used that Event Store HTTP responds to.
 */
final case class HttpSettings(
  protocol: String,
  host: String,
  port: Int,
  prefix: String
)

object HttpSettings {

  def apply(conf: Config): HttpSettings = HttpSettings(
    protocol = conf getString "http.protocol",
    host     = conf getString "address.host",
    port     = conf getInt    "http.port",
    prefix   = conf getString "http.prefix"
  )
}