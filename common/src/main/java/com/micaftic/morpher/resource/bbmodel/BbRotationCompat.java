package com.micaftic.morpher.resource.bbmodel;

/**
 * Blockbench 欧拉角(THREE 'XYZ' 序) 与 YSM 渲染端旋转语义 的互转。
 *
 * <p>背景：Blockbench 动画 keyframe 的 rotation 是 THREE.js Euler order 'XYZ'
 * （矩阵 Rx*Ry*Rz，列向量先 Rz 作用 = 先绕 Z 转）。而 mod/YSM 渲染端对旋转值
 * 做 RotationValue.convert（X/Y 取负转弧度、Z 取正）后按 JOML 后乘
 * rotateZ→rotateY→rotateX（矩阵 Rz*Ry*Rx，列向量先 Rx 作用 = 先绕 X 转）。
 * 对单轴旋转两者一致；对多轴组合旋转两者不同 → bbmodel 动画镜像。
 *
 * <p>本类把 Blockbench 原始角度转换为 YSM 渲染端语义下的等效存储角度：
 * 使「渲染端对转换后值求姿态」==「Blockbench 预览对原始值求姿态」。
 */
public final class BbRotationCompat {

    private BbRotationCompat() {
    }

    /**
     * 把 Blockbench 欧拉角(度, THREE 'XYZ' 序) 转换为 YSM 渲染端语义的等效角度(度)。
     *
     * <p>数学：令 A=rad(x), B=rad(y), C=rad(z)（Blockbench 弧度）。
     * Blockbench 矩阵 M = Rx(A)*Ry(B)*Rz(C)。
     * 渲染端存储角度 (sx,sy,sz) 的实际姿态为 Rz(rad(sz))*Ry(-rad(sy))*Rx(-rad(sx))。
     * 要求两者相等，即解 Rz(c)*Ry(b)*Rx(a) == M 的 ZYX 欧拉分解 (a,b,c)，
     * 其中 a=-rad(sx), b=-rad(sy), c=rad(sz)，故 sx=-deg(a), sy=-deg(b), sz=+deg(c)。
     */
    public static float[] convertBbEulerToYsm(float xDeg, float yDeg, float zDeg) {
        double aRad = Math.toRadians(xDeg);
        double bRad = Math.toRadians(yDeg);
        double cRad = Math.toRadians(zDeg);

        // M = Rx(a)*Ry(b)*Rz(c)
        double ca = Math.cos(aRad), sa = Math.sin(aRad);
        double cb = Math.cos(bRad), sb = Math.sin(bRad);
        double cc = Math.cos(cRad), sc = Math.sin(cRad);

        // Rx
        double[][] rx = {{1, 0, 0}, {0, ca, -sa}, {0, sa, ca}};
        // Ry
        double[][] ry = {{cb, 0, sb}, {0, 1, 0}, {-sb, 0, cb}};
        // Rz
        double[][] rz = {{cc, -sc, 0}, {sc, cc, 0}, {0, 0, 1}};

        double[][] m = mul3(mul3(rx, ry), rz);
        // m = Rz(c')*Ry(b')*Rx(a') 分解:
        // m[0][0]=cb'*cc', m[1][0]=cb'*sc', m[2][0]=-sb'
        // m[2][1]=sa'*cb', m[2][2]=ca'*cb'
        double syPrime = -m[2][0];
        double cyPrime = Math.sqrt(m[0][0] * m[0][0] + m[1][0] * m[1][0]);
        double bPrime = Math.atan2(syPrime, cyPrime);
        double cPrime = Math.atan2(m[1][0], m[0][0]);
        double aPrime = Math.atan2(m[2][1], m[2][2]);

        // 万向锁退化: cyPrime≈0 时 (b'≈±90°), c' 取 0, a' 从剩余元素推
        if (cyPrime < 1e-9) {
            aPrime = Math.atan2(-m[1][2], m[1][1]);
            cPrime = 0.0;
        }

        return new float[]{
                (float) -Math.toDegrees(aPrime),
                (float) -Math.toDegrees(bPrime),
                (float) Math.toDegrees(cPrime)
        };
    }

    private static double[][] mul3(double[][] a, double[][] b) {
        double[][] out = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                out[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
            }
        }
        return out;
    }

    /**
     * 判断三个分量是否都可转换（全部是数值；Molang 字符串无法静态转换，保持原样）。
     */
    public static boolean isNumeric(Object[] values) {
        if (values == null || values.length < 3) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            if (!(values[i] instanceof Number)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 若 post/pre 均为数值则原地转换 rotation keyframe 的角度值。
     */
    public static void convertRotationKeyframeValues(com.micaftic.morpher.resource.pojo.RawYsmModel.RawKeyframe kf) {
        if (kf == null) {
            return;
        }
        if (isNumeric(kf.postData)) {
            float[] conv = convertBbEulerToYsm(
                    ((Number) kf.postData[0]).floatValue(),
                    ((Number) kf.postData[1]).floatValue(),
                    ((Number) kf.postData[2]).floatValue());
            kf.postData = new Object[]{conv[0], conv[1], conv[2]};
        }
        if (kf.hasPreData && isNumeric(kf.preData)) {
            float[] conv = convertBbEulerToYsm(
                    ((Number) kf.preData[0]).floatValue(),
                    ((Number) kf.preData[1]).floatValue(),
                    ((Number) kf.preData[2]).floatValue());
            kf.preData = new Object[]{conv[0], conv[1], conv[2]};
        }
    }

    /**
     * 遍历整个动画文件，把所有 rotation 通道 keyframe 的数值角度转换为 YSM 渲染端语义。
     * 仅转换 rotation；position/scale 通道的语义与渲染端一致，不动。
     */
    public static void convertRawAnimationFile(com.micaftic.morpher.resource.pojo.RawYsmModel.RawAnimationFile file) {
        if (file == null || file.animations == null) {
            return;
        }
        for (com.micaftic.morpher.resource.pojo.RawYsmModel.RawAnimation anim : file.animations.values()) {
            if (anim == null || anim.boneAnimations == null) {
                continue;
            }
            for (com.micaftic.morpher.resource.pojo.RawYsmModel.RawBoneAnimation ba : anim.boneAnimations) {
                if (ba == null || ba.rotation == null) {
                    continue;
                }
                for (com.micaftic.morpher.resource.pojo.RawYsmModel.RawKeyframe kf : ba.rotation) {
                    convertRotationKeyframeValues(kf);
                }
            }
        }
    }
}
