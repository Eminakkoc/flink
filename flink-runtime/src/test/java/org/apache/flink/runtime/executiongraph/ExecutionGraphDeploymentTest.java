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

package org.apache.flink.runtime.executiongraph;

import static org.apache.flink.runtime.executiongraph.ExecutionGraphTestUtils.getInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doAnswer;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.deployment.TaskDeploymentDescriptor;
import org.apache.flink.runtime.execution.ExecutionState;
import org.apache.flink.runtime.instance.AllocatedSlot;
import org.apache.flink.runtime.instance.Instance;
import org.apache.flink.runtime.jobgraph.AbstractJobVertex;
import org.apache.flink.runtime.jobgraph.DistributionPattern;
import org.apache.flink.runtime.jobgraph.JobID;
import org.apache.flink.runtime.jobgraph.JobVertexID;
import org.apache.flink.runtime.jobmanager.scheduler.Scheduler;
import org.apache.flink.runtime.operators.RegularPactTask;
import org.apache.flink.runtime.messages.TaskManagerMessages.TaskOperationResult;
import org.apache.flink.runtime.testingUtils.TestingUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.mockito.Matchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class ExecutionGraphDeploymentTest {
	private static ActorSystem system;

	@BeforeClass
	public static void setup(){
		system = ActorSystem.create("TestingActorSystem", TestingUtils.testConfig());
	}

	@AfterClass
	public static void teardown(){
		JavaTestKit.shutdownActorSystem(system);
		system = null;
	}
	
	@Test
	public void testBuildDeploymentDescriptor() {
		try {
			TestingUtils.setCallingThreadDispatcher(system);
			final JobID jobId = new JobID();
			
			final JobVertexID jid1 = new JobVertexID();
			final JobVertexID jid2 = new JobVertexID();
			final JobVertexID jid3 = new JobVertexID();
			final JobVertexID jid4 = new JobVertexID();
			
			AbstractJobVertex v1 = new AbstractJobVertex("v1", jid1);
			AbstractJobVertex v2 = new AbstractJobVertex("v2", jid2);
			AbstractJobVertex v3 = new AbstractJobVertex("v3", jid3);
			AbstractJobVertex v4 = new AbstractJobVertex("v4", jid4);
			
			v1.setParallelism(10);
			v2.setParallelism(10);
			v3.setParallelism(10);
			v4.setParallelism(10);
			
			v1.setInvokableClass(RegularPactTask.class);
			v2.setInvokableClass(RegularPactTask.class);
			v3.setInvokableClass(RegularPactTask.class);
			v4.setInvokableClass(RegularPactTask.class);
			
			v2.connectNewDataSetAsInput(v1, DistributionPattern.BIPARTITE);
			v3.connectNewDataSetAsInput(v2, DistributionPattern.BIPARTITE);
			v4.connectNewDataSetAsInput(v2, DistributionPattern.BIPARTITE);
			
			ExecutionGraph eg = new ExecutionGraph(jobId, "some job", new Configuration());

			List<AbstractJobVertex> ordered = Arrays.asList(v1, v2, v3, v4);
			
			eg.attachJobGraph(ordered);
			
			ExecutionJobVertex ejv = eg.getAllVertices().get(jid2);
			ExecutionVertex vertex = ejv.getTaskVertices()[3];
			
			// just some reference (needs not be atomic)
			final AtomicReference<TaskDeploymentDescriptor> reference = new AtomicReference<TaskDeploymentDescriptor>();

			// create synchronous task manager
			final TestActorRef simpleTaskManager = TestActorRef.create(system,
					Props.create(ExecutionGraphTestUtils
					.SimpleAcknowledgingTaskManager.class));

			final Instance instance = spy(getInstance(simpleTaskManager));
			doAnswer(new Answer<TaskOperationResult>() {

				@Override
				public TaskOperationResult answer(InvocationOnMock invocation) throws Throwable {
					TaskDeploymentDescriptor tdd = (TaskDeploymentDescriptor)invocation.getArguments()[0];
					reference.set(tdd);
					return (TaskOperationResult) invocation.callRealMethod();
				}
			}).when(instance).submitTask(Matchers
					.<TaskDeploymentDescriptor>any());
			final AllocatedSlot slot = instance.allocateSlot(jobId);
			
			assertEquals(ExecutionState.CREATED, vertex.getExecutionState());
			
			vertex.deployToSlot(slot);
			
			assertEquals(ExecutionState.RUNNING, vertex.getExecutionState());
			
			TaskDeploymentDescriptor descr = reference.get();
			assertNotNull(descr);
			
			assertEquals(jobId, descr.getJobID());
			assertEquals(jid2, descr.getVertexID());
			assertEquals(3, descr.getIndexInSubtaskGroup());
			assertEquals(10, descr.getCurrentNumberOfSubtasks());
			assertEquals(RegularPactTask.class.getName(), descr.getInvokableClassName());
			assertEquals("v2", descr.getTaskName());
			
			assertEquals(2, descr.getOutputGates().size());
			assertEquals(1, descr.getInputGates().size());
			
			assertEquals(10, descr.getOutputGates().get(0).getChannels().size());
			assertEquals(10, descr.getOutputGates().get(1).getChannels().size());
			assertEquals(10, descr.getInputGates().get(0).getChannels().size());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}finally{
			TestingUtils.setGlobalExecutionContext();
		}
	}
	
	@Test
	public void testRegistrationOfExecutionsFinishing() {
		try {
			Map<ExecutionAttemptID, Execution> executions = setupExecution(7650, 2350);
			
			for (Execution e : executions.values()) {
				e.markFinished();
			}
			
			assertEquals(0, executions.size());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	@Test
	public void testRegistrationOfExecutionsFailing() {
		try {
			Map<ExecutionAttemptID, Execution> executions = setupExecution(7, 6);
			
			for (Execution e : executions.values()) {
				e.markFailed(null);
			}
			
			assertEquals(0, executions.size());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	@Test
	public void testRegistrationOfExecutionsFailedExternally() {
		try {
			Map<ExecutionAttemptID, Execution> executions = setupExecution(7, 6);
			
			for (Execution e : executions.values()) {
				e.fail(null);
			}
			
			assertEquals(0, executions.size());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	@Test
	public void testRegistrationOfExecutionsCanceled() {
		try {
			Map<ExecutionAttemptID, Execution> executions = setupExecution(19, 37);
			
			for (Execution e : executions.values()) {
				e.cancel();
				e.cancelingComplete();
			}
			
			assertEquals(0, executions.size());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	private Map<ExecutionAttemptID, Execution> setupExecution(int dop1, int dop2) throws Exception {
		final JobID jobId = new JobID();
		
		final JobVertexID jid1 = new JobVertexID();
		final JobVertexID jid2 = new JobVertexID();
		
		AbstractJobVertex v1 = new AbstractJobVertex("v1", jid1);
		AbstractJobVertex v2 = new AbstractJobVertex("v2", jid2);
		
		v1.setParallelism(dop1);
		v2.setParallelism(dop2);
		
		v1.setInvokableClass(RegularPactTask.class);
		v2.setInvokableClass(RegularPactTask.class);
		
		// execution graph that executes actions synchronously
		ExecutionGraph eg = new ExecutionGraph(jobId, "some job", new Configuration());
		eg.setQueuedSchedulingAllowed(false);
		
		List<AbstractJobVertex> ordered = Arrays.asList(v1, v2);
		eg.attachJobGraph(ordered);
		
		// create a mock taskmanager that accepts deployment calls
		ActorRef tm = system.actorOf(Props.create(ExecutionGraphTestUtils.SimpleAcknowledgingTaskManager.class));

		Scheduler scheduler = new Scheduler();
		for (int i = 0; i < dop1 + dop2; i++) {
			scheduler.newInstanceAvailable(ExecutionGraphTestUtils.getInstance(tm));
		}
		assertEquals(dop1 + dop2, scheduler.getNumberOfAvailableSlots());
		
		// schedule, this triggers mock deployment
		eg.scheduleForExecution(scheduler);
		
		Map<ExecutionAttemptID, Execution> executions = eg.getRegisteredExecutions();
		assertEquals(dop1 + dop2, executions.size());
		
		return executions;
	}
}
