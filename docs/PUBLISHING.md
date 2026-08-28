# 预发布与依赖引入

当前预发布目标为 `0.2.0-rc.1`，沿用现有无 `v` 前缀的 tag 命名。不是稳定商用版本，不修改既有 `0.1.0` tag，不补签生产 APK，也不替所有者选择开源许可证。

## 三个独立坐标

仓库为 `https://jitpack.io`，group 为 `com.github.kairowan.room-flow`：

| artifactId | 类型 | 应用中的配置 |
| --- | --- | --- |
| room-flow | AAR | implementation |
| room-flow-debug | AAR，传递依赖同版核心 | debugImplementation |
| room-flow-compiler | JAR + sources，构建期 KSP | ksp |

版本均与 tag 一致。不要使用包含所有模块的聚合依赖，不要将 compiler 放到 implementation。
完整 Gradle 接入代码见 [README](../README.md#setup)。

## 构建与验收

默认不传 `releaseVersion` 时仍使用隔离验收坐标，避免开发构建污染发布版本。
显式传 `-PreleaseVersion=<tag>` 才使用上面的发布坐标。JitPack 的 [构建配置](../jitpack.yml) 使用 JDK 17、Android SDK 36，固定以 Room 2.6.1 构建三个模块并安装到其构建容器的 Maven Local；不发布 app。

发布前用同一份基线制品验证两个宿主 Room 版本，分别消费 GMM/纯 POM、核心/Debug 模块：

```sh
RELEASE_VERSION=0.2.0-rc.1 bash scripts/check-artifact-consumer.sh --no-daemon --max-workers=2
```

脚本把制品写到打印的临时 Maven 目录，不写本机 `~/.m2`。它编译独立消费者和实际 KSP 生成代码，检查传递依赖及运行时不含处理器；不是设备运行测试。

Tag 推送后，再从远端验证（不会重新发布本地产物）：

```sh
RELEASE_VERSION=0.2.0-rc.1 RELEASE_REPOSITORY=https://jitpack.io \
  bash scripts/check-artifact-consumer.sh --no-daemon --max-workers=2
```

JitPack 首次请求可能需要构建。只有 tag 存在、HTTP 返回成功还不够，须检查 POM/AAR/JAR、Debug 到核心的同版本依赖和真实消费编译。失败版本不通过移动 tag 修复，修复后另发版本。参考 [JitPack 多模块与构建规则](https://docs.jitpack.io/building/)。

## 本地 Maven 包备用接入

若 Release 附带 `room-flow-0.2.0-rc.1-maven.zip`，解压到宿主项目的 `vendor/room-flow-maven`，该目录下应直接看到 `com/github/...`。不要只复制 AAR：那会丢失传递依赖和编译器信息。

```kotlin
// settings.gradle.kts，用它替代 JitPack 仓库；依赖坐标不变。
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri(rootDir.resolve("vendor/room-flow-maven"))
            content { includeGroup("com.github.kairowan.room-flow") }
        }
    }
}
```

此包只含本 SDK 三个模块及元数据/源码，不含所有第三方依赖；首次构建仍需 Google Maven/Maven Central 或已准备的依赖缓存。SHA-256 文件用于下载完整性核对，不等于作者签名或供应链认证。

## 发布边界

预发布便于接入和验收，不代表迁移、生命周期、真实故障、许可证和稳定 API 门槛已关闭。授权/许可证仍需项目所有者明确，不在 POM 中虚构 Apache/MIT 等许可。详见 [发布清单](RELEASE-CHECKLIST.md) 和 [PLAN](../PLAN.md)。
