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

package io.temporal.workflow.activityTests;

import io.temporal.activity.ActivityCancellationType;
import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowStub;
import io.temporal.failure.CanceledFailure;
import io.temporal.testing.internal.SDKTestOptions;
import io.temporal.testing.internal.SDKTestWorkflowRule;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.shared.TestActivities.TestActivitiesImpl;
import io.temporal.workflow.shared.TestActivities.VariousTestActivities;
import io.temporal.workflow.shared.TestWorkflows.TestWorkflow1;
import java.time.Duration;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class TryCancelActivityTest {

  private final TestActivitiesImpl activitiesImpl = new TestActivitiesImpl();

  @Rule
  public SDKTestWorkflowRule testWorkflowRule =
      SDKTestWorkflowRule.newBuilder()
          .setWorkflowTypes(TestTryCancelActivity.class)
          .setActivityImplementations(activitiesImpl)
          .build();

  @Test
  public void testTryCancelActivity() {
    TestWorkflow1 client = testWorkflowRule.newWorkflowStubTimeoutOptions(TestWorkflow1.class);
    WorkflowClient.start(client::execute, testWorkflowRule.getTaskQueue());
    testWorkflowRule
        .getTestEnvironment()
        .sleep(Duration.ofMillis(500)); // To let activityWithDelay start.
    WorkflowStub stub = WorkflowStub.fromTyped(client);
    testWorkflowRule.waitForOKQuery(stub);
    stub.cancel();
    long start = testWorkflowRule.getTestEnvironment().currentTimeMillis();
    try {
      stub.getResult(String.class);
      Assert.fail("unreachable");
    } catch (WorkflowFailedException e) {
      Assert.assertTrue(e.getCause() instanceof CanceledFailure);
    }
    long elapsed = testWorkflowRule.getTestEnvironment().currentTimeMillis() - start;
    Assert.assertTrue(String.valueOf(elapsed), elapsed < 500);
    activitiesImpl.assertInvocations("activityWithDelay");
  }

  public static class TestTryCancelActivity implements TestWorkflow1 {

    @Override
    public String execute(String taskQueue) {
      VariousTestActivities testActivities =
          Workflow.newActivityStub(
              VariousTestActivities.class,
              ActivityOptions.newBuilder(SDKTestOptions.newActivityOptionsForTaskQueue(taskQueue))
                  .setHeartbeatTimeout(Duration.ofSeconds(1))
                  .setCancellationType(ActivityCancellationType.TRY_CANCEL)
                  .build());
      testActivities.activityWithDelay(100000, true);
      return "foo";
    }
  }
}
