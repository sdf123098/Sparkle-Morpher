# Sparkle's Morpher (SPM) — 스파클의 변신기

> [English](README.md) | [中文](README_zh.md) | [日本語](README_ja.md) | **한국어**

**클라이언트 전용 Minecraft 커스텀 모델 모드**입니다. 커스텀 3D 모델, 텍스처, 애니메이션과 효과음으로 캐릭터를 꾸미고, 어떤 Minecraft 서버에서든 SPM을 설치한 플레이어끼리 서로의 모델을 볼 수 있습니다.

**SPM은 클라이언트에만 설치하면 됩니다. Minecraft 서버에 SPM이나 플러그인을 설치하거나 서버를 변경할 필요가 없습니다.** 멀티플레이 모델 공유는 SPM Cloud를 사용합니다. 양쪽 모두 같은 Cloud 인스턴스에 연결하고 현재 게임 계정을 연동·인증한 뒤, 상대가 접근할 수 있는 Cloud 모델을 선택하세요.

**QQ:** 1104823534 | **Discord:** [Discord 참여](https://discord.gg/3KqK7USF39) | **Telegram:** [Telegram 참여](https://t.me/sparklemorpher) | **Patreon:** [cw/Soid211](https://www.patreon.com/cw/Soid211) | **Afdian:** [Micaftic](https://afdian.com/a/Micaftic)

[빠른 시작](#quick-start) · [멀티플레이](#multiplayer) · [모델 형식](#model-formats) · [기능](#features) · [지원 빌드](#supported-builds) · [SPM Cloud](#spm-cloud) · [호환성](#compatibility) · [자주 묻는 질문](#faq) · [크레딧](#credits)

<a id="quick-start"></a>
## 빠른 시작

1. [Releases](https://github.com/sdf123098/Sparkle-Morpher/releases)에서 **Minecraft 버전과 로더**에 맞는 빌드를 다운로드하세요. 아래 표에 여섯 가지 빌드가 나와 있습니다.
2. SPM `.jar`를 클라이언트의 `mods` 폴더에 넣으세요. Fabric 빌드에는 Fabric API도 필요합니다. 선택한 릴리스가 추가 의존성을 요구하면 함께 설치하세요. 해당 Fabric 또는 NeoForge 프로필로 Minecraft를 실행하세요.
3. 월드나 서버에 접속한 뒤 **Alt + Y**로 모델 패널을 여세요. 로컬 모델을 가져오거나 Cloud 모델을 선택하고 텍스처와 설정을 조정하세요. **Z**로 애니메이션 휠을 열 수 있습니다. 키 설정은 Minecraft의 조작 설정에서 바꿀 수 있습니다.
4. 외형을 공유하려면 양쪽 모두 **같은 Cloud 인스턴스**를 선택하고 로그인한 뒤 현재 게임 계정을 연동하세요. 공개 Cloud 모델을 선택하거나 자신의 모델을 업로드하고 공개로 설정하세요. 개인정보 보호 모드가 꺼져 있는지도 확인하세요. 상대의 SPM 클라이언트가 접근 가능한 모델을 자동으로 다운로드하고 표시합니다.

로컬 모델은 Cloud를 설정하지 않아도 사용할 수 있습니다. 로컬로 파일을 가져오는 것만으로 모델이 자동 업로드되거나 공유되지는 않습니다.

<a id="multiplayer"></a>
## 어떤 서버에서든 서로의 모델 보기

SPM의 모델 공유는 Minecraft 서버의 모드 구성과 독립적으로 작동합니다. 바닐라 서버, 플러그인 서버, 모드 서버에서도 운영자에게 SPM 설치를 요청할 필요가 없습니다.

| 사용 상황 | 표시 결과 |
|---|---|
| 양쪽 모두 SPM 설치, 같은 Cloud 사용, 게임 계정 인증 완료, 접근 가능한 Cloud 모델 사용 | 각 클라이언트에 상대의 커스텀 모델과 텍스처가 표시됩니다. 지원되는 모델 설정과 휠/대기 동작도 동기화됩니다. |
| 상대가 SPM을 설치하지 않음 | 상대에게는 일반 Minecraft 외형이 보입니다. |
| 모델을 로컬로만 가져옴 | 로컬에서 사용할 수 있지만 Cloud로 자동 공유되지 않습니다. |
| 서로 다른 Cloud 사용, 게임 계정 미연동 또는 모델 접근 권한 없음 | 해당 조건을 해결하기 전에는 Cloud를 통한 외형 공유를 사용할 수 없습니다. |

모델 리소스와 외형 업데이트는 **SPM 클라이언트와 SPM Cloud** 사이에서 전송됩니다. Minecraft 서버는 계속 게임 처리를 담당합니다. 모델 교체는 클라이언트 렌더링을 바꾸며 서버 규칙, 히트박스나 권한을 바꾸지 않습니다.

<a id="model-formats"></a>
## 모델 형식

| 형식 | 가져오기 지원 |
|---|---|
| `.ysm` | 텍스처와 모델 자체 애니메이션을 포함한 YSM 모델. OpenYSM/YSMParser 기반 가져오기 파이프라인을 사용합니다. |
| `.bbmodel` | Blockbench 프로젝트의 큐브/메시 지오메트리, 본 계층, 텍스처와 지원되는 애니메이션. |
| `.zip` | 내용을 분석해 YSM 폴더, Blockbench 모델 팩, Figura 아바타 팩과 Bedrock 모델 팩을 구분합니다. |
| Bedrock 지오메트리 | `.geo.json` / `geometry.json`. Bedrock 팩에는 `.animation.json` 파일과 PNG 텍스처도 포함할 수 있습니다. |
| `.gltf` / `.glb` | 로컬 glTF 모델 가져오기. `.gltf`가 참조하는 외부 리소스를 모델과 함께 보관하세요. |

Figura 팩에서는 모델과 텍스처를 읽으며 Figura Lua 실행 환경은 제공하지 않습니다. 특정 형식을 가져올 수 있다고 해서 원본 프로그램의 모든 기능을 지원하는 것은 아닙니다. Cloud 업로드와 공유는 선택한 인스턴스가 지원하는 형식에 따라 달라집니다.

<a id="features"></a>
## 기능

- **커스텀 외형:** 플레이어 모델 교체, 텍스처 전환, 모델이 제공하는 설정 조정.
- **애니메이션 휠:** Z로 모델 자체 동작을 선택합니다. 지원 모델에서 애니메이션 컨트롤러, `loop` / `once` / `hold` 재생과 Molang 표현식을 사용할 수 있습니다.
- **모델 오디오:** 모델에 포함된 음성과 효과음을 재생하며 Opus 오디오 디코딩을 지원합니다.
- **모델 관리:** 로컬 파일, 폴더나 URL에서 가져오기, 모델 탐색, 그룹화와 즐겨찾기.
- **Cloud 모델 라이브러리:** 전체 모델, 최근 사용, 즐겨찾기, 내 모델과 공개 모델을 탐색합니다. 전체 모델은 검색어 없이 접근 가능한 모델을 나열합니다.
- **추가 모델 대상:** 지원되는 엔티티, 탈것과 투사체에도 커스텀 모델을 사용할 수 있습니다. 사용 가능한 대상은 모델과 선택한 빌드의 연동 기능에 따라 달라집니다.

<a id="supported-builds"></a>
## 지원 빌드

클라이언트에 맞는 빌드를 선택하세요. Fabric과 NeoForge는 별도의 배포 파일로 제공됩니다.

| 빌드 | 로더 | Minecraft | Git 브랜치 |
|---|---|---|---|
| Sparkle-Morpher-Fa1.21.1 | Fabric | 1.21.1 | `main` |
| Sparkle-Morpher-Fa26.1.2 | Fabric | 26.1.2 | `fa26.1.2` |
| Sparkle-Morpher-Fa26.2 | Fabric | 26.2 | `fa26.2` |
| Sparkle-Morpher-Fa26.3 | Fabric | 26.3 | `fa26.3` |
| Sparkle-Morpher-Neo1.21.1 | NeoForge | 1.21.1 | `neo1.21.1` |
| Sparkle-Morpher-Neo26.1.2 | NeoForge | 26.1.2 | `neo26.1.2` |
| Sparkle-Morpher-Neo26.2 | NeoForge | 26.2 | `neo26.2` |
| Sparkle-Morpher-Neo26.3 | NeoForge | 26.3 | `neo26.3` |

Minecraft 1.21.1에는 Java 21, Minecraft 26.1.2 / 26.2 / 26.3에는 Java 25를 사용하세요. 의존성은 해당 릴리스의 요구 사항을 따르세요. 함께 플레이할 때는 Cloud 프로토콜이 호환되는 빌드를 사용하세요.

<a id="spm-cloud"></a>
## SPM Cloud: 공식 또는 자체 호스팅

기본 제공 공식 Cloud를 사용하거나 커뮤니티/자체 호스팅 인스턴스에 연결할 수 있습니다. **SPM Cloud는 독립적인 모델·동기화 서비스이며 Minecraft 서버 모드가 아닙니다.** 외형을 공유하려는 플레이어는 같은 인스턴스를 선택해야 합니다.

- 게임 계정으로 로그인하거나 Cloud 계정으로 로그인한 뒤 현재 게임 계정을 연동하세요. 사용 가능한 인증 서비스와 가입 방식은 Cloud 운영자가 관리합니다.
- 연동된 계정은 로그인을 자동 복원하고 실패 시 일정 간격으로 재시도할 수 있습니다. 수동 로그아웃하면 인스턴스를 다시 선택할 때까지 자동 복원이 중지됩니다.
- 계정, 모델과 게임 계정 연동은 인스턴스별로 독립적입니다. 인스턴스를 바꿔도 라이브러리나 연동 정보가 이전되지 않습니다.
- 비공개 모델이 자동으로 공개되지는 않습니다. 일반적인 멀티플레이에서 보이게 하려면 공개 모델을 사용하세요. 비공개 모델을 선택하는 것만으로 다른 플레이어에게 접근 권한이 부여되지는 않습니다.

자체 호스팅은 독립적인 [SPM Cloud Rust 백엔드](https://github.com/sdf123098/spm-cloud)와 [English](https://github.com/sdf123098/spm-cloud/blob/main/README.md) / [中文](https://github.com/sdf123098/spm-cloud/blob/main/README_zh.md) 설치 안내를 참고하세요. Docker Compose, Linux 네이티브와 Windows 네이티브 배포를 지원합니다. 커스텀 인증 게이트웨이와 여러 인증 서비스는 Cloud 운영자가 설정합니다. 이 설정은 Cloud 서비스에 속하며 Minecraft 서버를 변경할 필요가 없습니다.

<a id="compatibility"></a>
## 모드 호환성

SPM에는 Better Combat, Curios, Create, Iris/Sodium과 스킨 레이어 렌더링 연동이 포함되어 있습니다. 사용 가능 여부는 Minecraft 버전, 로더와 다른 모드의 버전에 따라 달라지며 모든 조합을 보장하지는 않습니다. SPM을 사용하기 위해 이러한 선택적 연동 모드를 설치할 필요는 없습니다.

<a id="faq"></a>
## 자주 묻는 질문

### Minecraft 서버에도 SPM이 필요한가요?

아니요. 커스텀 모델을 표시하려는 클라이언트에 SPM을 설치하세요. Cloud가 Minecraft 서버와 독립적으로 외형 공유를 처리합니다.

### 둘 다 SPM을 설치했는데 서로의 모델이 보이지 않아요.

양쪽 모두 같은 Cloud 인스턴스에 연결했는지, 로그인했는지, 현재 게임 계정을 연동·인증했는지, 개인정보 보호 모드가 꺼져 있는지 확인하세요. 로컬로만 가져온 모델 대신 접근 가능한 Cloud 모델을 사용하고 Cloud 연결 상태도 확인하세요. 자체 호스팅에서 동작이 동기화되지 않으면 백엔드가 현재 애니메이션 프로토콜을 지원하는지도 확인하세요.

### Cloud 없이 SPM을 사용할 수 있나요?

로컬 모델은 사용할 수 있습니다. Cloud를 통한 멀티플레이 공유에는 정상적인 Cloud 연결이 필요합니다. Cloud에 장애가 발생하면 공유와 새 모델 다운로드가 중단될 수 있습니다.

### 외부 인증 서비스를 사용할 수 있나요?

선택한 Cloud 인스턴스에서 활성화된 인증 서비스를 사용하고 현재 게임 계정을 인증하세요. Minecraft 서버의 로그인 방식만으로 인증된 Cloud 계정 연동이 생성되지는 않습니다.

### README 번역의 내용은 동일한가요?

영어, 중국어, 일본어와 한국어는 같은 섹션 순서, 설치 절차, 멀티플레이 공유 조건과 빌드 표를 사용합니다. 섹션 ID도 같으므로 `#multiplayer` 같은 링크는 모든 언어에서 사용할 수 있습니다. 어떤 번역을 읽어도 사용 요구 사항은 같습니다.

<a id="credits"></a>
## 크레딧과 라이선스

- [OpenYSM](https://github.com/OpenYSM)(MIT)을 기반으로 개발했습니다.
- YSM 모델 파싱에 [OpenYSMDev/YSMParser](https://github.com/OpenYSMDev/YSMParser)(MIT)을 사용합니다.
- 기본 모델 라이브러리: [sdf123098/YSM-Model](https://github.com/sdf123098/YSM-Model).
- Blockbench: [JannisX11/Blockbench](https://github.com/JannisX11/blockbench).

SPM은 [MIT](LICENSE) 라이선스입니다. 모델 리소스에는 각 제작자의 라이선스와 이용 조건이 적용됩니다.
