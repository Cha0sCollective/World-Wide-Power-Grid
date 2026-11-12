"""Run only guarded-native numerical, ownership and allocation subprocess checks."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import time
from build_native_solver import inputs

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def source_inputs():return {name.replace('\\','/'):digest for name,digest in inputs().items()}
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-directory',type=Path,required=True)
    parser.add_argument('--library-directory',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=False)
    record=json.loads((args.library_directory/'manifest.json').read_text())
    extension='dll' if os.name=='nt' else 'so';suffix='.exe' if os.name=='nt' else ''
    library=args.library_directory/('libpowergridNative7.'+extension)
    if sha(library)!=record['sha256'] or record['inputs']!=source_inputs():
        raise ValueError('Native library or source fingerprint differs')
    cases=[('reduction_checks',()),('sparse_safety_tests',())]
    for mode,numbers in dict(init=range(1,4),resize=range(1,6),growth=range(1,3),bounds=(0,1)).items():
        cases += [('allocation_checks',(mode,str(n))) for n in numbers]
    for mode,numbers in dict(object=range(1,5),resize=range(1,12),refs=range(1,5),growth=range(1,3)).items():
        cases += [('solver_allocation_checks',(mode,str(n))) for n in numbers]
    report=dict(scope='focused native subprocess checks; not full acceptance',platform=platform.platform(),
        library=dict(path=str(library),sha256=sha(library)),inputs=source_inputs(),checks=[],passed=False)
    env=dict(os.environ,OPENBLAS_NUM_THREADS='1')
    for name,arguments in cases:
        executable=args.build_directory/(name+suffix)
        label='-'.join((name,*arguments));log=args.output/(label+'.log');start=time.monotonic()
        with log.open('xb') as stream:
            try:
                result=subprocess.run([str(executable),*arguments],stdout=stream,stderr=subprocess.STDOUT,env=env,timeout=30)
                code=result.returncode
            except subprocess.TimeoutExpired:code=124
        report['checks'].append(dict(name=label,exit_code=code,passed=code==0,seconds=time.monotonic()-start,
            executable_sha256=sha(executable),log=dict(path=str(log),sha256=sha(log))))
        (args.output/'result.json').write_text(json.dumps(report,indent=2)+'\n')
    report['passed']=all(row['passed'] for row in report['checks'])
    (args.output/'result.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(dict(passed=report['passed'],checks=len(cases),report=str(args.output/'result.json'))))
    return 0 if report['passed'] else 1
if __name__=='__main__':raise SystemExit(main())
