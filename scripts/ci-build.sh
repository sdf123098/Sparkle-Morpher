#!/usr/bin/env bash
set -euo pipefail

# merged-layout: this repo holds both loaders.
#   BUILD_ROOT=fabric   -> the repo root is the Fabric Gradle build
#   BUILD_ROOT=neoforge -> neoforge/ is a nested, independent Gradle build
# Each build root ships its own gradlew/gradle/scripts, so we run the same
# pipeline from inside the selected build root, but keep every artifact path
# and log directory repo-root-relative so one CI matrix row == one loader.
BUILD_ROOT="${BUILD_ROOT:-fabric}"
if [ "$BUILD_ROOT" = "fabric" ]; then
  BUILD_REL="."
else
  BUILD_REL="$BUILD_ROOT"
fi

repo_root="$(pwd)"
diag="$repo_root/build/ci-diagnostics"

# Only the hosted checkout is changed; developers keep their wrapper mirror.
python3 - "$BUILD_REL" <<'PY'
import sys
from pathlib import Path
p = Path(sys.argv[1]) / 'gradle/wrapper/gradle-wrapper.properties'
p.write_text(p.read_text().replace('mirrors.cloud.tencent.com/gradle/',
                                 'services.gradle.org/distributions/'))
PY

mkdir -p "$diag"
cd "$BUILD_REL"
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
  cd "$repo_root"
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
  cd "$repo_root/$BUILD_REL"
done
./gradlew -Pdist=modrinth printDistribution "${args[@]}" | tee "$diag/modrinth-distribution.txt"
grep -q '^distribution=native$' "$diag/modrinth-distribution.txt"
