"""Capture current steady-TPS baselines and their CPU placement evidence."""
import argparse
import csv
import json
import math
from pathlib import Path

from cpu_affinity import percentages
from summarize_tree import ROOT, evidence, read, sha, host_summary


def capture(name):
    folder=ROOT/'local/benchmarks'/name
    protocol=read(folder/'experiment.json')
    summary=read(folder/'benchmark/summary.json')
    assert protocol['validation']['passed'] and summary['passed']
    assert protocol['original_reference_unchanged'] and protocol['isolated_reference_unchanged']
    for kind in ('runtime','helper'):
        artifact=read(folder/'launch.json')[kind]
        assert sha(Path(artifact['path']))==artifact['sha256']
    with (folder/'benchmark/placement-ticks.csv').open() as stream:
        placed=[r for r in csv.DictReader(stream) if r['phase']=='STABLE']
    with (folder/'benchmark/ticks.csv').open() as stream:
        ticks=[r for r in csv.DictReader(stream) if r['phase']=='STABLE']
    assert ticks and all(int(r['pg_solve_calls'])==16 and int(r['matrix_assemblies'])==0 for r in ticks)
    assert read(folder/'benchmark/graph-start.json')==read(folder/'benchmark/graph-end.json')
    start,end=int(placed[0]['wall_unix_ms'])/1000,int(placed[-1]['wall_unix_ms'])/1000
    blocks=[]
    for block in range(math.ceil((end-start)/30)):
        selected=[r for r in placed if block*30 <= int(r['wall_unix_ms'])/1000-start < (block+1)*30]
        if selected:
            blocks.append(dict(block=block, ticks=len(selected), mean_tick_ms=sum(int(r['wall_tick_ns']) for r in selected)/len(selected)/1e6))
    result=dict(name=name,protocol=protocol,summary=summary,loading=read(folder/'benchmark/loading-results.json'),
                thirty_second_blocks=blocks,host=host_summary(folder),
                evidence=[evidence(folder/p) for p in ('experiment.json','launch.json','invocation.json','process.log',
                    'runner-exit.json','shutdown-save.json','host-metrics.jsonl','process-metrics.jsonl',
                    'benchmark/result.json','benchmark/summary.json','benchmark/loading-results.json',
                    'benchmark/ticks.csv','benchmark/placement-ticks.csv','benchmark/cpu-topology.json',
                    'benchmark/placement-fixed.json','benchmark/placement-restored.json',
                    'benchmark/graph-start.json','benchmark/graph-end.json','benchmark/pre-solve-storage.json')])
    mask=protocol.get('cpu_affinity_mask')
    if mask:
        proof=read(folder/'process-affinity.json')
        assert proof['requested_mask']==proof['readback_mask']==mask
        samples=[json.loads(line) for line in (folder/'process-metrics.jsonl').read_text().splitlines()]
        assert samples and all(s['cpu_affinity_mask']==mask for s in samples)
        numeric=int(mask,16)
        assert all(numeric & 1<<int(r[k]) for r in placed for k in ('entry_cpu','exit_cpu'))
        result['process_affinity']=proof
        result['affinity_readbacks']=len(samples)
        result['evidence'].append(evidence(folder/'process-affinity.json'))
        host=[json.loads(line) for line in (folder/'host-metrics.jsonl').read_text().splitlines()]
        totals=None;seconds=0;intervals=0
        for a,b in zip(host,host[1:]):
            if a['wall_unix']<start or b['wall_unix']>end:continue
            busy=percentages(a['logical_cpu_times'],b['logical_cpu_times'])
            elapsed=b['wall_unix']-a['wall_unix']
            if totals is None:totals=[0.0]*len(busy)
            totals=[v+p*elapsed for v,p in zip(totals,busy)]
            seconds+=elapsed;intervals+=1
        assert intervals>0
        result['stable_cpu_busy_percent']=[v/seconds for v in totals]
        result['stable_cpu_observation_seconds']=seconds
        result['cpu_observation_note']='Total per-core load including the benchmark; affinity restricts this process but does not reserve CPUs against other processes.'
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--attempts',nargs='+',required=True)
    parser.add_argument('--output',required=True)
    args=parser.parse_args()
    runs=[capture(n) for n in args.attempts]
    data=dict(schema=1,full_suite=False,runs=runs,
              note='Whole-process placement uses a different explicit helper overlay. These observations are not a same-protocol runtime optimization A/B or rendered-world evidence.')
    with (ROOT/args.output).open('x',encoding='utf-8',newline='\n') as stream:
        stream.write(json.dumps(data,indent=2)+'\n')
    print(json.dumps([dict(name=r['name'],tps=r['summary']['tps'],mspt=r['summary']['mspt'],loading_seconds=r['loading'][0]['total_restoration_seconds']) for r in runs]))


if __name__=='__main__':main()
