package com.micaftic.morpher.model.validation;

/**
 * 上传策略校验（R8 遗留②：validation 从 ServerModelManager.beginModelUpload 抽出）——
 * 纯判定链，返回拒绝原因；配置/能力/目录等输入由调用方（服务端）提供。
 *
 * <p>拒绝码与消息沿用旧实现（与客户端 ModelUploadSession.getRequestErrorText 对应）：
 * 6 disabled / 3 no permission / 5 invalid input / 2 exceeds limit / 1 already exists。
 */
public final class UploadPolicy {

    public enum RejectReason {
        NONE,
        DISABLED,
        NO_PERMISSION,
        INVALID_INPUT,
        EXCEEDS_LIMIT,
        ALREADY_EXISTS
    }

    private UploadPolicy() {
    }

    /**
     * 校验上传请求（顺序即优先级：先开关/连接，再输入合法性，最后重复检查）。
     *
     * @param uploadAllowed  服务器是否允许上传（ServerConfig.ALLOW_MODEL_UPLOAD）
     * @param playerConnected 发送者是否为已连接玩家
     * @param modelId        归一化后的模型 id（可 null）
     * @param importKindKnown 文件扩展名是否属于可导入类型
     * @param sha256         客户端上报的模型哈希（须为 64 位 hex）
     * @param totalBytes     上传总字节数
     * @param maxBytes       服务器单文件上限
     * @param modelExists    模型 id 是否已存在于目录/进行中的上传
     */
    public static RejectReason validate(boolean uploadAllowed, boolean playerConnected,
                                        String modelId, boolean importKindKnown,
                                        String sha256, int totalBytes, int maxBytes,
                                        boolean modelExists) {
        if (!uploadAllowed) {
            return RejectReason.DISABLED;
        }
        if (!playerConnected) {
            return RejectReason.NO_PERMISSION;
        }
        if (modelId == null || !importKindKnown || sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) {
            return RejectReason.INVALID_INPUT;
        }
        if (totalBytes <= 0 || totalBytes > maxBytes) {
            return RejectReason.EXCEEDS_LIMIT;
        }
        if (modelExists) {
            return RejectReason.ALREADY_EXISTS;
        }
        return RejectReason.NONE;
    }

    /** 拒绝原因 → 上传起始响应状态码（0 = 通过）。 */
    public static byte statusCode(RejectReason reason) {
        return switch (reason) {
            case DISABLED -> (byte) 6;
            case NO_PERMISSION -> (byte) 3;
            case INVALID_INPUT -> (byte) 5;
            case EXCEEDS_LIMIT -> (byte) 2;
            case ALREADY_EXISTS -> (byte) 1;
            case NONE -> (byte) 0;
        };
    }

    /** 拒绝原因 → 响应消息（供服务端拼进 UploadStartResult）。 */
    public static String statusMessage(RejectReason reason) {
        return switch (reason) {
            case DISABLED -> "Model import disabled";
            case NO_PERMISSION -> "No import permission";
            case INVALID_INPUT -> "Invalid model id or hash";
            case EXCEEDS_LIMIT -> "File exceeds server limit";
            case ALREADY_EXISTS -> "Model ID already exists";
            case NONE -> "";
        };
    }
}
