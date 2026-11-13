#!/usr/bin/env python3
"""Package a verified local/CI APK pair for the GitHub Pages download site."""
from pathlib import Path
import hashlib
import shutil
import zipfile

root = Path(__file__).resolve().parents[1]
downloads = root / 'website' / 'downloads'
downloads.mkdir(parents=True, exist_ok=True)
apks = []
for module in ('phone', 'wear'):
    source = root / module / 'build' / 'outputs' / 'apk' / 'debug' / f'{module}-debug.apk'
    if not source.is_file():
        raise SystemExit(f'Build both debug APKs first; missing {source.relative_to(root)}')
    destination = downloads / f'murmur-companion-{module}-debug.apk'
    shutil.copy2(source, destination)
    apks.append(destination)

checksums = downloads / 'SHA256SUMS'
checksums.write_text(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in apks))
with zipfile.ZipFile(downloads / 'murmur-companion-0.1.0-debug.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in apks + [checksums, root / 'LICENSE', root / 'PRIVACY.md', root / 'THIRD_PARTY_NOTICES.md']:
        archive.write(path, path.name)
    for path in (root / 'docs').rglob('*'):
        if path.is_file():
            archive.write(path, path.relative_to(root))
    archive.writestr('START-HERE.md', '# Murmur Companion 0.1.0 preview\n\nInstall the phone APK on Android and the matching watch APK on your paired Wear OS watch. Follow docs/INSTALL.md for every step and wireless ADB commands. See docs/TAILSCALE.md for remote access and docs/FEATURES.md for current limitations. These debug APKs share a signing certificate; keep the pair together.\n')
print('Website APK pair, bundle and checksums are ready.')
