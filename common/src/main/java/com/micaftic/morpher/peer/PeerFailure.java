package com.micaftic.morpher.peer;

import java.io.*;
import java.net.*;
import javax.net.ssl.SSLException;

/** Fixed diagnostic fields only: never send/log invitations, tickets, paths or exception messages. */
public final class PeerFailure extends IOException {
    public enum Stage { LISTEN, DNS, CONNECT, TLS, AUTH, CONTROL, ASSET_AUTH, TRANSFER, VERIFY, IMPORT, APPLY, RENDER }
    private final Stage stage;
    private final PeerFrame.ErrorCode code;
    public PeerFailure(Stage stage, PeerFrame.ErrorCode code) {
        super(stage.name() + "/" + code.name()); this.stage = stage; this.code = code;
    }
    public Stage stage() { return stage; }
    public PeerFrame.ErrorCode code() { return code; }
    public byte[] payload() { return new byte[] {(byte)stage.ordinal(), (byte)code.ordinal()}; }
    public static PeerFailure decode(byte[] bytes) throws IOException {
        if(bytes.length != 2 || Byte.toUnsignedInt(bytes[0]) >= Stage.values().length
                || Byte.toUnsignedInt(bytes[1]) >= PeerFrame.ErrorCode.values().length)
            throw new IOException("Invalid Peer error metadata");
        return new PeerFailure(Stage.values()[Byte.toUnsignedInt(bytes[0])], PeerFrame.ErrorCode.values()[Byte.toUnsignedInt(bytes[1])]);
    }
    public static PeerFailure classify(Stage stage, Throwable failure) {
        for(Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if(cause instanceof PeerFailure known) return known;
            String message = cause.getMessage();
            if("Peer room pin mismatch".equals(message)) return new PeerFailure(Stage.TLS, PeerFrame.ErrorCode.PIN_MISMATCH);
            if(cause instanceof UnknownHostException) return new PeerFailure(Stage.DNS, PeerFrame.ErrorCode.ENDPOINT_UNREACHABLE);
            if(cause instanceof BindException) return new PeerFailure(Stage.LISTEN, PeerFrame.ErrorCode.BIND_FAILED);
            if(cause instanceof SSLException) stage = Stage.TLS;
        }
        PeerFrame.ErrorCode code = switch(stage) {
            case LISTEN -> PeerFrame.ErrorCode.BIND_FAILED;
            case DNS, CONNECT -> PeerFrame.ErrorCode.ENDPOINT_UNREACHABLE;
            case TLS -> PeerFrame.ErrorCode.TLS_FAILED;
            case AUTH, ASSET_AUTH -> PeerFrame.ErrorCode.AUTH_FAILED;
            case VERIFY -> PeerFrame.ErrorCode.INVALID_MANIFEST;
            case IMPORT, APPLY, RENDER -> PeerFrame.ErrorCode.IMPORT_FAILED;
            default -> PeerFrame.ErrorCode.NETWORK_IO;
        };
        return new PeerFailure(stage, code);
    }
}
