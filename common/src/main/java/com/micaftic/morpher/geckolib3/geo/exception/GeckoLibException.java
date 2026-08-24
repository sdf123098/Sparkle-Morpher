package com.micaftic.morpher.geckolib3.geo.exception;

import net.minecraft.resources.Identifier;

import java.io.Serial;

public class GeckoLibException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1;

    public GeckoLibException(Identifier fileLocation, String message) {
        super(fileLocation + ": " + message);
    }

    public GeckoLibException(Identifier fileLocation, String message, Throwable cause) {
        super(fileLocation + ": " + message, cause);
    }
}