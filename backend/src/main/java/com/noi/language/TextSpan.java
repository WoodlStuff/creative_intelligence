package com.noi.language;

import com.google.gson.JsonObject;
import com.noi.tools.JsonTools;

public class TextSpan {
    private final String content;
    private final long beginOffset;
//            "content":string,
//                "beginOffset":integer

    private TextSpan(String content, long beginOffset) {
        this.content = content;
        this.beginOffset = beginOffset;
    }

    public static TextSpan parse(JsonObject jsonObject, String jsonLabel) {
        JsonObject text = jsonObject.getAsJsonObject(jsonLabel);
        if (text != null && !text.isJsonNull()) {
            String content = JsonTools.getAsString(text, "content");
            long beginOffset = JsonTools.getAsLong(text, "beginOffset");
            return new TextSpan(content, beginOffset);
        }
        return null;
    }

    public String getContent() {
        return content;
    }

    public long getBeginOffset() {
        return beginOffset;
    }

    @Override
    public String toString() {
        return "TextSpan{" +
                "content='" + content + '\'' +
                ", beginOffset=" + beginOffset +
                '}';
    }
}
