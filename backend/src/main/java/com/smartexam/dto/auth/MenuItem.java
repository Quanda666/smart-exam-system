package com.smartexam.dto.auth;

import java.util.ArrayList;
import java.util.List;

public class MenuItem {

    private String title;
    private String path;
    private String icon;
    private List<String> roles = new ArrayList<>();
    private List<MenuItem> children = new ArrayList<>();

    public MenuItem() {
    }

    public MenuItem(String title, String path, String icon, List<String> roles) {
        this.title = title;
        this.path = path;
        this.icon = icon;
        this.roles = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
    }

    public List<MenuItem> getChildren() {
        return children;
    }

    public void setChildren(List<MenuItem> children) {
        this.children = children == null ? new ArrayList<>() : new ArrayList<>(children);
    }
}
