#!/usr/bin/env bash
set -euo pipefail

# Symmetric multi-loader layout: this repository is ONE Gradle build that contains
# both `:fabric` and `:neoforge`. The BUILD_ROOT selector therefore only decides
# which loader's release JAR this matrix row audits and which module's sources get
# the javac-warning fingerprint gate; every Gradle invocation runs from the repo
# root with an explicit `:<loader>:<task>` path.
BUILD_ROOT="${BUILD_ROOT:-fabric}"
case "$BUILD_ROOT" in
  fabric|neoforge) ;;
  *) echo "Unsupported BUILD_ROOT='$BUILD_ROOT' (expected fabric or neoforge)" >&2; exit 2 ;;
esac

repo_root="$(pwd)"
diag="$repo_root/build/ci-diagnostics"
mkdir -p "$diag"

# Only the hosted checkout is changed; developers keep their wrapper mirror.
python3 - <<'PY'
from pathlib import Path
p = Path('gradle/wrapper/gradle-wrapper.properties')
p.write_text(p.read_text().replace('mirrors.cloud.tencent.com/gradle/',
                                  'services.gradle.org/distributions/'))
PY

chmod +x gradlew
python3 scripts/test_tweakeroo_freecam.py 2>&1 | tee "$diag/tweakeroo-freecam.log"
args=(--init-script gradle/ci.init.gradle --no-daemon --no-build-cache
      --console=plain --warning-mode fail --stacktrace --max-workers=2)

for dist in native curseforge; do
  ./gradlew "$BUILD_TASK" "-Pdist=$dist" "${args[@]}" 2>&1 | tee "$diag/$dist-build.log"
  pwsh -NoProfile -File scripts/check-javac-warning-fingerprints.ps1 \
    -LogPath "$diag/$dist-build.log" -RepoRoot "$repo_root" \
    -Branch "$DIAGNOSTIC_BRANCH" -Dist "$dist" -Task "$COMPILE_TASK" \
    -BaselinePath gradle/diagnostic-warning-baselines.json \
    -SnapshotPath "$diag/$dist-snapshot.json"
  # Gradle overwrites reports on the second variant; retain both suites.
  mkdir -p "$diag/$dist-tests"
  while IFS= read -r report; do
    target="$diag/$dist-tests/$(dirname "$report")"
    mkdir -p "$target"
    cp "$report" "$target/"
  done < <(find . -path '*/build/test-results/test/TEST-*.xml' -not -path './build/ci-diagnostics/*')
  DIST="$dist" python3 - <<'PY'
import glob, os, zipfile
pattern = os.environ['JAR_GLOB']
if os.environ['DIST'] == 'curseforge':
    pattern = os.environ['CURSEFORGE_GLOB']
jars = glob.glob(pattern)
assert len(jars) == 1, f'Expected one final JAR: {pattern}, found {jars}'
with zipfile.ZipFile(jars[0]) as jar:
    names = set(jar.namelist())
    assert os.environ['MOD_META'] in names
    assert 'com/micaftic/morpher/YesSteveModel.class' in names
    if os.environ['DIST'] == 'native':
        assert 'natives/windows-x64/ysm-core.dll' in names
        assert 'native-manifest.json' in names
    else:
        assert not any(n.startswith('natives/') or n == 'native-manifest.json' or
                       n.endswith(('.dll', '.so', '.dylib', '.jnilib', '.exe')) for n in names)
print(f'Final artifact smoke passed: {jars[0]}')
PY
done
./gradlew -Pdist=modrinth printDistribution "${args[@]}" | tee "$diag/modrinth-distribution.txt"
grep -q '^distribution=native$' "$diag/modrinth-distribution.txt"
