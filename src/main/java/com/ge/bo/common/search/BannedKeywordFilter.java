package com.ge.bo.common.search;

import java.util.Set;

public final class BannedKeywordFilter {

    private static final Set<String> BANNED_WORDS = Set.of(
            // 영어 - 일반 욕설
            "fuck", "shit", "bitch", "asshole", "dick", "cock", "pussy", "cunt",
            "bastard", "whore", "slut", "motherfucker", "dumbass", "twat", "wanker", "prick", "bollocks",
            // 영어 - 혐오/비하 표현
            "nigger", "nigga", "chink", "spic", "kike", "gook", "wetback", "coon", "faggot", "tranny",
            // 한글 - 일반 욕설
            "시발", "씨발", "씨발놈", "개새끼", "병신", "좆같은", "지랄", "미친놈", "미친년", "존나", "걸레",
            // 한글 - 혐오/비하 표현
            "깜둥이", "짱깨", "쪽바리", "왜놈"
    );

    private BannedKeywordFilter() {
    }

    public static boolean containsBannedWord(String keywordNorm) {
        if (keywordNorm == null || keywordNorm.isEmpty()) {
            return false;
        }
        for (String banned : BANNED_WORDS) {
            if (keywordNorm.contains(banned)) {
                return true;
            }
        }
        return false;
    }
}
