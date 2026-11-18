package com.micaftic.morpher.client.entity;

import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostControllerSlot;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.render.ModelBoneView;
import com.micaftic.morpher.client.render.ModelScenePose;
import com.micaftic.morpher.core.compat.immersiveaircraft.ImmersiveAirCraftCompat;
import com.micaftic.morpher.core.compat.simpleplanes.SimplePlanesCompat;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class VehicleRotationController extends HostControllerSlot<SpmVehicleEntity> {

    private @Nullable Vector3f vehicleRotation;

    public VehicleRotationController(SpmVehicleEntity entity, String name) {
        super(entity, name, 0.0f, (event, animatable) -> HostPlayState.Decision.STOP);
    }

    @Override
    public String getCurrentAnimation() {
        return "[Coded]";
    }

    @Nullable
    public Vector3f getVehicleRotation() {
        return this.vehicleRotation;
    }

    @Override
    public void applyProceduralPose(HostActionEvent<SpmVehicleEntity> event, ModelScenePose pose) {
        this.vehicleRotation = null;
        ImmersiveAirCraftCompat.getAircraftRotation(event)
                .or(() -> SimplePlanesCompat.getSimplePlanesRotation(event))
                .ifPresent(vector3f -> this.vehicleRotation = new Vector3f(vector3f));
        if (this.vehicleRotation != null && !pose.getOrderedBones().isEmpty()) {
            ModelBoneView root = pose.getOrderedBones().getFirst();
            Vector3f init = root.getInitialRotation();
            root.setRotation(
                    init.x + this.vehicleRotation.x,
                    init.y + this.vehicleRotation.y,
                    init.z + this.vehicleRotation.z);
        }
    }

    @Override
    public void reset() {
        super.reset();
        this.vehicleRotation = null;
    }
}
