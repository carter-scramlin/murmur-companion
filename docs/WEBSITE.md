# Download website

The static website lives in `website/`. It has no framework, build dependency, analytics script, external font request or backend. It includes direct APK/bundle downloads, feature coverage, keyboard-accessible installation tabs, copyable ADB commands, Tailscale guidance and preview limitations.

## Local preview

```bash
python3 -m http.server 8765 --directory website
```

Open `http://localhost:8765`. Download links use the public GitHub Release, so a local Android build is not required to preview the website. The optional `scripts/prepare-website.py` helper still packages local APKs into the ignored `website/downloads/` directory for release packaging.

## GitHub Pages deployment

The intended repository is `carter-scramlin/murmur-companion`, with a `main` branch. If moving the project, update GitHub links in `website/index.html` to the new repository.

The **Publish download website** workflow runs on pushes to `main` or manual dispatch. It deploys the static `website/` directory using GitHub's official Pages actions. Android tests, lint, builds and release packaging run in the separate release workflow.

The workflow requests Pages enablement during configuration. If repository policy prevents automatic enablement, open the repository's Settings → Pages → Source and choose **GitHub Actions**, then rerun the workflow. GitHub Pages and Actions must be available for the repository/account. The public URL after successful deployment is normally `https://carter-scramlin.github.io/murmur-companion/`; verify the actual deployment output before announcing it as live.

These are preview debug builds. A fresh clean Actions runner can create a different signing key from a previous run. Both APKs in a published release match and stay fixed, but stable upgrades across versions require a permanent release signing key. See [release signing](RELEASING.md). The site and install guide identify the builds as previews.

## Versioned release downloads

The download buttons now use the fixed v0.2.0 GitHub Release assets. The Pages workflow deploys only the static site. The separate preview release workflow builds and verifies both apps before publishing. See [RELEASING.md](RELEASING.md).
