package team.bjtuss.bjtuselfservice.shared.feature.mailbox

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.shared.domain.mailbox.MailAttachment
import team.bjtuss.bjtuselfservice.shared.domain.mailbox.MailMessage
import team.bjtuss.bjtuselfservice.shared.domain.mailbox.MailSummary
import team.bjtuss.bjtuselfservice.shared.feature.scroll.desktopTouchScroll
import team.bjtuss.bjtuselfservice.shared.util.schoolRichTextToPlainMultiline

private val mailboxSplitMinWidth = 980.dp

/**
 * 邮箱只读前端：宽屏为文件夹/列表/阅读三栏，紧凑屏为列表 -> 阅读二级页。
 *
 * 视觉上把“结构”和“内容”分开：文件夹是低对比度侧栏，邮件列表使用分隔行，
 * 阅读区才使用较大的留白和内容卡片。这样不会让每封邮件都像一个独立浮层。
 */
@Composable
fun MailboxWorkspace(
    model: MailboxScreenModel,
    expanded: Boolean,
    nativeDetail: Boolean = false,
    onOpenNativeDetail: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    if (nativeDetail) {
        // 原生详情页退出后，主邮箱页应回到列表而不是保留上一封选中邮件。
        DisposableEffect(model) {
            onDispose { model.clearSelectedMessage() }
        }
    }

    LaunchedEffect(model) {
        if (model.state.value == MailboxUiState.Idle) {
            // 先让一级页转场完成，再建立邮箱会话，避免页面初次进入时出现跳动。
            delay(450)
        }
        model.initialize()
    }

    when (val current = state) {
        MailboxUiState.Idle,
        MailboxUiState.Preparing,
        -> MailboxLoadingState(modifier)

        MailboxUiState.SessionUnavailable -> MailboxSessionUnavailable(
            onRetry = { scope.launch { model.refresh() } },
            modifier = modifier,
        )

        is MailboxUiState.Ready -> MailboxReadyWorkspace(
            state = current,
            expanded = expanded,
            nativeDetail = nativeDetail,
            onOpenNativeDetail = onOpenNativeDetail,
            onRefresh = { scope.launch { model.refresh() } },
            onLoadMore = { scope.launch { model.loadMore() } },
            onSelectFolder = { folderId -> scope.launch { model.selectFolder(folderId) } },
            onOpenMessage = { message -> scope.launch { model.openMessage(message) } },
            onBackFromMessage = model::clearSelectedMessage,
            onOpenWeb = { uriHandler.openUri(current.request.url) },
            modifier = modifier,
        )
    }
}

@Composable
private fun MailboxLoadingState(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MailboxSessionUnavailable(
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MailboxEnvelopeMark(
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(46.dp),
        )
        Text(
            "当前登录会话无法交给邮箱页面",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            "请退出后重新登录，再尝试打开邮箱。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 18.dp)) {
            Text("重试")
        }
    }
}

@Composable
private fun MailboxReadyWorkspace(
    state: MailboxUiState.Ready,
    expanded: Boolean,
    nativeDetail: Boolean,
    onOpenNativeDetail: (() -> Unit)?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSelectFolder: (Int) -> Unit,
    onOpenMessage: (MailSummary) -> Unit,
    onBackFromMessage: () -> Unit,
    onOpenWeb: () -> Unit,
    modifier: Modifier,
) {
    // expanded 是导航层给出的布局意图，宽度检查是第二道保险，避免窄窗口硬塞三栏。
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 1080.dp 是外层桌面窗口的默认宽度，扣除应用侧栏后邮箱内容区只有约 800.dp；
        // 这里按三栏自身的最小可读宽度判断，避免把外层窗口宽度误当成邮箱内容宽度。
        val split = expanded && maxWidth >= mailboxSplitMinWidth
        if (nativeDetail) {
            MailboxDetailPane(
                state = state,
                compact = false,
                onBack = onBackFromMessage,
                onRetry = onRefresh,
                onOpenWeb = onOpenWeb,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (split) {
            val sidebarWidth = (maxWidth * 0.22f).coerceIn(210.dp, 264.dp)
            val density = LocalDensity.current
            var listWidth by remember(maxWidth) {
                mutableStateOf((maxWidth * 0.31f).coerceIn(330.dp, 460.dp))
            }
            val minListWidth = 300.dp
            val maxListWidth = (maxWidth - sidebarWidth - 390.dp).coerceAtLeast(minListWidth)
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MailboxFolderPane(
                    folders = state.folders,
                    selectedFolderId = state.selectedFolderId,
                    onSelectFolder = onSelectFolder,
                    modifier = Modifier.width(sidebarWidth).fillMaxHeight(),
                )
                MailboxColumnDivider(
                    onDrag = { deltaPx ->
                        with(density) {
                            listWidth = (listWidth + deltaPx.toDp())
                                .coerceIn(minListWidth, maxListWidth)
                        }
                    },
                )
                MailboxListPane(
                    state = state,
                    onRefresh = onRefresh,
                    onLoadMore = onLoadMore,
                    onOpenMessage = onOpenMessage,
                    selectedMessageId = state.selectedMessage?.id,
                    onOpenNativeDetail = onOpenNativeDetail,
                    modifier = Modifier.width(listWidth).fillMaxHeight(),
                )
                MailboxColumnDivider()
                MailboxDetailPane(
                    state = state,
                    compact = false,
                    onBack = onBackFromMessage,
                    onRetry = onRefresh,
                    onOpenWeb = onOpenWeb,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        } else if (state.selectedMessage != null || state.isMessageLoading) {
            MailboxDetailPane(
                state = state,
                compact = true,
                onBack = onBackFromMessage,
                onRetry = onRefresh,
                onOpenWeb = onOpenWeb,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            MailboxListPane(
                state = state,
                onRefresh = onRefresh,
                onLoadMore = onLoadMore,
                onOpenMessage = onOpenMessage,
                selectedMessageId = null,
                onOpenNativeDetail = onOpenNativeDetail,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MailboxColumnDivider(onDrag: ((Float) -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(if (onDrag == null) 1.dp else 10.dp)
            .padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (onDrag == null) 0.45f else 0.18f))
            .then(
                if (onDrag == null) {
                    Modifier
                } else {
                    Modifier.draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta -> onDrag.invoke(delta) },
                    )
                },
            ),
    )
}

@Composable
private fun MailboxFolderPane(
    folders: List<MailboxFolderUi>,
    selectedFolderId: Int,
    onSelectFolder: (Int) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MailboxEnvelopeMark(modifier = Modifier.size(19.dp))
                    }
                }
                Text(
                    "邮箱",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Text(
                "主要文件夹",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 22.dp, bottom = 7.dp),
            )
            val primaryFolders = folders.filter { it.section == MailboxFolderSection.PRIMARY }
            val otherFolders = folders.filter { it.section == MailboxFolderSection.OTHER }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .desktopTouchScroll(listState),
                contentPadding = PaddingValues(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(primaryFolders, key = { it.id }) { folder ->
                    MailboxFolderRow(
                        folder = folder,
                        selected = folder.id == selectedFolderId,
                        onClick = { onSelectFolder(folder.id) },
                    )
                }
                if (otherFolders.isNotEmpty()) {
                    item(key = "other-folder-heading") {
                        Text(
                            "其他文件夹",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 4.dp),
                        )
                    }
                    items(otherFolders, key = { it.id }) { folder ->
                        MailboxFolderRow(
                            folder = folder,
                            selected = folder.id == selectedFolderId,
                            onClick = { onSelectFolder(folder.id) },
                        )
                    }
                }
            }
            Text(
                "只读查看",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 14.dp),
            )
        }
    }
}

@Composable
private fun MailboxFolderRow(
    folder: MailboxFolderUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        shape = RoundedCornerShape(11.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MailboxFolderGlyph(
                kind = folder.kind,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
            Text(
                folder.name,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            folder.unreadCount?.takeIf { it > 0 }?.let { unread ->
                Surface(
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape,
                ) {
                    Text(
                        unread.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MailboxListPane(
    state: MailboxUiState.Ready,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenMessage: (MailSummary) -> Unit,
    selectedMessageId: String?,
    onOpenNativeDetail: (() -> Unit)?,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val selectedFolder = state.folders.firstOrNull { it.id == state.selectedFolderId }
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
    ) {
        MailboxListHeader(
            title = selectedFolder?.name ?: "邮件",
            totalCount = state.totalCount,
            isLoading = state.isListLoading,
            onRefresh = onRefresh,
        )

        state.failure?.let { failure ->
            MailboxFailureBanner(failure = failure, onRetry = onRefresh)
        }

        if (state.isListLoading && state.messages.isEmpty()) {
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (state.messages.isEmpty()) {
            MailboxEmptyState(Modifier.weight(1f).fillMaxWidth())
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .desktopTouchScroll(listState),
                contentPadding = PaddingValues(top = 7.dp, bottom = 22.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MailboxMessageRow(
                        message = message,
                        folderKind = selectedFolder?.kind,
                        selected = message.id == selectedMessageId,
                        onClick = {
                            onOpenMessage(message)
                            onOpenNativeDetail?.invoke()
                        },
                    )
                }
                if (state.hasMoreMessages) {
                    item(key = "load-more") {
                        MailboxLoadMoreRow(
                            isLoading = state.isLoadingMore,
                            onClick = onLoadMore,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MailboxLoadMoreRow(
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = !isLoading,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(11.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.8.dp)
                Text("正在加载", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
            } else {
                Text("加载更多邮件", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun MailboxListHeader(
    title: String,
    totalCount: Int,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (totalCount > 0) "全部邮件 · $totalCount 封" else "暂无邮件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Surface(
            onClick = onRefresh,
            enabled = !isLoading,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = CircleShape,
            modifier = Modifier
                .size(38.dp)
                .semantics { contentDescription = if (isLoading) "邮箱同步中" else "刷新邮箱" },
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 1.8.dp)
                } else {
                    MailboxRefreshMark(modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun MailboxMessageRow(
    message: MailSummary,
    folderKind: MailboxFolderKind?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val party = if (folderKind == MailboxFolderKind.SENT && message.recipients.isNotEmpty()) {
        message.recipients.joinToString(", ")
    } else {
        message.sender
    }.ifBlank { "未知往来对象" }
    val date = formatMailboxDate(message.receivedAt.ifBlank { message.sentAt })
    Surface(
        onClick = onClick,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 82.dp)
                .padding(horizontal = 10.dp, vertical = 11.dp),
            verticalAlignment = Alignment.Top,
        ) {
            MailboxSenderAvatar(
                value = party,
                selected = selected,
                modifier = Modifier.padding(top = 1.dp),
            )
            Column(
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!message.isRead) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        party,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (message.isRead) FontWeight.Normal else FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    message.subject.ifBlank { "无主题" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (message.isRead) FontWeight.Medium else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (message.preview.isNotBlank()) {
                        Text(
                            schoolRichTextToPlainMultiline(message.preview),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (message.hasAttachments) {
                        MailboxAttachmentMark(
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp).size(15.dp),
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.44f),
    )
}

@Composable
private fun MailboxSenderAvatar(
    value: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }
    Surface(
        color = tint.copy(alpha = 0.18f),
        contentColor = tint,
        shape = CircleShape,
        modifier = modifier.size(34.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                mailboxAvatarLabel(value),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Coremail 发件人可能是 `"Name" <address>`；头像不应把包裹显示名的引号当作首字母。 */
private fun mailboxAvatarLabel(value: String): String {
    val normalized = value.trim()
    val displayName = normalized
        .substringBefore('<')
        .trim()
        .trim('"', '\'')
        .trim()
    return displayName.firstOrNull()?.toString()
        ?: normalized.substringAfter('<').substringBefore('>').trim().firstOrNull()?.toString()
        ?: "?"
}

@Composable
private fun MailboxDetailPane(
    state: MailboxUiState.Ready,
    compact: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenWeb: () -> Unit,
    modifier: Modifier,
) {
    val message = state.selectedMessage
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .desktopTouchScroll(scrollState)
            .padding(horizontal = if (compact) 18.dp else 28.dp, vertical = 12.dp),
    ) {
        if (compact) {
            Surface(
                onClick = onBack,
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.semantics { contentDescription = "返回邮件列表" },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MailboxBackMark(modifier = Modifier.size(17.dp))
                    Text(
                        "邮件列表",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
        }

        if (state.isMessageLoading) {
            Box(
                Modifier.fillMaxWidth().padding(top = 150.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (message == null) {
            if (state.failure != null) {
                MailboxFailureBanner(failure = state.failure, onRetry = onRetry)
            } else {
                MailboxDetailEmptyState()
            }
        } else {
            MailboxMessageDetail(message = message, onOpenWeb = onOpenWeb)
        }
    }
}

@Composable
private fun MailboxMessageDetail(
    message: MailMessage,
    onOpenWeb: () -> Unit,
) {
    val subject = message.subject.ifBlank { "无主题" }
    val sender = message.from.firstOrNull().orEmpty().ifBlank { "未知发件人" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 820.dp)
            .padding(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            subject,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            MailboxSenderAvatar(value = sender, selected = true)
            Column(modifier = Modifier.padding(start = 11.dp)) {
                Text(
                    sender,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatMailboxDate(message.sentAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                MailboxMetaRow("发件人", message.from.joinToString().ifBlank { "未知" })
                MailboxMetaRow("收件人", message.to.joinToString().ifBlank { "未知" })
                if (message.cc.isNotEmpty()) MailboxMetaRow("抄送", message.cc.joinToString())
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f))
        Text(
            "正文",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            schoolRichTextToPlainMultiline(message.bodyHtml).ifBlank { "这封邮件没有可显示的正文。" },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )

        if (message.attachments.isNotEmpty()) {
            Text(
                "附件",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                message.attachments.forEach { attachment ->
                    MailboxAttachmentRow(attachment)
                }
            }
        }

        Surface(
            onClick = onOpenWeb,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .align(Alignment.Start)
                .semantics { contentDescription = "在网页中打开" },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MailboxExternalMark(modifier = Modifier.size(17.dp))
                Text(
                    "在网页中打开",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MailboxAttachmentRow(attachment: MailAttachment) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(11.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MailboxAttachmentMark(modifier = Modifier.size(17.dp))
            Text(
                attachment.name.ifBlank { "未命名附件" },
                modifier = Modifier.weight(1f).padding(start = 9.dp),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            attachment.sizeBytes?.let { bytes ->
                Text(
                    formatMailboxSize(bytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.76f),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MailboxMetaRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            modifier = Modifier.widthIn(min = 52.dp, max = 60.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MailboxFailureBanner(
    failure: MailboxFailure,
    onRetry: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 9.dp, end = 5.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MailboxWarningMark(modifier = Modifier.size(17.dp))
            Text(
                mailboxFailureMessage(failure),
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun MailboxEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MailboxEnvelopeMark(
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            modifier = Modifier.size(42.dp),
        )
        Text(
            "这个文件夹里没有邮件",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun MailboxDetailEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 150.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MailboxEnvelopeMark(
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
            modifier = Modifier.size(48.dp),
        )
        Text(
            "选择一封邮件查看内容",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            "阅读区会在这里展开",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun mailboxFailureMessage(failure: MailboxFailure): String = when (failure) {
    MailboxFailure.NETWORK -> "邮箱暂时无法连接，请检查网络。"
    MailboxFailure.PARSE -> "邮箱返回的数据无法读取，请稍后重试。"
    MailboxFailure.SESSION_EXPIRED -> "邮箱登录会话已失效，请重新登录。"
}

private fun formatMailboxDate(value: String): String {
    val normalized = value.trim().replace('T', ' ')
    if (normalized.isBlank()) return "时间未知"
    val match = Regex("""(\d{4})[-/](\d{1,2})[-/](\d{1,2})\s+(\d{1,2}):(\d{2})""").find(normalized)
    return if (match != null) {
        val (year, month, day, hour, minute) = match.destructured
        "${year}年${month.padStart(2, '0')}月${day.padStart(2, '0')}日 $hour:$minute"
    } else {
        normalized.substringBefore('.').take(24)
    }
}

private fun formatMailboxSize(bytes: Int): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}

@Composable
private fun MailboxFolderGlyph(
    kind: MailboxFolderKind,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val left = size.width * 0.12f
        val right = size.width * 0.88f
        val top = size.height * 0.25f
        val bottom = size.height * 0.78f
        when (kind) {
            MailboxFolderKind.TODO -> {
                drawLine(tint, Offset(left, top), Offset(right, top), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(left, top), Offset(left, bottom), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(right, top), Offset(right, bottom), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(left, bottom), Offset(right, bottom), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.3f, size.height * 0.48f), Offset(size.width * 0.7f, size.height * 0.48f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.3f, size.height * 0.63f), Offset(size.width * 0.6f, size.height * 0.63f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            MailboxFolderKind.DRAFTS -> {
                drawRoundRect(tint, topLeft = Offset(left, top), size = Size(right - left, bottom - top), cornerRadius = CornerRadius(2.dp.toPx()), style = stroke)
                drawLine(tint, Offset(size.width * 0.25f, size.height * 0.38f), Offset(size.width * 0.75f, size.height * 0.38f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.25f, size.height * 0.55f), Offset(size.width * 0.68f, size.height * 0.55f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            MailboxFolderKind.SENT -> {
                drawLine(tint, Offset(left, size.height * 0.28f), Offset(right, size.height * 0.28f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(left, size.height * 0.72f), Offset(right, size.height * 0.72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(left, size.height * 0.28f), Offset(size.width * 0.5f, size.height * 0.54f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(right, size.height * 0.28f), Offset(size.width * 0.5f, size.height * 0.54f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            else -> {
                drawRoundRect(tint, topLeft = Offset(left, top), size = Size(right - left, bottom - top), cornerRadius = CornerRadius(3.dp.toPx()), style = stroke)
                drawLine(tint, Offset(left, top), Offset(size.width * 0.5f, size.height * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(right, top), Offset(size.width * 0.5f, size.height * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun MailboxEnvelopeMark(
    tint: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    val resolvedTint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onPrimaryContainer else tint
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val left = size.width * 0.12f
        val right = size.width * 0.88f
        val top = size.height * 0.24f
        val bottom = size.height * 0.76f
        drawRoundRect(
            resolvedTint,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = stroke,
        )
        drawLine(resolvedTint, Offset(left, top), Offset(size.width / 2f, size.height * 0.55f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(resolvedTint, Offset(right, top), Offset(size.width / 2f, size.height * 0.55f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Composable
private fun MailboxRefreshMark(modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val radius = size.minDimension * 0.32f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawArc(
            color = tint,
            startAngle = 34f,
            sweepAngle = 222f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = stroke,
        )
        val tip = Offset(center.x + radius * 0.82f, center.y - radius * 0.56f)
        val arrow = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x - 4.dp.toPx(), tip.y - 1.dp.toPx())
            lineTo(tip.x - 2.dp.toPx(), tip.y + 4.dp.toPx())
        }
        drawPath(arrow, tint, style = stroke)
    }
}

@Composable
private fun MailboxBackMark(modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val y = size.height / 2f
        drawLine(tint, Offset(size.width * 0.78f, y), Offset(size.width * 0.22f, y), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.22f, y), Offset(size.width * 0.46f, size.height * 0.25f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.22f, y), Offset(size.width * 0.46f, size.height * 0.75f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Composable
private fun MailboxAttachmentMark(
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(size.width * 0.66f, size.height * 0.24f)
            lineTo(size.width * 0.34f, size.height * 0.56f)
            cubicTo(
                size.width * 0.15f, size.height * 0.75f,
                size.width * 0.42f, size.height * 0.95f,
                size.width * 0.62f, size.height * 0.76f,
            )
            lineTo(size.width * 0.78f, size.height * 0.60f)
        }
        drawPath(path, tint, style = stroke)
        drawLine(tint, Offset(size.width * 0.52f, size.height * 0.43f), Offset(size.width * 0.36f, size.height * 0.59f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Composable
private fun MailboxExternalMark(modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.onPrimaryContainer
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            tint,
            topLeft = Offset(size.width * 0.14f, size.height * 0.28f),
            size = Size(size.width * 0.54f, size.height * 0.56f),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = stroke,
        )
        drawLine(tint, Offset(size.width * 0.48f, size.height * 0.18f), Offset(size.width * 0.84f, size.height * 0.18f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.84f, size.height * 0.18f), Offset(size.width * 0.84f, size.height * 0.54f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.84f, size.height * 0.18f), Offset(size.width * 0.43f, size.height * 0.59f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Composable
private fun MailboxWarningMark(modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.onErrorContainer
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(size.width / 2f, size.height * 0.12f)
            lineTo(size.width * 0.9f, size.height * 0.84f)
            lineTo(size.width * 0.1f, size.height * 0.84f)
            close()
        }
        drawPath(path, tint, style = stroke)
        drawLine(tint, Offset(size.width / 2f, size.height * 0.36f), Offset(size.width / 2f, size.height * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawCircle(tint, radius = 1.dp.toPx(), center = Offset(size.width / 2f, size.height * 0.73f))
    }
}
