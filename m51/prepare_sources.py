"""Deterministic opt-in source staging, not a second maintained copy of UI-01C. Historical files never edited."""
import argparse,hashlib,json,shutil,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE='0dfeef8ff09d60a68d18398ccb6b2530f3b87df4'
COCKPIT='app/src/main/java/com/jarvis/interfaceapp/cockpit/CockpitScreen.kt'
def sha(b):return hashlib.sha256(b).hexdigest()
def once(source,old,new):
    if source.count(old)!=1:raise ValueError('OVERLAY_PREIMAGE_AMBIGUOUS')
    return source.replace(old,new)
def candidate(text):
    text=once(text,'import android.content.Context','import com.jarvis.interfaceapp.m51.CapabilityPanel\nimport com.jarvis.interfaceapp.m51.rememberSystemMotionAllowed\nimport android.content.Context')
    text=once(text,'    var screen by rememberSaveable', '    val systemMotionAllowed by rememberSystemMotionAllowed()\n    var reduceMotion by remember { mutableStateOf(prefs.getBoolean("reduceMotion", false)) }\n    var screen by rememberSaveable')
    text=once(text,'tween(if (economy) 0 else 600)','tween(if (economy || reduceMotion || !systemMotionAllowed) 0 else 600)')
    text=once(text,'val animate = !sleeping && active && !economy','val animate = !reduceMotion && systemMotionAllowed && !sleeping && active && !economy')
    text=once(text,'Text("INTERFACE LOCALE", fontSize = 10.sp','Text("V1 LAB · INTERFACE LOCALE", modifier = Modifier.testTag("m51-banner"), fontSize = 10.sp')
    text=once(text,'"Supervision" -> Supervision(now, observation = observation)','"Supervision" -> CapabilityPanel()')
    text=once(text,'Text("Animations suspendues en arrière-plan et en économie d’énergie Android.")', '''Text("Animations suspendues en arrière-plan et en économie d’énergie Android.")
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(reduceMotion, { reduceMotion = it; prefs.edit().putBoolean("reduceMotion", it).apply() }, Modifier.testTag("reduce-motion"))
                                    Text("Réduire les mouvements", Modifier.weight(1f))
                                }
                                Text(if (systemMotionAllowed) "Animation système autorisée ; préférences locales respectées." else "Animations système désactivées : décor immobile.")''')
    old='''    var pulse by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate, quality) {
        if (animate) while (isActive) {
            val position = (android.os.SystemClock.uptimeMillis() % 10000L) / 5000f
            pulse = if (position <= 1f) position else 2f - position
            delay(if (quality) 33L else 66L)
        } else pulse = 0f
    }'''
    new='''    // InfiniteTransition follows the frame clock and Compose test animation policy.
    // Quantized derived state retains the former approximate 15/30 redraws per second.
    val pulseState: State<Float> = if (animate) {
        val transition = rememberInfiniteTransition(label = "office-vsync")
        val value = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse), label = "office-pulse")
        val steps = if (quality) 150 else 75
        remember(value, steps) { derivedStateOf(structuralEqualityPolicy()) { kotlin.math.round(value.value * steps) / steps } }
    } else rememberUpdatedState(0f)'''
    text=once(text,old,new)
    text=once(text,'            val w = size.width; val h = size.height','            val pulse = pulseState.value\n            val w = size.width; val h = size.height')
    return text

def main():
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    out=a.output.resolve();build=(ROOT/'m51/android-lab/build').resolve()
    if not out.is_relative_to(build):raise ValueError('OUTPUT_MUST_BE_LAB_BUILD_DIRECTORY')
    inputs={}
    for path in sorted((ROOT/'app/src/main/java').rglob('*.kt')):
        name=path.relative_to(ROOT).as_posix();base=subprocess.check_output(['git','show',BASE+':'+name],cwd=ROOT)
        if path.read_bytes()!=base:raise ValueError('FROZEN_UI_SOURCE_CHANGED:'+name)
        inputs[name]=sha(base)
    out.mkdir(parents=True,exist_ok=True)
    for part in ('shared','baseline','candidate'):
        dest=out/part
        if dest.exists():shutil.rmtree(dest)
        dest.mkdir()
    for name in inputs:
        if name==COCKPIT:continue
        rel=Path(name).relative_to('app/src/main/java');dest=out/'shared'/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes((ROOT/name).read_bytes())
    base=(ROOT/COCKPIT).read_bytes();proposed=candidate(base.decode()).encode()
    rel=Path(COCKPIT).relative_to('app/src/main/java')
    for variant,data in [('baseline',base),('candidate',proposed)]:
        dest=out/variant/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
    (out/'source-receipt.json').write_text(json.dumps({'base_commit':BASE,'inputs_sha256':inputs,'baseline_cockpit_sha256':sha(base),'candidate_cockpit_sha256':sha(proposed),'historical_files_changed':False},indent=2)+'\n')
if __name__=='__main__':main()
