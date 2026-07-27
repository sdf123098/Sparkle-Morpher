# 2026-07-27 兼容性与 YSMParser 修复记录

## 对话需求

1. 使用 Iris 光影时，第三人称下自定义人物模型消失。
2. 多人服务器中，其他玩家模型偶尔也会消失。
3. 其他光影没有同样现象。
4. 检查并同步 OpenYSMDev/YSMParser 新版本兼容性到六个分支。

## Iris 光影兼容性问题

### 现象

Eclipse Shader 在第三人称和服务器多人场景中会使本地或远端玩家模型消失；未启用该类光影时模型正常。

### 根因

Eclipse Shader 的 `shaders/dimensions/all_solid.vsh` 对实体 ID `1599`（实体阴影）执行了移出视锥的处理：

```glsl
if (entityId == 1599) gl_Position.z -= 10000.0;
```

模型的直连 GPU/VAO 绘制绕过了 Iris 的实体批处理和逐实体 uniform 更新，可能复用上一次绘制留下的 `entityId`。当旧值为 `1599` 时，Eclipse Shader 会把当前人物模型一起裁掉。多人场景中的绘制顺序变化使问题表现为偶发。

### 修复

- Iris/Oculus 光影启用时，禁用模型直连 GPU/VAO 路径。
- 改用 VertexConsumer-backed 的 Native SIMD/Java 回退路径，确保实体 uniform 状态由当前渲染管线管理。
- 未启用光影时保留原有 GPU 加速。
- NeoForge 26.x 增加 Iris 公共 API 的可选反射检测：
  `net.irisshaders.iris.api.v0.IrisApi.isShaderPackInUse()`。
- 未安装 Iris/Oculus 时不引入硬依赖，检测安全返回 `false`。

主要文件：

- `common/src/main/java/com/elfmcys/yesstevemodel/geckolib3/geo/ModelRendererBridge.java`
- `src/main/java/com/elfmcys/yesstevemodel/geckolib3/geo/ModelRendererBridge.java`
- `src/main/java/com/micaftic/morpher/core/compat/oculus/OculusCompat.java`

## YSMParser v0.3.6 兼容性检查

上游版本：[OpenYSMDev/YSMParser v0.3.6](https://github.com/OpenYSMDev/YSMParser/blob/main/version.txt)。

项目实际通过内部二进制解析器和 `YSMFolderDeserializer` 消费 YSMParser 输出，不是通过 Maven 版本号直接引入 parser。

### v0.3.6 相关变化

- 现代格式的动画类型 13 输出键名为 `iss`。
- 未知动画类型输出为 `unk_<数字>`。
- 部分骨骼未知字段不再因非零值直接崩溃。
- JSON 输出使用替换错误处理，避免异常 UTF-8 导致序列化失败。

### 兼容性修复

`YSMFolderDeserializer` 现在支持：

- `iss` 和旧名称 `irons_spell_books` → 类型 `13`
- `fp.arm` 和 `fp_arm` → 类型 `11`
- `unk_14` → 类型 `14`
- 任意合法 `unk_<数字>` 的双向映射
- 非法未知键安全回退为类型 `0`

骨骼未知字段的非零兼容已由现有 `YSMBinaryDeserializer` 的直接读取逻辑覆盖。

## 六个分支与提交

| 分支 | Iris 修复 | NeoForge 26 Iris 检测 | YSMParser 映射修复 |
|---|---|---|---|
| `main` | `78862cc` | — | `2972931` |
| `fa26.1.2` | `1535148` | — | `0d6aaa4` |
| `fa26.2` | `91a9373` | — | `915ddba` |
| `neo1.21.1` | `48c0213` | 已有实现 | `ce219ec` |
| `neo26.1.2` | `abef9b6` | `59d2515` | `9a4e616` |
| `neo26.2` | `6a3884c` | `ab33dfc` | `1b46168` |

## 验证结果

- 六个分支均执行 `compileJava` 成功。
- `iss=13`、`unk_14=14`、`unk_429 → 429` 映射已实际调用验证。
- 六个分支均已推送到远端，`ahead/behind = 0/0`。
- 六个分支均无未提交的已跟踪文件。
- 未进行 Minecraft 客户端内的实际 Iris/Eclipse Shader 多人运行测试；发布前应按第三人称、本地玩家、远端玩家三个场景回归。
