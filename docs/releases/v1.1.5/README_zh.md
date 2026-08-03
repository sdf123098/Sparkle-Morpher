> [English](https://github.com/sdf123098/Sparkle-Morpher/releases/tag/v1.1.5) | **中文**

# Sparkle Morpher 1.1.5

Sparkle Morpher 1.1.5 紧急修复版本（Hotfix），主要修复了 Fabric 1.21.1 环境下车万女仆（Touhou Little Maid）客户端兼容实现类的包路径不匹配导致运行期抛出 `NoClassDefFoundError` 的严重问题。

---

## 🐛 车万女仆 (Touhou Little Maid) Fabric 1.21.1 客户端兼容实现包路径修复

- **修复 `NoClassDefFoundError` 缺陷**：在 Fabric 1.21.1（Architectury 多模块架构）下，公共端 `client.compat` 期望的客户端实现包路径为 `com.micaftic.morpher.client.compat.touhoulittlemaid.fabric.TouhouLittleMaidClientCompatImpl`，而先前的实现类被误放在 `core.compat.touhoulittlemaid.fabric`，导致 Architectury `@ExpectPlatform` 在运行期加载实现时引发类找不到异常。
- **调整包路径与访问可见性**：
  - 将 `TouhouLittleMaidClientCompatImpl.java` 正确移至 `client.compat.touhoulittlemaid.fabric` 路径下并补全相关 import。
  - 将反射助手类 `TouhouLittleMaidAccess.java` 及其成员方法由包私有（package-private）提升为 `public` 可见性，确保跨包安全调用。
- **全量排查结论**：
  - **Fabric 1.21.1**：排查全部 40+ 个 `@ExpectPlatform` 接口，仅此一处包路径位置偏差并已彻底修复。
  - **Fabric 26.x / NeoForge 全版本**：采用单模块合并/直接 FQN 调用架构，类均位于同一编译单元/Jar 中，不受此问题影响。

---

## 📦 包含构建产物 (Release JARs)

- `sparkle-morpher-1.1.5-fa1.21.1.jar` (Fabric 1.21.1 / Java 21)
- `sparkle-morpher-1.1.5-fa26.1.x.jar` (Fabric 26.1.x / Java 25)
- `sparkle-morpher-1.1.5-fa26.2.jar` (Fabric 26.2 / Java 25)
- `sparkle-morpher-1.1.5-neo1.21.1.jar` (NeoForge 1.21.1 / Java 21)
- `sparkle-morpher-1.1.5-neo26.1.x.jar` (NeoForge 26.1.x / Java 25)
- `sparkle-morpher-1.1.5-neo26.2.jar` (NeoForge 26.2 / Java 25)
