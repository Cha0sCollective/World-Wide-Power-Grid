"""Reproducible documentation checks against immutable published artifacts."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,sys,urllib.request,zipfile
root=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(root/'tools'))
from package_api_release import package_example
SDK_SHA='18459121e29fcd9156763a1a8f829620b4502754efec80b63bc16fbe80b136d8'
RUNTIME_SHA='7c852948a308f22946637df1dcecee7ade680ff7f4e0906d95a1425df14212dd'
API_SHA='c68a9f5824ebc80810ad697f1132170b2cf070ba5f2b8a892a1695ededf91d21'
output=root/'build/documentation-checks'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def run(args,cwd=root):subprocess.run(list(map(str,args)),cwd=cwd,check=True)
def extract(archive,target):
    if target.exists():raise ValueError('Retain earlier evidence; extraction target already exists: '+str(target))
    if os.name!='nt':run(['unzip','-q',archive,'-d',target])
    else:
        with zipfile.ZipFile(archive) as z:
            for name in z.namelist():
                if not (target/name).resolve().is_relative_to(target.resolve()):raise ValueError('Unsafe archive member')
            z.extractall(target)
def fetch(name,expected):
    p=output/name
    if not p.exists():
        with urllib.request.urlopen('https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.1/'+name) as response:p.write_bytes(response.read())
    if sha(p)!=expected:raise ValueError('Published artifact hash mismatch: '+name)
    return p

def prepare():
    output.mkdir(parents=True,exist_ok=True)
    sdk=fetch('wwpg-0.2.1-developer-sdk.zip',SDK_SHA)
    fetch('wwpg-0.2.1.jar',RUNTIME_SHA)
    extract(sdk,output/'sdk')
    extract(output/'sdk/maven/wwpg-api-1.0.0-maven.zip',output/'maven with spaces')
    repository=output/'maven with spaces'
    api=repository/'org/cha0scollective/wwpg/wwpg-api/1.0.0/wwpg-api-1.0.0.jar'
    cee=repository/'org/cha0scollective/wwpg/wwpg-cee-api/1.0.0/wwpg-cee-api-1.0.0.jar'
    if sha(api)!=API_SHA:raise ValueError('Wrong API')
    # Exercise the released archives using the documented permission repair.
    for archive,name in [('wwpg-api-example-1.0.0-source.zip','general'),('wwpg-upstream-api-example-1.0.0-source.zip','native')]:
        project=output/(name+' published example')
        extract(output/'sdk/examples'/archive,project)
        if os.name!='nt':run(['chmod','+x','gradlew'],project)
        wrapper=project/('gradlew.bat' if os.name=='nt' else 'gradlew')
        run([sys.executable,root/'tools/run_checked.py','--timeout','1200','--',wrapper,'build','-PapiRepository='+str(repository),'--no-daemon'],project)
    future=output/'tutorial-source.zip'
    package_example(root/'examples/adjustable-load',root,future)
    project=output/'tutorial with spaces';extract(future,project)
    if os.name!='nt' and not os.access(project/'gradlew',os.X_OK):raise ValueError('Future wrapper lost executable permissions under standard unzip')
    wrapper=project/('gradlew.bat' if os.name=='nt' else 'gradlew')
    # No chmod here: this proves future ZIP metadata works without hidden repair.
    run([sys.executable,root/'tools/run_checked.py','--timeout','1200','--',wrapper,'build','sdkProvenance','-PapiRepository='+str(repository),'--no-daemon'],project)
    provenance=json.loads((project/'build/sdk-provenance.json').read_text(encoding='utf-8'))
    if provenance['api_sha256']!=API_SHA or provenance['wwpg_project_dependencies']:raise ValueError('Starter was not built independently from published API')
    classes=output/'contract-classes';classes.mkdir()
    run(['javac','-cp',str(api)+os.pathsep+str(cee),'-d',classes,root/'tools/documentation_acceptance/ApiContractChecks.java'])
    run(['java','-cp',str(classes)+os.pathsep+str(api)+os.pathsep+str(cee),'ApiContractChecks'])
    (output/'input-hashes.json').write_text(json.dumps(dict(sdk=SDK_SHA,runtime=RUNTIME_SHA,api=API_SHA,starter=provenance['jar_sha256']),indent=2)+'\n',encoding='utf-8')

def server(backend):
    project=output/'tutorial with spaces';directory='run/documentation-'+backend.lower()
    args=[sys.executable,root/'tools/run_checked.py','--',root/('gradlew.bat' if os.name=='nt' else 'gradlew'),'runDocumentationServer','-I','tools/documentation_acceptance/init.gradle',
          '-PdocStarterJar='+str(project/'build/libs/tutorial-load-0.1.0.jar'),'-PdocRuntimeJar='+str(output/'wwpg-0.2.1.jar'),'-PdocDirectory='+directory,
          '-PtestNamespaces=wwpg_doc','-PtestBackend='+backend,'-Pneo_version=21.1.248','--no-daemon']
    for phase in ['SETUP','VERIFY']:
        log=output/(backend.lower()+'-'+phase.lower()+'.log')
        with log.open('w',encoding='utf-8') as stream:subprocess.run(list(map(str,args))+['-PdocPhase='+phase],cwd=root,stdout=stream,stderr=subprocess.STDOUT,check=True)
        text=log.read_text(encoding='utf-8')
        if 'All 4 required tests passed' not in text:raise ValueError('Incomplete gameplay run')
    reports=[json.loads(p.read_text(encoding='utf-8')) for p in (root/directory/'doc-evidence').glob('*.json')]
    if not reports or any(r['runtime_sha256']!=RUNTIME_SHA or r['starter_sha256']!=sha(project/'build/libs/tutorial-load-0.1.0.jar') for r in reports):raise ValueError('Gameplay loaded wrong jars')
    if not any(r.get('actual_unloads',0)>0 and r.get('restored_wires_power_load') for r in reports):raise ValueError('No actual connected chunk reload')
    if not any(r.get('phase')=='VERIFY' and r.get('checked_before_first_solve') for r in reports):raise ValueError('No pre-solve cold persistence check')
    print('DOCUMENTATION_SERVER_PASSED:',backend)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['prepare','server']);parser.add_argument('--backend',choices=['NATIVE','JAVA']);args=parser.parse_args()
    if args.mode=='prepare':prepare()
    else:
        if not args.backend:parser.error('server requires --backend')
        server(args.backend)
