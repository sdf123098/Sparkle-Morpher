from pathlib import Path


source = Path(__file__).resolve().parents[1] / "common/src/main/java/com/micaftic/morpher/client/entity/PlayerGeoEntity.java"
text = source.read_text(encoding="utf-8")
method_start = text.find("public boolean shouldSkipAnimation(AnimationEvent<?> event)")
method_end = text.find("\n    }", method_start)
method = text[method_start:method_end]

if method_start < 0:
    raise SystemExit("FAIL: PlayerGeoEntity must define shouldSkipAnimation.")
if "return false;" not in method or "return true;" in method:
    raise SystemExit(
        "FAIL: first-person PlayerGeoEntity must evaluate arm animations so model-controlled visibility is applied."
    )

print("PASS: first-person arm animations are evaluated")
