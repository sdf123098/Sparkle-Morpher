package com.micaftic.morpher.resource.bbmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Blockbench 集合
 * 用于组织和管理模型元素
 */
public class BBCollection {
    public String uuid = "";
    public String name = "";
    public boolean isOpen = false;
    public String export_path = "";
    public boolean saved = false;
    public boolean locked = false;

    public List<String> children = new ArrayList<>();

    public BBCollection() {}

    public BBCollection(String uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }
}
