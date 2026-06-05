package com.smartexam.dto.auth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RoleOverviewResponse {

    private String role;
    private String title;
    private String description;
    private List<Map<String, Object>> cards = new ArrayList<>();
    private List<String> nextModules = new ArrayList<>();

    public RoleOverviewResponse() {
    }

    public RoleOverviewResponse(String role, String title, String description, List<Map<String, Object>> cards, List<String> nextModules) {
        this.role = role;
        this.title = title;
        this.description = description;
        this.cards = cards == null ? new ArrayList<>() : new ArrayList<>(cards);
        this.nextModules = nextModules == null ? new ArrayList<>() : new ArrayList<>(nextModules);
    }

    public static Map<String, Object> card(String label, Object value, String remark) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("label", label);
        card.put("value", value);
        card.put("remark", remark);
        return card;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Map<String, Object>> getCards() {
        return cards;
    }

    public void setCards(List<Map<String, Object>> cards) {
        this.cards = cards == null ? new ArrayList<>() : new ArrayList<>(cards);
    }

    public List<String> getNextModules() {
        return nextModules;
    }

    public void setNextModules(List<String> nextModules) {
        this.nextModules = nextModules == null ? new ArrayList<>() : new ArrayList<>(nextModules);
    }
}
