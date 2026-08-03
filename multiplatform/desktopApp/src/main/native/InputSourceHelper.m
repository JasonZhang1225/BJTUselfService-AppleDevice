#import <AppKit/AppKit.h>
#include <dispatch/dispatch.h>
#include <pthread.h>

static NSTextInputContext *gRestrictedContext = nil;
static NSArray<NSString *> *gPreviousAllowedLocales = nil;

static int32_t setRestrictedOnMainThread(Boolean restricted) {
    if (restricted) {
        if (gRestrictedContext != nil) return 0;
        NSTextInputContext *context = NSTextInputContext.currentInputContext;
        if (context == nil) return -1;
        gRestrictedContext = context;
        gPreviousAllowedLocales = [context.allowedInputSourceLocales copy];
        [context discardMarkedText];
        context.allowedInputSourceLocales = @[NSAllRomanInputSourcesLocaleIdentifier];
        return 0;
    }

    if (gRestrictedContext == nil) return 0;
    [gRestrictedContext discardMarkedText];
    gRestrictedContext.allowedInputSourceLocales = gPreviousAllowedLocales;
    gPreviousAllowedLocales = nil;
    gRestrictedContext = nil;
    return 0;
}

__attribute__((visibility("default")))
int32_t bjtuSetCredentialInputSourceRestricted(int32_t restricted) {
    __block int32_t result = 0;
    if (pthread_main_np() != 0) {
        result = setRestrictedOnMainThread(restricted != 0);
    } else {
        dispatch_sync(dispatch_get_main_queue(), ^{
            result = setRestrictedOnMainThread(restricted != 0);
        });
    }
    return result;
}
