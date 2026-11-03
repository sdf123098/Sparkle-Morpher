"""Exercise the real loader/verifier with multiple JVMs holding real Windows DLLs.

Only Minecraft/platform glue is replaced. Native extraction, hashing, file moves
and System.load execute from production code against an isolated temporary root.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASE = "com.micaftic.morpher."


@unittest.skipUnless(os.name == "nt", "Real Windows DLL locking regression")
class NativeMultiInstanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="spm-native-multi-")
        cls.work = Path(cls.temp.name)
        cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
        dependencies = ["com.google.code.gson/gson", "commons-io/commons-io",
                        "org.apache.commons/commons-lang3", "net.java.dev.jna/jna"]
        jars = [next(p for p in sorted((cache / dep).glob("*/*/*.jar"), reverse=True)
                     if not p.name.endswith(("-sources.jar", "-javadoc.jar"))) for dep in dependencies]
        cls.classpath = str(cls.work / "classes") + os.pathsep + os.pathsep.join(map(str, jars))
        stubs = {
            "org.jetbrains.annotations.Nullable": "public @interface Nullable {}",
            "org.jetbrains.annotations.NotNull": "public @interface NotNull {}",
            "net.minecraft.network.chat.Component": "public class Component {public static Component translatable(String k,Object...v){return new Component();}}",
            "net.minecraft.util.StringUtil": "public class StringUtil {public static boolean isNullOrEmpty(String s){return s==null || s.isEmpty();}}",
            "com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel": "public class GeoModel {public static int nGetAbiVersion(){return ModelAccelerationBridge.nGetAbiVersion();}}",
            "com.elfmcys.yesstevemodel.geckolib3.geo.ModelRendererBridge": "public class ModelRendererBridge {public static void submitVertices(Object v,int n,java.nio.ByteBuffer f,java.nio.ByteBuffer i){}}",
            BASE + "core.architectury.platform.Platform": "public class Platform {public static java.nio.file.Path getConfigFolder(){return java.nio.file.Path.of(System.getProperty(\"fixture.config\"));}}",
            "dev.architectury.platform.Platform": "public class Platform {public static java.nio.file.Path getConfigFolder(){return java.nio.file.Path.of(System.getProperty(\"fixture.config\"));}}",
            BASE + "core.api.PlatformAPI": "public class PlatformAPI {public static boolean isServer(){return Boolean.getBoolean(\"fixture.server\");}}",
            "net.neoforged.fml.loading.FMLPaths": "public class FMLPaths {public static final Config CONFIGDIR=new Config();public static class Config {public java.nio.file.Path get(){return java.nio.file.Path.of(System.getProperty(\"fixture.config\"));}}}",
            BASE + "YesSteveModel": """public class YesSteveModel {
                public static final String MOD_ID="sparkle_morpher";
                public static final Log LOGGER=new Log();
                public static class Log {
                    public void info(String m,Object...v){} public void warn(String m,Object...v){}
                    public void error(String m,Object...v){}
                }
            }""",
        }
        for name, body in stubs.items():
            cls.write(name, "package " + name.rsplit(".", 1)[0] + "; " + body)
        production = {
            "RuntimeAccelerationLoader": "common/src/native/java/",
            "core.nativeutil.NativeArtifactVerifier": "common/src/main/java/",
            "core.storage.AtomicFileMover": "common/src/main/java/",
            "util.DigestUtil": "common/src/main/java/",
        }
        for suffix, prefix in production.items():
            name = BASE + suffix
            cls.write(name, (ROOT / prefix / (name.replace(".", "/") + ".java")).read_text(encoding="utf-8"))
        bridge = "com.elfmcys.yesstevemodel.geckolib3.geo.render.built.ModelAccelerationBridge"
        cls.write(bridge, (ROOT / "common/src/native/java" / (bridge.replace(".", "/") + ".java")).read_text(encoding="utf-8"))
        cls.write("fixture.Probe", """
            package fixture;
            import java.nio.file.*;
            import com.micaftic.morpher.RuntimeAccelerationLoader;
            public class Probe {
                public static void main(String[] args) throws Exception {
                    String gate=System.getProperty("fixture.start");
                    if(gate!=null) while(!Files.exists(Path.of(gate))) Thread.sleep(5);
                    if(args[0].equals("hold")) System.load(Path.of(args[1]).toAbsolutePath().toString());
                    else {
                        RuntimeAccelerationLoader.init();
                        if(!RuntimeAccelerationLoader.isAvailable()) throw new AssertionError("Native failure must not disable config initialization");
                        boolean expectedLoaded=!args[0].equals("fallback");
                        if(RuntimeAccelerationLoader.isLoaded()!=expectedLoaded) throw new AssertionError("Unexpected native acceleration state");
                    }
                    Files.writeString(Path.of(args[2]),"READY");
                    if(args[0].equals("hold")) System.in.read();
                }
            }
        """)
        result = subprocess.run([shutil.which("javac"), "-cp", cls.classpath, "-d", str(cls.work / "classes"),
                                 *map(str, cls.work.rglob("*.java"))], capture_output=True, text=True)
        if result.returncode:
            raise AssertionError(result.stdout + result.stderr)
        native_file = next(ROOT / prefix / "natives/windows-x64/ysm-core.dll"
                           for prefix in ["common/src/main/resources", "src/main/resources"]
                           if (ROOT / prefix / "natives/windows-x64/ysm-core.dll").exists())
        cls.dll = native_file.read_bytes()
        # PE permits overlay data: different trusted artifact hashes, same valid DLL code.
        cls.new_dll = cls.dll + b"SPM multi-instance regression build B"
        cls.resources = cls.work / "resources"
        (cls.resources / "natives/windows-x64").mkdir(parents=True)
        (cls.resources / "natives/windows-x64/ysm-core.dll").write_bytes(cls.new_dll)
        cls.digest = hashlib.sha256(cls.new_dll).hexdigest()
        (cls.resources / "native-manifest.json").write_text(json.dumps({"artifacts": [{
            "platform": "windows-x64", "filename": "ysm-core.dll", "sha256": cls.digest,
            "abi": 3, "version": "fixture-build-B"}]}), encoding="utf-8")

    @classmethod
    def write(cls, name, text):
        path = cls.work / "src" / (name.replace(".", "/") + ".java")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def setUp(self):
        self.case = Path(tempfile.mkdtemp(dir=self.work))
        self.cache_root = self.case / "tmp/spm"
        self.cache_root.mkdir(parents=True)
        self.config_root = self.case / "config"
        self.config_root.mkdir()

    def command(self, mode, arg, ready):
        return [shutil.which("java"), "--enable-native-access=ALL-UNNAMED",
                "-Djava.io.tmpdir=" + str(self.case / "tmp"),
                "-Dfixture.config=" + str(self.config_root), "-cp",
                str(self.resources) + os.pathsep + self.classpath,
                "fixture.Probe", mode, str(arg), str(ready)]

    def hold(self, library):
        ready = self.case / "holder.ready"
        process = subprocess.Popen(self.command("hold", library, ready), stdin=subprocess.PIPE,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        self.addCleanup(self.release, process)
        deadline = time.monotonic() + 15
        while not ready.exists() and process.poll() is None and time.monotonic() < deadline:
            time.sleep(0.02)
        if not ready.exists():
            process.kill()
            self.fail("DLL holder did not start: " + process.communicate(timeout=5)[0])
        return process

    @staticmethod
    def release(process):
        if process.poll() is not None:
            process.communicate(timeout=5)
            return
        try:
            process.communicate("x\n", timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.communicate(timeout=5)

    def probe(self, mode="load"):
        result = subprocess.run(self.command(mode, "unused", self.case / "probe.ready"),
                                capture_output=True, text=True, timeout=20)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_legacy_dll_in_use_does_not_block_another_build(self):
        legacy = self.cache_root / "ysm-core.dll"
        legacy.write_bytes(self.dll)
        self.hold(legacy)
        self.probe()
        self.assertEqual(legacy.read_bytes(), self.dll, "live legacy DLL must remain unchanged")

    def test_unwritable_cache_still_allows_config_and_java_fallback(self):
        shutil.rmtree(self.cache_root)
        self.cache_root.write_bytes(b"blocked temp cache")
        self.config_root.rmdir()
        self.config_root.write_bytes(b"blocked fallback config path")
        self.probe("fallback")

    def test_same_build_reuses_loaded_verified_dll(self):
        self.probe()
        native = next(p for p in self.cache_root.rglob("*.dll") if p.read_bytes() == self.new_dll)
        before = native.stat().st_mtime_ns
        self.hold(native)
        self.probe()
        self.assertEqual(native.stat().st_mtime_ns, before, "verified loaded native must not be rewritten")

    def test_corrupt_digest_cache_in_use_gets_private_install(self):
        target = self.cache_root / "windows-x64" / self.digest / "ysm-core.dll"
        target.parent.mkdir(parents=True)
        target.write_bytes(self.dll)
        self.hold(target)
        self.probe()
        self.assertEqual(target.read_bytes(), self.dll, "locked corrupt cache must remain unchanged")

    def test_concurrent_first_installers_all_load_verified_dll(self):
        gate = self.case / "start"
        children = []
        for index in range(6):
            command = self.command("load", "unused", self.case / (str(index) + ".ready"))
            command.insert(1, "-Dfixture.start=" + str(gate))
            child = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            children.append(child)
            self.addCleanup(self.release, child)
        gate.touch()
        results = [child.communicate(timeout=20)[0] for child in children]
        for child, output in zip(children, results):
            self.assertEqual(child.returncode, 0, output)
        target = self.cache_root / "windows-x64" / self.digest / "ysm-core.dll"
        self.assertEqual(target.read_bytes(), self.new_dll)


if __name__ == "__main__":
    unittest.main()
