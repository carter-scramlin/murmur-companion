#!/usr/bin/env python3
"""Verify and package a persistent-key install/debug quartet. No private keys leave the host."""
from pathlib import Path
import hashlib, os, re, shutil, subprocess, zipfile
root=Path(__file__).resolve().parents[1]
version=re.search(r'versionName = "([^"]+)"',(root/'phone/build.gradle.kts').read_text())[1]
code=re.search(r'versionCode = (\d+)',(root/'phone/build.gradle.kts').read_text())[1]
assert version==re.search(r'versionName = "([^"]+)"',(root/'wear/build.gradle.kts').read_text())[1]
assert code==re.search(r'versionCode = (\d+)',(root/'wear/build.gradle.kts').read_text())[1]
out=root/'artifacts'/f'beta-{version}'
out.mkdir(parents=True,exist_ok=True)
if any(out.iterdir()): raise SystemExit('Use an empty beta output directory to avoid mixing builds.')
sdk=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
pin=(root/'signing/beta-certificate.sha256').read_text().strip()
for module in ('phone','wear'):
    for variant,kind in (('release','install'),('debug','debug')):
        source=root/module/f'build/outputs/apk/{variant}/{module}-{variant}.apk'
        verified=subprocess.check_output([sdk/'apksigner','verify','--verbose','--print-certs',source],text=True)
        cert=re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)',verified)[1]
        if cert!=pin: raise SystemExit('Signing certificate differs from the pinned beta key.')
        manifest=subprocess.check_output([sdk/'aapt','dump','badging',source],text=True)
        if "name='dev.murmur.companion.beta'" not in manifest or f"versionName='{version}'" not in manifest or f"versionCode='{code}'" not in manifest: raise SystemExit('Unexpected application identity or version.')
        if ('application-debuggable' in manifest)!=(kind=='debug'): raise SystemExit('Incorrect debugging flag.')
        shutil.copyfile(source,out/f'murmur-companion-{module}-{kind}.apk')
shutil.copyfile(root/'docs/INSTALL.md',out/'INSTALL.md')
(out/'SIGNING-CERTIFICATE.txt').write_text(f'Murmur Beta {version}\nApplication ID: dev.murmur.companion.beta\nCertificate SHA-256: {pin}\n\nAll four APKs share this persistent private beta key. The key is not a release asset.\nInstall and debug variants replace one another. Install the phone file on the phone and the wear file on the watch.\n')
apks=sorted(out.glob('*.apk'))
with zipfile.ZipFile(out/f'murmur-companion-{version}-install.zip','w',zipfile.ZIP_DEFLATED) as archive:
    for apk in apks:
        if '-install.' in apk.name: archive.write(apk,apk.name)
    archive.writestr('SHA256SUMS',''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in apks if '-install.' in p.name))
    for path in (root/'docs').rglob('*'):
        if path.is_file():archive.write(path,path.relative_to(root))
    for name in ('LICENSE','PRIVACY.md','THIRD_PARTY_NOTICES.md'):archive.write(root/name,name)
(out/'SHA256SUMS').write_text(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in sorted(out.iterdir())))
print(f'Verified beta install/debug downloads: {out}')
