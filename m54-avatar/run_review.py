"""Compile the isolated controller against the unchanged AvatarSession, then run JVM/Python safety tests."""
import argparse,hashlib,json,os,re,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
COMPILER=[('org.jetbrains.kotlin','kotlin-compiler-embeddable','2.1.21'),('org.jetbrains.kotlin','kotlin-stdlib','2.1.21'),('org.jetbrains.kotlin','kotlin-script-runtime','2.1.21'),('org.jetbrains.kotlin','kotlin-reflect','1.6.10'),('org.jetbrains.kotlin','kotlin-daemon-embeddable','2.1.21'),('org.jetbrains.intellij.deps','trove4j','1.0.20200330'),('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.8.0'),('org.jetbrains','annotations','13.0')]
RUNTIME=[('org.jetbrains.kotlin','kotlin-stdlib','2.1.21'),('org.jetbrains','annotations','13.0'),('junit','junit','4.13.2'),('org.hamcrest','hamcrest-core','1.3')]
def jar(cache,c):
 g,n,v=c;files=list((cache/g/n/v).glob('*/'+n+'-'+v+'.jar'));assert len(files)==1
 data=files[0].read_bytes();pins=json.loads((ROOT/'m54-avatar/JARS.json').read_text())
 expected=next(p['sha256'] for p in pins if (p['group'],p['artifact'],p['version'])==c)
 assert hashlib.sha256(data).hexdigest()==expected;return files[0]
def main():
 p=argparse.ArgumentParser();p.add_argument('--jdk',type=Path,required=True);p.add_argument('--cache',type=Path,required=True);a=p.parse_args()
 output=ROOT/'build/m54-avatar/reports';output.mkdir(parents=True,exist_ok=True)
 classes=ROOT/'build/m54-avatar/classes';classes.mkdir(parents=True,exist_ok=True)
 src=[ROOT/'app/src/main/java/com/jarvis/interfaceapp/avatar/AvatarContract.kt']+list((ROOT/'m54-avatar/src').glob('*.kt'))+list((ROOT/'m54-avatar/tests').glob('*.kt'))
 observed=src+list((ROOT/'m54-avatar').glob('*.py'))+list((ROOT/'m54-avatar/tests').glob('*.py'))+[ROOT/'m54-avatar/JARS.json']
 inputs={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in observed}
 compiler=[jar(a.cache,c) for c in COMPILER];runtime=[jar(a.cache,c) for c in RUNTIME]
 def run(args,name,timeout=120):
  r=subprocess.run(args,cwd=ROOT,capture_output=True,text=True,timeout=timeout);text=r.stdout+r.stderr
  (output/(name+'.log')).write_text(text);sys.stdout.write(text);return r,text
 args=[str(a.jdk/'bin/java'),'-Xmx1g','-cp',os.pathsep.join(map(str,compiler)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-classpath',os.pathsep.join(map(str,runtime)),'-d',str(classes)]+list(map(str,src))
 compiled,_=run(args,'compile');assert compiled.returncode==0
 junit,text=run([str(a.jdk/'bin/java'),'-cp',os.pathsep.join(map(str,[classes]+runtime)),'org.junit.runner.JUnitCore','com.jarvis.interfaceapp.avatar.review.AvatarSceneTest'],'junit')
 matched=re.search(r'OK \((\d+) tests\)',text);assert junit.returncode==0 and matched
 python,pytext=run([sys.executable,'-m','unittest','discover','-s','m54-avatar/tests','-p','test_*.py','-v'],'python')
 matchpy=re.search(r'Ran (\d+) tests',pytext);assert python.returncode==0 and matchpy
 assert inputs=={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in observed}
 result={'schema':'jarvis.m54.avatar-review/1','source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'source_tree':subprocess.check_output(['git','rev-parse','HEAD^{tree}'],cwd=ROOT,text=True).strip(),'worktree_dirty':bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT)),
 'source_sha256':inputs,'jvm':'PASS','jvm_cases':int(matched[1]),'python':'PASS','python_cases':int(matchpy[1]),'source_stable':True,
 'rendering':'NOT_IMPLEMENTED','android':'NOT_RUN','artistic_master':'NOT_PROVIDED','private_inputs_processed':False,'audio':'NOT_RUN','visemes':'UNSUPPORTED','runtime_connected':False,'gate_voice_avatar':'NOT_VALIDATED'}
 (output/'results.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
if __name__=='__main__':main()
