"""Generate a bounded documentation companion, without modifying released SDKs."""
from pathlib import Path
import json,hashlib,sys
root=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(root/'tools'))
from package_api_release import package_example
output=root/'website/public/tutorial/0.2.1/tutorial-load-0.1.0-source.zip'
output.parent.mkdir(parents=True,exist_ok=True)
# Derived output is replaceable; immutable release files are never touched.
if output.exists(): output.unlink()
result=package_example(root/'examples/adjustable-load',root,output)
result.update(api_version='1.0.0',wwpg_version='0.2.1',purpose='documentation companion; not part of the original released SDK')
(output.parent/'source-manifest.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
print(json.dumps(result))
