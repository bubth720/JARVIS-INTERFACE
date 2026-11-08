"""Verify and stage the exact APKs that will be installed by the emulator job, no signing keys."""
import hashlib,json,os,re,shutil,subprocess,zipfile,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def run(*args):return subprocess.check_output(list(map(str,args)),text=True,cwd=ROOT)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    out=ROOT/'build/m51-delivery';out.mkdir(parents=True,exist_ok=True)
    tools=Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0';rows=[];baseline_native=None
    for variant in ('baseline','candidate'):
        folder=ROOT/f'm51/android-lab/build/outputs/apk/{variant}/debug'
        files=list(folder.glob('*.apk'));assert len(files)==1
        apk=files[0];package='fr.jarvis.v1.lab'+('.baseline' if variant=='baseline' else '')
        badging=run(tools/'aapt2','dump','badging',apk);manifest=run(tools/'aapt2','dump','xmltree',apk,'--file','AndroidManifest.xml')
        signature=run(tools/'apksigner','verify','--verbose','--print-certs',apk)
        assert f"package: name='{package}'" in badging
        assert re.search(r'android:testOnly\([^\n]+\)=(?:true|\(type 0x12\)0xffffffff)',manifest)
        assert re.search(r'android:allowBackup\([^\n]+\)=(?:false|\(type 0x12\)0x0)',manifest)
        permissions=re.findall(r"uses-permission(?:-sdk-\d+)?: name='([^']+)'",badging)
        assert all(p==package+'.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION' for p in permissions),permissions
        if permissions:assert re.search(r'android:protectionLevel\([^\n]+\)=0x00000002',manifest)
        with zipfile.ZipFile(apk) as z:
            native_names=[n for n in z.namelist() if n.startswith('lib/') and n.endswith('.so')]
            allowed={'lib/'+abi+'/libandroidx.graphics.path.so' for abi in ('arm64-v8a','armeabi-v7a','x86','x86_64')}
            assert set(native_names)==allowed,native_names
            natives={n:hashlib.sha256(z.read(n)).hexdigest() for n in sorted(native_names)}
            if variant=='baseline':baseline_native=natives
            else:assert natives==baseline_native,'UI native dependencies must match frozen baseline exactly'
            assert not any(n.lower().endswith(('.gguf','.onnx','.safetensors','.key','.jks','.keystore','.mp4','.wav')) for n in z.namelist())
        cert=re.search(r'certificate SHA-256 digest: ([a-f0-9]{64})',signature).group(1)
        name='M51-'+variant+'.apk';shutil.copy2(apk,out/name)
        tests=list((ROOT/f'm51/android-lab/build/outputs/apk/androidTest/{variant}/debug').glob('*.apk'));assert len(tests)==1
        testname='M51-'+variant+'-tests.apk';shutil.copy2(tests[0],out/testname)
        for label,text in [('manifest',manifest),('badging',badging),('signature',signature)]: (out/f'{variant}-{label}.txt').write_text(text)
        rows.append({'variant':variant,'package':package,'apk':name,'sha256':sha(out/name),'test_apk':testname,'test_sha256':sha(out/testname),
                     'certificate_sha256':cert,'test_only':True,'allow_backup':False,'permissions':permissions,'native_libraries':natives,
                     'abi':['arm64-v8a','armeabi-v7a','x86','x86_64'],'native_scope':'UI_GRAPHICS_PATH_ONLY_NO_LLM','page_16k_android':'NOT_RUN','min_sdk':26,'target_sdk':35,'version_code':51,'version_name':'0.51.0-m51-lab'})
    suites={}
    for name,path in [('historical',ROOT/'app/build/test-results/testDebugUnitTest'),('lab-baseline',ROOT/'m51/android-lab/build/test-results/testBaselineDebugUnitTest'),('lab-candidate',ROOT/'m51/android-lab/build/test-results/testCandidateDebugUnitTest')]:
        files=list(path.glob('*.xml'));assert files,name
        totals={k:sum(int(ET.parse(p).getroot().get(k,'0')) for p in files) for k in ('tests','failures','errors','skipped')}
        assert totals['tests']>0 and totals['failures']==totals['errors']==totals['skipped']==0
        suites[name]=totals
    record={'source_commit':run('git','rev-parse','HEAD').strip(),'source_tree':run('git','rev-parse','HEAD^{tree}').strip(),
            'ui_base_commit':'0dfeef8ff09d60a68d18398ccb6b2530f3b87df4','apks':rows,'jvm_tests':suites,
            'runtime_connections':['INTERFACE_LOCAL_ONLY'],'physical_android':'NOT_RUN','llm_generation':'NOT_RUN'}
    shutil.copy2(ROOT/'m51/android-lab/build/generated/m51/source-receipt.json',out/'source-receipt.json')
    (out/'build.json').write_text(json.dumps(record,indent=2)+'\n')
    (out/'SHA256SUMS').write_text(''.join(sha(p)+'  '+p.name+'\n' for p in sorted(out.iterdir()) if p.is_file() and p.name!='SHA256SUMS'))
    print(json.dumps(record,indent=2))
if __name__=='__main__':main()
