package com.micaftic.morpher.event;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientEventBootstrapTest {
    @Test
    void everyReflectiveClientHookExistsAndExposesItsRegistrationMethod() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        List<String> hooks = new ArrayList<>();
        try (InputStream source = loader.getResourceAsStream("com/micaftic/morpher/event/YsmEventBootstrap.class")) {
            assertNotNull(source);
            new ClassReader(source).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (!name.equals("register")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        private String className;

                        @Override
                        public void visitLdcInsn(Object value) {
                            className = value instanceof String text ? text : null;
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
                            if (owner.equals("com/micaftic/morpher/event/YsmEventBootstrap") && method.equals("registerClient")) {
                                assertNotNull(className, "Client hook registration must specify a class name");
                                hooks.add(className);
                            }
                            className = null;
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertFalse(hooks.isEmpty(), "The client bootstrap should expose its hooks for validation");
        for (String name : hooks) {
            Class<?> hook = assertDoesNotThrow(() -> Class.forName(name, false, loader),
                    "Client bootstrap references a missing hook: " + name);
            var register = hook.getMethod("register");
            assertTrue(Modifier.isPublic(register.getModifiers()) && Modifier.isStatic(register.getModifiers()), name);
            assertEquals(void.class, register.getReturnType(), name);
        }
    }

    @Test
    void joinAndQuitNotificationsAreAutomaticallySubscribedOnTheClient() throws Exception {
        Class<?> hook = Class.forName("com.micaftic.morpher.client.event.ClientPlayerJoinNotification", false,
                getClass().getClassLoader());
        var subscriber = hook.getAnnotation(EventBusSubscriber.class);
        assertNotNull(subscriber, "NeoForge must discover the join notification without manual bootstrap registration");
        assertEquals("sparkle_morpher", subscriber.modid());
        assertEquals(EventBusSubscriber.Bus.GAME, subscriber.bus());
        assertEquals(List.of(Dist.CLIENT), Arrays.asList(subscriber.value()));
        assertNotNull(hook.getMethod("onJoin", ClientPlayerNetworkEvent.LoggingIn.class).getAnnotation(SubscribeEvent.class));
        assertNotNull(hook.getMethod("onQuit", ClientPlayerNetworkEvent.LoggingOut.class).getAnnotation(SubscribeEvent.class));
    }
}
