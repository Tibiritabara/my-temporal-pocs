package io.antmendoza.samples._20231205;

import java.util.Objects;

public class UIRequest {
    private String id;
    private String screenId;

    public UIRequest() {

    }

    public UIRequest(String screenId) {
        this.id = "" + Math.random();
        this.screenId = screenId;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UIRequest uiRequest = (UIRequest) o;
        return Objects.equals(id, uiRequest.id) && Objects.equals(screenId, uiRequest.screenId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, screenId);
    }

    public boolean isScreenId(String value) {
        if(value == null  && this.screenId == null){
            return true;
        }
        return this.screenId.equals(value);
    }
}
