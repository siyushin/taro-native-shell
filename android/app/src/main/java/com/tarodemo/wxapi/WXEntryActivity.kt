package com.tarodemo.wxapi

import android.app.Activity
import android.os.Bundle
import com.theweflex.react.WeChatModule

// react-native-wechat-lib support：微信登录/分享回调入口（包名必须为 wxapi，微信 SDK 约定）
class WXEntryActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WeChatModule.handleIntent(intent)
    finish()
  }
}
