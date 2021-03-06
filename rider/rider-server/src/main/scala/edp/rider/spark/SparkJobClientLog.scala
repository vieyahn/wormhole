/*-
 * <<
 * wormhole
 * ==
 * Copyright (C) 2016 - 2017 EDP
 * ==
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * >>
 */


package edp.rider.spark

import edp.rider.common.SparkAppStatus._
import edp.rider.common.{RiderLogger, SparkRiderStatus}
import edp.rider.spark.SubmitSparkJob._

import scala.language.postfixOps
import scala.sys.process._

object SparkJobClientLog extends RiderLogger {

  def getLogByAppName(appName: String) = {
    assert(appName != "" || appName != null, "Refresh Spark Application log, app name couldn't be null or blank.")
    val logPath = getLogPath(appName)
    val command = s"tail -500 $logPath"
    riderLogger.debug(s"Refresh Spark Application $appName client log command: $command.")
    try {
      command !!
    } catch {
      case runTimeEx: java.lang.RuntimeException =>
        riderLogger.warn(s"Refresh Spark Application $appName client log command failed", runTimeEx)
        if (runTimeEx.getMessage.contains("Nonzero exit value: 1"))
          "The stream doesn't have log file."
        else runTimeEx.getMessage
      case ex: Exception => ex.getMessage
    }
  }

  def getAppStatusByLog(appName: String, curStatus: String): String = {
    assert(appName != "" && appName != null, "Refresh Spark Application log, app name couldn't be null or blank.")
    try {
      val fileLines = getLogByAppName(appName).split("\\n")
      riderLogger.debug(s"Refresh Spark Application status from client log success.")
      val hasException = fileLines.count(s => s contains "Exception") + fileLines.count(s => s contains "Error")
      val isRunning = fileLines.count(s => s contains s"(state: $RUNNING)")
      val isAccepted = fileLines.count(s => s contains s"(state: $ACCEPTED)")
      val isFinished = fileLines.count(s => s contains s"((state: $FINISHED))")
      if (hasException == 0 && isRunning > 0) SparkRiderStatus.RUNNING.toString
      else if (hasException > 0) SparkRiderStatus.FAILED.toString
      else if (isAccepted > 0) SparkRiderStatus.WAITING.toString
      else if (isFinished > 0) SparkRiderStatus.FINISHED.toString
      else curStatus
    }
    catch {
      case ex: Exception =>
        riderLogger.error(s"Refresh Spark Application status from client log failed.", ex)
        curStatus
    }
  }
}
