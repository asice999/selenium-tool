package com.example.seleniumtool.cookie;

public class CookieCloudCookie {

    private final String domain;
    private final String name;
    private final String value;
    private final String path;
    private final boolean secure;
    private final boolean httpOnly;

    /**
     * CookieCloud 返回的单条 Cookie 数据对象。
     */
    public CookieCloudCookie(String domain, String name, String value, String path, boolean secure, boolean httpOnly) {
        this.domain = domain;
        this.name = name;
        this.value = value;
        this.path = path;
        this.secure = secure;
        this.httpOnly = httpOnly;
    }

    public String getDomain() {
        return domain;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public String getPath() {
        return path;
    }

    public boolean isSecure() {
        return secure;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }
}
