/*
 *  Copyright (c) 2020 Temporal Technologies, Inc. All Rights Reserved
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

package com.antmendoza;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.ChildWorkflowFailure;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.workflow.*;

import java.time.Duration;

/**
 * Sample Temporal Workflow Definition that demonstrates the execution of a Child Workflow. Child
 * workflows allow you to group your Workflow logic into small logical and reusable units that solve
 * a particular problem. They can be typically reused by multiple other Workflows.
 */
public class HelloChild {

    // Define the task queue name
    static final String TASK_QUEUE = "HelloChildTaskQueue";

    // Define the workflow unique id
    static final String WORKFLOW_ID = "HelloChildWorkflow";

    /**
     * With the workflow, and child workflow defined, we can now start execution. The main method is
     * the workflow starter.
     */
    public static void main(String[] args) {

        // Get a Workflow service stub.
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();

        /*
         * Get a Workflow service client which can be used to start, Signal, and Query Workflow Executions.
         */
        WorkflowClient client = WorkflowClient.newInstance(service);

        /*
         * Define the worker factory. It is used to create workers for a specific task queue.
         */
        WorkerFactory factory = WorkerFactory.newInstance(client);

        /*
         * Define the worker. Workers listen to a defined task queue and process workflows and
         * activities.
         */
        Worker worker = factory.newWorker(TASK_QUEUE);

        /*
         * Register the parent and child workflow implementation with the worker.
         * Since workflows are stateful in nature,
         * we need to register the workflow types.
         */
        worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class, GreetingChildImpl.class);

        worker.registerActivitiesImplementations(new MyActivityImpl());

        /*
         * Start all the workers registered for a specific task queue.
         * The started workers then start polling for workflows and activities.
         */
        factory.start();

        // Start a workflow execution. Usually this is done from another program.
        // Uses task queue from the GreetingWorkflow @WorkflowMethod annotation.

        // Create our parent workflow client stub. It is used to start the parent workflow execution.
        GreetingWorkflow workflow =
                client.newWorkflowStub(
                        GreetingWorkflow.class,
                        WorkflowOptions.newBuilder()
                                .setWorkflowId(WORKFLOW_ID)
                                .setTaskQueue(TASK_QUEUE)
                                .build());

        // Execute our parent workflow and wait for it to complete.
        String greeting = workflow.getGreeting("World");

        // Display the parent workflow execution results
        System.out.println(greeting);
        System.exit(0);
    }

    /**
     * Define the parent workflow interface. It must contain one method annotated with @WorkflowMethod
     *
     * @see WorkflowInterface
     * @see WorkflowMethod
     */
    @WorkflowInterface
    public interface GreetingWorkflow {

        /**
         * Define the parent workflow method. This method is executed when the workflow is started. The
         * workflow completes when the workflow method finishes execution.
         */
        @WorkflowMethod
        String getGreeting(String name);
    }


    @ActivityInterface
    public interface MyActivity {
        String getGreeting(Boolean name);
    }


    /**
     * Define the child workflow Interface. It must contain one method annotated with @WorkflowMethod
     *
     * @see WorkflowInterface
     * @see WorkflowMethod
     */
    @WorkflowInterface
    public interface GreetingChild {

        /**
         * Define the child workflow method. This method is executed when the workflow is started. The
         * workflow completes when the workflow method finishes execution.
         */
        @WorkflowMethod
        String runAndThrowException(Boolean throwException);
    }

    public static class MyActivityImpl implements MyActivity {

        @Override
        public String getGreeting(final Boolean throwException) {


            if (throwException) {
                throw ApplicationFailure.newNonRetryableFailure("my exception", "myException");
            }

            return "";
        }
    }

    // Define the parent workflow implementation. It implements the getGreeting workflow method
    public static class GreetingWorkflowImpl implements GreetingWorkflow {

        @Override
        public String getGreeting(String name) {
            /*
             * Define the child workflow stub. Since workflows are stateful,
             * a new stub must be created for each child workflow.
             */
            try {
                GreetingChild child = Workflow.newChildWorkflowStub(GreetingChild.class);
                Async.function(child::runAndThrowException, true).get();
            } catch (ChildWorkflowFailure exception) {
                if (exception.getCause() instanceof ActivityFailure) {
                    //Do nothing
                }
            }


            try {
                final ChildWorkflowOptions childWorkflowOptions = ChildWorkflowOptions.newBuilder().setWorkflowId("my_child_workflowId").build();
                GreetingChild childWithId =
                        Workflow.newChildWorkflowStub(GreetingChild.class, childWorkflowOptions);
                Async.function(childWithId::runAndThrowException, false);
                Workflow.getWorkflowExecution(childWithId).get();


                //This will fail since there is another workflow running with the same id
                GreetingChild childWithDuplicated =
                        Workflow.newChildWorkflowStub(GreetingChild.class, childWorkflowOptions);
                Async.function(childWithDuplicated::runAndThrowException, false);
                Workflow.getWorkflowExecution(childWithDuplicated).get();

            } catch (ChildWorkflowFailure exception) {
                if (exception.getCause() instanceof WorkflowExecutionAlreadyStarted) {
                    throw exception;
                }

            }


            // Wait for the child workflow to complete and return its results
            return "done";
        }
    }

    /**
     * Define the parent workflow implementation. It implements the getGreeting workflow method
     *
     * <p>Note that a workflow implementation must always be public for the Temporal library to be
     * able to create its instances.
     */
    public static class GreetingChildImpl implements GreetingChild {


        MyActivity activityStub = Workflow.newActivityStub(MyActivity.class,
                ActivityOptions.newBuilder()
                        .setStartToCloseTimeout(Duration.ofSeconds(2)).build());

        @Override
        public String runAndThrowException(final Boolean throwException) {

            activityStub.getGreeting(throwException);
            Workflow.sleep(Duration.ofSeconds(2));
            return "";
        }
    }
}
