import logging
from pathlib import Path
from typing import MutableMapping

import pytest

from shared_zone_test_context import SharedZoneTestContext

STATE_FILE = Path("testing_state.json")

logger = logging.getLogger(__name__)

ctx_cache: MutableMapping[str, SharedZoneTestContext] = {}


@pytest.fixture(scope="session")
def shared_zone_test_context(tmp_path_factory, worker_id):
    if worker_id == "master":
        partition_id = "1"
    else:
        partition_id = str(int(worker_id.replace("gw", "")) + 1)

    if ctx_cache.get(partition_id) is not None:
        return ctx_cache[partition_id]

    ctx = ctx_cache[partition_id] = SharedZoneTestContext(partition_id)
    ctx.setup()
    yield ctx
    del ctx_cache[partition_id]
    ctx.tear_down()


@pytest.hookimpl(tryfirst=True)
def pytest_keyboard_interrupt():
    print("cleaning up state due to interrupt")
    for partition_id, context in ctx_cache.items():
        context.tear_down()
