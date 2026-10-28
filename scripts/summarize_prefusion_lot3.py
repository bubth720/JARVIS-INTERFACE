#!/usr/bin/env python3
"""Publish bounded diagnostics from public synthetic Android XML, never whole logs."""
import argparse
import os
from pathlib import Path
import xml.etree.ElementTree as ET


def collect(folder):
    result={'reports':0,'tests':0,'failures':0,'errors':0,'skipped':0,'details':[]}
    for file in sorted(folder.rglob('TEST-*.xml')):
        try:
            root=ET.parse(file).getroot()
            counts={key:int(root.get(key,0)) for key in ['tests','failures','errors','skipped']}
            if root.tag!='testsuite' or any(value<0 for value in counts.values()):
                raise ValueError('Invalid suite counts')
        except (ET.ParseError,ValueError,OSError):
            result['details'].append('Invalid public XML: '+file.name[:120])
            continue
        result['reports']+=1
        for key,value in counts.items():result[key]+=value
        for case in root.findall('testcase'):
            if not case.get('classname','').startswith('com.jarvis.interfaceapp.'):
                continue
            for problem in list(case.findall('failure'))+list(case.findall('error')):
                # Only this public fixture suite is run. Never include logcat, environment,
                # stack traces, tokens, URI, package certificate or private file contents.
                text=problem.get('message','').splitlines()[0] if problem.get('message') else problem.tag
                text=''.join(c if c.isprintable() else ' ' for c in text)[:240]
                result['details'].append(case.get('classname','')[:120]+'.'+case.get('name','')[:120]+': '+text)
    result['details']=result['details'][:12]
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--folder',type=Path,default=Path('app/build/outputs/androidTest-results/connected'))
    parser.add_argument('--api',type=int,required=True,choices=[26,35])
    args=parser.parse_args()
    r=collect(args.folder)
    lines=['## Public Android fixture API'+str(args.api),
           '', 'XML reports: '+str(r['reports'])+', tests: '+str(r['tests'])+
           ', failures: '+str(r['failures'])+', errors: '+str(r['errors'])+', skipped: '+str(r['skipped'])+'.',
           '', 'This summary is diagnostic only. Missing XML does not establish execution or success.']
    if not r['reports']:lines+=['','No executed Android XML available; inspect emulator/runner step failure.']
    lines+=['','```text',*r['details'],'```']
    text='\n'.join(lines)+'\n';print(text)
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'],'a') as stream:stream.write(text)
    for detail in r['details']:
        escaped=detail.replace('%','%25').replace('\r','%0D').replace('\n','%0A')
        print('::warning title=Public Android fixture diagnostic::'+escaped)

if __name__=='__main__':main()
