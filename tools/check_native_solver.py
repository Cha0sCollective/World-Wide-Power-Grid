"""Run a completed native build's focused checks and retain real outcomes."""
import argparse
import hashlib
import json
import platform
from pathlib import Path
import subprocess
import sys


def descriptor(path):
    return dict(path=str(path.resolve()),sha256=hashlib.sha256(path.read_bytes()).hexdigest(),bytes=path.stat().st_size)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build',type=Path,required=True)
    parser.add_argument('--library',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=False)
    suffix='.exe' if sys.platform=='win32' else ''
    results=[]
    for name,marker in [('reduction_checks','PASS 40740 exact scalar/candidate comparisons'),('sparse_safety_tests','PASS 37 native safety checks plus 100 valid refactor/cleanup cycles')]:
        executable=(args.build/(name+suffix)).resolve();log=args.output/(name+'.log')
        with log.open('xb') as stream:
            process=subprocess.run([str(executable)],stdout=stream,stderr=subprocess.STDOUT,timeout=120)
        results.append(dict(name=name,executable=descriptor(executable),exit_code=process.returncode,log=descriptor(log),passed=process.returncode==0 and marker in log.read_text(errors='replace')))
    proof=dict(schema=1,platform=platform.platform(),library=descriptor(args.library),checks=results,passed=all(r['passed'] for r in results))
    (args.output/'result.json').write_text(json.dumps(proof,indent=2)+'\n')
    print(json.dumps(dict(passed=proof['passed'],checks=[dict(name=r['name'],passed=r['passed']) for r in results])))
    return 0 if proof['passed'] else 1


if __name__=='__main__':sys.exit(main())
