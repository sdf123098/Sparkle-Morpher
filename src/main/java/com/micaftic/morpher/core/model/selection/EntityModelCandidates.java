package com.micaftic.morpher.core.model.selection;

import com.micaftic.morpher.core.model.ModelRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * R6.1 EntityModelCandidates — 候选模型集合。
 *
 * <p>审计文档 3.6/R6.1：一个实体在一次解析中可能面临多个来源的候选模型
 * （服务器强制 / 本地预览 / 云端 / 旧式服务器 / 默认），不能"谁先到谁用"。
 * 本类按优先级收纳候选，交由 {@link EntityModelResolver} 决策。</p>
 *
 * <p>不可变：通过 {@link #builder()} 构建，构建后只读。</p>
 */
public final class EntityModelCandidates {

    private final List<Candidate> candidates;

    private EntityModelCandidates(List<Candidate> candidates) {
        this.candidates = candidates;
    }

    /** 单个候选：模型引用 + 优先级。 */
    public record Candidate(ModelRef ref, ModelPriority priority) {
        public Candidate {
            if (ref == null) {
                throw new NullPointerException("ref");
            }
            if (priority == null) {
                throw new NullPointerException("priority");
            }
        }
    }

    public List<Candidate> candidates() {
        return candidates;
    }

    public boolean isEmpty() {
        return candidates.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 不可变构建器（按插入序保留同优先级顺序）。 */
    public static final class Builder {
        private final List<Candidate> list = new ArrayList<>(4);

        public Builder add(ModelRef ref, ModelPriority priority) {
            list.add(new Candidate(ref, priority));
            return this;
        }

        public EntityModelCandidates build() {
            return new EntityModelCandidates(Collections.unmodifiableList(new ArrayList<>(list)));
        }
    }
}