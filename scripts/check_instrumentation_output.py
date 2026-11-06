from pathlib import Path
import sys
text = Path(sys.argv[1]).read_text()
if 'OK (1 test)' not in text or any(word in text for word in ('FAILURES!!!', 'INSTRUMENTATION_FAILED', 'shortMsg=')):
    raise SystemExit('Process restart probe failed; inspect ' + sys.argv[1])
print('Process restart probe verified:', sys.argv[1])
