from datetime import timedelta

from temporalio import workflow

with workflow.unsafe.imports_passed_through():
    from activities import say_hello_activity
    from shared import user_id


@workflow.defn
class SayHelloWorkflow:
    def __init__(self) -> None:
        self._complete = False

    @workflow.run
    async def run(self, name: str) -> str:
        workflow.logger.info(f"Workflow called by user {user_id.get()}")

        user_id.set("random")

        # Wait for signal then run activity
        await workflow.wait_condition(lambda: self._complete)
        activity_result = await workflow.execute_activity(say_hello_activity, name,
                                                   start_to_close_timeout=timedelta(minutes=5))

        workflow.logger.info(f"Workflow called by user {user_id.get()}")

        return activity_result

    @workflow.signal
    async def signal_complete(self) -> None:
        workflow.logger.info(f"Signal called by user {user_id.get()}")
        self._complete = True
