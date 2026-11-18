package com.micaftic.morpher.client.animation.molang;

import com.micaftic.morpher.client.core.HostMolangScript;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatLists;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.List;

public class MolangEventDispatcher {

    public static final String PLAYER_INIT = "player_init";

    public static final String PLAYER_UPDATE = "player_update";

    public static final String SYNC = "sync";

    public static final String DEFER = "defer";

    public static HostMolangScript createExpression(List<HostMolangScript> list, FloatArrayList floatArrayList) {
        return createUpdateExpression(list, floatArrayList != null ? floatArrayList : FloatLists.emptyList());
    }

    public static HostMolangScript createInitExpression(List<HostMolangScript> list) {
        return createUpdateExpression(list, ObjectLists.emptyList());
    }

    public static HostMolangScript createUpdateExpression(List<HostMolangScript> list, List<?> args) {
        if (list == null || list.isEmpty()) {
            return HostMolangScript.ZERO;
        }
        StringBuilder sb = new StringBuilder();
        if (args != null) {
            for (int i = 0; i < args.size(); i++) {
                Object arg = args.get(i);
                String literal = "0";
                if (arg instanceof Boolean b) {
                    literal = b ? "1" : "0";
                } else if (arg instanceof Number n && Double.isFinite(n.doubleValue())) {
                    literal = Double.toString(n.doubleValue());
                }
                sb.append("t.args").append(i).append('=').append(literal).append(';');
            }
        }
        for (HostMolangScript script : list) {
            if (script != null && !script.source().isBlank()) {
                sb.append(script.source());
                if (!script.source().trim().endsWith(";")) {
                    sb.append(';');
                }
            }
        }
        return HostMolangScript.compile(sb.toString());
    }
}
