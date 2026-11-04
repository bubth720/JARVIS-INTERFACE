#!/usr/bin/env python3
"""Pinned developer tools outside checkouts; inherited proxy/TLS, no model or device installation."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import urllib.parse
import urllib.request
import zipfile

ARTIFACTS = [
    ('gradle.zip', 'https://downloads.gradle.org/distributions/gradle-8.13-bin.zip', '20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78'),
    ('jdk17.tar.gz', 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.16%2B8/OpenJDK17U-jdk_x64_linux_hotspot_17.0.16_8.tar.gz', '166774efcf0f722f2ee18eba0039de2d685b350ee14d7b69e6f83437dafd2af1'),
    ('android.zip', 'https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip', '2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258'),
]


def environment(root):
    env = os.environ.copy()
    env['JAVA_HOME'] = str(root / 'jdk-17.0.16+8')
    env['ANDROID_HOME'] = str(root / 'sdk')
    env['ANDROID_SDK_ROOT'] = env['ANDROID_HOME']
    env['GRADLE_USER_HOME'] = str(root / 'gradle-cache')
    env['PATH'] = str(root / 'jdk-17.0.16+8/bin') + ':' + env.get('PATH', '')
    env['JAVA_TOOL_OPTIONS'] = env.get('JAVA_TOOL_OPTIONS', '') + f' -Duser.home={root}/user -Djavax.net.ssl.trustStore={root}/cacerts'
    proxy = urllib.parse.urlsplit(env.get('HTTPS_PROXY') or env.get('HTTP_PROXY', ''))
    if proxy.hostname and proxy.port:
        env['GRADLE_OPTS'] = env.get('GRADLE_OPTS', '') + f' -Dhttps.proxyHost={proxy.hostname} -Dhttps.proxyPort={proxy.port} -Dhttp.proxyHost={proxy.hostname} -Dhttp.proxyPort={proxy.port}'
    return env


def provision(root):
    root.mkdir(parents=True, exist_ok=True)
    for name, url, expected in ARTIFACTS:
        path = root / name
        if not path.exists():
            partial = path.with_suffix('.part')
            with urllib.request.urlopen(url, timeout=60) as source, partial.open('wb') as target:
                shutil.copyfileobj(source, target, 1024 * 1024)
            assert hashlib.sha256(partial.read_bytes()).hexdigest() == expected, 'Tool checksum mismatch'
            partial.replace(path)
        assert hashlib.sha256(path.read_bytes()).hexdigest() == expected, 'Tool checksum mismatch'
    if not (root / 'gradle-8.13/bin/gradle').exists():
        with zipfile.ZipFile(root / 'gradle.zip') as archive: archive.extractall(root)
        (root / 'gradle-8.13/bin/gradle').chmod(0o755)
    if not (root / 'jdk-17.0.16+8/bin/java').exists():
        with tarfile.open(root / 'jdk17.tar.gz') as archive: archive.extractall(root, filter='data')
    sdk = root / 'sdk/cmdline-tools/verified'
    if not (sdk / 'bin/sdkmanager').exists():
        with zipfile.ZipFile(root / 'android.zip') as archive:
            for entry in archive.infolist():
                relative = Path(entry.filename).relative_to('cmdline-tools')
                assert '..' not in relative.parts and not relative.is_absolute()
                path = sdk / relative
                if entry.is_dir(): path.mkdir(parents=True, exist_ok=True)
                else:
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(archive.read(entry))
        for path in (sdk / 'bin').iterdir(): path.chmod(0o755)
    (root / 'user').mkdir(exist_ok=True)
    if not (root / 'cacerts').exists(): shutil.copyfile('/etc/ssl/certs/java/cacerts', root / 'cacerts')
    for cert in Path('/usr/local/share/ca-certificates').glob('*.crt'):
        alias = 'm50-ca-' + hashlib.sha256(cert.read_bytes()).hexdigest()[:16]
        cmd = [str(root / 'jdk-17.0.16+8/bin/keytool'), '-keystore', str(root / 'cacerts'), '-storepass', 'changeit', '-alias', alias]
        if subprocess.run(cmd + ['-list'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
            subprocess.run(cmd + ['-importcert', '-noprompt', '-file', str(cert)], check=True, stdout=subprocess.DEVNULL)
    env = environment(root)
    proxy = urllib.parse.urlsplit(env.get('HTTPS_PROXY') or env.get('HTTP_PROXY', ''))
    cmd = [str(sdk / 'bin/sdkmanager'), '--sdk_root=' + env['ANDROID_HOME']]
    if proxy.hostname and proxy.port: cmd += ['--proxy=http', '--proxy_host=' + proxy.hostname, '--proxy_port=' + str(proxy.port)]
    cmd += ['platforms;android-35', 'build-tools;35.0.0', 'platform-tools']
    subprocess.run(cmd, input='y\n' * 100, text=True, env=env, check=True, timeout=600)
    assert (root / 'sdk/platforms/android-35/android.jar').is_file()
    print('PASS pinned JDK17, Gradle8.13 and SDK35 tools; no model or APK installed')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path('/workspace/.m50-s1b-tools'))
    parser.add_argument('command', choices=('provision', 'gradle'))
    args, remaining = parser.parse_known_args()
    root = args.root.resolve()
    if args.command == 'provision': provision(root)
    else: raise SystemExit(subprocess.call([str(root / 'gradle-8.13/bin/gradle'), *remaining], env=environment(root)))
