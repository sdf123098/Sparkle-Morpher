from pathlib import Path


repo = Path(__file__).resolve().parents[1]
source = repo / "common/src/main/java/com/micaftic/morpher/mixin/client/EntityRenderDispatcherMixin.java"
submit_buffer = repo / "common/src/main/java/com/micaftic/morpher/client/renderer/SubmitMultiBufferSource.java"
maid_renderer = repo / "common/src/main/java/com/micaftic/morpher/client/renderer/MaidEntityRenderer.java"

text = source.read_text(encoding="utf-8")
errors = []

if "new SubmitMultiBufferSource(collector, poseStack)" not in text:
    errors.append("EntityRenderDispatcherMixin must create a SubmitMultiBufferSource for MC 26.2 rendering.")
if "getLegacyBufferSourceOrNull" in text:
    errors.append("EntityRenderDispatcherMixin must not use removed renderBuffers().bufferSource() compatibility path.")
if ".endBatch()" in text:
    errors.append("EntityRenderDispatcherMixin must not flush SubmitMultiBufferSource via endBatch().")
if "collector.submitCustomGeometry" not in submit_buffer.read_text(encoding="utf-8"):
    errors.append("SubmitMultiBufferSource must submit recorded geometry to the collector.")
if "SubmitNodeCollector collector" not in maid_renderer.read_text(encoding="utf-8"):
    errors.append("Maid renderer must receive the submit collector.")

if errors:
    print("FAIL: fa26.2 maid submit-render regression check")
    for error in errors:
        print(f" - {error}")
    raise SystemExit(1)

print("PASS: fa26.2 maid submit-render regression check")
