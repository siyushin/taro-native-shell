#import <RCTAppDelegate.h>
#import <Expo/Expo.h>
#import <UIKit/UIKit.h>
// react-native-wechat-lib support（微信 SDK 回调需要 AppDelegate 实现 WXApiDelegate）
#import "WXApi.h"

@interface AppDelegate : EXAppDelegateWrapper <WXApiDelegate>

@end
