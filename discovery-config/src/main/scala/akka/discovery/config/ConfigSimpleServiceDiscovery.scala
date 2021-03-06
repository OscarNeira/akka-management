/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.discovery.config

import akka.actor.ExtendedActorSystem
import akka.discovery.{ Lookup, SimpleServiceDiscovery }
import akka.discovery.SimpleServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.event.Logging
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.collection.{ breakOut, immutable }
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object ConfigServicesParser {
  def parse(config: Config): Map[String, Resolved] = {
    val byService = config
      .root()
      .entrySet()
      .asScala
      .map { en =>
        (en.getKey, config.getConfig(en.getKey))
      }
      .toMap

    byService.map {
      case (serviceName, full) =>
        val endpoints = full.getConfigList("endpoints").asScala
        val resolvedTargets: immutable.Seq[ResolvedTarget] = endpoints.map { c =>
          val host = c.getString("host")
          val port = if (c.hasPath("port")) Some(c.getInt("port")) else None
          ResolvedTarget(host = host, port = port, address = None)
        }(breakOut)
        (serviceName, Resolved(serviceName, resolvedTargets))
    }
  }
}

class ConfigSimpleServiceDiscovery(system: ExtendedActorSystem) extends SimpleServiceDiscovery {

  private val log = Logging(system, getClass)

  private val resolvedServices = ConfigServicesParser.parse(
    system.settings.config.getConfig(system.settings.config.getString("akka.discovery.config.services-path"))
  )

  log.debug("Config discovery serving: {}", resolvedServices)

  override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] = {
    // TODO or fail or change the Resolved type to an ADT?
    Future
      .successful(resolvedServices.getOrElse(lookup.serviceName, Resolved(lookup.serviceName, immutable.Seq.empty)))
  }

}
