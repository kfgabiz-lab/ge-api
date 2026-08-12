package com.ge.bo.common.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * page_data.data_json 안의 리치텍스트(에디터) 값에 대한 저장형 XSS 방지 처리.
 * R1: 값 기반 게이트로 HTML로 보이는 String만 선별
 * R2: jsoup Safelist로 위험 태그/속성 제거 + style 값/iframe 호스트/URL 프로토콜 후처리
 */
@Component
public class RichTextSanitizer {

    private static final String[] ALLOWED_TAGS = {
        "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li",
        "blockquote", "pre", "hr", "br", "strong", "b", "em", "i", "s", "u", "code",
        "span", "mark", "sub", "sup", "a", "img", "table", "thead", "tbody", "tr", "th", "td",
        "colgroup", "col", "iframe"
    };

    private static final Set<String> ALLOWED_STYLE_PROPS = Set.of(
        "text-align", "color", "background-color", "min-width", "width");

    private static final Set<String> ALLOWED_IFRAME_HOSTS = Set.of(
        "www.youtube.com", "youtube.com", "www.youtube-nocookie.com", "youtu.be");

    private static final Pattern DATA_IMAGE_PATTERN = Pattern.compile(
        "^data:image/(png|jpeg|jpg|gif|webp);base64,", Pattern.CASE_INSENSITIVE);

    private static final Safelist SAFELIST = buildSafelist();

    private static Safelist buildSafelist() {
        Safelist safelist = new Safelist();
        safelist.addTags(ALLOWED_TAGS);
        for (String tag : ALLOWED_TAGS) {
            safelist.addAttributes(tag, "style", "class");
        }
        safelist.addAttributes("a", "href", "target", "rel");
        safelist.addAttributes("img", "src", "alt", "title", "width", "height", "loading", "decoding");
        safelist.addAttributes("th", "colspan", "rowspan", "scope", "colwidth");
        safelist.addAttributes("td", "colspan", "rowspan", "scope", "colwidth");
        safelist.addAttributes("table", "width", "colwidth");
        safelist.addAttributes("col", "width", "colwidth");
        safelist.addAttributes("div", "data-youtube-video");
        safelist.addAttributes("iframe",
            "src", "width", "height", "allowfullscreen", "frameborder", "allow",
            "start", "autoplay", "disablekbcontrols", "enableiframeapi", "endtime",
            "ivloadpolicy", "loop", "modestbranding", "origin", "playlist", "rel");
        return safelist;
    }

    public Map<String, Object> sanitizeDataJson(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(key, sanitizeValue(value)));
        return result;
    }

    @SuppressWarnings("unchecked")
    public Object sanitizeValue(Object value) {
        if (value instanceof Map) {
            return sanitizeDataJson((Map<String, Object>) value);
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(sanitizeValue(item));
            }
            return result;
        }
        if (!(value instanceof String)) {
            return value;
        }
        String str = (String) value;
        if (!shouldSanitize(str)) {
            return str;
        }
        return cleanHtml(str);
    }

    private boolean shouldSanitize(String value) {
        return value.indexOf('<') >= 0;
    }

    private String cleanHtml(String html) {
        Document dirty = Jsoup.parseBodyFragment(html);
        Cleaner cleaner = new Cleaner(SAFELIST);
        Document clean = cleaner.clean(dirty);
        clean.outputSettings().prettyPrint(false).escapeMode(Entities.EscapeMode.base);

        sanitizeStyleAttributes(clean);
        validateIframeHosts(clean);
        validateUrlProtocols(clean);

        return clean.body().html();
    }

    private void sanitizeStyleAttributes(Document doc) {
        for (Element el : doc.getAllElements()) {
            if (!el.hasAttr("style")) {
                continue;
            }
            String filtered = filterStyleValue(el.attr("style"));
            if (filtered.isBlank()) {
                el.removeAttr("style");
            } else {
                el.attr("style", filtered);
            }
        }
    }

    private String filterStyleValue(String styleValue) {
        StringBuilder result = new StringBuilder();
        for (String decl : styleValue.split(";")) {
            String trimmed = decl.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colonIdx = trimmed.indexOf(':');
            if (colonIdx < 0) {
                continue;
            }
            String prop = trimmed.substring(0, colonIdx).trim().toLowerCase();
            String value = trimmed.substring(colonIdx + 1).trim();
            if (!ALLOWED_STYLE_PROPS.contains(prop)) {
                continue;
            }
            String lowerValue = value.toLowerCase();
            if (lowerValue.contains("expression(") || lowerValue.contains("javascript:") || lowerValue.contains("url(")) {
                continue;
            }
            if (result.length() > 0) {
                result.append("; ");
            }
            result.append(prop).append(": ").append(value);
        }
        return result.toString();
    }

    private void validateIframeHosts(Document doc) {
        for (Element iframe : new ArrayList<>(doc.select("iframe"))) {
            if (!VideoUrlHostValidator.isAllowedHost(iframe.attr("src"), ALLOWED_IFRAME_HOSTS)) {
                iframe.remove();
            }
        }
    }

    private void validateUrlProtocols(Document doc) {
        for (Element a : doc.select("a[href]")) {
            if (!isAllowedHrefValue(a.attr("href"))) {
                a.removeAttr("href");
            }
        }
        for (Element img : doc.select("img[src]")) {
            if (!isAllowedImgSrcValue(img.attr("src"))) {
                img.removeAttr("src");
            }
        }
    }

    private boolean isAllowedHrefValue(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return false;
        }
        String lower = v.toLowerCase();
        return v.startsWith("/") || v.startsWith("#")
            || lower.startsWith("http:") || lower.startsWith("https:")
            || lower.startsWith("mailto:") || lower.startsWith("tel:");
    }

    private boolean isAllowedImgSrcValue(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return false;
        }
        if (isAllowedHrefValue(v)) {
            return true;
        }
        return DATA_IMAGE_PATTERN.matcher(v).find();
    }
}
