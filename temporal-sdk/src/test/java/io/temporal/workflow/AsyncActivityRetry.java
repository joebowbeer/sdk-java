/*
 *  Copyright (C) 2020 Temporal Technologies, Inc. All Rights Reserved.
 *
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package io.temporal.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowException;
import io.temporal.client.WorkflowStub;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowRule;
import io.temporal.testing.WorkflowReplayer;
import java.io.IOException;
import java.time.Duration;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;

public class AsyncActivityRetry {
  private final WorkflowTest.TestActivitiesImpl activitiesImpl =
      new WorkflowTest.TestActivitiesImpl(null);

  @Rule
  public TestWorkflowRule testWorkflowRule =
      TestWorkflowRule.newBuilder()
          .setWorkflowTypes(TestAsyncActivityRetry.class)
          .setActivityImplementations(activitiesImpl)
          .setUseExternalService(Boolean.parseBoolean(System.getenv("USE_DOCKER_SERVICE")))
          .setTarget(System.getenv("TEMPORAL_SERVICE_ADDRESS"))
          .build();

  @Test
  public void testAsyncActivityRetry() {
    WorkflowTest.TestWorkflow1 workflowStub =
        testWorkflowRule
            .getWorkflowClient()
            .newWorkflowStub(
                WorkflowTest.TestWorkflow1.class,
                WorkflowTest.newWorkflowOptionsBuilder(testWorkflowRule.getTaskQueue()).build());
    try {
      workflowStub.execute(testWorkflowRule.getTaskQueue());
      Assert.fail("unreachable");
    } catch (WorkflowException e) {
      Assert.assertTrue(
          String.valueOf(e.getCause().getCause()),
          e.getCause().getCause() instanceof ApplicationFailure);
      Assert.assertEquals(
          IOException.class.getName(), ((ApplicationFailure) e.getCause().getCause()).getType());
    }
    Assert.assertEquals(activitiesImpl.toString(), 3, activitiesImpl.invocations.size());
    WorkflowExecution execution = WorkflowStub.fromTyped(workflowStub).getExecution();
    WorkflowTest.regenerateHistoryForReplay(execution, "testAsyncActivityRetryHistory");
  }

  @Test
  public void testAsyncActivityRetryReplay() throws Exception {
    // Avoid executing 4 times
    Assume.assumeFalse("skipping for docker tests", WorkflowTest.useExternalService);
    WorkflowReplayer.replayWorkflowExecutionFromResource(
        "testAsyncActivityRetryHistory.json", TestAsyncActivityRetry.class);
  }

  public static class TestAsyncActivityRetry implements WorkflowTest.TestWorkflow1 {

    private WorkflowTest.TestActivities activities;

    @Override
    public String execute(String taskQueue) {
      ActivityOptions options =
          ActivityOptions.newBuilder()
              .setTaskQueue(taskQueue)
              .setHeartbeatTimeout(Duration.ofSeconds(5))
              .setScheduleToCloseTimeout(Duration.ofSeconds(5))
              .setScheduleToStartTimeout(Duration.ofSeconds(5))
              .setStartToCloseTimeout(Duration.ofSeconds(10))
              .setRetryOptions(
                  RetryOptions.newBuilder()
                      .setMaximumInterval(Duration.ofSeconds(1))
                      .setInitialInterval(Duration.ofSeconds(1))
                      .setMaximumAttempts(3)
                      .build())
              .build();
      this.activities = Workflow.newActivityStub(WorkflowTest.TestActivities.class, options);
      Async.procedure(activities::heartbeatAndThrowIO).get();
      return "ignored";
    }
  }
}