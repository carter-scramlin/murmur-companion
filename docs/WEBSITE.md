# Download website

The static website lives in `website/`. It has no framework, build dependency, analytics script, external font request or backend. It includes direct APK/bundle downloads, feature coverage, keyboard-accessible installation tabs, copyable ADB commands, Tailscale guidance and preview limitations.

## Local preview

```bash
python3 scripts/prepare-website.py
python3 -m http.server 8765 --directory website
```

Open `http://localhost:8765`. Build both APKs before running the preparation script. Generated APKs, ZIPs and checksums in `website/downloads/` are ignored by Git; deployment builds them on GitHub Actions from this repository's source.

## GitHub Pages deployment

The intended repository is `carter-scramlin/murmur-companion`, with a `main` branch. If moving the project, update GitHub links in `website/index.html` to the new repository.

The **Publish download website** workflow runs on pushes to `main` or manual dispatch. It uses JDK 17, installs Android SDK 36, runs the tests and lint checks, builds both APKs in the same job, packages downloads, and deploys `website/` using GitHub's official Pages actions.

The workflow requests Pages enablement during configuration. If repository policy prevents automatic enablement, open the repository's Settings → Pages → Source and choose **GitHub Actions**, then rerun the workflow. GitHub Pages and Actions must be available for the repository/account. The public URL after successful deployment is normally `https://carter-scramlin.github.io/murmur-companion/`; verify the actual deployment output before announcing it as live.

These are preview debug builds. A fresh clean Actions runner can create a different signing key from a previous run. The phone/watch pair from each run matches, but stable upgrades require a permanent release signing key. See [release signing](RELEASING.md). The site and install guide identify the builds as previews.
