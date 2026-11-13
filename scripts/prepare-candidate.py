#!/usr/bin/env python3
"""Package optimized candidate APKs; preview signing is not a stable update channel."""
from pathlib import Path
import hashlib
import os
import re
import shutil
import subprocess
import zipfile
import sys

root=Path(__file__).resolve().parents[1]
signed='--signed' in sys.argv
label='signed-beta' if signed else 'candidate'
suffix='' if signed else '-candidate'
version=re.search(r'versionName = "([^"]+)"',(root/'phone/build.gradle.kts').read_text()).group(1)
watch_version=re.search(r'versionName = "([^"]+)"',(root/'wear/build.gradle.kts').read_text()).group(1)
if version!=watch_version:raise SystemExit('Phone/watch versions differ.')
sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if not sdk:raise SystemExit('Set ANDROID_HOME to the Android SDK.')
tools=Path(sdk)/'build-tools/36.0.0'
output=root/'artifacts'/f'{label}-{version}'
output.mkdir(parents=True,exist_ok=True)
if any(output.iterdir()):raise SystemExit('Candidate output must be empty before packaging.')
certificates=[];apks=[]
for module in ('phone','wear'):
    source=root/module/'build/outputs/apk/release'/f'{module}-release.apk'
    result=subprocess.check_output([str(tools/'apksigner'),'verify','--print-certs',str(source)],text=True)
    if signed and ('android debug' in result.lower() or 'murmur companion candidate' in result.lower()):raise SystemExit('Signed beta requires a private distribution key, not an Android debug key.')
    certificates.append(re.search(r'certificate SHA-256 digest: (\w+)',result).group(1))
    badging=subprocess.check_output([str(tools/'aapt'),'dump','badging',str(source)],text=True)
    if 'application-debuggable' in badging:raise SystemExit('Candidate must use a non-debuggable release build.')
    if f"versionName='{version}'" not in badging:raise SystemExit('APK version does not match source version.')
    destination=output/f'murmur-companion-{module}{suffix}.apk';shutil.copy2(source,destination);apks.append(destination)
if len(set(certificates))!=1:raise SystemExit('Phone/watch signing certificates differ.')
shutil.copy2(root/'docs/INSTALL.md',output/'INSTALL.md')
(output/'SIGNING-CERTIFICATE.txt').write_text(
    f'Murmur Companion {version} beta candidate\nPublic certificate SHA-256:\n{certificates[0]}\n\n'
    + ('Optimized release APKs signed with the configured private distribution key.\nKeep this pair together and preserve the same private key for updates.\n' if signed else 'Optimized release APKs signed with the temporary build runner preview key.\nThis is not a stable beta signing channel. Keep this exact pair together.\n'))
inside=''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in apks)
with zipfile.ZipFile(output/f'murmur-companion-{version}-{label}.zip','w',zipfile.ZIP_DEFLATED) as z:
    for p in apks+[output/'INSTALL.md',output/'SIGNING-CERTIFICATE.txt',root/'LICENSE',root/'PRIVACY.md',root/'THIRD_PARTY_NOTICES.md']:z.write(p,p.name)
    for p in (root/'docs').rglob('*'):
        if p.is_file():z.write(p,p.relative_to(root))
    z.writestr('SHA256SUMS',inside)
    z.writestr('START-HERE.md',f'# Murmur Companion {version}\n\nInstall the phone APK on Android and the matching Wear OS APK on your watch. See docs/INSTALL.md and docs/BETA.md. Build type: {label}. Read SIGNING-CERTIFICATE.txt for the signing channel. Physical device validation is still required.\n')
files=sorted(output.iterdir())
(output/'SHA256SUMS').write_text(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in files))
print(f'Optimized {label} pair verified and packaged in {output.relative_to(root)}. Device beta gates remain documented.')
