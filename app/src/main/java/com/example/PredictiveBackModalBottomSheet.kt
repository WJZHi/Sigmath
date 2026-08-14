package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 修复 Jetpack Compose Material3 ModalBottomSheet 预测返回 (Predictive Back) 手势底部白块问题的完整解决方案组件。
 *
 * 【现象与根因分析】：
 * 在 Android 14+ 触发侧边滑动预测返回 (Predictive Back) 手势时，ModalBottomSheet 整个弹窗容器会随手势向上/向内缩放。
 * 原生 ModalBottomSheet 的 containerColor 若为默认底色，在缩放离地时底层 Dialog Window 的纯白背景就会透出（即底部白块现象）。
 *
 * 【实现思路与关键点】：
 * 1. 【关键点1】 ModalBottomSheet 的 containerColor 设置为 Color.Transparent，底色全部交由内部容器接管；
 * 2. 【关键点2】 在弹窗内部最外层创建一个 Box 作为整体内容与背景缓冲容器；
 * 3. 【关键点3】 该缓冲容器配置：向下 offset(y = 56.dp) 偏移 + 额外扩展 56.dp 缓冲区高度，背景色与列表面板底色完全一致；
 * 4. 【关键点4】 缓冲区仅渲染纯色背景，不放置任何交互 UI 元素；滚动 LazyColumn 保持原有布局范围，仅给列表底部增加 padding 避开缓冲区域；
 * 5. 【关键点5】 缓冲区高度取 56.dp，作为预留安全区间，覆盖预测返回最大回缩距离；
 * 6. 【关键点6】 严禁修改列表原有业务逻辑、条目样式、滚动行为；不新增空白列表项充当缓冲区；
 * 7. 【关键点7】 基于原生 Material3 ModalBottomSheet 组件改造，不自定义 BottomSheetDialog。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictiveBackModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    panelColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(panelColor),
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    bufferHeight: Dp = 56.dp, // 修复关键点5: 缓冲区高度取 56.dp，作为预留安全区间，覆盖预测返回最大回缩距离
    content: @Composable ColumnScope.() -> Unit
) {
    // 修复关键点1: ModalBottomSheet 的 containerColor 必须设置为 Color.Transparent
    // 将底色绘制全部交由弹窗内部容器接管，从根本上防止预测返回手势导致 Sheet 整体向上回缩时透出底层 Dialog 的纯白背景。
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = Color.Transparent, // 【关键点1】原生 ModalBottomSheet 容器背景设为透明
        contentColor = contentColor,
        tonalElevation = 0.dp,
        scrimColor = scrimColor,
        dragHandle = null // 拖拽句柄 (DragHandle) 交由内部排版统一渲染
    ) {
        // 修复关键点2: 在弹窗内部最外层创建一个 Box 作为背景缓冲与主内容容器
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 修复关键点3: 背景缓冲容器配置
            // - 向下 offset(y = bufferHeight) 偏移 56.dp
            // - 配合 matchParentSize() 随主内容高度扩展，多延伸出 56.dp 的底域区间
            // - 背景色 (background) 与列表面板底色 (panelColor) 完全一致
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .matchParentSize()
                    .offset(y = bufferHeight) // 【关键点3】向下 offset 偏移 56.dp
                    .background(panelColor)   // 【关键点3】背景色与列表面板底色完全一致
            )

            // 主面板圆角背景层（与内部主业务内容区域对齐，顶部设置 28.dp 圆角）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .matchParentSize()
                    .background(
                        color = panelColor,
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    )
            )

            // 修复关键点4: 内部主业务布局
            // 缓冲区仅渲染上述纯色 Box 背景，不放置任何交互 UI 元素；
            // 内部 Column 与 LazyColumn 保持原有布局范围，不向下延伸业务条目
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                // BottomSheet 顶部拖拽手势条 (Drag Handle)
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        .align(Alignment.CenterHorizontally)
                )

                // 渲染业务方传入的内容（如包含 LazyColumn 的业务列表）
                content()
            }
        }
    }
}

/**
 * 完整示例：基于 PredictiveBackModalBottomSheet 搭建的完整 Compose 弹窗组件
 * 包含完整的 LazyColumn 列表渲染、头部标题与交互条目。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictiveBackDemoModalBottomSheet(
    itemsList: List<String>,
    onDismiss: () -> Unit,
    onItemClick: (String) -> Unit = {},
    onClearAll: () -> Unit = {}
) {
    PredictiveBackModalBottomSheet(
        onDismissRequest = onDismiss,
        panelColor = MaterialTheme.colorScheme.surface
    ) {
        // 1. 标题头与操作按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "历史记录",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "计算历史记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (itemsList.isNotEmpty()) {
                    IconButton(onClick = onClearAll) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "清空历史",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // 2. 关键点4 & 6：滚动 LazyColumn 保持原有业务逻辑与布局范围
        // 仅添加底部 padding 避开缓冲区与系统导航栏，不改变业务样式或新增空白项
        if (itemsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无计算历史记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(itemsList) { itemText ->
                    Card(
                        onClick = { onItemClick(itemText) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Text(
                            text = itemText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
