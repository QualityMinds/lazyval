package scenarios.edge

import kotlin.Int
import kotlin.jvm.JvmStatic

/**
 * Java-facing access to ValueClassFactoryPreferred, whose payload is a Kotlin value class.
 *
 * Kotlin compiles a value class away: the payload accessor's JVM name carries a signature hash and the constructor is private in the bytecode, neither of which Java can name. These two functions are the way in, and they trade in the type the value class wraps, because that is all Java can see of it.
 *
 * `internal`, so it is callable from generated Java in this compilation without becoming part of the module's API.
 */
internal object ValueClassFactoryPreferredJvmAccess {
  /**
   * Reads the unwrapped payload, which is all Java can see of Score.
   */
  @JvmStatic
  public fun score(instance: ValueClassFactoryPreferred): Int = instance.score.points

  /**
   * Rebuilds ValueClassFactoryPreferred from an unwrapped payload, re-wrapping through Score's own contract.
   */
  @JvmStatic
  public fun of(payload: Int): ValueClassFactoryPreferred = ValueClassFactoryPreferred(scenarios.edge.Score.of(payload))
}
