#import <AppKit/AppKit.h>
#include <dispatch/dispatch.h>
#include <pthread.h>

typedef void (*BJTUCredentialCallback)(int32_t event, const char *value);

static const int32_t BJTUCredentialEventUsernameChanged = 1;
static const int32_t BJTUCredentialEventPasswordChanged = 2;
static const int32_t BJTUCredentialEventPasswordSubmit = 3;

static BOOL BJTUAppearanceIsDark(NSAppearance *appearance) {
    NSString *match = [appearance bestMatchFromAppearancesWithNames:@[
        NSAppearanceNameAqua,
        NSAppearanceNameDarkAqua,
    ]];
    return [match isEqualToString:NSAppearanceNameDarkAqua];
}

static NSColor *BJTUCredentialAccentColor(NSAppearance *appearance) {
    if (BJTUAppearanceIsDark(appearance)) {
        return [NSColor colorWithSRGBRed:169.0 / 255.0
                                   green:199.0 / 255.0
                                    blue:242.0 / 255.0
                                   alpha:1.0];
    }
    return [NSColor colorWithSRGBRed:56.0 / 255.0
                               green:88.0 / 255.0
                                blue:133.0 / 255.0
                               alpha:1.0];
}

static NSString *BJTUAsciiCredentialString(NSString *source) {
    NSMutableString *result = [NSMutableString stringWithCapacity:source.length];
    for (NSUInteger index = 0; index < source.length; index += 1) {
        unichar character = [source characterAtIndex:index];
        if (character >= 0x20 && character <= 0x7E) {
            [result appendFormat:@"%C", character];
        }
    }
    return result;
}

@interface BJTUCredentialHost : NSView <
    NSTextFieldDelegate
>
@property(nonatomic, strong) NSTextField *usernameField;
@property(nonatomic, strong) NSSecureTextField *passwordField;
@property(nonatomic, assign) BJTUCredentialCallback callback;
@property(nonatomic, assign) BOOL updating;
@property(nonatomic, weak) NSTextField *activeField;
@property(nonatomic, copy) NSString *lastUsernameValue;
@property(nonatomic, copy) NSString *lastPasswordValue;
- (instancetype)initWithCallback:(BJTUCredentialCallback)callback;
- (void)updateUsername:(NSString *)username password:(NSString *)password enabled:(BOOL)enabled;
@end

@implementation BJTUCredentialHost

- (instancetype)initWithCallback:(BJTUCredentialCallback)callback {
    self = [super initWithFrame:NSZeroRect];
    if (self == nil) return nil;

    _callback = callback;
    _lastUsernameValue = @"";
    _lastPasswordValue = @"";
    _usernameField = [[NSTextField alloc] initWithFrame:NSZeroRect];
    _passwordField = [[NSSecureTextField alloc] initWithFrame:NSZeroRect];
    [self configureField:_usernameField placeholder:@"学号"];
    [self configureField:_passwordField placeholder:@"密码"];
    _usernameField.contentType = NSTextContentTypeUsername;
    _passwordField.contentType = NSTextContentTypePassword;
    _usernameField.accessibilityLabel = @"学号";
    _passwordField.accessibilityLabel = @"密码";
    _usernameField.nextKeyView = _passwordField;
    _passwordField.nextKeyView = _usernameField;

    [self addSubview:_usernameField];
    [self addSubview:_passwordField];
    return self;
}

- (void)configureField:(NSTextField *)field placeholder:(NSString *)placeholder {
    field.delegate = self;
    field.placeholderString = placeholder;
    field.font = [NSFont systemFontOfSize:15.0 weight:NSFontWeightRegular];
    field.bezelStyle = NSTextFieldRoundedBezel;
    field.controlSize = NSControlSizeLarge;
    // 系统焦点环会跟随用户强调色（可能是黄色）；由应用主色绘制一致的细边框。
    field.focusRingType = NSFocusRingTypeNone;
    field.wantsLayer = YES;
    field.layer.cornerRadius = 14.0;
    field.layer.borderWidth = 0.0;
    field.maximumNumberOfLines = 1;
    field.usesSingleLineMode = YES;
    field.automaticTextCompletionEnabled = NO;
    field.allowsCharacterPickerTouchBarItem = NO;
    ((NSTextFieldCell *)field.cell).allowedInputSourceLocales =
        @[NSAllRomanInputSourcesLocaleIdentifier];
    if (@available(macOS 15.2, *)) {
        field.allowsWritingTools = NO;
    }
}

- (void)layout {
    [super layout];
    CGFloat spacing = 16.0;
    CGFloat availableHeight = MAX(0.0, self.bounds.size.height - spacing);
    CGFloat fieldHeight = MIN(56.0, availableHeight / 2.0);
    CGFloat fieldsHeight = fieldHeight * 2.0 + spacing;
    CGFloat bottomInset = MAX(0.0, (self.bounds.size.height - fieldsHeight) / 2.0);
    CGFloat width = self.bounds.size.width;
    _passwordField.frame = NSMakeRect(0.0, bottomInset, width, fieldHeight);
    _usernameField.frame = NSMakeRect(
        0.0,
        bottomInset + fieldHeight + spacing,
        width,
        fieldHeight
    );
}

- (void)updateUsername:(NSString *)username password:(NSString *)password enabled:(BOOL)enabled {
    self.updating = YES;
    if (![self.usernameField.stringValue isEqualToString:username]) {
        self.usernameField.stringValue = username;
    }
    if (![self.passwordField.stringValue isEqualToString:password]) {
        self.passwordField.stringValue = password;
    }
    if (self.usernameField.enabled != enabled) self.usernameField.enabled = enabled;
    if (self.passwordField.enabled != enabled) self.passwordField.enabled = enabled;
    self.lastUsernameValue = username;
    self.lastPasswordValue = password;
    self.updating = NO;
}

- (void)controlTextDidBeginEditing:(NSNotification *)notification {
    NSTextField *field = notification.object;
    self.activeField = field;
    NSColor *accent = BJTUCredentialAccentColor(self.effectiveAppearance);
    field.layer.borderColor = accent.CGColor;
    field.layer.borderWidth = 2.0;

    NSTextView *editor = (NSTextView *)field.currentEditor;
    if ([editor isKindOfClass:NSTextView.class]) {
        NSMutableDictionary<NSAttributedStringKey, id> *selection =
            [editor.selectedTextAttributes mutableCopy] ?: [NSMutableDictionary dictionary];
        selection[NSBackgroundColorAttributeName] = [accent colorWithAlphaComponent:0.28];
        selection[NSForegroundColorAttributeName] = NSColor.labelColor;
        editor.selectedTextAttributes = selection;
        editor.insertionPointColor = accent;
    }
}

- (void)controlTextDidEndEditing:(NSNotification *)notification {
    NSTextField *field = notification.object;
    field.layer.borderWidth = 0.0;
    if (self.activeField == field) self.activeField = nil;
}

- (void)viewDidChangeEffectiveAppearance {
    [super viewDidChangeEffectiveAppearance];
    if (self.activeField != nil) {
        self.activeField.layer.borderColor =
            BJTUCredentialAccentColor(self.effectiveAppearance).CGColor;
    }
}

- (void)controlTextDidChange:(NSNotification *)notification {
    if (self.updating) return;
    NSTextField *field = notification.object;
    NSString *previous = field == self.usernameField
        ? self.lastUsernameValue
        : self.lastPasswordValue;
    NSString *candidate = field.stringValue;
    // 某些密码提供器把整段凭据插到光标后；批量追加时只保留新插入的完整值。
    if (
        previous.length > 0 &&
        candidate.length > previous.length + 1 &&
        [candidate hasPrefix:previous]
    ) {
        candidate = [candidate substringFromIndex:previous.length];
    }
    NSString *sanitized = BJTUAsciiCredentialString(candidate);
    if (![field.stringValue isEqualToString:sanitized]) {
        self.updating = YES;
        field.stringValue = sanitized;
        self.updating = NO;
    }
    if (field == self.usernameField) {
        self.lastUsernameValue = sanitized;
    } else {
        self.lastPasswordValue = sanitized;
    }
    if (self.callback == NULL) return;
    int32_t event = field == self.usernameField
        ? BJTUCredentialEventUsernameChanged
        : BJTUCredentialEventPasswordChanged;
    self.callback(event, sanitized.UTF8String);
}

- (BOOL)control:(NSControl *)control
        textView:(NSTextView *)textView
doCommandBySelector:(SEL)commandSelector {
    (void)textView;
    if (
        control == self.usernameField &&
        (commandSelector == @selector(insertTab:) || commandSelector == @selector(insertNewline:))
    ) {
        [self.window makeFirstResponder:self.passwordField];
        return YES;
    }
    if (control == self.passwordField && commandSelector == @selector(insertNewline:)) {
        if (self.callback != NULL) {
            self.callback(BJTUCredentialEventPasswordSubmit, NULL);
        }
        return YES;
    }
    return NO;
}

@end

static NSView *BJTUContentViewForWindowHandle(uint64_t windowHandle) {
    if (windowHandle == 0) return nil;
    id nativeObject = (__bridge id)(void *)(uintptr_t)windowHandle;
    if ([nativeObject isKindOfClass:NSWindow.class]) {
        return ((NSWindow *)nativeObject).contentView;
    }
    if ([nativeObject isKindOfClass:NSView.class]) {
        NSView *view = (NSView *)nativeObject;
        return view.window.contentView ?: view;
    }
    return nil;
}

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

__attribute__((visibility("default")))
void *bjtuCreateCredentialFields(uint64_t windowHandle, BJTUCredentialCallback callback) {
    __block void *result = NULL;
    dispatch_block_t work = ^{
        NSView *contentView = BJTUContentViewForWindowHandle(windowHandle);
        if (contentView == nil) return;
        BJTUCredentialHost *host = [[BJTUCredentialHost alloc] initWithCallback:callback];
        host.hidden = YES;
        [contentView addSubview:host positioned:NSWindowAbove relativeTo:nil];
        result = (__bridge_retained void *)host;
    };
    if (pthread_main_np() != 0) work(); else dispatch_sync(dispatch_get_main_queue(), work);
    return result;
}

__attribute__((visibility("default")))
void bjtuUpdateCredentialFields(
    void *hostPointer,
    const char *username,
    const char *password,
    int32_t enabled
) {
    if (hostPointer == NULL) return;
    NSString *usernameValue = username == NULL ? @"" : [NSString stringWithUTF8String:username];
    NSString *passwordValue = password == NULL ? @"" : [NSString stringWithUTF8String:password];
    dispatch_async(dispatch_get_main_queue(), ^{
        BJTUCredentialHost *host = (__bridge BJTUCredentialHost *)hostPointer;
        [host updateUsername:usernameValue password:passwordValue enabled:enabled != 0];
    });
}

__attribute__((visibility("default")))
void bjtuSetCredentialFieldsFrame(
    void *hostPointer,
    double x,
    double y,
    double width,
    double height,
    double density
) {
    if (hostPointer == NULL || width <= 0.0 || height <= 0.0) return;
    dispatch_async(dispatch_get_main_queue(), ^{
        BJTUCredentialHost *host = (__bridge BJTUCredentialHost *)hostPointer;
        NSView *contentView = host.superview;
        if (contentView == nil) return;
        CGFloat scale = density > 0.0 ? density : 1.0;
        CGFloat pointX = x / scale;
        CGFloat pointY = y / scale;
        CGFloat pointWidth = width / scale;
        CGFloat pointHeight = height / scale;
        host.frame = NSMakeRect(
            pointX,
            contentView.bounds.size.height - pointY - pointHeight,
            pointWidth,
            pointHeight
        );
        host.hidden = NO;
        [host setNeedsLayout:YES];
    });
}

__attribute__((visibility("default")))
void bjtuDestroyCredentialFields(void *hostPointer) {
    if (hostPointer == NULL) return;
    dispatch_async(dispatch_get_main_queue(), ^{
        BJTUCredentialHost *host = (__bridge_transfer BJTUCredentialHost *)hostPointer;
        [host removeFromSuperview];
        (void)host;
    });
}
