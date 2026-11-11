#!/usr/bin/env python3
import re,sys
from pathlib import Path
text=Path(sys.argv[1]).read_text()
assert re.search(r'OK \([1-9][0-9]* tests?\)',text) and not re.search(r'FAILURES|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED',text),text
print('Instrumentation outcome verified:',sys.argv[1])
