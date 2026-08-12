package com.ge.bo.common.html;

import java.net.URI;
import java.util.Set;

/**
 * URL이 https 스킴이며 호스트가 허용 목록에 포함되는지 판정하는 공통 로직.
 * SsqHtmlSupport(SSQ version_desc의 data-oembed-url 판정)와 RichTextSanitizer(iframe src 판정)가
 * 동일한 판정 로직을 공유하되, 각자 허용 호스트 목록은 별도로 관리한다.
 */
public final class VideoUrlHostValidator {

    private VideoUrlHostValidator() {
    }

    public static boolean isAllowedHost(String url, Set<String> allowedHosts) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                && allowedHosts.contains(host.toLowerCase());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
