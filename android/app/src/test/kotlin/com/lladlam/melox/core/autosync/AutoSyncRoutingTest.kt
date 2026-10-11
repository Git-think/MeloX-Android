package com.lladlam.melox.core.autosync

import org.junit.Assert.*
import org.junit.Test

class AutoSyncRoutingTest {
    @Test fun japaneseCannotFallThroughToChineseCif() {
        assertNull(AutoSyncText.detectLanguage(listOf("君の知らない物語", "空を見上げている")))
    }

    @Test fun chineseWithEnglishLineKeepsCifAndRefusesThatMixedLine() {
        assertEquals("zh", AutoSyncText.detectLanguage(listOf("天空之中飘落的雨滴", "诉说着那些遥远的故事", "你好 hello")))
        assertFalse(ZhText.isAlignableForChinese("你好 hello"))
        assertFalse(ZhText.isAlignableForChinese("你好 OK"))
    }
}
