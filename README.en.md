# CMS-UI

[简体中文](README.md) · **English**

A ready-to-use UI and motion component library for Android. Components, theme, fonts, a native
spring engine and the full documentation all live in this repository: clone it and the sample app
runs, copy `library/` into your project and you are done.

**Version 1.1.0** · Kotlin `2.3.21` · Compose BOM `2025.04.01` · minSdk 31 (Android 12)

## Get started

```bash
git clone https://github.com/overloadshop/CMS-UI.git
```

- **See it running** — open the repository root in Android Studio and run `example`, or download
  the APK from [Releases](https://github.com/overloadshop/CMS-UI/releases/latest).
- **Add it to your app** — put `library/` next to your `app` module, merge
  `gradle/settings-snippet.gradle.kts`, then `implementation(project(":cms-core"))`.
  Namespace, resources, fonts and JNI come with the module; nothing in the host has to be renamed.
- **Source only** — copy `library/cms/com`, `library/theme/com`, `library/res` and `library/native`,
  then follow [`docs/03-集成指南.md`](docs/03-集成指南.md).

Defaults are usable as-is: one `CMS.Appearance.Provide { CMS.Appearance.Theme { … } }` sets the
light/dark palette, accent color, glass material and haptics for the whole tree, while every
component can still be overridden individually.

## What is inside

| Path | Contents |
| --- | --- |
| `library/` | The component library: Compose/custom-view components, theme, resources, JNI bridge and three dependency modules |
| `example/` | The CMS sample app (source); the APK ships through GitHub Releases |
| `docs/` | 36 component documents plus integration guides; also bundled into the app as offline docs |

Highlights:

- **Cross-component gesture arbitration** — one configurable policy for paging, vertical scrolling
  and local horizontal scrolling: interrupt on press, change axis mid-drag, settle by velocity.
- **Sampled liquid glass** — the glass reads the content behind it, including extra sibling layers;
  refraction is limited to the edge ring, with dispersion, fusion and shared highlights.
- **Native spring engine** — a C++17 engine loaded over JNI, with a pure-Kotlin spring fallback.
- **Interactive documentation** — the code blocks and tables inside the docs are the library
  components themselves.

## License

- This project: Apache License 2.0 — see [`LICENSE`](LICENSE).
- Third-party components, fonts and the proprietary engine binary: see [`NOTICE.md`](NOTICE.md).

---

© 2026 芜湖灵动亿创网络科技有限公司 (Wuhu Lindicra Network Technology Co., Ltd)

The legally binding entity name is the Chinese one above.
