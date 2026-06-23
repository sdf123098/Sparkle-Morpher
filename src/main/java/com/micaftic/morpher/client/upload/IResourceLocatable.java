package com.micaftic.morpher.client.upload;

import net.minecraft.resources.Identifier;

import java.util.Optional;

public interface IResourceLocatable {
    Optional<Identifier> getResourceLocation();
}