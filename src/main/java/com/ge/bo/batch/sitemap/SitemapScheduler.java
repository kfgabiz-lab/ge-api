package com.ge.bo.batch.sitemap;

import com.ge.bo.service.SitemapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * FO sitemap.xml 일배치 재생성 — 매일 03:30.
 *
 * <p>읽기 전용 쿼리만 수행하고 결과를 각 인스턴스의 인메모리 스냅샷에 담으므로,
 * 멀티 인스턴스여도 advisory lock 없이 각자 생성한다(EmailAutoResendScheduler와 달리 write 없음).</p>
 */
@Slf4j
@Component
@Profile("dev | prod")
@RequiredArgsConstructor
public class SitemapScheduler {

    private final SitemapService sitemapService;

    @Scheduled(cron = "0 30 3 * * *")
    public void regenerate() {
        try {
            SitemapService.Snapshot snap = sitemapService.rebuild();
            log.info("sitemap 일배치 재생성 — URL {}건", snap.urlCount());
        } catch (RuntimeException e) {
            log.error("sitemap 일배치 재생성 실패", e);
        }
    }
}
