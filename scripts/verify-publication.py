#!/usr/bin/env python3
"""Verify public binaries built on the private signing host before GitHub publishes them."""
from pathlib import Path
import hashlib,json,os,re,subprocess
root=Path(__file__).resolve().parents[1]
staged=root/'staged-release'
metadata=json.loads((staged/'PUBLICATION.json').read_text())
source=metadata['source_sha'];version=metadata['version']
assert re.fullmatch(r'[0-9a-f]{40}',source)
assert re.fullmatch(r'\d+\.\d+\.\d+-beta\.\d+',version)
assert version==re.search(r'versionName = "([^"]+)"',(root/'phone/build.gradle.kts').read_text())[1]
subprocess.run(['git','merge-base','--is-ancestor',source,'HEAD'],check=True)
assert subprocess.check_output(['git','show',f'{source}:signing/beta-certificate.sha256'],text=True).strip()==(root/'signing/beta-certificate.sha256').read_text().strip()
# The publication branch may add assets, but may not alter source relative to the tested commit.
changed=subprocess.check_output(['git','diff','--name-only',source,'HEAD'],text=True).splitlines()
assert changed and all(path.startswith('staged-release/') for path in changed)
expected={f'murmur-companion-{module}-{kind}.apk' for module in ('phone','wear') for kind in ('install','debug')}
expected|={f'murmur-companion-{version}-install.zip','INSTALL.md','SIGNING-CERTIFICATE.txt'}
lines=(staged/'SHA256SUMS').read_text().splitlines();seen=set()
for line in lines:
    digest,name=line.split('  ',1);assert name in expected and name not in seen;seen.add(name)
    assert hashlib.sha256((staged/name).read_bytes()).hexdigest()==digest
assert seen==expected
assert {p.name for p in staged.iterdir()}==expected|{'SHA256SUMS','PUBLICATION.json'}
sdk=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
pin=(root/'signing/beta-certificate.sha256').read_text().strip()
for module in ('phone','wear'):
    for kind in ('install','debug'):
        apk=staged/f'murmur-companion-{module}-{kind}.apk'
        verified=subprocess.check_output([sdk/'apksigner','verify','--verbose','--print-certs',apk],text=True)
        assert re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)',verified)[1]==pin
        manifest=subprocess.check_output([sdk/'aapt','dump','badging',apk],text=True)
        assert "name='dev.murmur.companion.beta'" in manifest and f"versionName='{version}'" in manifest
        assert ('application-debuggable' in manifest)==(kind=='debug')
        assert ("android.hardware.type.watch" in manifest)==(module=='wear')
with open(os.environ['GITHUB_ENV'],'a') as env:
    env.write(f'SOURCE_SHA={source}\nRELEASE_VERSION={version}\nRELEASE_TAG=v{version}\n')
print('All install/debug APKs match the pinned beta certificate and the tested source publication.')
