package com.micaftic.morpher.core.gui.molang;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.config.ServerConfig;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.resource.GeckoLibCache;
import com.micaftic.morpher.molang.parser.ParseException;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SRequestExecuteMolangPacket;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.math.NumberUtils;
import com.micaftic.morpher.core.gui.Option;

import java.util.function.Consumer;

public final class MolangOption {

    private MolangOption() {
    }

    public static Option<Boolean> ofBoolean(String title, String description, AnimatableEntity<?> animatable, String expr) {
        boolean[] cache = new boolean[]{false};
        evaluate(animatable, expr, s -> cache[0] = toFloat(s) > 0.0f);
        return new LiveOption<>(title, description, () -> cache[0], v -> {
            cache[0] = v;
            execute(animatable, expr + "=" + (v ? "1" : "0"));
        });
    }

    public static Option<Double> ofDouble(String title, String description, AnimatableEntity<?> animatable, String expr) {
        double[] cache = new double[]{0.0};
        evaluate(animatable, expr, s -> cache[0] = toFloat(s));
        return new LiveOption<>(title, description, () -> cache[0], v -> {
            cache[0] = v;
            execute(animatable, expr + "=" + v);
        });
    }

    public static Option<Integer> ofIndex(String title, String description, AnimatableEntity<?> animatable, String readExpr, String[] writeExprs) {
        int[] cache = new int[]{0};
        evaluate(animatable, readExpr, s -> cache[0] = Math.round(toFloat(s)));
        return new LiveOption<>(title, description, () -> cache[0], v -> {
            int idx = Math.max(0, Math.min(writeExprs.length - 1, v));
            cache[0] = idx;
            execute(animatable, writeExprs[idx]);
        });
    }

    private static float toFloat(String s) {
        if (s == null || "null".equals(s)) return 0.0f;
        if (NumberUtils.isParsable(s)) return Float.parseFloat(s);
        Boolean b = BooleanUtils.toBooleanObject(s);
        return b != null && b ? 1.0f : 0.0f;
    }

    private static void evaluate(AnimatableEntity<?> animatable, String expr, Consumer<String> consumer) {
        try {
            animatable.executeExpression(GeckoLibCache.parseSimpleExpression(expr), true, false, consumer);
        } catch (ParseException e) {
            YesSteveModel.LOGGER.error(e);
        }
    }

    private static void execute(AnimatableEntity<?> animatable, String expr) {
        try {
            animatable.executeExpression(GeckoLibCache.parseSimpleExpression(expr), true, false, null);
            if (!GeckoLibCache.isRoamingVariableAssignment(expr) && NetworkHandler.isClientConnected() && !ServerConfig.LOW_BANDWIDTH_USAGE.get()) {
                NetworkHandler.sendToServer(new C2SRequestExecuteMolangPacket(expr, animatable.getEntity().getId()));
            }
        } catch (ParseException e) {
            YesSteveModel.LOGGER.error(e);
        }
    }
}
