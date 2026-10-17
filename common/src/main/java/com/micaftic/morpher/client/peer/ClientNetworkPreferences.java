package com.micaftic.morpher.client.peer;

import com.micaftic.morpher.client.PrivacyMode;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.core.network.*;
import com.micaftic.morpher.cloud.client.*;

/** Main-thread config adapter; account credentials and local selections survive preset changes. */
public final class ClientNetworkPreferences {
    private ClientNetworkPreferences() {}
    public static NetworkPreset preset() {
        try { return GeneralConfig.NETWORK_PRESET.get(); } catch(IllegalStateException unavailable) { return NetworkPreset.AUTO; }
    }
    public static void select(NetworkPreset preset) {
        GeneralConfig.NETWORK_PRESET.set(preset); GeneralConfig.NETWORK_PRESET.save(); refresh();
    }
    public static void refresh() {
        if(!NetworkAccess.update(preset(), PrivacyMode.isActive())) return;
        if(!NetworkAccess.allowsPeer()) ClientPeerRuntime.disconnect();
        if(!NetworkAccess.allowsCloud()) {
            CloudPlayerModelSync.disconnect(); CloudEntityModelSync.disconnect();
        }
        CloudClientRuntime.networkPolicyChanged();
    }
}
