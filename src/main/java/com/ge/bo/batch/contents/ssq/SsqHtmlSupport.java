package com.ge.bo.batch.contents.ssq;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * SSQ version_desc(HTML) 안전 처리 — jsoup 기반 실제 파서를 사용(정규식만으로 처리하지 않음).
 * - sanitize(): 매뉴얼/소프트웨어 설명용 — 위험 태그(script 등)와 인라인 스타일을 제거하되 h1~h6/hr/표 같은
 *   구조 태그는 보존한다(실데이터에 <h1>Content</h1>, <hr/> 구조가 실제로 쓰이고 있어 Safelist.basic()은 과함).
 * - extractVideoUrl(): 영상 타입 전용 — data-oembed-url 속성값을 추출하고 허용 도메인(youtube.com/youtu.be)만 통과시킨다.
 */
@Component
public class SsqHtmlSupport {

    private static final Set<String> ALLOWED_VIDEO_HOSTS = Set.of("youtube.com", "www.youtube.com", "youtu.be");

    public String sanitize(String html) {
        if (html == null) {
            return null;
        }
        return Jsoup.clean(html, Safelist.relaxed());
    }

    public Optional<String> extractVideoUrl(String html) {
        if (html == null) {
            return Optional.empty();
        }
        Document doc = Jsoup.parseBodyFragment(html);
        Element el = doc.select("[data-oembed-url]").first();
        if (el == null) {
            return Optional.empty();
        }
        String url = el.attr("data-oembed-url").trim();
        return isAllowedVideoUrl(url) ? Optional.of(url) : Optional.empty();
    }

    public boolean isAllowedVideoUrl(String url) {
        return com.ge.bo.common.html.VideoUrlHostValidator.isAllowedHost(url, ALLOWED_VIDEO_HOSTS);
    }
}
