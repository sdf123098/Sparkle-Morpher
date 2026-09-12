package com.micaftic.morpher.resource;

/**
 * 1.2.7 §24.2：从 {{@code YSMFolderDeserializer}} 内部 record 提升为包内顶层类型，
 * 使 {{@link YsmTextureParsing#parseImageMeta(byte[], String)}} 的返回值可被同包调用点直接引用。
 * 字段语义与拆分前完全一致。
 */
record ImageMeta(int width, int height, int format) {
}
