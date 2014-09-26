/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.testingUtils

import akka.actor.{Props, ActorSystem}
import akka.testkit.CallingThreadDispatcher
import com.typesafe.config.ConfigFactory
import org.apache.flink.configuration.{ConfigConstants, Configuration}
import org.apache.flink.core.io.IOReadableWritable
import org.apache.flink.runtime.akka.AkkaUtils
import org.apache.flink.runtime.akka.serialization.IOReadableWritableSerializer
import org.apache.flink.runtime.executiongraph.ExecutionGraphTestUtils.ActionQueue
import org.apache.flink.runtime.minicluster.FlinkMiniCluster
import org.apache.flink.runtime.taskmanager.TaskManager

import scala.concurrent.ExecutionContext

object TestingUtils {
  val testConfig = ConfigFactory.parseString(getDefaultTestingActorSystemConfigString)

  def getDefaultTestingActorSystemConfigString: String = {
    val ioRWSerializerClass = classOf[IOReadableWritableSerializer].getCanonicalName
    val ioRWClass = classOf[IOReadableWritable].getCanonicalName

    s"""akka.daemonic = on
      |akka.loggers = ["akka.event.slf4j.Slf4jLogger"]
      |akka.loglevel = "INFO"
      |akka.logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
      |akka.stdout-loglevel = "INFO"
      |akka.jvm-exit-on-fata-error = off
      |akka.log-config-on-start = off
      |akka.actor.serializers {
      | IOReadableWritable = "$ioRWSerializerClass"
      |}
      |akka.actor.serialization-bindings {
      | "$ioRWClass" = IOReadableWritable
      |}
    """.stripMargin
  }

  def startTestingTaskManagerWithConfiguration(hostname: String, config: Configuration)
                                              (implicit system: ActorSystem) = {
    val (connectionInfo, jobManagerURL, numberOfSlots, memorySize, pageSize, tmpDirPaths,
    networkConnectionConfig, memoryUsageLogging, profilingInterval, cleanupInterval) =
      TaskManager.parseConfiguration(hostname, config);

    system.actorOf(Props(new TaskManager(connectionInfo, jobManagerURL, numberOfSlots,
      memorySize, pageSize, tmpDirPaths, networkConnectionConfig, memoryUsageLogging,
      profilingInterval, cleanupInterval) with TestingTaskManager))
  }

  def startTestingCluster(numSlots: Int, numTaskManagers: Int = 1): FlinkMiniCluster = {
    val config = new Configuration()
    config.setInteger(ConfigConstants.TASK_MANAGER_NUM_TASK_SLOTS, numSlots)
    config.setInteger(ConfigConstants.LOCAL_INSTANCE_MANAGER_NUMBER_TASK_MANAGER, numTaskManagers)
    val cluster = new TestingCluster
    cluster.start(config)

    cluster
  }

  def setGlobalExecutionContext(): Unit = {
    AkkaUtils.globalExecutionContext = ExecutionContext.global
  }

  def setCallingThreadDispatcher(system: ActorSystem): Unit = {
    AkkaUtils.globalExecutionContext = system.dispatchers.lookup(CallingThreadDispatcher.Id)
  }

  def setExecutionContext(context: ExecutionContext): Unit = {
    AkkaUtils.globalExecutionContext = context;
  }

  class QueuedActionExecutionContext(queue: ActionQueue) extends ExecutionContext {
    var automaticExecution = false

    def toggleAutomaticExecution() = {
      automaticExecution = !automaticExecution
    }

    override def execute(runnable: Runnable): Unit = {
      if(runnable.getClass.getName.equals("scala.concurrent.impl.CallbackRunnable")) {
        runnable.run()
      }else{
        queue.queueAction(runnable)
      }
    }

    override def reportFailure(t: Throwable): Unit = {
      t.printStackTrace()
    }
  }
}
