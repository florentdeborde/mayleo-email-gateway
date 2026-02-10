package com.florentdeborde.mayleo.dto.internal;

public class PostcardHtml {
    private final String html;
    private final Postcard postcard;

    public PostcardHtml(String html, Postcard postcard) {
        this.html = html;
        this.postcard = postcard;
    }

    public String getHtml() { return html; }
    public Postcard getPostcard() { return postcard; }
}
