package com.ysm.parser;

public class YSMParser {
    private static UnsatisfiedLinkError unavailable() {
        return new UnsatisfiedLinkError("YSM native parser is not available in the CurseForge build");
    }

    public static boolean parse(String ysmFilePath, String outputDir) { throw unavailable(); }
    public static boolean parseBytes(byte[] ysmData, String outputDir) { throw unavailable(); }
    public static int getVersion(String ysmFilePath) { throw unavailable(); }
}
