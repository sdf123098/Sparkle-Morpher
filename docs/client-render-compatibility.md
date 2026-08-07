# 客户端渲染兼容接口

Sparkle Morpher 通过 `ClientRenderCompatibility` 向可选渲染器提供模型与纹理生命周期事件。主渲染流程只认识通用接口，不直接引用 Caustica、Iris 等具体模组；具体实现位于独立兼容子模块中。

官方兼容子模块会以 Fabric nested JAR 的形式内嵌到 Sparkle Morpher 正式产物，因此分发时只需要一个主模组 JAR。子模块仍保留独立的源码目录、`fabric.mod.json`、Mixin 配置和构建任务，第三方实现也可以作为独立扩展 JAR 发布。

## 接口与生命周期

接口位于：

```text
common/src/main/java/com/micaftic/morpher/client/compat/ClientRenderCompatibility.java
```

各方法的用途如下：

| 方法 | 调用时机 | 典型用途 |
| --- | --- | --- |
| `isAvailable()` | 模块被发现后、注册前 | 检查目标渲染器是否已安装；返回 `false` 时模块保持休眠 |
| `initialize()` | 注册成功后，客户端模型开始加载前 | 注册资源包、建立反射句柄或初始化兼容侧缓存 |
| `resolveTextureLocation(texture)` | Sparkle Morpher 为纹理分配位置时 | 返回稳定的自定义纹理 ID；不接管时返回 `null` |
| `onModelAssemblyCreated(assembly)` | 模型装配创建完成后 | 发布尚未上传到 GPU 的模型纹理资源 |
| `onTextureRegistered(location, texture, replaced)` | 纹理写入 `TextureManager` 后 | 标记纹理活跃；处理同一位置的纹理实例替换 |
| `onTextureUploaded(location, texture)` | GPU 缓存回收后重新上传纹理时 | 使渲染器丢弃旧 GPU image view |
| `onTextureInactive(location)` | 最后一个活跃纹理所有者释放后 | 停止预热或移除活跃引用 |
| `tick()` | 客户端 tick | 执行去抖后的兼容任务 |
| `flush()` | 一批模型同步或本地模型加载完成后 | 立即处理已经排队的兼容任务 |

`ClientRenderCompatibilityRegistry` 负责注册、顺序分发和异常隔离。某个兼容模块抛出运行时异常时，错误会被记录，但不会阻止其他兼容模块继续接收事件。

`flush()` 不是“无条件重建渲染器状态”。实现必须先检查自己的 dirty 标记，只处理确实已经排队的工作。纹理激活、GPU view 替换等轻量事件不应被升级成世界材质表重建。

## Fabric 扩展入口

兼容模块通过自定义 Fabric entrypoint 被发现：

```json
{
  "entrypoints": {
    "sparkle_morpher_render_compat": [
      "example.compat.ExampleRenderCompatibility"
    ]
  }
}
```

实现类直接实现通用接口：

```java
public final class ExampleRenderCompatibility implements ClientRenderCompatibility {
    @Override
    public boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded("example_renderer");
    }

    @Override
    public void initialize() {
        // 在模型加载前安装资源提供器或初始化兼容缓存。
    }

    @Override
    public Identifier resolveTextureLocation(OuterFileTexture texture) {
        return null; // 不需要自定义纹理位置时交还给主流程。
    }
}
```

如果兼容 JAR会内嵌在主模组中，目标渲染器应放在 `suggests`，不能作为硬 `depends`。否则即使玩家没有安装目标渲染器，Fabric Loader 也会因内嵌子模组缺少依赖而拒绝启动。

```json
{
  "depends": {
    "sparkle_morpher": ">=1.1.5"
  },
  "suggests": {
    "example_renderer": "*"
  }
}
```

兼容实现还应避免在 `isAvailable()` 执行前静态链接可选渲染器的类。可以使用反射，或在确认目标模组已加载后再初始化相关对象。

## 构建与内嵌

官方兼容实现使用独立 Gradle 子项目。以 Caustica 为例：

```text
caustica-compat/
├─ build.gradle
└─ src/main/
   ├─ java/com/micaftic/morpher/compat/caustica/
   └─ resources/
      ├─ fabric.mod.json
      └─ sparkle_morpher_caustica_compat.mixins.json
```

主 Fabric 项目使用 Loom 的 `include(project(...))` 把子项目作为 nested JAR 写入正式产物：

```groovy
dependencies {
    include(project(path: ':caustica-compat', configuration: 'namedElements')) {
        transitive = false
    }
}
```

构建主分发 JAR：

```powershell
.\gradlew.bat :fabric:remapJar
```

最终结构包含子模组，而不是把兼容类合并进主类目录：

```text
sparkle-morpher-<version>-fa26.2.jar
└─ META-INF/jars/
   └─ sparkle-morpher-caustica-compat-<version>-fa26.2-dev.jar
```

`fabric:remapJar` 在构建末尾会断言正式产物中恰好存在一个 Caustica 兼容子 JAR，防止分发包静默漏装兼容模块。

子模块也可以单独构建，便于开发和调试：

```powershell
.\gradlew.bat :caustica-compat:remapJar
```

若只检出兼容子模块或需要针对现成的 Sparkle Morpher JAR 编译，可以指定：

```powershell
.\gradlew.bat :caustica-compat:remapJar `
  -PsparkleMorpherDevJar="D:\path\to\sparkle-morpher-1.1.5-fa26.2.jar"
```

## Caustica PBR 示例

Caustica 实现在：

```text
caustica-compat/src/main/java/com/micaftic/morpher/compat/caustica/CausticaDynamicPbrResources.java
```

其处理流程如下：

1. `isAvailable()` 通过 Fabric Loader 检查 `caustica` 是否存在。未安装 Caustica 时，内嵌子模块不会进入兼容注册表。
2. `initialize()` 从磁盘缓存预载动态 PBR 资源，并在客户端资源包仓库安装一个始终启用的资源包。
3. `resolveTextureLocation()` 对 albedo、normal、specular 数据计算 SHA-256，生成稳定、可跨会话复用的内容寻址纹理位置。
4. `onModelAssemblyCreated()` 将模型装配中的 PBR 纹理发布到动态资源包；此阶段不要求纹理已经上传到 GPU。
5. `onTextureRegistered()` 和 `onTextureUploaded()` 只维护活跃纹理与 Caustica 实体 image-view 缓存，并预热实体纹理槽。
6. 只有动态资源字节确实新增或变化时才设置 `materialDirty`，随后由 `tick()` 或 `flush()` 合并执行材质表刷新。

Caustica 的 `RtComposite.bindWorldTextures()` 会重建材质表并请求 RT terrain full clear，因此不能把它用于普通纹理激活或纹理实例替换。当前实现将两类状态分开：

```text
资源内容变化 ──> materialDirty ──> 材质表重建（必要时）
纹理激活/替换 ─> entityPrewarmPending ─> 清理实体 viewCache + 预热纹理槽
```

这一分层也是其他渲染器兼容实现应遵循的原则：接口事件描述“发生了什么”，兼容模块自行选择最小范围的更新操作，不能让主模型流程承担某个渲染器的专用刷新语义。

Caustica 子模块仅包含 PBR 与渲染材质兼容，不包含 OYSM 纸娃娃功能。
