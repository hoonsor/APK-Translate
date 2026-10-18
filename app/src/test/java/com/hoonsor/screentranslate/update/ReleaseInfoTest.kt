package com.hoonsor.screentranslate.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseInfoTest {

    private fun json(
        tag: String = "v0.11.0",
        prerelease: Boolean = false,
        body: String = "\"- 新增 App 內更新\"",
        assets: String = """[
            {"name": "other.txt", "size": 10, "browser_download_url": "https://example.com/other.txt"},
            {"name": "ScreenTranslate-v0.11.0.apk", "size": 12345, "browser_download_url": "https://github.com/hoonsor/APK-Translate/releases/download/v0.11.0/ScreenTranslate-v0.11.0.apk"}
        ]""",
    ) = """{
        "tag_name": "$tag", "draft": false, "prerelease": $prerelease, "body": $body,
        "html_url": "https://github.com/hoonsor/APK-Translate/releases/tag/$tag",
        "assets": $assets
    }"""

    @Test fun 解析正式版() {
        val r = ReleaseInfo.parse(json(), "ScreenTranslate-")!!
        assertEquals("v0.11.0", r.tag)
        assertEquals("0.11.0", r.version)
        assertEquals("- 新增 App 內更新", r.notes)
        assertEquals("ScreenTranslate-v0.11.0.apk", r.apkName)
        assertEquals(12345L, r.apkSize)
    }

    @Test fun 預先發行版不算() = assertNull(ReleaseInfo.parse(json(prerelease = true), "ScreenTranslate-"))

    @Test fun 沒有APK附件時回傳null() = assertNull(ReleaseInfo.parse(json(assets = "[]"), "ScreenTranslate-"))

    @Test fun 內文為null時視為空字串() = assertEquals("", ReleaseInfo.parse(json(body = "null"), "ScreenTranslate-")!!.notes)

    @Test fun 優先選本App命名的APK() {
        val assets = """[
            {"name": "VoiceHub-v1.apk", "size": 1, "browser_download_url": "https://example.com/VoiceHub-v1.apk"},
            {"name": "ScreenTranslate-v0.11.0.apk", "size": 2, "browser_download_url": "https://example.com/ScreenTranslate-v0.11.0.apk"}
        ]"""
        assertEquals("ScreenTranslate-v0.11.0.apk", ReleaseInfo.parse(json(assets = assets), "ScreenTranslate-")!!.apkName)
    }

    @Test fun 非HTTPS下載網址不接受() {
        val assets = """[{"name": "ScreenTranslate-v0.11.0.apk", "size": 2, "browser_download_url": "http://example.com/a.apk"}]"""
        assertNull(ReleaseInfo.parse(json(assets = assets), "ScreenTranslate-"))
    }
}
