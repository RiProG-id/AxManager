package frb.axeron.manager.ui.screen.advanced

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import frb.axeron.manager.ui.component.TerminalControlPanel
import frb.axeron.manager.ui.component.TerminalInputView

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun AdvancedTerminalScreen(navigator: DestinationsNavigator) {
    val viewModel: AdvancedTerminalViewModel = viewModel()

    Scaffold { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            AdvancedTerminalView(viewModel, onBack = { navigator.popBackStack() })
        }
    }
}

@Composable
fun AdvancedTerminalView(viewModel: AdvancedTerminalViewModel, onBack: () -> Unit) {
    val emulator = viewModel.terminalEmulator
    val lines = emulator.outputLines
    val cursorRow = emulator.cursorRow
    val cursorCol = emulator.cursorCol
    val adbStatus by viewModel.adbStatus.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Auto-scroll to bottom when content changes
    LaunchedEffect(emulator.revision, cursorRow) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        // Status bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ADB: $adbStatus",
                color = if (adbStatus == "Connected") Color.Green else Color.Red,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Filled.Terminal, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusRequester.requestFocus()
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            val density = LocalDensity.current
            val charWidth = with(density) { 7.2.sp.toDp() } // Approximate for 12sp monospace
            val charHeight = with(density) { 14.sp.toDp() }

            val cols = (maxWidth / charWidth).toInt().coerceAtLeast(10)
            val rows = (maxHeight / charHeight).toInt().coerceAtLeast(10)

            LaunchedEffect(cols, rows) {
                viewModel.terminalEmulator.resize(rows, cols)
            }

            // Real input view instead of hidden TextField
            AndroidView(
                factory = { ctx ->
                    TerminalInputView(ctx).apply {
                        onTextInput = { text -> viewModel.sendInput(text) }
                        onActionKey = { keyCode ->
                            when (keyCode) {
                                android.view.KeyEvent.KEYCODE_ENTER -> viewModel.sendInput("\n")
                                android.view.KeyEvent.KEYCODE_DEL -> viewModel.sendRaw(byteArrayOf(0x7f))
                                android.view.KeyEvent.KEYCODE_TAB -> viewModel.sendInput("\t")
                                android.view.KeyEvent.KEYCODE_DPAD_UP -> viewModel.sendInput("\u001b[A")
                                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> viewModel.sendInput("\u001b[B")
                                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> viewModel.sendInput("\u001b[D")
                                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> viewModel.sendInput("\u001b[C")
                            }
                        }
                    }
                },
                update = { view ->
                    view.requestTerminalFocus()
                },
                modifier = Modifier.size(1.dp) // Non-zero size to ensure it can receive focus
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(4.dp)
            ) {
                lines.forEachIndexed { index, line ->
                    TerminalLine(
                        line = line,
                        isCursorLine = index == cursorRow,
                        cursorCol = cursorCol
                    )
                }
            }
        }

        // Control Panel
        TerminalControlPanel(
            onKeyPress = { key ->
                viewModel.sendSpecialKey(key)
            },
            onHistoryNavigate = { up ->
                // History navigate via special keys or handled in ViewModel
            },
            isCtrlPressed = viewModel.isCtrlPressed,
            isAltPressed = viewModel.isAltPressed,
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
        )
    }
}

@Composable
fun TerminalLine(line: AnnotatedString, isCursorLine: Boolean, cursorCol: Int) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Box {
        BasicText(
            text = line,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFE5E5E5),
                fontSize = 12.sp,
                lineHeight = 14.sp
            ),
            onTextLayout = { textLayoutResult = it }
        )
        if (isCursorLine) {
            val cursorOffset = if (textLayoutResult != null) {
                if (cursorCol in 0 until line.length) {
                    textLayoutResult?.getHorizontalPosition(cursorCol, true) ?: 0f
                } else {
                    textLayoutResult?.getLineRight(0) ?: 0f
                }
            } else 0f

            val density = LocalDensity.current
            BlinkingCursor(
                offset = with(density) { cursorOffset.toDp() },
                height = with(density) { 14.sp.toDp() }
            )
        }
    }
}

@Composable
fun BlinkingCursor(offset: Dp, height: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        Modifier
            .padding(start = offset)
            .width(8.dp)
            .height(height)
            .background(Color.White.copy(alpha = alpha))
    )
}
