"""Run the production camera queries and HUD pose cache with a small JVM fixture.

The fixture models a first-person body pose whose head was hidden by a model
expression. It does not need a game window or optional First-person Model jar.
"""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASE = "com.micaftic.morpher."


class FirstPersonHudTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="spm-firstperson-hud-")
        cls.work = Path(cls.temp.name)
        sources = {
            "org.jetbrains.annotations.Nullable": "public @interface Nullable {}",
            "net.minecraft.world.entity.Entity": "public class Entity { public int tickCount; }",
            "net.minecraft.client.CameraType": "public enum CameraType { FIRST_PERSON, THIRD_PERSON_BACK, THIRD_PERSON_FRONT; public boolean isFirstPerson(){return this==FIRST_PERSON;} }",
            "net.minecraft.client.Minecraft": """public class Minecraft {
                private static final Minecraft INSTANCE=new Minecraft();
                public net.minecraft.world.entity.Entity player=new net.minecraft.world.entity.Entity(), camera=player;
                public Options options=new Options();
                public Object screen;
                public static Minecraft getInstance(){return INSTANCE;}
                public net.minecraft.world.entity.Entity getCameraEntity(){return camera;}
                public static class Options {
                    public CameraType type=CameraType.FIRST_PERSON;
                    public CameraType getCameraType(){return type;}
                }
            }""",
            "net.minecraft.util.Mth": "public class Mth { public static float clamp(float v,float a,float b){return Math.max(a,Math.min(v,b));} }",
            BASE+"core.compat.oculus.OculusCompat": "public class OculusCompat { public static boolean active; public static boolean isPBRActive(){return active;} }",
            BASE+"core.compat.firstperson.FirstPersonCompat": "public class FirstPersonCompat { public static boolean active; public static boolean isFirstPersonActive(){return active;} }",
            BASE+"client.renderer.ModelPreviewRenderer": """public class ModelPreviewRenderer {
                public static boolean world;
                public static boolean isWorldRender(){return world;}
                public static boolean isPreview(){return com.micaftic.morpher.client.render.RenderContext.isModelPreview();}
                public static boolean isFirstPerson(){return com.micaftic.morpher.client.render.RenderContext.isFirstPerson() || com.micaftic.morpher.core.compat.firstperson.FirstPersonCompat.active;}
            }""",
            BASE+"geckolib3.core.AnimatableEntity": """public class AnimatableEntity<T extends net.minecraft.world.entity.Entity> {
                public T entity; public com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel model=new com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel();
                public T getEntity(){return entity;}
                public com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel getCurrentModel(){return model;}
                public int getLastAnimationEvaluationFrameId(){return 1;}
                public com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel getLastAnimationEvaluationModel(){return model;}
                public float getLastAnimationEvaluationSeekTime(){return 0;}
                public float getSeekTime(){return 0;}
            }""",
            BASE+"client.entity.IPreviewAnimatable": "public interface IPreviewAnimatable {}",
            BASE+"client.entity.LivingAnimatable": """public class LivingAnimatable<T extends net.minecraft.world.entity.Entity> extends com.micaftic.morpher.geckolib3.core.AnimatableEntity<T> {
                public float getHeadBaseRotationX(){return 0;} public float getHeadBaseRotationY(){return 0;}
            }""",
            BASE+"client.entity.GeoEntity": "public class GeoEntity<T extends net.minecraft.world.entity.Entity> extends LivingAnimatable<T> { public String getModelId(){return \"model\";} }",
            BASE+"client.animation.debug.AnimationFrameProfiler": "public class AnimationFrameProfiler {public static int getRenderFrameId(){return 1;} }",
            BASE+"geckolib3.geo.animated.AnimatedGeoModel": """public class AnimatedGeoModel {
                public float[] matrices={0,0,0,0}, pivots={0};
                public float[] getMatrixData(){return matrices;} public float[] getAbsPivotData(){return pivots;}
            }""",
            BASE+"geckolib3.core.molang.context.IContext": """public interface IContext<T extends net.minecraft.world.entity.Entity> {
                T entity(); net.minecraft.client.Minecraft mc(); com.micaftic.morpher.geckolib3.core.AnimatableEntity<T> geoInstance();
            }""",
            BASE+"client.gui.HudLayoutScreen": "public class HudLayoutScreen {}",
            BASE+"util.InputUtil": "public class InputUtil {public static Object getCurrentScreen(){return null;} }",
            BASE+"config.ExtraPlayerRenderConfig": "public class ExtraPlayerRenderConfig {public static Value CLASSIC_HUD_ANIMATION_SAMPLE_FPS=new Value();public static class Value {public int get(){return 60;}} }",
            BASE+"geckolib3.core.event.predicate.AnimationEvent": "public class AnimationEvent<T> {public float getPartialTick(){return 0.5f;} }",
        }
        for name, body in sources.items():
            package, simple = name.rsplit(".", 1)
            cls.write(name, f"package {package}; {body}")
        for name in ["util.CameraUtil", "client.render.RenderContext", "client.render.RenderPass",
                     "client.renderer.hud.HudPoseCache", "client.renderer.hud.HudAnimationGate",
                     "client.renderer.hud.HudSampledPose"]:
            relative = BASE + name
            path = ROOT / "common/src/main/java" / (relative.replace(".", "/") + ".java")
            cls.write(relative, path.read_text(encoding="utf-8"))
        cls.write("fixture.Harness", """
            package fixture;
            import com.micaftic.morpher.client.render.*;
            import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
            import com.micaftic.morpher.client.renderer.hud.HudPoseCache;
            import com.micaftic.morpher.client.renderer.hud.HudAnimationGate;
            import com.micaftic.morpher.client.renderer.hud.HudAnimationGate.Decision;
            import com.micaftic.morpher.client.entity.GeoEntity;
            import com.micaftic.morpher.util.CameraUtil;
            import com.micaftic.morpher.core.compat.firstperson.FirstPersonCompat;
            import net.minecraft.client.*;
            import net.minecraft.world.entity.Entity;
            public class Harness {
                static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
                static com.micaftic.morpher.geckolib3.core.molang.context.IContext<Entity> context(GeoEntity<Entity> body){
                    return new com.micaftic.morpher.geckolib3.core.molang.context.IContext<>(){
                        public Entity entity(){return body.entity;}
                        public Minecraft mc(){return Minecraft.getInstance();}
                        public GeoEntity<Entity> geoInstance(){return body;}
                    };
                }
                public static void main(String[] args){
                    Minecraft mc=Minecraft.getInstance();
                    GeoEntity<Entity> body=new GeoEntity<>(); body.entity=mc.player;
                    ModelPreviewRenderer.world=true;
                    if(args[0].equals("cache")){
                        mc.options.type=CameraType.THIRD_PERSON_BACK;
                        HudPoseCache.publish(body,0.5f);
                        check(HudPoseCache.canReuse(body,0.5f),"third-person poses should remain reusable");
                        mc.options.type=CameraType.FIRST_PERSON;
                        body.model.matrices[3]=1; // visibility bit written by a model hiding expression
                        HudPoseCache.publish(body,0.5f);
                        RenderContext.enter(RenderPass.OLD_HUD);
                        check(!HudPoseCache.canReuse(body,0.5f),"HUD must not reuse a first-person body pose with hidden head bones");
                        check(!HudPoseCache.applyTo(body),"rejected pose must not be applied to the HUD");
                    } else if(args[0].equals("camera")){
                        check(CameraUtil.isFirstPerson(body),"world body head must remain hidden in first person");
                        check(CameraUtil.getCameraType(context(body))==CameraType.FIRST_PERSON.ordinal(),"world Molang perspective must stay first person");
                        FirstPersonCompat.active=true;
                        for(RenderPass pass:new RenderPass[]{RenderPass.OLD_HUD,RenderPass.GUI_PREVIEW,RenderPass.PAPER_DOLL}){
                            RenderContext.enter(pass);
                            check(!CameraUtil.isFirstPerson(body),pass+" must not inherit camera head hiding");
                            check(CameraUtil.getCameraType(context(body))==CameraType.THIRD_PERSON_FRONT.ordinal(),pass+" Molang expressions must see the full model perspective");
                        }
                        RenderContext.enter(RenderPass.FIRST_PERSON);
                        RenderContext.setModelPreview(true);
                        check(!CameraUtil.isFirstPerson(body),"model preview must override first-person compatibility flags");
                    } else if(args[0].equals("schedule")){
                        RenderContext.enter(RenderPass.OLD_HUD);
                        check(HudAnimationGate.shouldEvaluate(Decision.FULL_EVAL,false),"HUD full sample must evaluate even after the world consumed the tick");
                        check(!HudAnimationGate.shouldEvaluate(Decision.HOLD_SAMPLED_POSE,false),"held HUD pose must retain sampling throttle");
                        check(!HudAnimationGate.shouldEvaluate(Decision.REUSE_WORLD_POSE,false),"safe cached world pose must not trigger evaluation");
                        RenderContext.enter(RenderPass.WORLD);
                        check(!HudAnimationGate.shouldEvaluate(Decision.FULL_EVAL,false),"world animation must retain its tick throttle");
                        check(HudAnimationGate.shouldEvaluate(Decision.FULL_EVAL,true),"scheduled world evaluation must remain scheduled");
                    } else {
                        RenderContext.enter(RenderPass.FIRST_PERSON);
                        check(CameraUtil.isFirstPerson(body),"first-person hand/body pass must retain camera context");
                        mc.camera=new Entity();
                        check(!CameraUtil.isFirstPerson(body),"detached camera must not hide the player head");
                    }
                }
            }
        """)
        compiled = subprocess.run([shutil.which("javac"), "-d", str(cls.work / "classes"),
                                  *map(str, cls.work.rglob("*.java"))], capture_output=True, text=True)
        if compiled.returncode:
            raise AssertionError(compiled.stdout + compiled.stderr)

    @classmethod
    def write(cls, name, text):
        path = cls.work / (name.replace(".", "/") + ".java")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def run_case(self, case):
        result = subprocess.run([shutil.which("java"), "-cp", str(self.work / "classes"),
                                 "fixture.Harness", case], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_hud_rejects_firstperson_world_pose(self):
        self.run_case("cache")

    def test_previews_ignore_world_camera_flags(self):
        self.run_case("camera")

    def test_world_and_detached_camera(self):
        self.run_case("world")

    def test_hud_evaluates_after_world_consumed_tick(self):
        self.run_case("schedule")


if __name__ == "__main__":
    unittest.main()
