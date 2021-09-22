#!/usr/bin/env python
import os
import sys

basedir = os.path.dirname(os.path.realpath(__file__))

report_dir = os.path.join(os.path.dirname(os.path.realpath(__file__)), '../target/pytest_reports')
if not os.path.exists(report_dir):
    os.system('mkdir -p ' + report_dir)

import pytest

result = 1
result = pytest.main(list(sys.argv[1:]))

sys.exit(result)


