# Sparkle's Morpher — 스파클의 변신기

> [English](README.md) | [中文](README_zh.md) | [日本語](README_ja.md) | **한국어**

Minecraft 종합 커스텀 모델 로더. 플레이어에게 커스텀 모델, 애니메이션, 사운드 이펙트를 장착——그 블록 캐릭터에게 작별을 고하세요.

> 이것은**종합 모델 로더**입니다. 현재 `.ysm` 형식(OpenYSM 기반, MIT 라이선스)과 `.bbmodel` 형식(Blockbench)을 지원하며, 향후 다른 주류 모델 형식에 대한 지원을 추가할 예정입니다.

---

## 기능

### 커스텀 플레이어 모델 & 스킨

기본 플레이어 모델을 완전히 커스텀된 3D 모델로 교체합니다. 모든 커스텀 모델은 멀티플레이어에서**다른 플레이어에게도 보입니다**

### 모델 형식 지원

- **`.ysm`** — OpenYSM/YSMParser 기반 네이티브 형식. 완전한 스켈레탈 모델과 가중치 애니메이션을 지원합니다.
- **`.bbmodel`** — Blockbench 프로젝트 파일을 직접 임포트. 메쉬 삼각화(N각형 팬 삼각화), UV 정규화, 면 회전, 인플레이트 확장, 내장 Base64 텍스처 추출, PNG IHDR 헤더 파싱을 지원합니다.
- **Figura 아바타 아카이브** — Figura `.zip` 패키지를 직접 임포트. 내장 `ZipModelSniffer`가 YSM 폴더, Figura 아바타, 일반 BBModel ZIP을 자동 감지 및 분기합니다.

### 애니메이션 시스템

- **애니메이션 캐러셀**（기본 키: Z）— 라디얼 메뉴로 현재 모델의 애니메이션을 빠르게 전환.
- **애니메이션 컨트롤러** — 스테이트 머신 기반 애니메이션 컨트롤러를 완전 지원. `loop`（루프）、`once`（원샷）、`hold`（홀드） 재생 모드 지원.
- **Molang 표현식** — 데이터 포인트가 원시 수치와 Molang 표현식 문자열 모두를 지원하여 동적 애니메이션 블렌딩을 구현.

### 사운드 이펙트

모델 내장 보이스라인과 사운드 이펙트를 스킬이나 액션으로 트리거 재생. **Opus** 디코딩으로 크로스 플랫폼 네이티브 가속, 저지연 재생을 구현합니다.

### 멀티 모델 관리

- 로컬 파일, 디렉토리 또는 URL에서 모델 임포트. 가속 다운로드 지원.
- 그룹화와 즐겨찾기로 모델 정리.
- 자동 디렉토리 스캔으로 `.ysm`、`.zip`、`.bbmodel` 파일 인식.

### 서버 사이드 기능

- 서버 운영자가 모델 매니페스트를 정의하고 클라이언트에 배포 가능.
- 설정 가능한 블랙리스트(`config/sparkle_morpher/blacklist.txt`)로 특정 모델 제한.
- Cardinal Components 엔티티 데이터를 통한 클라이언트-서버 간 모델 상태 동기화.

### 모드 호환성

인기 모드와의 병용에 대응:

| 카테고리 | 호환 모드 |
|---------|---------|
| 전투 | Better Combat |
| 액세서리 | Curios |
| 건설 & 자동화 | Create |
| 렌더링 | Iris、Sodium |
| 플레이어 스킨 | 스킨 레이어 호환 |

### 크로스 플랫폼

4개의 빌드 변형으로 주요 로더와 버전 조합을 모두 커버:

| 변형 | 로더 | Minecraft 버전 |
|------|------|---------------|
| Sparkle-Morpher-Fa1.21.1 | Fabric | 1.21.1 |
| Sparkle-Morpher-Fa26.1.2 | Fabric | 26.1.2 |
| Sparkle-Morpher-Neo1.21.1 | NeoForge | 1.21.1 |
| Sparkle-Morpher-Neo26.1.2 | NeoForge | 26.1.2 |

---

## 작동 원리

### 모델 임포트 파이프라인

모델 파일을 임포트하면 Sparkle's Morpher가 지능형 처리 파이프라인을 실행합니다:

1. **ZIP 스니핑** — 아카이브를 콘텐츠로 분류: YSM 폴더、Figura 아바타(`avatar.json` + `.bbmodel` 포함)、일반 BBModel ZIP 또는 미지정.
2. **파싱** — `.ysm` 파일은 YSMParser로 처리. `.bbmodel` 파일은 내장 `BBModelParser`로 아웃라인 트리、큐브/메쉬 요소、텍스처、애니메이션、컨트롤러 상태를 처리.
3. **변환** — 파싱된 데이터를 엔진 내부 `RawGeometry` 형식으로 변환. N 정점 메쉬 면은 팬 삼각화로 처리、UV 좌표는 텍스처 해상도로 정규화、ZIP 내부의 외부 PNG 텍스처는 내장 Base64 소스보다 우선.
4. **렌더링** — 변환된 모델이 활성화되면 바닐라 플레이어 렌더러를 대체하고、기본 플레이어 모델을 자동 숨김.

### BBModel 호환성

Blockbench 형식을 완전 지원:

- 아웃라인 트리의 중첩된 본 계층과 부모-자식 관계
- 큐브 및 메쉬 요소의 올바른 면 UV 매핑
- 내장 텍스처(Base64)의 PNG 헤더 크기 감지
- 애니메이션 재생과 루프 모드 매핑
- Blockbench 5 "free" 형식 호환（슬림 아웃라인 노드 + `groups[]` 폴백）
- 고아 요소 처리（참조되지 않는 요소를 기본 본에 자동 할당）

---

## 아키텍처

Sparkle's Morpher는**공통 코어 + 플랫폼 어댑터**계층 아키텍처를 채택:

- **`common`** — 모든 변형이 공유하는 코어 로직: 모델 파싱、메쉬 처리、ZIP 스니핑、애니메이션 컨트롤러、오디오 디코딩、Molang 평가.
- **`fabric`** / **`neoforge`** — 초기화、네트워킹、컴포넌트 등록、렌더링 훅을 처리하는 플랫폼별 어댑터.
- **네이티브 레이어** — Opus 오디오 디코딩용 크로스 플랫폼 네이티브 라이브러리.

---

## 의존성

빌드 변형에 따라 다릅니다——자세한 내용은 `mods.toml`(NeoForge) 또는 `fabric.mod.json`(Fabric)을 참조하세요. Fabric 변형은 Fabric API를 별도로 설치해야 합니다. 다른 의존성은 Jar-in-Jar으로 번들링됩니다.

---

## 크레딧 & 라이선스

- [OpenYSM](https://github.com/OpenYSM)(MIT 라이선스)을 기반으로 개발.
- `.ysm` 모델 파싱에 [OpenYSM/YSMParser](https://github.com/OpenYSM/YSMParser)(MIT) 사용.
- 기본 모델 라이브러리: [sdf123098/YSM-Model](https://github.com/sdf123098/YSM-Model).
- Blockbench 형식은 [JannisX11/Blockbench](https://github.com/JannisX11/blockbench)에 의해 개발.

**라이선스:** MIT
