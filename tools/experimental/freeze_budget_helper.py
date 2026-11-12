"""Build a separate loading helper with an observed, finite wire-queue stage."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
from freeze_tree import ROOT, sha


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name',required=True)
    parser.add_argument('--inputs',required=True)
    args=parser.parse_args()
    inputs=json.loads((ROOT/args.inputs).read_text())
    folder=(ROOT/'local/frozen'/args.name).resolve()
    assert folder.is_relative_to(ROOT/'local/frozen')
    folder.mkdir(parents=True,exist_ok=False)
    original=ROOT/'local/inputs/wwpg-benchmark-0.1.0.jar'
    assert sha(original)=='cb67f3bbe08568992e1664558a3dc503c876be8cad5badf7872efbd763e3224e'
    package='org/cha0scollective/wwpg/benchmark'
    source=folder/'src'/package;source.mkdir(parents=True)
    text=(ROOT/'local/inputs/helper-src'/package/'Session.java').read_text()
    replacements=[
        ('private LoadingGate loadingGate;', 'private LoadingGate loadingGate;\n    private long queueReadyNs=-1, queueReadyTick=-1, rootsAtChunkReady, maxPendingRoots;'),
        ('boolean chunks=grid.ready(),graph=false,accepted=false;', 'long queued=org.cha0scollective.wwpg.experimental.WireTreeBatch.pending(server.overworld());maxPendingRoots=Math.max(maxPendingRoots,queued);\n        boolean chunks=grid.ready(),graph=false,accepted=false;'),
        ('readySince=tick;chunksReadyNs=now;', 'readySince=tick;chunksReadyNs=now;rootsAtChunkReady=queued;'),
        ('var topology=Metrics.topology();', 'if(queued==0&&queueReadyNs<0){queueReadyNs=now;queueReadyTick=tick;}\n            var topology=Metrics.topology();'),
        ('if(graph){if(graphReadyNs<0)', 'graph &= queued==0;\n            if(graph){if(graphReadyNs<0)'),
        ('loadingGate.observe(tick-phaseTick,now,progress,chunks,graph,accepted)', 'loadingGate.observe(tick-phaseTick,now,progress,chunks,graph,accepted,queued)'),
        ('result.addProperty("healthy_ticks",loadingGate.healthyTicks());', 'result.addProperty("healthy_ticks",loadingGate.healthyTicks());result.addProperty("queued_roots_at_chunk_ready",rootsAtChunkReady);result.addProperty("maximum_pending_roots",maxPendingRoots);result.addProperty("queue_drained_tick",queueReadyTick-phaseTick);result.addProperty("queue_drain_seconds",(queueReadyNs-chunksReadyNs)/1e9);'),
        ('loadingGate=null;observedGraph=null;', 'loadingGate=null;queueReadyNs=queueReadyTick=-1;rootsAtChunkReady=maxPendingRoots=0;observedGraph=null;')]
    for old,new in replacements:
        assert text.count(old)==1,old
        text=text.replace(old,new)
    (source/'Session.java').write_text(text,encoding='utf-8',newline='\n')
    for name in ('LoadingGate.java','LoadingGateChecks.java'):
        shutil.copy2(ROOT/'tools/experimental/budget-helper'/name,source/name)
    env=dict(os.environ);env['JAVA_HOME']=inputs['java_home'];env['PATH']=str(Path(inputs['java_home'])/'bin')+os.pathsep+env['PATH']
    command=['gradlew.bat','checkBudgetLoadingGate','--offline','--no-daemon','-I','tools/experimental/budget-helper.gradle',
             '-PbudgetHelperDirectory='+str(folder),'-PbudgetRuntime='+inputs['artifacts']['runtime']['path']]
    with (folder/'compile.log').open('w') as stream:
        result=subprocess.run(command,cwd=ROOT,env=env,stdout=stream,stderr=subprocess.STDOUT)
    log=(folder/'compile.log').read_text(errors='replace')
    if result.returncode or 'BUDGET_GATE_CHECKS passed=7' not in log:
        print(log[-12000:]);raise SystemExit(1)
    output=folder/'wwpg-benchmark-0.1.0-budget.jar'
    expected={package+'/'+name+'.class' for name in ('LoadingGate','LoadingGate$Stage','Session')}
    with zipfile.ZipFile(original) as old,zipfile.ZipFile(output,'w') as new:
        for item in old.infolist():
            replacement=folder/'classes'/item.filename
            data=replacement.read_bytes() if item.filename in expected else old.read(item)
            new.writestr(item,data)
    with zipfile.ZipFile(original) as old,zipfile.ZipFile(output) as new:
        changed={n for n in old.namelist() if old.read(n)!=new.read(n)}
        assert changed==expected and old.namelist()==new.namelist()
    sources=[dict(path=p.relative_to(ROOT).as_posix(),sha256=sha(p)) for p in sorted((folder/'src').rglob('*.java'))]
    manifest=dict(schema=1,helper=dict(path=str(output),sha256=sha(output)),base_helper=dict(path=str(original),sha256=sha(original)),
                  runtime=inputs['artifacts']['runtime'],sources=sources,changed_entries=sorted(changed),gate_checks=7,
                  command=command,compile_log=dict(path=(folder/'compile.log').relative_to(ROOT).as_posix(),sha256=sha(folder/'compile.log')))
    (folder/'inputs.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps(dict(inputs=str(folder/'inputs.json'),helper=manifest['helper'],gate_checks=7)))


if __name__=='__main__':main()
