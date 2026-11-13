# Third-party notices

Murmur Companion is independent software and is not affiliated with or endorsed by Umbrel, Tailscale or Google. Names identify compatibility only.

## Murmur icon

The unmodified maskable icon in `phone/src/main/res/drawable-nodpi/murmur_icon.png` and `wear/src/main/res/drawable-nodpi/murmur_icon.png` comes from [getumbrel/murmur](https://github.com/getumbrel/murmur), revision `27253d221302af4c2b76b95f993428d698322519`, `design/icon/murmur-icon-maskable-512.png`.

Copyright and MIT terms are preserved in [docs/UPSTREAM-LICENSE](docs/UPSTREAM-LICENSE). The native monochrome/small notification glyph is a simplified vector adaptation. No Umbrel endorsement is asserted.

## Native Buddy characters

`shared-ui/src/main/java/dev/murmur/companion/ui/BuddyView.java` adapts the seeded Buddy geometry and traits from Murmur's `src/ui/buddy.tsx`. `core/AgentLook.java` follows its `src/lib/hues.ts` palette and colour metadata. Design-source baseline: `27253d221302af4c2b76b95f993428d698322519`. The characters are drawn natively using Android Canvas, with adapted shading and expressions. Copyright and MIT terms are retained in [docs/UPSTREAM-LICENSE](docs/UPSTREAM-LICENSE).

## Dependencies

- AndroidX Activity and related AndroidX components: Apache License 2.0.
- OkHttp and Okio: Apache License 2.0.
- Kotlin standard library (transitive dependency): Apache License 2.0.
- JUnit and MockWebServer: test/build dependencies under their respective project licenses.
- JSON-java: build/test dependency under its published license; the app uses Android's platform `org.json` implementation.
- Google Play services Wearable: distributed under Google's applicable Android/Google Play services terms.

The Gradle wrapper is distributed under Gradle's Apache License 2.0 terms. See the official dependency repositories for complete license texts. The Murmur frontend is served by the user's server and is not bundled in these APKs.
