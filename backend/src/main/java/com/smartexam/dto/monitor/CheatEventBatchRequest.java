package com.smartexam.dto.monitor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class CheatEventBatchRequest {

    @Valid
    @NotEmpty(message = "Monitor events cannot be empty")
    @Size(max = 100, message = "At most 100 monitor events can be reported at once")
    private List<@Valid CheatEventRequest> events;

    public List<CheatEventRequest> getEvents() {
        return events;
    }

    public void setEvents(List<CheatEventRequest> events) {
        this.events = events;
    }
}
