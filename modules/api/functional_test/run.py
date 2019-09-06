#!/usr/bin/env python
import os
import sys

basedir = os.path.dirname(os.path.realpath(__file__))
vedir = os.path.join(basedir, '.virtualenv')
os.system('./bootstrap.sh')

activate_virtualenv = os.path.join(vedir, 'bin', 'activate_this.py')
print('Activating virtualenv at ' + activate_virtualenv)

report_dir = os.path.join(os.path.dirname(os.path.realpath(__file__)), '../target/pytest_reports')
if not os.path.exists(report_dir):
    os.system('mkdir -p ' + report_dir)

execfile(activate_virtualenv, dict(__file__=activate_virtualenv))

import pytest

# Let's generate our test fixture
sys.path.append(os.path.abspath("./live_tests/shared_zone_test_context.py"))
from shared_zone_test_context import SharedZoneTestContext



result = 1

result = pytest.main(list(sys.argv[1:]))

sys.exit(result)


