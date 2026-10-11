"""Record a completed graphical run without changing its frozen jar or saved ZIP."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import shutil


def describe(path):
    return {'file':path.name,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'bytes':path.stat().st_size}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run',type=Path,required=True)
    parser.add_argument('--log',type=Path,required=True)
    parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--backend',choices=['NATIVE','JAVA'],required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    extraction=json.loads((args.run/'extracted-yard.json').read_text(encoding='utf-8'))
    text=args.log.read_text(encoding='utf-8',errors='replace')
    assert describe(args.jar)['sha256']==extraction['jar']['sha256'],'Client used a different jar'
    assert 'EXPANDED_REAL_CLIENT_FAILED' not in text,'Retain this failed run; it cannot become passing evidence'
    assert 'BUILD SUCCESSFUL' in text and 'EXPANDED_REAL_CLIENT_PASSED' in text,'Graphical run did not complete'
    markers=['EXPANDED_CLIENT_BASELINE_PASSED','EXPANDED_CLIENT_PANEL_PACKETS_PASSED','EXPANDED_CLIENT_P2_PACKETS_PASSED','STATIONARY_CLIENT_PLOTTER_GRAPH_PASSED','STATIONARY_CLIENT_DUPLEX_MODEL_PASSED','STATIONARY_CLIENT_DESIGN_SCREEN_PASSED','STATIONARY_CLIENT_NATIVE_PORTAL_PASSED',f'WWPG_TEST_BACKEND_CONFIG: requested={args.backend}, configured={args.backend}']
    if extraction['profile']=='PINOUT':markers.append('STATIONARY_CLIENT_PINOUT_KEYBOARD_PASSED')
    for marker in markers:assert marker in text,f'Missing actual graphical checkpoint: {marker}'
    views=re.findall(r'EXPANDED_CLIENT_VIEW_PASSED: (\S+)',text)
    expected=150 if extraction['profile']=='PINOUT' else 148
    assert len(views)==len(set(views))==expected,'Incomplete or duplicate graphical station visits'
    assert set(re.findall(r'STATIONARY_CLIENT_CRT_BEAM: (S\d+)',text))=={'S24','S25','S26'},'Not all native CRT variants rendered a beam'
    assert 'Unable to load model:' not in text or 'duplex_wire_segment' not in '\n'.join(line for line in text.splitlines() if 'Unable to load model:' in line),'Duplex model still missing'
    output=args.output
    assert not output.exists(),'Keep each graphical run in a separate evidence directory'
    output.mkdir(parents=True)
    (output/'client.log.gz').write_bytes(gzip.compress(args.log.read_bytes(),mtime=0))
    screenshots={}
    for code in ['B08','P2','T2','S15','S24','S25','S26','S61','S77','S79']:
        shots=list((args.run/'screenshots').glob(f'*-{code}.png'))
        assert len(shots)==1,f'Missing actual screenshot for {code}'
        screenshots[code]=shots[0]
    for name in ['native-saved-design-editor.png','native-design-saved.png','native-nether-route.png','factory-off.png','factory-on.png']:
        path=args.run/'screenshots'/name
        assert path.is_file(),f'Missing actual workflow screenshot: {name}'
        screenshots[name]=path
    if extraction['profile']=='PINOUT':
        for name in ['pinout-all-off.png','pinout-odd-pins.png','pinout-signed-voltage-terminal.png','pinout-all-restored.png']:
            screenshots[name]=args.run/'screenshots'/name
            assert screenshots[name].is_file(),f'Missing actual Lua screenshot: {name}'
    for path in screenshots.values():shutil.copyfile(path,output/path.name)
    report={'schema':1,'passed':True,'mode':'Real graphical Minecraft client with an integrated server, native GUI packets and optional CC keyboard input','profile':extraction['profile'],'backend':args.backend,'jar':describe(args.jar),'exact_archive_sha256':extraction['sha256'],'views':views,'view_count':expected,'client_server_gauge_comparisons':len(re.findall(r'EXPANDED_CLIENT_READING:',text)),'checkpoints':markers,'screenshots':{code:describe(path) for code,path in screenshots.items()},'log':describe(output/'client.log.gz')}
    (output/'verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'passed':True,'profile':extraction['profile'],'backend':args.backend,'views':expected,'output':str(output)}))


if __name__=='__main__':main()
