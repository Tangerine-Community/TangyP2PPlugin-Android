package org.rti.tangerine.p2p;

import org.json.JSONObject;

public class Message extends JSONObject {

    private String messageType = "";
    private String message = "";
    private JSONObject object = null;

    public Message(String messageType, String message, JSONObject object) {
        super();
        this.messageType = messageType;
        this.message = message;
        this.object = object;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public JSONObject getObject() {
        return object;
    }

    public void setObject(JSONObject object) {
        this.object = object;
    }




}
