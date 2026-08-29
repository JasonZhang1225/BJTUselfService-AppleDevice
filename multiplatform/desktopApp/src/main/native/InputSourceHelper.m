#import <AppKit/AppKit.h>
#include <dispatch/dispatch.h>
#include <pthread.h>

typedef void (*BJTUCredentialCallback)(int32_t event, const char *value);
typedef void (*BJTUTrackpadPagerCallback)(int32_t direction);

static const int32_t BJTUCredentialEventUsernameChanged = 1;
static const int32_t BJTUCredentialEventPasswordChanged = 2;
static const int32_t BJTUCredentialEventPasswordSubmit = 3;

/**
 * 判断当前是否为深色外观。
 *
 * 注意：Compose Desktop (Skiko) 会把 NSApp.appearance 固定为 Aqua，让 Compose 自行绘制
 * 深色内容；此时 NSApp.effectiveAppearance 恒报浅色，不能作为依据（字段会误用浅色白底）。
 * 判断策略：NSApp 级外观未被固定（nil）时走标准 effectiveAppearance；被固定时改读系统
 * 外观偏好 AppleInterfaceStyle，与 Compose isSystemInDarkTheme() 保持一致。
 */
static BOOL BJTUAppIsDark(void) {
    NSString *systemStyle = [[NSUserDefaults standardUserDefaults] stringForKey:@"AppleInterfaceStyle"];
    if (NSApp.appearance != nil) {
        return [systemStyle isEqualToString:@"Dark"];
    }
    NSAppearance *appearance = NSApp.effectiveAppearance;
    if (appearance == nil) {
        appearance = [NSApp.mainWindow effectiveAppearance];
    }
    if (appearance == nil) {
        appearance = [NSAppearance currentDrawingAppearance];
    }
    NSString *match = [appearance bestMatchFromAppearancesWithNames:@[
        NSAppearanceNameAqua,
        NSAppearanceNameDarkAqua,
        NSAppearanceNameVibrantDark,
        NSAppearanceNameVibrantLight,
    ]];
    return [match isEqualToString:NSAppearanceNameDarkAqua] ||
        [match isEqualToString:NSAppearanceNameVibrantDark];
}

static NSColor *BJTUCredentialAccentColor(void) {
    if (BJTUAppIsDark()) {
        // DarkColors.primary
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

/** 深色用接近卡片 surface 的深灰，绝不用系统 textBackground 白底。 */
static NSColor *BJTUCredentialFieldFillColor(void) {
    if (BJTUAppIsDark()) {
        // 略亮于 DarkColors.surface(0x17191D)，接近 surfaceVariant 0x2A2D33
        return [NSColor colorWithSRGBRed:0x2A / 255.0
                                   green:0x2D / 255.0
                                    blue:0x33 / 255.0
                                   alpha:1.0];
    }
    // 浅色：surfaceVariant 灰，在 surface 卡片上可辨
    return [NSColor colorWithSRGBRed:0xE8 / 255.0
                               green:0xEA / 255.0
                                blue:0xF0 / 255.0
                               alpha:1.0];
}

static NSColor *BJTUCredentialFieldTextColor(void) {
    if (BJTUAppIsDark()) {
        return [NSColor colorWithSRGBRed:0.92 green:0.93 blue:0.95 alpha:1.0];
    }
    return [NSColor colorWithSRGBRed:0.10 green:0.11 blue:0.13 alpha:1.0];
}

static NSColor *BJTUCredentialFieldPlaceholderColor(void) {
    if (BJTUAppIsDark()) {
        return [NSColor colorWithSRGBRed:0.62 green:0.64 blue:0.68 alpha:1.0];
    }
    return [NSColor colorWithSRGBRed:0.45 green:0.47 blue:0.52 alpha:1.0];
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

/**
 * 视觉高度 56pt，但不要把 NSTextField 本体拉高：拉高后 cell 默认贴顶，
 * 而 field editor 又不总是跟自定义 drawingRect 对齐，会出现“占位符居中、输入贴顶”。
 * 做法：外层 shell 负责圆角底色与边框；内部用系统自然高度的文本框垂直居中。
 */
static const CGFloat BJTUCredentialShellHeight = 56.0;
static const CGFloat BJTUCredentialFieldTextHeight = 22.0;
static const CGFloat BJTUCredentialHorizontalInset = 14.0;

@interface BJTUCredentialShellView : NSView
@property(nonatomic, weak) NSTextField *field;
@end

@implementation BJTUCredentialShellView

- (BOOL)acceptsFirstResponder {
    return NO;
}

- (void)mouseDown:(NSEvent *)event {
    // 点到圆角空白区域时，把焦点交给内部文本框。
    if (self.field != nil && self.window != nil) {
        [self.window makeFirstResponder:self.field];
        return;
    }
    [super mouseDown:event];
}

@end

@interface BJTUCredentialHost : NSView <
    NSTextFieldDelegate
>
@property(nonatomic, strong) BJTUCredentialShellView *usernameShell;
@property(nonatomic, strong) BJTUCredentialShellView *passwordShell;
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
    // 宿主透明：底色画在 shell 上，文本框只负责文字。
    self.wantsLayer = YES;
    self.layer.backgroundColor = NSColor.clearColor.CGColor;

    _usernameShell = [[BJTUCredentialShellView alloc] initWithFrame:NSZeroRect];
    _passwordShell = [[BJTUCredentialShellView alloc] initWithFrame:NSZeroRect];
    _usernameField = [[NSTextField alloc] initWithFrame:NSZeroRect];
    _passwordField = [[NSSecureTextField alloc] initWithFrame:NSZeroRect];
    _usernameShell.field = _usernameField;
    _passwordShell.field = _passwordField;

    // 自定义宿主视图本身不承载可操作控件；显式把两个真实输入框
    // 暴露为无障碍子元素，避免 VoiceOver/Computer Use 只看到 Compose
    // 上方的占位文本而无法把焦点交给 NSTextField。
    self.accessibilityElement = NO;
    self.accessibilityChildren = @[_usernameField, _passwordField];
    self.accessibilityChildrenInNavigationOrder = @[_usernameField, _passwordField];
    _usernameShell.accessibilityElement = NO;
    _passwordShell.accessibilityElement = NO;

    [self configureShell:_usernameShell];
    [self configureShell:_passwordShell];
    [self configureField:_usernameField placeholder:@"学号"];
    [self configureField:_passwordField placeholder:@"密码"];
    _usernameField.contentType = NSTextContentTypeUsername;
    _passwordField.contentType = NSTextContentTypePassword;
    _usernameField.accessibilityLabel = @"学号";
    _passwordField.accessibilityLabel = @"密码";
    _usernameField.nextKeyView = _passwordField;
    _passwordField.nextKeyView = _usernameField;

    [_usernameShell addSubview:_usernameField];
    [_passwordShell addSubview:_passwordField];
    [self addSubview:_usernameShell];
    [self addSubview:_passwordShell];
    [self syncAppearanceFromApp];
    return self;
}

- (void)configureShell:(BJTUCredentialShellView *)shell {
    shell.wantsLayer = YES;
    shell.layer.cornerRadius = 14.0;
    shell.layer.masksToBounds = YES;
    shell.layer.borderWidth = 0.0;
    shell.layer.backgroundColor = BJTUCredentialFieldFillColor().CGColor;
}

- (void)configureField:(NSTextField *)field placeholder:(NSString *)placeholder {
    field.delegate = self;
    field.placeholderString = placeholder;
    // Compose 的宿主树看不到这个 AppKit overlay；明确声明原生字段为可访问元素，
    // 让 VoiceOver、键盘导航和 Computer Use 都能找到真实的编辑控件，而不是只看到
    // 外层 Compose 的占位文本。
    field.accessibilityElement = YES;
    field.accessibilityRole = NSAccessibilityTextFieldRole;
    field.accessibilityLabel = placeholder;
    field.accessibilityIdentifier = placeholder;
    field.font = [NSFont systemFontOfSize:15.0 weight:NSFontWeightRegular];
    // 文本框保持系统自然高度；视觉大圆角由 shell 提供。
    field.bordered = NO;
    field.bezeled = NO;
    field.drawsBackground = NO;
    field.backgroundColor = NSColor.clearColor;
    field.editable = YES;
    field.selectable = YES;
    field.enabled = YES;
    field.controlSize = NSControlSizeRegular;
    field.focusRingType = NSFocusRingTypeNone;
    field.wantsLayer = NO;
    field.maximumNumberOfLines = 1;
    field.usesSingleLineMode = YES;
    field.automaticTextCompletionEnabled = NO;
    field.allowsCharacterPickerTouchBarItem = NO;
    field.cell.font = field.font;
    field.cell.alignment = NSTextAlignmentLeft;
    field.cell.lineBreakMode = NSLineBreakByTruncatingTail;
    field.cell.editable = YES;
    field.cell.selectable = YES;
    field.cell.enabled = YES;
    ((NSTextFieldCell *)field.cell).drawsBackground = NO;
    ((NSTextFieldCell *)field.cell).backgroundColor = NSColor.clearColor;
    ((NSTextFieldCell *)field.cell).allowedInputSourceLocales =
        @[NSAllRomanInputSourcesLocaleIdentifier];
    if (@available(macOS 15.2, *)) {
        field.allowsWritingTools = NO;
    }
    [self applyFieldChrome:field];
}

/** 强制字段跟随真实系统外观（NSApp 级外观可能被 Compose 固定为 Aqua，不能直接沿用）。 */
- (void)syncAppearanceFromApp {
    BOOL dark = BJTUAppIsDark();
    NSAppearance *fieldAppearance =
        [NSAppearance appearanceNamed:dark ? NSAppearanceNameDarkAqua : NSAppearanceNameAqua];
    self.appearance = fieldAppearance;
    self.usernameShell.appearance = fieldAppearance;
    self.passwordShell.appearance = fieldAppearance;
    self.usernameField.appearance = fieldAppearance;
    self.passwordField.appearance = fieldAppearance;
    [self applyChromeToAllFields];
}

/** 按系统深浅色刷新 shell 底色 / 文字 / 占位符。 */
- (void)applyFieldChrome:(NSTextField *)field {
    NSColor *fill = BJTUCredentialFieldFillColor();
    NSColor *text = BJTUCredentialFieldTextColor();
    NSColor *placeholder = BJTUCredentialFieldPlaceholderColor();
    field.drawsBackground = NO;
    field.backgroundColor = NSColor.clearColor;
    field.textColor = text;
    ((NSTextFieldCell *)field.cell).drawsBackground = NO;
    ((NSTextFieldCell *)field.cell).backgroundColor = NSColor.clearColor;
    ((NSTextFieldCell *)field.cell).textColor = text;

    BJTUCredentialShellView *shell =
        (field == self.passwordField) ? self.passwordShell : self.usernameShell;
    if (shell.layer != nil) {
        shell.layer.backgroundColor = fill.CGColor;
        shell.layer.cornerRadius = 14.0;
        shell.layer.masksToBounds = YES;
    }

    NSString *placeholderText = field.placeholderString ?: @"";
    if (placeholderText.length == 0 && field.placeholderAttributedString != nil) {
        placeholderText = field.placeholderAttributedString.string;
    }
    if (placeholderText.length == 0) {
        placeholderText = (field == self.passwordField) ? @"密码" : @"学号";
        field.placeholderString = placeholderText;
    }
    field.placeholderAttributedString = [[NSAttributedString alloc]
        initWithString:placeholderText
        attributes:@{
            NSForegroundColorAttributeName: placeholder,
            NSFontAttributeName: field.font ?: [NSFont systemFontOfSize:15.0],
        }];
}

- (void)applyChromeToAllFields {
    [self applyFieldChrome:self.usernameField];
    [self applyFieldChrome:self.passwordField];
    if (self.activeField != nil) {
        NSColor *accent = BJTUCredentialAccentColor();
        BJTUCredentialShellView *shell =
            (self.activeField == self.passwordField) ? self.passwordShell : self.usernameShell;
        shell.layer.borderColor = accent.CGColor;
        shell.layer.borderWidth = 2.0;
    }
}

- (void)layout {
    [super layout];
    CGFloat spacing = 16.0;
    CGFloat availableHeight = MAX(0.0, self.bounds.size.height - spacing);
    CGFloat shellHeight = MIN(BJTUCredentialShellHeight, availableHeight / 2.0);
    CGFloat shellsHeight = shellHeight * 2.0 + spacing;
    CGFloat bottomInset = MAX(0.0, (self.bounds.size.height - shellsHeight) / 2.0);
    CGFloat width = self.bounds.size.width;
    self.passwordShell.frame = NSMakeRect(0.0, bottomInset, width, shellHeight);
    self.usernameShell.frame = NSMakeRect(
        0.0,
        bottomInset + shellHeight + spacing,
        width,
        shellHeight
    );
    [self layoutField:self.usernameField inShell:self.usernameShell];
    [self layoutField:self.passwordField inShell:self.passwordShell];
}

- (void)layoutField:(NSTextField *)field inShell:(BJTUCredentialShellView *)shell {
    CGFloat shellHeight = NSHeight(shell.bounds);
    CGFloat fieldHeight = MIN(BJTUCredentialFieldTextHeight, shellHeight);
    CGFloat y = MAX(0.0, (shellHeight - fieldHeight) / 2.0);
    CGFloat width = MAX(0.0, NSWidth(shell.bounds) - BJTUCredentialHorizontalInset * 2.0);
    field.frame = NSMakeRect(
        BJTUCredentialHorizontalInset,
        y,
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

- (BJTUCredentialShellView *)shellForField:(NSTextField *)field {
    if (field == self.passwordField) return self.passwordShell;
    if (field == self.usernameField) return self.usernameShell;
    return nil;
}

- (void)controlTextDidBeginEditing:(NSNotification *)notification {
    NSTextField *field = notification.object;
    self.activeField = field;
    [self syncAppearanceFromApp];
    [self applyFieldChrome:field];
    NSColor *accent = BJTUCredentialAccentColor();
    BJTUCredentialShellView *shell = [self shellForField:field];
    shell.layer.borderColor = accent.CGColor;
    shell.layer.borderWidth = 2.0;

    NSTextView *editor = (NSTextView *)field.currentEditor;
    if ([editor isKindOfClass:NSTextView.class]) {
        NSMutableDictionary<NSAttributedStringKey, id> *selection =
            [editor.selectedTextAttributes mutableCopy] ?: [NSMutableDictionary dictionary];
        selection[NSBackgroundColorAttributeName] = [accent colorWithAlphaComponent:0.28];
        selection[NSForegroundColorAttributeName] = BJTUCredentialFieldTextColor();
        editor.selectedTextAttributes = selection;
        editor.insertionPointColor = accent;
        editor.drawsBackground = NO;
        editor.backgroundColor = NSColor.clearColor;
        editor.textColor = BJTUCredentialFieldTextColor();
        // 编辑器夹层也可能是白底，强制透明让 shell 填色露出来。
        if (editor.enclosingScrollView != nil) {
            editor.enclosingScrollView.drawsBackground = NO;
            editor.enclosingScrollView.backgroundColor = NSColor.clearColor;
        }
    }
}

- (void)controlTextDidEndEditing:(NSNotification *)notification {
    NSTextField *field = notification.object;
    BJTUCredentialShellView *shell = [self shellForField:field];
    shell.layer.borderWidth = 0.0;
    if (self.activeField == field) self.activeField = nil;
    [self applyFieldChrome:field];
}

- (void)viewDidChangeEffectiveAppearance {
    [super viewDidChangeEffectiveAppearance];
    [self syncAppearanceFromApp];
}

- (void)viewDidMoveToWindow {
    [super viewDidMoveToWindow];
    [self syncAppearanceFromApp];
    if (self.window != nil) {
        // 系统外观切换时 App 的 effectiveAppearance 会变；监听后同步字段。
        [NSApp addObserver:self
                forKeyPath:@"effectiveAppearance"
                   options:NSKeyValueObservingOptionNew
                   context:NULL];
        // Compose 会把 NSApp.appearance 固定为 Aqua，effectiveAppearance 不再变化；
        // 系统深浅色切换只反映在 AppleInterfaceStyle 偏好里，需监听 defaults 变化。
        [[NSNotificationCenter defaultCenter] addObserver:self
                                                 selector:@selector(defaultsDidChange:)
                                                     name:NSUserDefaultsDidChangeNotification
                                                   object:nil];
    }
}

- (void)viewWillMoveToWindow:(NSWindow *)newWindow {
    if (self.window != nil && newWindow == nil) {
        @try {
            [NSApp removeObserver:self forKeyPath:@"effectiveAppearance"];
        } @catch (__unused NSException *exception) {
        }
        [[NSNotificationCenter defaultCenter]
            removeObserver:self
                      name:NSUserDefaultsDidChangeNotification
                    object:nil];
    }
    [super viewWillMoveToWindow:newWindow];
}

- (void)defaultsDidChange:(__unused NSNotification *)notification {
    [self syncAppearanceFromApp];
}

- (void)observeValueForKeyPath:(NSString *)keyPath
                      ofObject:(id)object
                        change:(NSDictionary<NSKeyValueChangeKey, id> *)change
                       context:(void *)context {
    if ([keyPath isEqualToString:@"effectiveAppearance"]) {
        [self syncAppearanceFromApp];
        return;
    }
    [super observeValueForKeyPath:keyPath ofObject:object change:change context:context];
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

/**
 * 原生触摸板分页器：只读取手指 phase，momentumPhase（松手后的惯性）完全忽略。
 * 因此一次物理手势最多回调一页；下一次 Began 立即恢复，不需要猜测冷却时间。
 */
@interface BJTUTrackpadPagerHost : NSObject
@property(nonatomic, weak) NSView *contentView;
@property(nonatomic, weak) NSWindow *window;
@property(nonatomic, assign) BJTUTrackpadPagerCallback callback;
@property(nonatomic, assign) NSRect targetFrame;
@property(nonatomic, strong) id eventMonitor;
@property(nonatomic, assign) BOOL gestureActive;
@property(nonatomic, assign) BOOL pageSent;
@property(nonatomic, assign) CGFloat accumulatedX;
@property(nonatomic, assign) NSTimeInterval lastEventTimestamp;
- (instancetype)initWithContentView:(NSView *)contentView callback:(BJTUTrackpadPagerCallback)callback;
- (void)updateFrameX:(double)x y:(double)y width:(double)width height:(double)height density:(double)density;
- (void)stop;
@end

@implementation BJTUTrackpadPagerHost

- (instancetype)initWithContentView:(NSView *)contentView callback:(BJTUTrackpadPagerCallback)callback {
    self = [super init];
    if (self == nil) return nil;
    _contentView = contentView;
    _window = contentView.window;
    _callback = callback;
    _targetFrame = NSZeroRect;
    _lastEventTimestamp = 0.0;
    __weak BJTUTrackpadPagerHost *weakSelf = self;
    _eventMonitor = [NSEvent addLocalMonitorForEventsMatchingMask:NSEventMaskScrollWheel
        handler:^NSEvent *(NSEvent *event) {
            BJTUTrackpadPagerHost *host = weakSelf;
            if (host == nil || event.window != host.window || host.contentView == nil) return event;
            NSPoint point = [host.contentView convertPoint:event.locationInWindow fromView:nil];
            if (!NSPointInRect(point, host.targetFrame)) return event;

            NSEventPhase momentum = event.momentumPhase;
            if (momentum != NSEventPhaseNone) return event;

            NSEventPhase phase = event.phase;
            BOOL began = (phase & (NSEventPhaseBegan | NSEventPhaseMayBegin)) != 0;
            BOOL ended = (phase & (NSEventPhaseEnded | NSEventPhaseCancelled)) != 0;
            // 少数驱动不给 phase；仅对此退化路径使用很短的事件间隔识别新手势。
            BOOL fallbackNewGesture = phase == NSEventPhaseNone &&
                host.lastEventTimestamp > 0.0 &&
                event.timestamp - host.lastEventTimestamp > 0.08;
            if (began || !host.gestureActive || fallbackNewGesture) {
                host.gestureActive = YES;
                host.pageSent = NO;
                host.accumulatedX = 0.0;
            }
            host.lastEventTimestamp = event.timestamp;
            if (ended) {
                host.gestureActive = NO;
                host.pageSent = NO;
                host.accumulatedX = 0.0;
                return event;
            }
            if (host.pageSent) return event;

            CGFloat deltaX = event.scrollingDeltaX;
            CGFloat deltaY = event.scrollingDeltaY;
            if (fabs(deltaX) <= fabs(deltaY) || deltaX == 0.0) return event;
            if (host.accumulatedX != 0.0 && (host.accumulatedX > 0.0) != (deltaX > 0.0)) {
                host.accumulatedX = 0.0;
            }
            host.accumulatedX += deltaX;
            if (fabs(host.accumulatedX) >= 28.0 && host.callback != NULL) {
                host.pageSent = YES;
                host.callback(host.accumulatedX > 0.0 ? 1 : -1);
            }
            return event;
        }];
    return self;
}

- (void)updateFrameX:(double)x y:(double)y width:(double)width height:(double)height density:(double)density {
    if (self.contentView == nil || width <= 0.0 || height <= 0.0) return;
    CGFloat scale = density > 0.0 ? density : 1.0;
    CGFloat pointX = x / scale;
    CGFloat pointY = y / scale;
    CGFloat pointWidth = width / scale;
    CGFloat pointHeight = height / scale;
    self.targetFrame = NSMakeRect(
        pointX,
        self.contentView.bounds.size.height - pointY - pointHeight,
        pointWidth,
        pointHeight
    );
}

- (void)stop {
    if (self.eventMonitor != nil) {
        [NSEvent removeMonitor:self.eventMonitor];
        self.eventMonitor = nil;
    }
}

- (void)dealloc {
    [self stop];
}

@end

__attribute__((visibility("default")))
void *bjtuCreateTrackpadPager(uint64_t windowHandle, BJTUTrackpadPagerCallback callback) {
    __block void *result = NULL;
    dispatch_block_t work = ^{
        NSView *contentView = BJTUContentViewForWindowHandle(windowHandle);
        if (contentView == nil || callback == NULL) return;
        BJTUTrackpadPagerHost *host = [[BJTUTrackpadPagerHost alloc]
            initWithContentView:contentView
            callback:callback];
        result = (__bridge_retained void *)host;
    };
    if (pthread_main_np() != 0) work(); else dispatch_sync(dispatch_get_main_queue(), work);
    return result;
}

__attribute__((visibility("default")))
void bjtuSetTrackpadPagerFrame(
    void *hostPointer,
    double x,
    double y,
    double width,
    double height,
    double density
) {
    if (hostPointer == NULL) return;
    dispatch_async(dispatch_get_main_queue(), ^{
        BJTUTrackpadPagerHost *host = (__bridge BJTUTrackpadPagerHost *)hostPointer;
        [host updateFrameX:x y:y width:width height:height density:density];
    });
}

__attribute__((visibility("default")))
void bjtuDestroyTrackpadPager(void *hostPointer) {
    if (hostPointer == NULL) return;
    dispatch_async(dispatch_get_main_queue(), ^{
        BJTUTrackpadPagerHost *host = (__bridge_transfer BJTUTrackpadPagerHost *)hostPointer;
        [host stop];
        (void)host;
    });
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
