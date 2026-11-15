# legacy-v1.0

AndroidCMS 1.0 的源码快照，包名 `ldyc.qy.music` / `ldyc.qy.music.ui.theme`。

**不参与编译。** `cms-core/build.gradle.kts` 只编译 `cms/com` 与 `theme/com`。

保留原因：1.0 的「源码复制到宿主工程」接入方式仍有人使用，且历史组件实现可作为对照。
新代码请使用 `cms/com/lindicra/cms/` 下的 1.1.0 版本。

| 目录 | 内容 |
| --- | --- |
| `cms/` | 1.0 组件源码（28 个 .kt）与 `halfmodal/` |
| `theme/` | 1.0 主题（6 个 .kt） |
| `tests/ldyc/` | 1.0 单元测试（6 个，测试的是 `ldyc.qy.music` 包，当前不可编译） |

如果需要运行 1.0 的历史版本，把它复制回 `cms/`、`theme/`，并把 `cms-core/build.gradle.kts`
的 `java.srcDirs` 改回 `../cms`、`../theme`，同时删掉同名的 `com` 子目录，避免两套包同时编译。
