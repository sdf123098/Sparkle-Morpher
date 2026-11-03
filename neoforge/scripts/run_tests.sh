#!/usr/bin/env bash
# SparkleMorpher canonical test entry — Hermes 验证检测识别此命令（scripts/run_tests.sh）。
# 用法（在分支仓库根目录）: bash scripts/run_tests.sh
# 输出: Gradle 结果尾部 + tests/failures 汇总；退出码 = Gradle 退出码。
set -u
ORIG_PATH="$PATH"
case "$(basename "$PWD")" in
  *1.21.1*)
    # Gradle wrapper 8.12.1 需要 JDK21
    export JAVA_HOME="C:\\Program Files\\Java\\jdk-21.0.12"
    export PATH="/c/Program Files/Java/jdk-21.0.12/bin:$ORIG_PATH"
    if [ -f "settings.gradle" ] && grep -q "^include 'common'" settings.gradle 2>/dev/null; then
      T=":common:test"
    else
      T=":test"
    fi
    ;;
  *)
    # Gradle wrapper 9.5.1 使用 Java 25（默认）；Fabric 多模块走 :fabric:test，Neo 单体走 :test
    export JAVA_HOME="C:\\Program Files\\Microsoft\\jdk-25.0.4.7-hotspot"
    export PATH="$ORIG_PATH"
    if [ -d "fabric" ]; then
      T=":fabric:test"
    else
      T=":test"
    fi
    ;;
esac

OUT=$(./gradlew $T --console=plain 2>&1)
RC=$?
echo "$OUT" | tail -25
if [ $RC -eq 0 ]; then
  if [ -d "fabric/build/test-results/test" ]; then R="fabric/build/test-results/test"; elif [ -d "common/build/test-results/test" ]; then R="common/build/test-results/test"; else R="build/test-results/test"; fi
  TS=$(grep -hoE 'tests="[0-9]+"' $R/TEST-*.xml 2>/dev/null | grep -oE '[0-9]+' | awk '{s+=$1} END{print s+0}')
  FS=$(grep -hoE 'failures="[0-9]+"' $R/TEST-*.xml 2>/dev/null | grep -oE '[0-9]+' | awk '{s+=$1} END{print s+0}')
  echo "SPM_TESTS tests=$TS failures=$FS"
else
  echo "SPM_TESTS FAILED (rc=$RC)"
fi
exit $RC
