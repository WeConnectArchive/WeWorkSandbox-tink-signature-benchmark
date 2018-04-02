/*
 * Copyright 2016 WeWork LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("SignatureBenchmark")
package co.we.tink

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.converters.FileConverter
import com.google.crypto.tink.Config
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.proto.KeyTemplate
import com.google.crypto.tink.signature.PublicKeySignFactory
import com.google.crypto.tink.signature.SignatureConfig
import com.google.crypto.tink.signature.SignatureKeyTemplates
import org.nield.kotlinstatistics.geometricMean
import org.nield.kotlinstatistics.median
import org.nield.kotlinstatistics.standardDeviation
import java.io.File
import java.io.FileWriter
import java.io.Writer
import java.security.SecureRandom
import java.text.DecimalFormat
import kotlin.math.roundToInt
import kotlin.math.roundToLong

fun main(args: Array<String>) {
  Config.register(SignatureConfig.TINK_1_0_0)

  val params = BenchmarkParameters()
  val jcomm = JCommander.newBuilder()
    .addObject(params)
    .build()
  jcomm.parse(*args)

  if (params.help) {
    jcomm.usage()
    return
  }

  println("Generating key of type ${params.algorithm.name}")
  val keySet = KeysetHandle.generateNew(params.algorithm.keyTemplate)
  val signer = PublicKeySignFactory.getPrimitive(keySet)

  println("Preparing random ${params.dataSize} byte inputs for signing")
  val random = SecureRandom()
  val inputs = 1.rangeTo(params.numOpsPerRound).map { ByteArray(params.dataSize, { random.nextInt().toByte() }) }

  println("Performing ${params.numWarmupRounds} warmup rounds")
  measurePerformance(signer, params.numWarmupRounds, inputs, null)

  println("Performing ${params.numRounds} rounds, measuring ${params.numOpsPerRound} ops per round")
  val results = FileWriter(params.resultsOutputFile).use { measurePerformance(signer, params.numRounds, inputs, it) }

  val median = results.median().roundToLong()
  val mean = results.geometricMean().roundToLong()
  val standardDeviation = results.standardDeviation().roundToLong()

  println("Basic analysis:")
  println("- Median operation time: ${ median } ns")
  println("- Mean operation time: ${ mean } ns")
  println("- Operation time stddev: ${ standardDeviation } ns")
  println("- Median operations per second: ${ (1e9 / median).roundToInt() }")
  println("- Mean operations per second: ${ (1e9 / mean).roundToInt() }")
}

fun measurePerformance(
  signer: PublicKeySign,
  numRounds: Int,
  opInputData: List<ByteArray>,
  out: Writer?
): List<Long> {
  val results = ArrayList<Long>(numRounds)
  val opsPerRound = opInputData.lastIndex + 1

  out?.write("roundStartTime,opAverageDurationInRound")

  for (round in 1 .. numRounds) {
    var roundDuration = 0L
    val roundStartTime = System.nanoTime()

    for (i in opInputData.indices) {
      val opStartTime = System.nanoTime()
      signer.sign(opInputData[i])
      val opEndTime = System.nanoTime()
      roundDuration += opEndTime - opStartTime
    }

    val opAverageDurationInRound = roundDuration / opsPerRound
    out?.write("\n${roundStartTime},${opAverageDurationInRound}")
    results.add(opAverageDurationInRound)

    renderProgress(round, numRounds)
  }

  return results
}

fun renderProgress(round: Int, numRounds: Int) {
  val percentComplete = (round.toDouble() / numRounds * 100)
  val percentCompleteString = DecimalFormat("###.0").format(percentComplete).padStart(5)
  val completionBar = "#".repeat(percentComplete.roundToInt() / 2).padEnd(50)
  println("[${completionBar}] ${percentCompleteString}%")
}

class BenchmarkParameters {
  @Parameter(
    names = ["--alg"],
    description = "The signature algorithm to benchmark.")
  var algorithm: SignatureAlgorithm = SignatureAlgorithm.ED25519;

  @Parameter(
    names = ["--output"],
    description = "The file to which the raw results should be written.",
    converter = FileConverter::class)
  var resultsOutputFile = File("results.csv")

  @Parameter(
    names = ["--warmup-rounds"],
    description = "The number of benchmark numRounds to perform as a warmup. Timings captured from these numRounds " +
      "will be discarded.")
  var numWarmupRounds: Int = 10

  @Parameter(
    names = ["--rounds"],
    description = "The number of benchmark numRounds to be performed")
  var numRounds: Int = 1_000

  @Parameter(
    names = ["--ops"],
    description = "The number of signing operations to be performed per round")
  var numOpsPerRound: Int = 1_000

  @Parameter(
    names = ["--data-size"],
    description = "The size (in bytes) of the messages to sign")
  var dataSize: Int = 59

  @Parameter(
    names = ["--help"],
    help = true)
  var help: Boolean = false
}

enum class SignatureAlgorithm(val keyTemplate: KeyTemplate) {
  ED25519(SignatureKeyTemplates.ED25519),
  ECDSA_P256(SignatureKeyTemplates.ECDSA_P256),
  ECDSA_P384(SignatureKeyTemplates.ECDSA_P384),
  ECDSA_P521(SignatureKeyTemplates.ECDSA_P521)
}