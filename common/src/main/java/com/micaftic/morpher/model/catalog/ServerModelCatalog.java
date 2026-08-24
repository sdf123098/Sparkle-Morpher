package com.micaftic.morpher.model.catalog;

import com.micaftic.morpher.util.ModelIdUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * R8-3 ServerModelCatalog — 服务端模型目录状态（从 ServerModelManager 的
 * {@code CACHE_NAME_INFO} / {@code AUTH_MODELS} / {@code modelHashSet} 三个静态字段抽取）。
 *
 * <p>语义（与原实现一致）：</p>
 * <ul>
 *   <li>整表替换：native 加载完成后 {@link #replaceAll} 一次性替换 byName/auth/hashes
 *       （不是增量 update）；</li>
 *   <li>归一回退查询：{@link #lookupNormalized} 先按原名查，未命中再用
 *       {@link ModelIdUtil#normalizeImportModelId} 归一后查（大小写/空格/扩展名差异容错）；</li>
 *   <li>泛型 T 不依赖 ServerModelData，纯 Java 可测。</li>
 * </ul>
 */
public final class ServerModelCatalog<T> {

    private Map<String, T> byName = new HashMap<>();
    private Set<String> authModels = new HashSet<>();
    private IntOpenHashSet modelHashes = new IntOpenHashSet();

    /** 整表替换（native 加载完成）。 */
    public void replaceAll(Map<String, T> definitions, Set<String> auth, IntOpenHashSet hashes) {
        this.byName = new HashMap<>(definitions);
        this.authModels = new HashSet<>(auth);
        this.modelHashes = new IntOpenHashSet(hashes);
    }

    /** 只替换 byName + auth（modelHashes 保持原值；无服务器路径的加载完成分支语义）。 */
    public void replaceDefinitionsAndAuth(Map<String, T> definitions, Set<String> auth) {
        this.byName = new HashMap<>(definitions);
        this.authModels = new HashSet<>(auth);
    }

    /** 只替换 auth 集合（同步加载路径在 replaceAll 前先发布 auth）。 */
    public void replaceAuth(Set<String> auth) {
        this.authModels = new HashSet<>(auth);
    }

    /** 清空全部状态（服务器重置/重载）。 */
    public void clear() {
        byName.clear();
        authModels.clear();
        modelHashes.clear();
    }

    /** 按原名查询（未命中返回 null）。 */
    @Nullable
    public T lookup(String name) {
        return byName.get(name);
    }

    /** 原名查询 + 归一化回退（原 CACHE_NAME_INFO 双 get 语义）。 */
    @Nullable
    public T lookupNormalized(@Nullable String name) {
        T data = byName.get(name);
        if (data == null) {
            data = byName.get(ModelIdUtil.normalizeImportModelId(name));
        }
        return data;
    }

    public boolean contains(String name) {
        return byName.containsKey(name);
    }

    /** 全部条目（不可变视图）。 */
    public Map<String, T> all() {
        return Collections.unmodifiableMap(byName);
    }

    /** 授权模型 id 集合（原 AUTH_MODELS）。 */
    public Set<String> authModels() {
        return authModels;
    }

    /** 模型 hash id 集合（原 modelHashSet，用于动画 key 保留判定）。 */
    public IntOpenHashSet modelHashes() {
        return modelHashes;
    }

    public boolean isEmpty() {
        return byName.isEmpty();
    }

    public int size() {
        return byName.size();
    }
}
