/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentsubscriptionfrontend.support

import com.codahale.metrics.MetricRegistry
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import scala.jdk.CollectionConverters._

trait MetricTestSupport {
  self: GuiceOneAppPerSuite with Matchers =>

  private var metricsRegistry: MetricRegistry = _

  def givenCleanMetricRegistry(): Unit = {
    val registry = app.injector.instanceOf[Metrics].defaultRegistry
    for (metric <- registry.getMetrics.keySet().iterator().asScala)
      registry.remove(metric)
    metricsRegistry = registry
  }

  def timerShouldExistAndBeUpdated(metricName: String): Assertion = {
    val timers = metricsRegistry.getTimers
    val metric = timers.get(s"Timer-$metricName")
    if (metric == null) throw new Exception(s"Metric [$metricName] not found, try one of ${timers.keySet()}")
    metric.getCount should be >= 1L
  }

  def metricShouldExistAndBeUpdated(metricNames: String*): Unit = {
    val meters = metricsRegistry.getMeters
    metricNames.foreach { metricName =>
      val metric = meters.get(metricName)
      if (metric == null) throw new Exception(s"Metric [$metricName] not found, try one of ${meters.keySet()}")
      metric.getCount should be >= 1L
    }
  }

  def noMetricExpectedAtThisPoint(): Assertion = {
    val meters = metricsRegistry.getMeters
    meters.size() shouldBe 0
  }
}
