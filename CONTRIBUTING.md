# Contributing

Start with a bug report or a concrete use case. Include client/server versions and the phone/watch model; redact private data. See [development](docs/DEVELOPMENT.md) for the architecture and local build.

Keep the phone compatible with the upstream Murmur UI. Keep the watch focused on short conversations. Avoid an unrestricted JavaScript bridge, embedded server credentials on the watch, unrequested tool approvals, automatic mutation retries or silent fallback to another Hermes.

Changes to the relay should include a meaningful protocol test. Changes to device UI should include actual emulator/device screenshots and the sizes tested. Do not describe an illustration as a screenshot or a build as a hardware test. Run the verification command in the README before submitting.

This repository is an independent client. Do not file these client patches against Umbrel's repository unless their contribution policy permits it.
