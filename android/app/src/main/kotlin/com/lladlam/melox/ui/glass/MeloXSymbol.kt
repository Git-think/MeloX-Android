package com.lladlam.melox.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.lladlam.melox.R
import com.lladlam.melox.ui.animation.MeloXMotion

/**
 * Semantic icon inventory aligned with SF Symbols names. The bundled SF font is
 * the only icon font used by this component; unresolved symbols fail fast.
 */
enum class MeloXSymbol(
    val sfSymbolName: String,
    val materialLigature: String,
) {
    Home("house", "home"),
    Explore("safari", "explore"),
    Library("music.note.list", "library_music"),
    Settings("gear", "settings"),
    Person("person.crop.circle", "account_circle"),
    Search("magnifyingglass", "search"),
    ChevronLeft("chevron.left", "chevron_left"),
    ChevronRight("chevron.right", "chevron_right"),
    ChevronUpDown("chevron.up.chevron.down", "unfold_more"),
    Xmark("xmark", "close"),
    Ellipsis("ellipsis", "more_horiz"),
    Clock("clock", "schedule"),
    Plus("plus", "add"),
    Download("arrow.down.circle", "download"),
    Share("square.and.arrow.up", "ios_share"),
    Mail("paperplane", "mail"),
    Message("message", "chat_bubble"),
    Info("info.circle", "info"),
    Heart("heart", "favorite"),
    Star("star", "star"),
    List("list.bullet", "format_list_bulleted"),
    Check("checkmark", "check"),
    ArrowUp("arrow.up.circle.fill", "arrow_upward"),
    ArrowDown("arrow.down.circle", "arrow_downward"),
    Refresh("arrow.clockwise", "refresh"),
    MusicNote("music.note", "music_note"),
    Calendar("calendar", "calendar_month"),
    Flame("flame.fill", "local_fire_department"),
    RadioWaves("dot.radiowaves.left.and.right", "graphic_eq"),
    Walk("figure.walk.motion", "directions_walk"),
    Sparkles("sparkles", "auto_awesome"),
    Quote("quote.bubble", "format_quote"),
    Devices("display", "devices"),
    Landscape("rectangle.landscape.rotate", "screen_rotation"),
    PictureInPicture("pip", "picture_in_picture"),
    Apps("circle.grid.2x2.fill", "apps"),
    Microphone("mic", "mic"),
    Storage("internaldrive", "storage"),
    Bug("ladybug", "bug_report"),
    Play("play.fill", "play_arrow"),
    Pause("pause.fill", "pause"),
    Previous("backward.fill", "skip_previous"),
    Next("forward.fill", "skip_next"),
    Shuffle("shuffle", "shuffle"),
    Repeat("repeat", "repeat"),
    RepeatOne("repeat.1", "repeat_one"),
    Infinity("infinity", "all_inclusive"),
    Lyrics("quote.bubble", "lyrics"),
    AutoMix("waveform", "graphic_eq"),
    AddToPlaylist("text.badge.plus", "playlist_add"),
    Trash("trash", "delete"),
    Moon("moon", "bedtime"),
    Switch("switch.2", "swap_horiz"),
    Book("book.pages", "menu_book"),
    Comment("bubble.left", "chat_bubble"),
    Volume("speaker.wave.2.fill", "volume_up"),
    Queue("text.line.first.and.arrowtriangle.forward", "queue_music"),
    MoreVertical("ellipsis", "more_vert"),
    Circle("circle", "radio_button_unchecked"),
    CheckCircle("checkmark.circle.fill", "check_circle"),
    Unknown("questionmark.circle", "help"),
}

enum class MeloXSymbolVariant {
    Regular,
    Fill,
}

private val MeloXSfSymbolCodePoints = mapOf(
    "house" to 0x10039E,
    "safari" to 0x1003AC,
    "music.note.list" to 0x10046C,
    "gear" to 0x10035F,
    "person.crop.circle" to 0x10026D,
    "magnifyingglass" to 0x1002AB,
    "chevron.left" to 0x100189,
    "chevron.right" to 0x10018A,
    "chevron.up.chevron.down" to 0x10018F,
    "xmark" to 0x100184,
    "ellipsis" to 0x100360,
    "clock" to 0x10042B,
    "plus" to 0x10017C,
    "square.and.arrow.up" to 0x100202,
    "message" to 0x100324,
    "info.circle" to 0x100174,
    "heart" to 0x1002B4,
    "list.bullet" to 0x1002F2,
    "checkmark" to 0x100185,
    "arrow.down.circle" to 0x100078,
    "paperplane" to 0x10021F,
    "arrow.up.circle.fill" to 0x100077,
    "arrow.clockwise" to 0x100148,
    "music.note" to 0x10046A,
    "calendar" to 0x100249,
    "flame.fill" to 0x10066D,
    "dot.radiowaves.left.and.right" to 0x100319,
    "figure.walk.motion" to 0x101411,
    "sparkles" to 0x1001BF,
    "quote.bubble" to 0x10032E,
    "display" to 0x1008B9,
    "pip" to 0x100833,
    "circle.grid.2x2.fill" to 0x1007BF,
    "rectangle.landscape.rotate" to 0x101EEF,
    "mic" to 0x1002B0,
    "internaldrive" to 0x10097E,
    "ladybug" to 0x100BD4,
    "play.fill" to 0x100284,
    "pause.fill" to 0x100286,
    "backward.fill" to 0x10028A,
    "forward.fill" to 0x10028C,
    "shuffle" to 0x10029D,
    "repeat" to 0x10029E,
    "repeat.1" to 0x10029F,
    "infinity" to 0x100BE0,
    "waveform" to 0x10066B,
    "text.badge.plus" to 0x1002F8,
    "trash" to 0x100211,
    "moon" to 0x1001B9,
    "switch.2" to 0x10070A,
    "book.pages" to 0x10173E,
    "bubble.left" to 0x10032A,
    "speaker.wave.2.fill" to 0x1002A7,
    "text.line.first.and.arrowtriangle.forward" to 0x10163F,
    "questionmark.circle" to 0x10005C,
    "checkmark.circle.fill" to 0x100063,
)

@Composable
fun MeloXSymbolIcon(
    symbol: MeloXSymbol,
    modifier: Modifier = Modifier,
    color: Color,
    variant: MeloXSymbolVariant = MeloXSymbolVariant.Regular,
    iconSize: TextUnit = 24.sp,
    contentDescription: String? = null,
) {
    // Brand overrides: these SF Symbol glyphs are replaced by traced vectors.
    //   Settings  -> the MeloX chainring disc instead of the SF gear.
    //   Microphone-> ic_melox_mic, extracted from the same `mic` glyph with its
    //                broken stem winding fixed (see the drawable's header note).
    // Both the bottom-bar Settings tab and the Settings-list "通用" item flow
    // through this symbol, so they pick up the brand disc automatically; likewise
    // every 听歌识曲 entry (search field + Home quick action card) gets the
    // sealed-off microphone without any change at the call sites.
    when (symbol) {
        MeloXSymbol.Settings -> {
            Icon(
                painter = painterResource(R.drawable.ic_melox_brand_gear),
                contentDescription = contentDescription,
                modifier = modifier,
                tint = color,
            )
            return
        }
        MeloXSymbol.Microphone -> {
            MicrophoneVectorIcon(
                modifier = modifier,
                color = color,
                iconSize = iconSize,
                contentDescription = contentDescription,
            )
            return
        }
        MeloXSymbol.Plus -> {
            PlusVectorIcon(
                modifier = modifier,
                color = color,
                iconSize = iconSize,
                contentDescription = contentDescription,
            )
            return
        }
        MeloXSymbol.Star -> {
            StarVectorIcon(
                modifier = modifier,
                color = color,
                iconSize = iconSize,
                contentDescription = contentDescription,
                variant = variant,
            )
            return
        }
        MeloXSymbol.MoreVertical -> {
            // 「更多」横向三点。原本落到 SfGlyphIcon 渲染 `ellipsis`（0x100360）字形 ——
            // 与 Plus / Microphone 同类跨设备基线 bug：不同 Android / Skia 版本对同一
            // glyph 的 bounds 差 1~2px，会把省略号三点算得参差错位（issue #76）。
            // 改走手绘矢量，跨设备一致。
            // ⚠ 保持横向 `…` 形态，不改成竖向：原版横向省略号在 vivo 上本就显示正常，
            // 问题只在部分机型三点被算得参差，故矢量必须沿用横向排布、只把三点画整齐。
            MoreVerticalVectorIcon(
                modifier = modifier,
                color = color,
                contentDescription = contentDescription,
            )
            return
        }
        MeloXSymbol.Repeat, MeloXSymbol.RepeatOne -> {
            // 循环 / 单曲循环改走 **SF 真实轮廓**的实心矢量，与播放页「列表按钮右上角
            // 徽标」（`MeloXNowPlayingCoreControls.ScenePlaybackModeBadge`）同款 ——
            // 用户 2026-10-15 要求队列面板顶部模式开关里的循环图标跟徽标一致。
            //   徽标走 [MeloXPlaybackModeGlyph]（1:1 复刻 sf_pro_subset.ttf 的 repeat /
            //   repeat.1 轮廓，绕向已归一化）；这里同样走它，字号按 SF 度量换算，任何
            //   `iconSize` 下都与原字体字形同光学尺寸。
            PlaybackModeVectorIcon(
                symbol = symbol,
                modifier = modifier,
                color = color,
                iconSize = iconSize,
                contentDescription = contentDescription,
            )
            return
        }
        else -> Unit
    }
    // Material Symbols uses a taller font box than SF Symbols. Rendering the
    // glyph at a slightly smaller em size leaves a real optical inset inside
    // callers' 18/20/24dp icon boxes, instead of clipping the gear and arrows
    // at their ascender/descender edges.
    val codePoint = requireNotNull(MeloXSfSymbolCodePoints[symbol.sfSymbolName]) {
        "Missing SF Symbol mapping: ${symbol.sfSymbolName}"
    }
    SfGlyphIcon(
        codePoint = codePoint,
        modifier = modifier,
        color = color,
        iconSize = iconSize,
        contentDescription = contentDescription,
        weight = if (variant == MeloXSymbolVariant.Fill) FontWeight.Medium else FontWeight.Normal,
    )
}

// sf_pro_subset.ttf 中 mic 字形的度量，用于把矢量还原到与字号渲染同尺寸。
private const val MIC_UPM = 2048f
private const val MIC_INK_UNITS = 2140f
// ic_melox_mic.xml 的 viewport 与墨迹高度。
private const val MIC_VIEWPORT = 24f
private const val MIC_INK_H = 23.90f

// sf_pro_subset.ttf 中 plus（U+10017C）字形的度量，同样用于把矢量还原到与字号渲染同尺寸。
// 该 glyph 由两条独立轮廓（竖直棒 + 水平棒）组成，两条都是实体，无绕向问题。
// 字体墨迹 bbox = (255,-103)-(1905,1547)，即 1650 × 1650 units。
private const val PLUS_UPM = 2048f
private const val PLUS_INK_UNITS = 1650f
// ic_melox_plus.xml 的 viewport 与墨迹高度（18.29 ≈ 1650/2048 × 24 缩放后，与字模同形）。
private const val PLUS_VIEWPORT = 24f
private const val PLUS_INK_H = 18.29f

/**
 * 听歌识曲麦克风。字体里那支 `mic` 字形（U+1002B0）在 nonzero 规则下自身是破的
 * —— 竖杆绕向与其余实体相反，在「拾音弧底部」「底座横线」两处被挖成孔，
 *   所以改为使用 [R.drawable.ic_melox_mic]：同一字形 glyf 轮廓的 1:1 提取，
 *   只把竖杆绕向翻过来，余下像素逐点未变（推导见该 drawable 的头部注释）。
 *
 * ⚠ **尺寸刻意不跟随调用方的 Box**：SF 字形本来就是按 `iconSize`（sp）渲染的，
 *   外面的 18dp / 22dp 图标盒只起占位作用 —— mic 的墨迹纵向本来就会溢出。
 *   所以这里照字体的度量换算，而不是直接把矢量塞进 `modifier`：
 *     字体 unitsPerEm = 2048，mic 墨迹 = 1431 × 2140 单位
 *     ⇒ 墨迹 = iconSize × 2140/2048，默认 24.sp 时 = 16.77 × 25.08 dp
 *     本矢量墨迹占 23.90/24 ⇒ 渲染尺寸 = 墨迹 ÷ (23.90/24)
 *   这样默认 24.sp 得到 25.2dp，首页快捷卡那种 `iconSize = 72.sp` 的大水印得到
 *   75.6dp，**任何字号下都与原字形同尺寸**，换成矢量不会被压缩掉光学字号。
 *
 * ⚠ alpha 走 [androidx.compose.ui.draw.alpha] 而不是带 alpha 的 tint。单路径
 *   fill 本身不会双重混合，但一旦将来把它拆成多条路径，逐路径施加的
 *   ColorFilter.tint 会在轮廓叠合处二次混合出深斑；图层级 alpha 免疫这个问题。
 */
@Composable
private fun MicrophoneVectorIcon(
    modifier: Modifier,
    color: Color,
    iconSize: TextUnit,
    contentDescription: String?,
) {
    val density = LocalDensity.current
    val renderSize = remember(density, iconSize) {
        val emPx = with(density) { iconSize.toPx() }
        with(density) { (emPx * MIC_INK_UNITS / MIC_UPM * MIC_VIEWPORT / MIC_INK_H).toDp() }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(R.drawable.ic_melox_mic),
            contentDescription = contentDescription,
            // ⚠ 必须 requiredSize 而不是 size：`size()` 会服从父约束，而调用方给的
            //   18dp / 22dp 图标盒只是占位，mic 本来就会溢出它。用 `size()` 会被
            //   压成 18dp（实测墨迹掉到 12 × 18 dp，整整小三成）—— `requiredSize`
            //   才等价于原来 Canvas 里按 24.sp 自由绘制的行为。
            modifier = Modifier.requiredSize(renderSize).alpha(color.alpha),
            tint = color.copy(alpha = 1f),
        )
    }
}

/**
 * SF Symbols `plus`（U+10017C）的矢量兜底。
 *
 * 换矢量的动机 —— 原先走 `SfGlyphIcon` 靠 `Paint.getTextBounds` 计算 baseline，
 *   但不同 Android 版本 / Skia 版本对同一 glyph 的 `bounds.top` / `bounds.bottom`
 *   会因 hinting、`SUBPIXEL_TEXT_FLAG` 等差异而差 1~2 像素，导致「加号」在不同机型
 *   上纵向偏移 2~4dp（用户 2026-09-26 反馈：其他机型加号偏下）。改走矢量与
 *   [MicrophoneVectorIcon] / [MeloXSymbol.Settings] 同一条兜底路径，跨设备一致。
 *
 * 尺寸刻意不跟随调用方的 Box：调用方给的 `Modifier.size(20.dp)` 只是**占位**，
 *   字号仍是 24.sp；本矢量按同样的字体度量换算，任何 `iconSize` 下都还原成与
 *   原字形**同墨迹尺寸**的矢量（默认 24.sp ⇒ 25.37dp），换成矢量不会被压扁。
 *
 * ⚠ alpha 走 [androidx.compose.ui.draw.alpha] 而不是带 alpha 的 tint（与 mic 一致）：
 *   单路径 fill 本身不会双重混合，但一旦将来把两条棒拆成两条 path，逐路径施加的
 *   ColorFilter.tint 会在叠合处二次混合出深斑；图层级 alpha 免疫这个问题。
 */
@Composable
private fun PlusVectorIcon(
    modifier: Modifier,
    color: Color,
    iconSize: TextUnit,
    contentDescription: String?,
) {
    val density = LocalDensity.current
    val renderSize = remember(density, iconSize) {
        val emPx = with(density) { iconSize.toPx() }
        with(density) { (emPx * PLUS_INK_UNITS / PLUS_UPM * PLUS_VIEWPORT / PLUS_INK_H).toDp() }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(R.drawable.ic_melox_plus),
            contentDescription = contentDescription,
            // 同 mic：requiredSize 而非 size，避免被父 20dp 图标盒压扁。
            modifier = Modifier.requiredSize(renderSize).alpha(color.alpha),
            tint = color.copy(alpha = 1f),
        )
    }
}

/**
 * 播放页收藏按钮星形。两个子集字体都没有 star 字形，所以这里用手绘矢量五角星兜底
 * （与 mic / plus 同一条品牌矢量路径）：
 *   - [MeloXSymbolVariant.Regular] → 描边（未收藏）
 *   - [MeloXSymbolVariant.Fill]    → 实心（已收藏）
 *
 * 内凹比 0.40 取自示意图量取的实体/内点半径比；星形外缘占满调用方给的图标盒
 * （与 ellipsis 同样 22.dp），保证两个按钮视觉重量一致（用户要求“协调好大小”）。
 *
 * ⚠ alpha 走图层级 [androidx.compose.ui.draw.alpha] 而不是带 alpha 的 tint，
 *   与 mic / plus 一致：单路径 fill 不会双重混合，但描边在轮廓叠合处若用逐路径
 *   tint 会二次混合出深斑；图层级 alpha 免疫该问题。
 */
@Composable
private fun StarVectorIcon(
    modifier: Modifier,
    color: Color,
    iconSize: TextUnit,
    contentDescription: String?,
    variant: MeloXSymbolVariant,
) {
    Canvas(
        modifier = modifier
            .alpha(color.alpha)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics(mergeDescendants = true) {
                        this.contentDescription = contentDescription
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = min(size.width, size.height) / 2f * 0.92f
        val innerRatio = 0.40f
        val strokeW = outer * 0.16f
        // 描边态沿路径中心线绘制，若中心线取 outer，外缘会比实心态多出 strokeW/2。
        // 这里把描边态的路径内缩半个描边宽，使外缘恰好落在 outer 上，
        // 两种态外轮廓半径一致 → 实心星与描边星视觉大小相同。
        val drawOuter = if (variant == MeloXSymbolVariant.Fill) outer else outer - strokeW / 2f
        val inner = drawOuter * innerRatio
        val path = Path().apply {
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) drawOuter else inner
                val angle = (-Math.PI / 2.0 + i * Math.PI / 5.0).toFloat()
                val x = cx + r * cos(angle)
                val y = cy + r * sin(angle)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(
            path = path,
            color = color.copy(alpha = 1f),
            style = if (variant == MeloXSymbolVariant.Fill) {
                Fill
            } else {
                Stroke(
                    width = strokeW,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
            },
        )
    }
}

/**
 * SF Symbols 播放模式字形的**真实填充轮廓**（`shuffle` 0x10029D / `repeat` 0x10029E /
 * `repeat.1` 0x10029F）：fontTools 从 `sf_pro_subset.ttf` 抽出的 100×100 归一化盒
 * （max-span = 100）SVG path data（M/L/Q/Z 绝对坐标，全部二次曲线 ⇒ Compose
 * `quadraticTo` 一一对应）。数据由 `.workbuddy/tmp/normalize_winding.py` 生成。
 *
 * ⚠ **绕向已归一化**：TrueType 走 nonzero 填充；`repeat` / `repeat.1` 的第 4 段轮廓
 * 绕向与其余段相反，和左下箭头重叠处绕数相消 ⇒ 渲染器会**掏出空洞**。这里每段字形的
 * 所有轮廓已统一成同一绕向，nonzero 即等价于并集，观感 = SF 原版。
 * `shuffle` 四段本就同向（shoelace 有向面积逐段核验：4 段同号）。
 */
private object SfGlyphPaths {

    const val SHUFFLE = 0
    const val REPEAT = 1
    const val REPEAT_ONE = 2

    private val SHUFFLE_PATHS = listOf(
        "M77.5 13.3Q77.5 12.0 78.2 11.3Q78.9 10.6 80.2 10.6Q80.9 10.6 81.4 10.8Q82.0 11.0 82.5 11.4L99.0 25.0Q100.0 25.9 100.0 27.1Q100.0 28.3 99.0 29.2L82.5 42.8Q82.0 43.2 81.4 43.4Q80.9 43.6 80.2 43.6Q78.9 43.6 78.2 42.9Q77.5 42.2 77.5 40.9Z",
        "M0.0 72.3Q0.0 70.7 1.1 69.7Q2.2 68.7 4.0 68.7L14.1 68.7Q17.4 68.7 20.0 67.3Q22.5 66.0 25.4 62.7L51.7 31.9Q55.6 27.3 59.3 25.5Q62.9 23.8 68.4 23.8L83.9 23.8Q85.4 23.8 86.4 24.8Q87.5 25.9 87.5 27.4Q87.5 28.9 86.4 30.0Q85.4 31.0 83.9 31.0L68.6 31.0Q66.2 31.0 64.3 31.6Q62.3 32.2 60.6 33.5Q58.8 34.8 56.9 37.0L30.5 67.8Q26.6 72.3 23.0 74.1Q19.3 75.9 13.9 75.9L4.0 75.9Q2.2 75.9 1.1 74.9Q0.0 73.9 0.0 72.3Z",
        "M77.5 86.7L77.5 59.1Q77.5 57.8 78.2 57.1Q78.9 56.4 80.2 56.4Q80.9 56.4 81.4 56.6Q82.0 56.8 82.5 57.2L99.0 70.8Q100.0 71.7 100.0 72.9Q100.0 74.1 99.0 75.0L82.5 88.6Q82.0 89.0 81.4 89.2Q80.9 89.4 80.2 89.4Q78.9 89.4 78.2 88.7Q77.5 88.0 77.5 86.7Z",
        "M0.0 27.7Q0.0 26.1 1.1 25.1Q2.2 24.1 4.0 24.1L13.9 24.1Q19.3 24.1 23.0 25.9Q26.6 27.7 30.5 32.2L56.9 63.0Q58.8 65.2 60.6 66.5Q62.3 67.8 64.3 68.4Q66.2 69.0 68.6 69.0L83.9 69.0Q85.4 69.0 86.4 70.0Q87.5 71.1 87.5 72.6Q87.5 74.1 86.4 75.2Q85.4 76.2 83.9 76.2L68.4 76.2Q62.9 76.2 59.3 74.5Q55.6 72.7 51.7 68.1L25.4 37.3Q22.5 34.0 20.0 32.7Q17.4 31.3 14.1 31.3L4.0 31.3Q2.2 31.3 1.1 30.3Q0.0 29.3 0.0 27.7Z",
    )

    private val REPEAT_PATHS = listOf(
        "M58.5 11.3Q58.5 9.8 59.3 9.1Q60.1 8.3 61.5 8.3Q62.1 8.3 62.8 8.5Q63.4 8.7 63.9 9.2L81.6 23.9Q82.8 24.9 82.8 26.1Q82.8 27.4 81.6 28.3L63.9 43.0Q63.4 43.4 62.8 43.7Q62.1 43.9 61.5 43.9Q60.1 43.9 59.3 43.1Q58.5 42.3 58.5 40.9Z",
        "M3.9 51.7Q2.3 51.7 1.1 50.5Q0.0 49.4 0.0 47.7L0.0 42.7Q0.0 36.3 2.8 31.7Q5.5 27.2 10.6 24.6Q15.7 22.1 22.7 22.1L65.4 22.1Q67.0 22.1 68.2 23.3Q69.3 24.4 69.3 26.0Q69.3 27.6 68.2 28.8Q67.0 29.9 65.4 29.9L21.9 29.9Q15.6 29.9 11.7 33.6Q7.9 37.2 7.9 43.2L7.9 47.7Q7.9 49.4 6.8 50.5Q5.6 51.7 3.9 51.7Z",
        "M41.5 88.7Q41.5 90.1 40.7 90.9Q40.0 91.7 38.5 91.7Q37.9 91.7 37.2 91.4Q36.6 91.2 36.1 90.8L18.4 76.1Q17.2 75.1 17.2 73.9Q17.2 72.6 18.4 71.6L36.1 57.0Q36.6 56.5 37.2 56.3Q37.9 56.1 38.5 56.1Q40.0 56.1 40.7 56.9Q41.5 57.6 41.5 59.1Z",
        "M98.9 49.5Q97.8 48.3 100.0 52.3Q100.0 50.6 100.0 57.3Q100.0 57.3 97.2 68.2Q100.0 63.6 89.4 75.4Q94.5 72.8 77.3 77.9Q84.3 77.9 34.6 77.9Q34.6 77.9 31.8 76.7Q33.0 77.9 30.7 73.9Q30.7 75.6 31.8 71.2Q30.7 72.3 34.6 70.0Q33.0 70.0 78.1 70.0Q78.1 70.0 88.3 66.4Q84.4 70.0 92.1 56.8Q92.1 62.8 92.1 52.3Q92.1 52.3 93.3 49.5Q92.1 50.6 96.1 48.3Q94.4 48.3 96.1 48.3Z",
    )

    private val REPEAT_ONE_PATHS = listOf(
        "M48.3 12.0Q48.3 10.6 49.1 9.8Q49.9 9.1 51.3 9.1Q52.0 9.1 52.6 9.3Q53.2 9.5 53.8 9.9L71.4 24.6Q72.6 25.6 72.6 26.9Q72.5 28.1 71.4 29.1L53.8 43.7Q53.2 44.1 52.6 44.3Q52.0 44.6 51.3 44.6Q49.9 44.6 49.1 43.8Q48.3 43.0 48.3 41.6Z",
        "M3.9 52.3Q2.3 52.3 1.1 51.2Q0.0 50.0 0.0 48.4L0.0 43.4Q0.0 37.1 2.8 32.5Q5.5 27.9 10.6 25.4Q15.7 22.9 22.6 22.9L55.2 22.9Q56.8 22.9 58.0 24.0Q59.1 25.2 59.1 26.8Q59.1 28.4 58.0 29.5Q56.8 30.6 55.2 30.6L21.9 30.6Q15.5 30.6 11.7 34.3Q7.9 37.9 7.9 43.9L7.9 48.4Q7.9 50.0 6.8 51.2Q5.6 52.3 3.9 52.3Z",
        "M41.4 89.3Q41.4 90.7 40.6 91.5Q39.9 92.3 38.4 92.3Q37.8 92.3 37.1 92.0Q36.5 91.8 36.0 91.4L18.3 76.7Q17.2 75.8 17.2 74.5Q17.2 73.2 18.3 72.3L36.0 57.6Q36.5 57.2 37.1 57.0Q37.8 56.8 38.4 56.8Q39.9 56.8 40.6 57.5Q41.4 58.3 41.4 59.7Z",
        "M98.6 50.2Q97.5 49.0 99.8 53.0Q99.8 51.3 99.8 58.0Q99.8 58.0 97.0 68.9Q99.8 64.3 89.2 76.0Q94.2 73.5 77.1 78.5Q84.1 78.5 34.5 78.5Q34.5 78.5 31.8 77.3Q32.9 78.5 30.6 74.5Q30.6 76.2 31.8 71.8Q30.6 73.0 34.5 70.7Q32.9 70.7 77.9 70.7Q77.9 70.7 88.1 67.0Q84.2 70.7 91.9 57.4Q91.9 63.4 91.9 53.0Q91.9 53.0 93.0 50.2Q91.9 51.3 95.8 49.0Q94.2 49.0 95.8 49.0Z",
        "M95.9 38.9Q93.9 38.9 92.8 37.8Q91.7 36.7 91.7 34.7L91.7 15.0L91.2 15.0L86.2 19.0Q85.3 19.7 84.3 19.7Q83.1 19.7 82.3 19.0Q81.6 18.3 81.6 17.2Q81.6 16.5 81.8 15.9Q82.1 15.4 82.8 14.8L89.1 10.0Q90.6 8.9 91.8 8.3Q93.1 7.7 95.0 7.7Q97.2 7.7 98.6 9.1Q100.0 10.5 100.0 12.9L100.0 34.7Q100.0 36.7 98.9 37.8Q97.8 38.9 95.9 38.9Z",
    )

    fun paths(kind: Int): List<String> = when (kind) {
        SHUFFLE -> SHUFFLE_PATHS
        REPEAT_ONE -> REPEAT_ONE_PATHS
        else -> REPEAT_PATHS
    }
}

/**
 * 把 [SfGlyphPaths] 的 SVG path data 解析成**单个** Compose [Path]：多轮廓靠
 * `moveTo` 开新子路径，nonzero 填充即 SF 原版字形。
 * 只支持本数据出现的 M / L / Q / Z（全绝对坐标；无隐式重复、无相对指令）。
 * 坐标从 100×100 归一化盒缩放到目标 [w]×[h]。
 */
private fun buildSfGlyphPath(data: String, w: Float, h: Float): Path {
    val sx = w / 100f
    val sy = h / 100f
    var i = 0
    var cmd = ' '
    val n = data.length
    val path = Path()

    fun skipSep() {
        while (i < n && (data[i] == ' ' || data[i] == ',')) i++
    }

    fun nextFloat(): Float {
        skipSep()
        val start = i
        if (i < n && (data[i] == '-' || data[i] == '+')) i++
        while (i < n && (data[i].isDigit() || data[i] == '.')) i++
        return data.substring(start, i).toFloat()
    }

    while (i < n) {
        skipSep()
        if (i >= n) break
        if (data[i].isLetter()) {
            cmd = data[i]
            i++
            if (cmd == 'Z') {
                path.close()
                continue
            }
        }
        when (cmd) {
            'M' -> {
                val x = nextFloat()
                val y = nextFloat()
                path.moveTo(x * sx, y * sy)
            }
            'L' -> {
                val x = nextFloat()
                val y = nextFloat()
                path.lineTo(x * sx, y * sy)
            }
            'Q' -> {
                val cx = nextFloat()
                val cy = nextFloat()
                val x = nextFloat()
                val y = nextFloat()
                path.quadraticTo(cx * sx, cy * sy, x * sx, y * sy)
            }
        }
    }
    return path
}


/**
 * 播放模式字形（随机 / 循环 / 单曲循环）的**唯一几何来源**。
 *
 * 消费方有两处，共用同一份字形 ⇒ 永远「同款」：
 *   1. 播放页「列表按钮右上角徽标」`MeloXNowPlayingCoreControls.ScenePlaybackModeBadge`；
 *   2. [MeloXSymbolIcon] 渲染 [MeloXSymbol.Repeat] / [MeloXSymbol.RepeatOne] 时，
 *      —— 即队列面板顶部模式开关里的「列表循环 / 单曲循环」按钮。
 *
 * [size] = 归一化盒（max-span = size）边长；字形自带 SF 光学边距。
 */
@Composable
fun MeloXPlaybackModeGlyph(
    symbol: MeloXSymbol,
    size: Dp,
    modifier: Modifier = Modifier,
    color: Color,
) {
    val kind = when (symbol) {
        MeloXSymbol.Shuffle -> SfGlyphPaths.SHUFFLE
        MeloXSymbol.RepeatOne -> SfGlyphPaths.REPEAT_ONE
        else -> SfGlyphPaths.REPEAT
    }
    val density = LocalDensity.current
    val glyphPath = remember(kind, size, density.density, density.fontScale) {
        val px = with(density) { size.toPx() }
        buildSfGlyphPath(SfGlyphPaths.paths(kind).joinToString(separator = ""), px, px)
    }
    Canvas(modifier = modifier.size(size)) {
        drawPath(glyphPath, color)
    }
}

/**
 * 三个播放模式字形在 SF 字体里的 ink 宽度（字体单位 / upm）。矢量按「max-span = size」
 * 归一化，故 `iconSize` 要乘上该系数才能还原成与原字体字形**同光学尺寸**的矢量。
 */
private val MeloXPlaybackModeInkEm = mapOf(
    MeloXSymbol.Shuffle to 2298f / 2048f,
    MeloXSymbol.Repeat to 2134f / 2048f,
    MeloXSymbol.RepeatOne to 2139f / 2048f,
)

/**
 * [MeloXSymbolIcon] 里 [MeloXSymbol.Repeat] / [MeloXSymbol.RepeatOne] 的矢量兜底：
 * 用 [MeloXPlaybackModeGlyph] 渲染，尺寸按 SF 字体度量换算（**不跟随调用方 Box**，
 * 与 Mic / Plus 同一惯用法），任何 `iconSize` 下都与原字体字形同尺寸。
 *
 * ⚠ alpha 走图层级 [androidx.compose.ui.draw.alpha] 而非带 alpha 的 tint，避免多轮廓
 *   填充在重叠处二次混合出深斑（与 mic / plus / star / kebab 一致）。
 */
@Composable
private fun PlaybackModeVectorIcon(
    symbol: MeloXSymbol,
    modifier: Modifier,
    color: Color,
    iconSize: TextUnit,
    contentDescription: String?,
) {
    val density = LocalDensity.current
    val inkEm = MeloXPlaybackModeInkEm[symbol] ?: 1f
    val renderSize = remember(density, iconSize, inkEm) {
        with(density) { (iconSize.toPx() * inkEm).toDp() }
    }
    Box(
        modifier = modifier
            .alpha(color.alpha)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics(mergeDescendants = true) {
                        this.contentDescription = contentDescription
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        MeloXPlaybackModeGlyph(
            symbol = symbol,
            size = renderSize,
            color = color.copy(alpha = 1f),
        )
    }
}

/**
 * Apple Music-style search affordance: the magnifier and back arrow share one
 * 300ms path-like transition instead of abruptly replacing the glyph.
 */
@Composable
fun MeloXSearchBackMorphIcon(
    focused: Boolean,
    modifier: Modifier = Modifier,
    color: Color,
    contentDescription: String? = null,
) {
    AnimatedContent(
        targetState = focused,
        transitionSpec = {
            fadeIn(tween(MeloXMotion.IconEnterMillis)) togetherWith
                fadeOut(tween(MeloXMotion.IconExitMillis))
        },
        modifier = modifier,
        label = "search-back-sf-transition",
    ) { isFocused ->
        MeloXSymbolIcon(
            symbol = if (isFocused) MeloXSymbol.ChevronLeft else MeloXSymbol.Search,
            modifier = Modifier,
            color = color,
            iconSize = 24.sp,
            contentDescription = contentDescription,
        )
    }
}

/**
 * **SF 子集字体的进程级 Typeface 缓存**（按 weight，2026-10-14 卡顿修复，源自第三方
 * 0.6.4-jank-fix 包的 F1）。
 *
 * ### 为什么加
 * 原实现里每个 `SfGlyphIcon` 调用点各自
 * `remember(context, weight) { ResourcesCompat.getFont(...); Typeface.create(...) }`
 * —— `remember` 是**每调用点一份**的，一屏 20 个 SF 图标 = 首帧上执行 20 次
 * `getFont`（`TypefaceCompat.findFromCache` 查找 + 首次真实读资源）+ 20 次
 * `Typeface.create`（native 调用）。这些全部压在首帧主线程上，而页面推入转场
 * （`Theme.MeloX.Page` 原生 Activity 转场）恰好在等这一帧。
 *
 * 改后：进程内每个 weight 只做一次真实加载，其余全是 map 查找（微秒级）。
 * **渲染行为逐像素不变** —— 同一个字体文件、同一个 weight、Paint 参数原样。
 *
 * ⚠ `internal` 而非 `private`：播放页核心按钮（`SceneCupertinoGlyph` 的歌词镂空路径，
 * `MeloXNowPlayingCoreControls`）有同一份同步加载代码，同样要走本缓存。
 */
internal object MeloSfTypefaceCache {
    private val lock = Any()
    private val cache = HashMap<Int, android.graphics.Typeface>()

    fun obtain(context: android.content.Context, weight: Int): android.graphics.Typeface? {
        synchronized(lock) { cache[weight]?.let { return it } }
        // 在锁外加载（149KB 小字体，读取期间不阻塞其他 weight 的命中者）。
        val font = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.sf_pro_subset)
            ?: return null
        val typeface = android.graphics.Typeface.create(font, weight)
        synchronized(lock) { cache.putIfAbsent(weight, typeface) }
        return typeface
    }
}

/**
 * 「更多」横向三点 `…` 的手绘矢量兜底（与 mic / plus / star 同一条品牌矢量路径）。
 *
 * 动机：原本 `MoreVertical` 落到 [SfGlyphIcon] 渲染 `ellipsis`（0x100360）字形，
 *   而该字形在不同 Android / Skia 版本下对同一 glyph 的 `getTextBounds` 因
 *   hinting、`SUBPIXEL_TEXT_FLAG` 差 1~2px，导致省略号三点被算得参差错位
 *   （issue #76 截图里「三点错位」即此）。改走矢量，彻底脱离字体度量，跨设备一致。
 *
 * ⚠ **保持横向 `…` 形态，不改成竖向**：原版落到 `ellipsis`（0x100360）横向省略号，
 *   用户机型（vivo）上本就显示为横向三点、且视为「正常」；问题只在部分机型三点被
 *   算得参差，故矢量必须沿用横向排布，只把三点画整齐。
 *
 * 三个实心圆横向等距排列、垂直居中，尺寸随调用方 Box 自适应（不依赖任何字体度量）：
 *   - 圆点半径 = min(w,h) × 0.10
 *   - 左右两点到中心的偏移 = min(w,h) × 0.30 ⇒ 三点跨度约 0.80·min，上下留白约 0.10·min
 *
 * ⚠ alpha 走图层级 [androidx.compose.ui.draw.alpha] 而非带 alpha 的 tint（与
 *   mic / plus / star 一致）：单色 fill 不会被双重混合，但仍统一此惯用法。
 */
@Composable
private fun MoreVerticalVectorIcon(
    modifier: Modifier,
    color: Color,
    contentDescription: String?,
) {
    Canvas(
        modifier = modifier
            .alpha(color.alpha)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics(mergeDescendants = true) {
                        this.contentDescription = contentDescription
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val m = min(size.width, size.height)
        val dotR = m * 0.10f
        val gap = m * 0.30f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.copy(alpha = 1f).toArgb()
        }
        drawIntoCanvas { canvas ->
            for (dx in listOf(-gap, 0f, gap)) {
                canvas.nativeCanvas.drawCircle(cx + dx, cy, dotR, paint)
            }
        }
    }
}

@Composable
private fun SfGlyphIcon(
    codePoint: Int,
    modifier: Modifier,
    color: Color,
    iconSize: TextUnit,
    contentDescription: String?,
    weight: FontWeight,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val typeface = androidx.compose.runtime.remember(context, weight) {
        MeloSfTypefaceCache.obtain(context, weight.weight)
    }
    // 保持原实现的 fail-fast：字体加载失败在组合期抛，而不是拖到绘制期 NPE。
    val paintTypeface = typeface ?: error("SF Symbols font could not be loaded")
    androidx.compose.foundation.Canvas(
        modifier = modifier,
    ) {
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.SUBPIXEL_TEXT_FLAG).apply {
                this.color = color.toArgb()
                this.typeface = paintTypeface
                textSize = with(density) { iconSize.toPx() }
                textAlign = android.graphics.Paint.Align.LEFT
                fontFeatureSettings = "'ss16' 1"
            }
            val glyph = String(Character.toChars(codePoint))
            val bounds = android.graphics.Rect()
            paint.getTextBounds(glyph, 0, glyph.length, bounds)
            val x = size.width / 2f - (bounds.left + bounds.right) / 2f
            val baseline = size.height / 2f - (bounds.top + bounds.bottom) / 2f
            canvas.nativeCanvas.drawText(glyph, x, baseline, paint)
        }
    }
}

@Composable
fun MeloXActionIcon(
    token: String,
    modifier: Modifier = Modifier,
    color: Color,
    enabled: Boolean = true,
    contentDescription: String? = null,
    // ★ SF 字形是按字号渲染的，`Modifier.size(...)` 只是占位框、不裁剪溢出墨迹。
    //   默认 24.sp 对「墨迹近乎满 em」的字形（Share / Plus / 方框类）会溢出到
    //   ~26dp 高 —— 放在 30dp 圆底按钮里就会把圆底顶满。需要贴合框时显式调小。
    iconSize: TextUnit = 24.sp,
) {
    val symbol = when (token) {
        "◷" -> MeloXSymbol.Clock
        "+", "＋" -> MeloXSymbol.Plus
        "↓×" -> MeloXSymbol.Download
        "↗" -> MeloXSymbol.Share
        "✉" -> MeloXSymbol.Mail
        "◎", "◌" -> MeloXSymbol.Message
        "i", "#" -> MeloXSymbol.Info
        "♡", "♥" -> MeloXSymbol.Heart
        "▣", "⇥", "♬" -> MeloXSymbol.List
        "✓" -> MeloXSymbol.Check
        "○" -> MeloXSymbol.Circle
        "♫", "♬" -> MeloXSymbol.MusicNote
        "✦" -> MeloXSymbol.Sparkles
        "❞" -> MeloXSymbol.Quote
        "▣" -> MeloXSymbol.Devices
        "▱" -> MeloXSymbol.Landscape
        "▤" -> MeloXSymbol.PictureInPicture
        "☷", "▦", "▥" -> MeloXSymbol.Apps
        "⌁" -> MeloXSymbol.Microphone
        "▰" -> MeloXSymbol.Storage
        "⚙" -> MeloXSymbol.Settings
        "ⓘ" -> MeloXSymbol.Info
        "⌘" -> MeloXSymbol.Bug
        "↑" -> MeloXSymbol.ArrowUp
        "↓" -> MeloXSymbol.Download
        "‹" -> MeloXSymbol.ChevronLeft
        "›" -> MeloXSymbol.ChevronRight
        "•••", "…" -> MeloXSymbol.Ellipsis
        "×" -> MeloXSymbol.Xmark
        "♪" -> MeloXSymbol.MusicNote
        "↻" -> MeloXSymbol.Refresh
        else -> MeloXSymbol.Unknown
    }
    val semanticLabel = contentDescription ?: when (symbol) {
        MeloXSymbol.Clock -> "历史记录"
        MeloXSymbol.Plus -> "添加"
        MeloXSymbol.Download -> "下载"
        MeloXSymbol.Share -> "分享"
        MeloXSymbol.Mail -> "私信"
        MeloXSymbol.Message -> "消息"
        MeloXSymbol.Info -> "信息"
        MeloXSymbol.Heart -> "收藏"
        MeloXSymbol.Star -> "收藏"
        MeloXSymbol.List -> "列表"
        MeloXSymbol.Check -> "完成"
        MeloXSymbol.MusicNote -> "音乐"
        MeloXSymbol.Sparkles -> "智能功能"
        MeloXSymbol.Quote -> "引用"
        MeloXSymbol.Devices -> "设备"
        MeloXSymbol.Landscape -> "横屏"
        MeloXSymbol.PictureInPicture -> "画中画"
        MeloXSymbol.Apps -> "页面布局"
        MeloXSymbol.Microphone -> "听歌识曲"
        MeloXSymbol.Storage -> "存储"
        MeloXSymbol.Settings -> "设置"
        MeloXSymbol.Bug -> "诊断"
        MeloXSymbol.Play -> "播放"
        MeloXSymbol.Pause -> "暂停"
        MeloXSymbol.Previous -> "上一首"
        MeloXSymbol.Next -> "下一首"
        MeloXSymbol.Shuffle -> "随机播放"
        MeloXSymbol.Repeat -> "重复播放"
        MeloXSymbol.Volume -> "音量"
        MeloXSymbol.Queue -> "播放队列"
        MeloXSymbol.MoreVertical -> "更多操作"
        MeloXSymbol.Circle -> "未选择"
        MeloXSymbol.CheckCircle -> "已选择"
        MeloXSymbol.ArrowUp -> "上移"
        MeloXSymbol.ArrowDown -> "下移"
        MeloXSymbol.ChevronLeft -> "返回"
        MeloXSymbol.ChevronRight -> "打开"
        MeloXSymbol.ChevronUpDown -> "选择选项"
        MeloXSymbol.Ellipsis -> "更多操作"
        MeloXSymbol.Xmark -> "关闭"
        MeloXSymbol.Refresh -> "刷新"
        MeloXSymbol.Unknown -> "未知操作图标 $token"
        else -> symbol.sfSymbolName
    }
    MeloXSymbolIcon(
        symbol = symbol,
        modifier = modifier,
        color = color.copy(alpha = if (enabled) color.alpha else color.alpha * 0.38f),
        variant = if (token == "♥") MeloXSymbolVariant.Fill else MeloXSymbolVariant.Regular,
        iconSize = iconSize,
        contentDescription = semanticLabel,
    )
}
