#!/usr/bin/env python3
"""Build a small discovery-only APK from the actual MiPlay wire adapter, then probe over ADB."""
from pathlib import Path
import argparse
import os
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    args = parser.parse_args()
    sdk = Path(os.environ.get('ANDROID_HOME', '/mnt/ntfs_files_sda4/android-sdk'))
    tools = sdk / 'build-tools/37.0.0'
    platform = sdk / 'platforms/android-37.0/android.jar'
    if not platform.exists():
        platform = sdk / 'platforms/android-34/android.jar'
    out = ROOT / 'android/build/miplay-probe'
    out.mkdir(parents=True, exist_ok=True)
    classes, dex = out / 'classes', out / 'dex'
    classes.mkdir(exist_ok=True)
    dex.mkdir(exist_ok=True)
    manifest = out / 'AndroidManifest.xml'
    manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
      package="com.lladlam.melox.miplay.probe">
      <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/>
      <uses-permission android:name="android.permission.INTERNET"/>
      <queries><package android:name="com.milink.service"/></queries>
      <application android:label="MeloX MiPlay probe" android:debuggable="true"/>
      <instrumentation android:name="com.lladlam.melox.miplay.MiPlayProbeRunner"
        android:targetPackage="com.lladlam.melox.miplay.probe"/>
    </manifest>''')

    def run(*command):
        env = os.environ.copy()
        env['JAVA_TOOL_OPTIONS'] = '-Xmx256m -XX:+UseSerialGC'
        subprocess.run([str(value) for value in command], cwd=ROOT, env=env, check=True, timeout=120)

    run('javac', '--release', '8', '-classpath', platform, '-d', classes,
        ROOT / 'android/app/src/main/java/com/lladlam/melox/miplay/MiPlayTransport.java',
        *sorted((ROOT / 'android/app/src/androidTest/java/com/lladlam/melox/miplay').glob('*.java')))
    jar = out / 'probe.jar'
    with zipfile.ZipFile(jar, 'w') as archive:
        for file in classes.rglob('*.class'):
            archive.write(file, file.relative_to(classes))
    run(tools / 'd8', '--lib', platform, '--min-api', '26', '--output', dex, jar)
    apk = out / 'probe.apk'
    run(tools / 'aapt2', 'link', '-I', platform, '--manifest', manifest, '-o', apk)
    with zipfile.ZipFile(apk, 'a') as archive:
        archive.write(dex / 'classes.dex', 'classes.dex')
    key = Path.home() / '.android/debug.keystore'
    run(tools / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android', '--key-pass', 'pass:android', apk)
    run('adb', '-s', args.serial, 'install', '-r', apk)
    result = subprocess.run(['adb', '-s', args.serial, 'shell', 'am', 'instrument', '-w',
        'com.lladlam.melox.miplay.probe/com.lladlam.melox.miplay.MiPlayProbeRunner'],
        cwd=ROOT, check=True, capture_output=True, text=True, timeout=90)
    print(result.stdout, end='')
    if 'INSTRUMENTATION_CODE: -1' not in result.stdout:
        raise RuntimeError('MiPlay discovery probe failed; see instrumentation output above')


if __name__ == '__main__':
    main()
