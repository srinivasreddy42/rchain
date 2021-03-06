package coop.rchain.casper.genesis.contracts
import coop.rchain.casper.helper.RhoSpec
import coop.rchain.rholang.build.CompiledRholangSource
import org.scalatest.{AppendedClues, FlatSpec, Matchers}

import scala.concurrent.duration._
import monix.execution.Scheduler.Implicits.global

class TimeoutResultCollectorSpec extends FlatSpec with AppendedClues with Matchers {
  it should "testFinished should be false if execution hasn't finished within timeout" in {
    RhoSpec
      .getResults(CompiledRholangSource("TimeoutResultCollectorTest.rho"), Seq.empty)
      .runSyncUnsafe(5.seconds)
      .hasFinished should be(false)
  }
}
