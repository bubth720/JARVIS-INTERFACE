"""Add two observation calls to a generated test copy; never edit assertions or original sources."""
import argparse,hashlib,json,subprocess,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];BASE='9c8960a7c9d3f572dff41fe18a5b002c9f66ac8a'
INSERTIONS=[('        var record: String? = null','        com.jarvis.interfaceapp.r1.PickerWitness.fixture(context, original, name)\n'),
 ('                val root = instrumentation.uiAutomation.rootInActiveWindow','                com.jarvis.interfaceapp.r1.PickerWitness.window(context, root)\n')]
def instrument(text):
    for anchor,extra in INSERTIONS:
        assert text.count(anchor)==1,'DIAGNOSTIC_PREIMAGE_AMBIGUOUS'
        text=text.replace(anchor,extra+anchor) if anchor.startswith('        var') else text.replace(anchor,anchor+'\n'+extra.rstrip())
    return text

def main():
    a=argparse.ArgumentParser();a.add_argument('--out',type=Path,required=True);p=a.parse_args();dest=p.out.resolve()
    assert dest.is_relative_to((ROOT/'m51/android-lab/build').resolve())
    if dest.exists():shutil.rmtree(dest)
    dest.mkdir(parents=True);rows={}
    for source in sorted((ROOT/'app/src/androidTest/java').rglob('*.kt')):
        path=source.relative_to(ROOT).as_posix();base=subprocess.check_output(['git','show',BASE+':'+path],cwd=ROOT);assert source.read_bytes()==base
        new=instrument(base.decode()).encode() if source.name=='DocumentPickerContractTest.kt' else base
        out=dest/source.relative_to(ROOT/'app/src/androidTest/java');out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(new)
        rows[path]={'before_sha256':hashlib.sha256(base).hexdigest(),'after_sha256':hashlib.sha256(new).hexdigest()}
    (dest/'receipt.json').write_text(json.dumps({'base':BASE,'observation_only':True,'assertions_changed':False,'timeouts_changed':False,'tests':rows},indent=2)+'\n')
if __name__=='__main__':main()
