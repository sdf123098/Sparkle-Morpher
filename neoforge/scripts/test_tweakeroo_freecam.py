"""Execute the production cache/render entry against a minimal camera/game fixture.

No Tweakeroo installation is required. Its public CameraEntity inheritance and
the spectator spoof are represented here; the code under test is compiled from
this checkout, rather than reimplementing its decisions in Python.
"""
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class FreecamCompatibilityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="spm-freecam-")
        cls.work = Path(cls.temp.name)
        store = next(p for prefix in ["fabric/src/main/java", "common/src/main/java", "src/main/java"]
                     for p in (ROOT / prefix / "com/micaftic/morpher/capability").glob("**/PlayerCapabilityClientStore.java"))
        event = ROOT / "common/src/main/java/com/micaftic/morpher/client/event/ReplacePlayerRenderEvent.java"
        if not event.exists():
            event = ROOT / "src/main/java/com/micaftic/morpher/client/event/ReplacePlayerRenderEvent.java"
        store_package = re.search(r"package ([\w.]+);", store.read_text(encoding="utf-8")).group(1)
        buffer_class = re.search(r"import ([\w.]+MultiBufferSource);", event.read_text(encoding="utf-8")).group(1)
        has_collector = "import net.minecraft.client.renderer.SubmitNodeCollector;" in event.read_text(encoding="utf-8")
        sources = {
            "net.minecraft.world.entity.Entity": "public int id; public int getId(){return id;}",
            "net.minecraft.world.entity.player.Player": """
                public final java.util.UUID uuid; public boolean spectator;
                public Player(java.util.UUID uuid,int id){this.uuid=uuid;this.id=id;}
                public java.util.UUID getUUID(){return uuid;}
                public boolean isSpectator(){return spectator;}
                public float getYRot(){return 0;}
                public Name getName(){return new Name();} public static class Name{public String getString(){return "player";}}
            """,
            "net.minecraft.client.player.AbstractClientPlayer": "public AbstractClientPlayer(java.util.UUID u,int id){super(u,id);}",
            "net.minecraft.client.player.LocalPlayer": "public LocalPlayer(java.util.UUID u,int id){super(u,id);}",
            "fi.dy.masa.tweakeroo.util.CameraEntity": "public CameraEntity(java.util.UUID u,int id){super(u,id);spectator=true;}",
            "fixture.DerivedCamera": "public DerivedCamera(java.util.UUID u,int id){super(u,id);}",
            "net.minecraft.world.level.GameType": "SURVIVAL, CREATIVE, SPECTATOR",
            "net.minecraft.client.multiplayer.MultiPlayerGameMode": "public net.minecraft.world.level.GameType mode=net.minecraft.world.level.GameType.CREATIVE; public net.minecraft.world.level.GameType getPlayerMode(){return mode;}",
            "net.minecraft.client.multiplayer.ClientLevel": "public net.minecraft.world.entity.Entity body; public net.minecraft.world.entity.Entity getEntity(int id){return body!=null&&body.getId()==id?body:null;}",
            "net.minecraft.client.Minecraft": """
                private static final Minecraft INSTANCE=new Minecraft();
                public net.minecraft.client.player.LocalPlayer player;
                public net.minecraft.client.multiplayer.ClientLevel level=new net.minecraft.client.multiplayer.ClientLevel();
                public net.minecraft.client.multiplayer.MultiPlayerGameMode gameMode=new net.minecraft.client.multiplayer.MultiPlayerGameMode();
                public net.minecraft.world.entity.Entity camera;
                public static Minecraft getInstance(){return INSTANCE;}
                public net.minecraft.world.entity.Entity getCameraEntity(){return camera;}
            """,
            "com.micaftic.morpher.YesSteveModel": "public static boolean isAvailable(){return true;} public static final Logger LOGGER=new Logger(); public static class Logger{public void info(String s,Object...v){} public void warn(String s,Object...v){}}",
            "com.micaftic.morpher.capability.PlayerCapability": """
                public final net.minecraft.world.entity.player.Player entity; public boolean active;
                public PlayerCapability(net.minecraft.world.entity.player.Player p){entity=p;}
                public static java.util.Optional<PlayerCapability> get(net.minecraft.world.entity.player.Player p){return STORE.get(p);}
                public void tickModel(){} public boolean isModelActive(){return active;}
                public boolean hasRenderableModel(){return active;} public boolean isModelReady(){return active;}
                public String getModelId(){return "custom";}
            """.replace("STORE", store_package + ".PlayerCapabilityClientStore"),
            "com.micaftic.morpher.core.config.ConfigPolicies": "public static boolean disableSelf,disableOther; public static Render render(){return new Render();} public static Render diagnostics(){return new Render();} public static class Render{public boolean animationDebugLog(){return false;} public boolean disableSelfModel(){return disableSelf;} public boolean disableOtherModel(){return disableOther;} public boolean disableExternalFirstPersonAnimation(){return false;}}",
            "com.micaftic.morpher.client.renderer.ModelPreviewRenderer": "public static final float FRONT_FACING_YAW=180;public static boolean isInventoryPreviewFrontFacing(){return false;}",
            "com.micaftic.morpher.util.CameraUtil": "public static boolean isFirstPerson(Object c){return false;}",
            "com.micaftic.morpher.core.compat.firstperson.FirstPersonCompat": "public static boolean isFirstPersonActive(){return false;}",
            "com.micaftic.morpher.core.compat.realcamera.RealCameraCompat": "public static boolean isActive(){return false;}",
            "com.micaftic.morpher.core.compat.playeranimator.PlayerAnimatorCompat": "public static boolean isPlayerAnimated(Object p){return false;}",
            "com.micaftic.morpher.client.renderer.RendererManager": "public static int renders; public static Renderer getPlayerRenderer(){return new Renderer();} public static class Renderer{public void render(Object...args){renders++;}}",
            "com.micaftic.morpher.client.peer.ClientPeerRuntime": "public static boolean ownsAppearance(java.util.UUID u){return false;} public static void renderState(Object...args){}",
            "com.micaftic.morpher.peer.PeerFrameWork": "public static final PeerFrameWork PEER=new PeerFrameWork();public boolean admitModel(String id){return true;}",
            "com.micaftic.morpher.peer.PeerObservation": "public enum State{WAITING_RENDER,RENDER_SUBMITTED,HIDDEN,FAILED}",
            "com.mojang.blaze3d.vertex.PoseStack": "",
            "net.minecraft.client.renderer.SubmitNodeCollector": "",
            buffer_class: "",
        }
        parents = {
            "net.minecraft.world.entity.player.Player": "net.minecraft.world.entity.Entity",
            "net.minecraft.client.player.AbstractClientPlayer": "net.minecraft.world.entity.player.Player",
            "net.minecraft.client.player.LocalPlayer": "net.minecraft.client.player.AbstractClientPlayer",
            "fi.dy.masa.tweakeroo.util.CameraEntity": "net.minecraft.client.player.LocalPlayer",
            "fixture.DerivedCamera": "fi.dy.masa.tweakeroo.util.CameraEntity",
        }
        for name, body in sources.items():
            package, simple = name.rsplit(".", 1)
            kind = "enum" if name.endswith("GameType") else "class"
            parent = " extends " + parents[name] if name in parents else ""
            cls.write(name, f"package {package}; public {kind} {simple}{parent} {{{body}}}")
        cls.write("fixture.Harness", """
            package fixture;
            import net.minecraft.client.Minecraft;
            import net.minecraft.client.player.*;
            import net.minecraft.world.level.GameType;
            import com.micaftic.morpher.capability.PlayerCapability;
            import com.micaftic.morpher.client.event.ReplacePlayerRenderEvent;
            import com.micaftic.morpher.client.renderer.RendererManager;
            import com.micaftic.morpher.core.config.ConfigPolicies;
            import STORE;
            import BUFFER;
            public class Harness {
                static final Minecraft mc=Minecraft.getInstance();
                static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
                static boolean render(net.minecraft.world.entity.player.Player p){return ReplacePlayerRenderEvent.onRenderPlayerPre(p,RENDER_ARGS new com.mojang.blaze3d.vertex.PoseStack(),new BUFFER(),1);}
                public static void main(String[] args){
                    var id=java.util.UUID.randomUUID();var body=new LocalPlayer(id,17);
                    mc.player=body;mc.level.body=body;mc.camera=body;
                    var original=PlayerCapabilityClientStore.get(body).orElseThrow();original.active=true;
                    var camera=new fi.dy.masa.tweakeroo.util.CameraEntity(id,17);
                    switch(args[0]){
                        case "camera_cache" -> {
                            check(PlayerCapabilityClientStore.get(camera).isEmpty(),"camera surrogate must never acquire a player model capability");
                            check(PlayerCapabilityClientStore.get(body).orElseThrow()==original,"camera lookup replaced the real body capability");
                        }
                        case "camera_subclass" -> check(PlayerCapabilityClientStore.get(new DerivedCamera(id,17)).isEmpty(),"a derived camera must also be excluded");
                        case "spoofed_spectator" -> {
                            body.spectator=true;mc.camera=camera;
                            check(render(body),"freecam's spectator spoof must not restore the vanilla body");
                            check(RendererManager.renders==1,"custom renderer did not receive the real body");
                        }
                        case "actual_spectator" -> {
                            body.spectator=true;mc.gameMode.mode=GameType.SPECTATOR;mc.camera=camera;
                            check(!render(body),"real spectators must retain vanilla rendering");
                        }
                        case "remote_spectator" -> {
                            mc.camera=camera;var other=new AbstractClientPlayer(java.util.UUID.randomUUID(),22);other.spectator=true;
                            PlayerCapabilityClientStore.get(other).orElseThrow().active=true;
                            check(!render(other),"freecam must not change other players' spectator status");
                        }
                        case "ordinary_camera" -> {
                            body.spectator=true;mc.camera=new net.minecraft.world.entity.Entity();
                            check(!render(body),"unrelated cameras must not bypass spectator rendering");
                        }
                        case "disabled_self" -> {
                            mc.camera=camera;body.spectator=true;ConfigPolicies.disableSelf=true;
                            check(!render(body),"freecam must respect DisableSelfModel");
                        }
                        case "missing_gamemode" -> {
                            mc.camera=camera;body.spectator=true;mc.gameMode=null;
                            check(!render(body),"missing game mode must preserve the spectator fallback");
                        }
                        case "respawn" -> {
                            var replacement=new LocalPlayer(id,18);mc.player=replacement;mc.level.body=replacement;
                            check(PlayerCapabilityClientStore.get(replacement).orElseThrow()!=original,"a real respawn must replace stale capability");
                        }
                        case "repeat_toggle" -> {
                            for(int i=0;i<20;i++){
                                mc.camera=camera;body.spectator=true;PlayerCapabilityClientStore.get(camera);
                                check(PlayerCapabilityClientStore.get(body).orElseThrow()==original,"toggle lost model state");
                                check(render(body),"freecam toggle showed vanilla body");
                                mc.camera=body;body.spectator=false;check(render(body),"freecam exit lost custom model");
                            }
                        }
                        case "disconnect" -> {
                            PlayerCapabilityClientStore.clear();
                            check(PlayerCapabilityClientStore.get(body).orElseThrow()!=original,"disconnect must release cached capabilities");
                        }
                        default -> throw new AssertionError(args[0]);
                    }
                }
            }
        """.replace("import STORE;", "import " + store_package + ".PlayerCapabilityClientStore;")
          .replace("BUFFER", buffer_class).replace("RENDER_ARGS", "0," if has_collector else "0,0,"))
        production = [store, event]
        for name in ["client/render/PlayerRenderPolicy.java", "core/compat/tweakeroo/TweakerooCompat.java"]:
            for prefix in ["common/src/main/java", "src/main/java"]:
                candidate = ROOT / prefix / "com/micaftic/morpher" / name
                if candidate.exists():
                    production.append(candidate)
                    break
        java_home = os.environ.get("JAVA_HOME")
        cls.java = str(Path(java_home) / "bin/java") if java_home else shutil.which("java")
        javac = str(Path(java_home) / "bin/javac") if java_home else shutil.which("javac")
        command = [javac, "-encoding", "UTF-8", "-d", str(cls.work / "classes")]
        command += [str(p) for p in cls.work.rglob("*.java")] + [str(p) for p in production]
        result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace")
        if result.returncode:
            raise RuntimeError(result.stdout + result.stderr)

    @classmethod
    def write(cls, name, text):
        target = cls.work / (name.replace(".", "/") + ".java")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8")

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def run_case(self, case):
        result = subprocess.run([self.java, "-cp", str(self.work / "classes"), "fixture.Harness", case],
                                capture_output=True, text=True, encoding="utf-8", errors="replace")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)


for _case in ["camera_cache", "camera_subclass", "spoofed_spectator", "actual_spectator", "remote_spectator",
              "ordinary_camera", "disabled_self", "missing_gamemode", "respawn", "repeat_toggle", "disconnect"]:
    setattr(FreecamCompatibilityTest, "test_" + _case, lambda self, case=_case: self.run_case(case))

if __name__ == "__main__":
    unittest.main(verbosity=2)
