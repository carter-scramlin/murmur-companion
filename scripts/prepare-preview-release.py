#!/usr/bin/env python3
"""Verify the debug APK pair and assemble the public preview assets."""
from pathlib import Path
import hashlib
import os
import re
import runpy
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if not sdk:
    raise SystemExit('Set ANDROID_HOME to the Android SDK directory.')
apksigner = Path(sdk) / 'build-tools' / '36.0.0' / 'apksigner'
certificates = []
for module in ('phone', 'wear'):
    apk = root / module / 'build' / 'outputs' / 'apk' / 'debug' / f'{module}-debug.apk'
    result = subprocess.run([str(apksigner), 'verify', '--verbose', '--print-certs', str(apk)],
                            check=True, capture_output=True, text=True)
    match = re.search(r'^Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)$', result.stdout, re.MULTILINE)
    if not match:
        raise SystemExit(f'Cannot identify the signing certificate for {module}.')
    certificates.append(match.group(1).lower())
if len(set(certificates)) != 1:
    raise SystemExit('Phone and watch signing certificates differ. Refusing to package.')

runpy.run_path(str(root / 'scripts' / 'prepare-website.py'), run_name='__main__')
version = re.search(r'versionName = "([^"]+)"', (root / 'phone' / 'build.gradle.kts').read_text()).group(1)
output = root / 'artifacts' / f'preview-release-{version}'
output.mkdir(parents=True, exist_ok=True)
# Refuse to mix an old package with a freshly signed pair.
if any(output.iterdir()):
    raise SystemExit('Preview release output directory must be empty before packaging.')
for name in ('murmur-companion-phone-debug.apk', 'murmur-companion-wear-debug.apk',
             f'murmur-companion-{version}-debug.zip'):
    shutil.copy2(root / 'website' / 'downloads' / name, output / name)
shutil.copy2(root / 'docs' / 'INSTALL.md', output / 'INSTALL.md')
(output / 'SIGNING-CERTIFICATE.txt').write_text(
    f'Murmur Companion {version} preview\n'
    'Both APKs are debug-signed with this public certificate SHA-256 digest:\n'
    + certificates[0] + '\n\n'
    'This fingerprint is public verification information, not a signing key.\n'
    'Keep this phone/watch pair together. Other preview builds may use a different key.\n')
files = sorted(output.iterdir())
(output / 'SHA256SUMS').write_text(''.join(
    f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n' for path in files))
print('Release APK signatures verified; matching pair, bundle, guide and checksums ready.')
