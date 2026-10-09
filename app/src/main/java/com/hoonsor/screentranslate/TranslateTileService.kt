package com.hoonsor.screentranslate

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** 快速設定面板的開關：顯示／隱藏浮動小圓點 */
class TranslateTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val svc = TranslatorAccessibilityService.instance
        if (svc == null) {
            // 無障礙服務尚未啟用：開啟 App 設定頁引導
            openSettings()
            return
        }
        svc.setBubbleEnabled(!svc.isBubbleShown)
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val svc = TranslatorAccessibilityService.instance
        when {
            svc == null -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = "需啟用服務"
            }
            svc.isBubbleShown -> {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = "浮動按鈕開啟"
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = "浮動按鈕關閉"
            }
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openSettings() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
