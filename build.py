#!/usr/bin/env python3
"""Standalone Linux build: Python 3 + JRE 17 + keytool, no Android SDK required."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent
TOOLS = ROOT / '.tools'
BUILD = ROOT / 'app/build'
DEPS = {
    'ecj.jar': ('https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.39.0/ecj-3.39.0.jar', '01f5a92ac19bb2b3bf85e295a68f2c73c264369109158b566ce9b490af982948'),
    'android.jar': ('https://repo.maven.apache.org/maven2/com/google/android/android/4.1.1.4/android-4.1.1.4.jar', '84072541cbb711eff89f7277100ff854929a446dba7ceb1b195c340e0b4fd3cb'),
    'xposed.jar': ('https://maven.aliyun.com/repository/public/de/robv/android/xposed/api/82/api-82.jar', 'f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25'),
    'r8.jar': ('https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.6.27/r8-8.6.27.jar', '9007144728d47b5f11338c9292d5b74899b6579ad9ddc6942d419e3600c3d6d7'),
    'aapt2.jar': ('https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/8.6.1-11315950/aapt2-8.6.1-11315950-linux.jar', '955f14e61cac2e1d70a4703b1aa539ca4f348469ac64a155dcce47bda3de52c8'),
    'apksigner.jar': ('https://dl.google.com/dl/android/maven2/com/android/tools/build/apksig/8.6.1/apksig-8.6.1.jar', 'c070ed1394629d74641aa0906f60b2ffa1ee77e6366a1f93437f59717b1aeb89'),
}

def run(*args):
    subprocess.run([str(x) for x in args], check=True, cwd=ROOT)

def main():
    TOOLS.mkdir(exist_ok=True)
    BUILD.mkdir(parents=True, exist_ok=True)
    for name, (url, digest) in DEPS.items():
        dest = TOOLS / name
        if not dest.exists():
            print('Download', name, flush=True)
            with urllib.request.urlopen(url, timeout=90) as response, dest.open('wb') as out:
                shutil.copyfileobj(response, out)
        if hashlib.sha256(dest.read_bytes()).hexdigest() != digest:
            raise RuntimeError('Dependency checksum mismatch: ' + name)
    aapt = TOOLS / 'aapt2'
    with zipfile.ZipFile(TOOLS / 'aapt2.jar') as z:
        aapt.write_bytes(z.read('aapt2'))
    aapt.chmod(0o755)
    classes = BUILD / 'classes'
    signer = BUILD / 'signer'
    dex = BUILD / 'dex'
    for p in (classes, signer, dex):
        if p.exists(): shutil.rmtree(p)
        p.mkdir()
    cp = os.pathsep.join(str(TOOLS / x) for x in ('android.jar', 'xposed.jar'))
    run('java', '-jar', TOOLS/'ecj.jar', '-8', '-encoding', 'UTF-8', '-cp', cp, '-d', classes,
        ROOT/'app/src/main/java/local/xiaocan/noads/Entry.java')
    run('java', '-jar', TOOLS/'ecj.jar', '-17', '-cp', TOOLS/'apksigner.jar', '-d', signer, ROOT/'build-support/Sign.java')
    run('java', '-cp', TOOLS/'r8.jar', 'com.android.tools.r8.D8', '--min-api', '24', '--lib', TOOLS/'android.jar',
        '--classpath', TOOLS/'xposed.jar', '--output', dex, *sorted(classes.rglob('*.class')))
    run(aapt, 'compile', '--dir', ROOT/'app/src/main/res', '-o', BUILD/'res.zip')
    run(aapt, 'link', '-o', BUILD/'base.apk', '-I', TOOLS/'android.jar', '--manifest', ROOT/'app/src/main/AndroidManifest.xml',
        '-A', ROOT/'app/src/main/assets', BUILD/'res.zip')
    with zipfile.ZipFile(BUILD/'base.apk') as src, zipfile.ZipFile(BUILD/'unsigned.apk', 'w') as dst:
        for item in src.infolist(): dst.writestr(item, src.read(item.filename))
        dst.write(dex/'classes.dex', 'classes.dex', compress_type=zipfile.ZIP_DEFLATED)
    key = BUILD/'module.jks'
    if not key.exists():
        run('keytool', '-genkeypair', '-keystore', key, '-storetype', 'JKS', '-storepass', 'android', '-keypass', 'android',
            '-alias', 'module', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
            '-dname', 'CN=XiaoCanNoAds Test Module', '-noprompt')
    output = BUILD/'XiaoCan-NoAds-v0.4.apk'
    run('java', '-cp', os.pathsep.join((str(TOOLS/'apksigner.jar'), str(signer))), 'Sign', BUILD/'unsigned.apk', output, key)
    print('Built:', output)

if __name__ == '__main__':
    main()
