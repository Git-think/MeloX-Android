// PV Tool — Copyright (c) 2026 DanteAlighieri13210914
// Template data ported to Kotlin under the PV Tool Non-Commercial License.
package com.lladlam.melox.ui.player

import androidx.compose.ui.graphics.Color
import com.lladlam.melox.ui.settings.MeloXTextPVStyle

internal enum class TextPVLayer { Background, Decoration, Text, Overlay }

internal enum class TextPVEffectKind {
    BackgroundBlocks, BalancingCircles, BigOutlineText, BloodSplatter, BurstLines,
    CenteredSquares, Checkerboard, ChromaticAberration, ColorMask, CompositionGuides,
    ConcentricCircles, CrayonShatter, CrimeTape, CuteOutlineText, DataMonitors,
    DesktopIcon, DiagonalHatch, DiagonalSplit, DiagonalStructure, DotScreen,
    EdgeClouds, FallingText, FilmGrain, FlowingLines, FormulaText, GlitchBars,
    GlowTextCards, GradientOverlay, HalftoneBlocks, HeroText, HudCorners,
    HudInfoPanel, HudStatusText, LayeredText, LightSpot, MotionBrackets,
    NoiseText, PerspectiveGrid, PinkGrid, PinkStripes, PixelTypewriter,
    PixelWindow, Planet, PulsingCircle, RadialRectangles, ScalloppedBorder,
    Scanlines, ScatteredShapes, ScatteredText, ScreenBorder, ShadowShapes,
    StaggeredText, TextureBackground, TriangleGrid, VerticalSubText,
    VictimOutline, Vignette, WaveText, WebLines,
}

internal data class TextPVEffect(
    val kind: TextPVEffectKind,
    val layer: TextPVLayer,
    val config: Map<String, Any> = emptyMap(),
)

internal data class TextPVPalette(
    val background: String,
    val primary: String,
    val secondary: String,
    val accent: String,
    val text: String,
) {
    val backgroundColor: Color get() = parseColor(background)
    private val lineColor: Color
        get() {
            val color = backgroundColor
            val luminance = color.red * .299f + color.green * .587f + color.blue * .114f
            return if (luminance > .55f) Color(0xFF999999) else Color.White
        }

    fun resolve(token: String, fallback: String = "#000000"): Color = when (token) {
        "\$background" -> backgroundColor
        "\$primary" -> parseColor(primary)
        "\$secondary" -> parseColor(secondary)
        "\$accent" -> parseColor(accent)
        "\$text" -> parseColor(text)
        "\$line" -> lineColor
        else -> parseColor(if (token.startsWith("#")) token else fallback)
    }
}

internal data class TextPVPostFX(
    val shake: Float = 0f,
    val zoom: Float = 0f,
    val tilt: Float = 0f,
    val glitch: Float = 0f,
    val hueShift: Float = 0f,
)

internal data class TextPVTemplate(
    val style: MeloXTextPVStyle,
    val palette: TextPVPalette,
    val effects: List<TextPVEffect>,
    val bpm: Float = 120f,
    val animationSpeed: Float? = null,
    val backgroundOpacity: Float = 1f,
    val postFX: TextPVPostFX = TextPVPostFX(),
)

internal fun parseColor(value: String): Color {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6) return Color.Black
    val number = hex.toLongOrNull(16) ?: return Color.Black
    return Color(
        red = ((number shr 16) and 0xFF) / 255f,
        green = ((number shr 8) and 0xFF) / 255f,
        blue = (number and 0xFF) / 255f,
    )
}

internal fun Map<String, Any>.number(key: String, fallback: Float): Float =
    (this[key] as? Number)?.toFloat() ?: fallback

internal fun Map<String, Any>.integer(key: String, fallback: Int): Int =
    (this[key] as? Number)?.toInt() ?: fallback

internal fun Map<String, Any>.text(key: String, fallback: String): String =
    this[key] as? String ?: fallback

internal fun Map<String, Any>.flag(key: String, fallback: Boolean): Boolean =
    this[key] as? Boolean ?: fallback

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any>.objects(key: String): List<Map<String, Any>> =
    (this[key] as? List<*>)?.mapNotNull { it as? Map<String, Any> }.orEmpty()

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any>.strings(key: String): List<String> =
    (this[key] as? List<*>)?.mapNotNull { it as? String }.orEmpty()

private fun cfg(vararg pairs: Pair<String, Any>): Map<String, Any> = mapOf(*pairs)

private fun effect(
    kind: TextPVEffectKind,
    layer: TextPVLayer,
    vararg pairs: Pair<String, Any>,
) = TextPVEffect(kind, layer, cfg(*pairs))

internal fun MeloXTextPVStyle.visualSignature(): String {
    val template = TextPVTemplateCatalog.template(this)
    return buildString {
        append(template.palette.background).append('|')
        append(template.palette.primary).append('|')
        append(template.palette.accent).append('|')
        append(template.bpm).append('|')
        template.effects.forEach { effect ->
            append(effect.kind.name).append(':').append(effect.layer.name).append(',')
        }
    }
}

internal object TextPVTemplateCatalog {
    fun template(style: MeloXTextPVStyle): TextPVTemplate = when (style) {
        MeloXTextPVStyle.BlueBold, MeloXTextPVStyle.Dynamic -> blueBold
        MeloXTextPVStyle.KineticSplit -> kineticSplit
        MeloXTextPVStyle.BluePlane -> bluePlane
        MeloXTextPVStyle.CyberGrunge, MeloXTextPVStyle.Cyber -> cyberGrunge
        MeloXTextPVStyle.Geometric -> geometric
        MeloXTextPVStyle.RainCity -> rainCity
        MeloXTextPVStyle.CyberpunkHUD -> cyberpunkHUD
        MeloXTextPVStyle.EmotionCinema -> emotionCinema
        MeloXTextPVStyle.HystericNight -> hystericNight
        MeloXTextPVStyle.SpiderWeb -> spiderWeb
        MeloXTextPVStyle.StaggeredText -> staggeredText
        MeloXTextPVStyle.CalmVillain, MeloXTextPVStyle.Minimal -> calmVillain
        MeloXTextPVStyle.GirlyClouds -> girlyClouds
        MeloXTextPVStyle.SweetPink -> sweetPink
        MeloXTextPVStyle.FlyMeToTheMoon -> flyMeToTheMoon
        MeloXTextPVStyle.KawaiiPixel -> kawaiiPixel
        MeloXTextPVStyle.CrimeScene -> crimeScene
        MeloXTextPVStyle.Haruhikage -> haruhikage
    }

    val blueBold = TextPVTemplate(
        style = MeloXTextPVStyle.BlueBold,
        palette = TextPVPalette("#1122ee", "#ffffff", "#e0e0e0", "#0022aa", "#e0e0e0"),
        effects = listOf(
            effect(
                TextPVEffectKind.BigOutlineText, TextPVLayer.Decoration,
                "color" to "#d8d8e0", "strokeColor" to "#ffffff",
                "staggerDelay" to .18f,
            ),
            effect(
                TextPVEffectKind.ShadowShapes, TextPVLayer.Text,
                "color" to "#ffffff", "shadowColor" to "#000055",
                "shadowAlpha" to .45f, "shadowOffX" to 14f, "shadowOffY" to 16f,
                "shapes" to listOf(
                    mapOf("type" to "square", "x" to .38f, "y" to .32f, "size" to .16f, "rotation" to -.08f),
                    mapOf("type" to "diamond", "x" to .55f, "y" to .58f, "size" to .15f, "rotation" to .785f),
                    mapOf("type" to "rect", "x" to .68f, "y" to .40f, "size" to .12f, "rotation" to .02f),
                    mapOf("type" to "square", "x" to .25f, "y" to .62f, "size" to .09f, "rotation" to .1f),
                ),
            ),
            effect(
                TextPVEffectKind.Vignette, TextPVLayer.Overlay,
                "color" to "#000022", "alpha" to .35f, "radius" to .7f,
            ),
        ),
    )

    val kineticSplit = TextPVTemplate(
        style = MeloXTextPVStyle.KineticSplit,
        palette = TextPVPalette("#f0ece8", "#1a1a1a", "#ffffff", "#8b1a1a", "#1a1a1a"),
        effects = listOf(
            effect(TextPVEffectKind.DiagonalHatch, TextPVLayer.Background, "spacing" to 7f, "lineWidth" to .6f, "color" to "\$accent", "alpha" to .1f),
            effect(TextPVEffectKind.DiagonalSplit, TextPVLayer.Decoration, "color" to "\$accent", "alpha" to 1f, "rotSpeed" to .25f, "baseHalfAngle" to .6f, "angleVariation" to .3f, "initRotation" to -1.5708f),
            effect(TextPVEffectKind.ScreenBorder, TextPVLayer.Decoration, "color" to "\$primary", "lineWidth" to 1.5f, "alpha" to .5f, "margin" to 18f, "gap" to 5f, "starSize" to 4f),
            effect(TextPVEffectKind.LayeredText, TextPVLayer.Text, "color" to "\$primary", "fontSize" to 90f, "maxLayers" to 4),
            effect(TextPVEffectKind.Vignette, TextPVLayer.Overlay, "intensity" to .3f),
        ),
    )

    val bluePlane = TextPVTemplate(
        style = MeloXTextPVStyle.BluePlane,
        palette = TextPVPalette("#f0ece6", "#0028B4", "#c8c8c8", "#3264E6", "#141414"),
        effects = listOf(
            effect(TextPVEffectKind.BackgroundBlocks, TextPVLayer.Background, "count" to 7, "alpha" to .5f),
            effect(TextPVEffectKind.ConcentricCircles, TextPVLayer.Decoration, "count" to 5, "maxRadius" to 500f, "x" to .5f, "y" to .5f, "color" to "\$secondary", "strokeWidth" to 1f, "alpha" to .4f),
            effect(TextPVEffectKind.DiagonalStructure, TextPVLayer.Decoration, "color" to "#f0f0f0", "alpha" to .3f, "step" to 100f),
            effect(TextPVEffectKind.BurstLines, TextPVLayer.Decoration, "color" to "#b4b4b4", "alpha" to .25f, "rayCount" to 8, "innerRadius" to .08f, "outerRadius" to .65f, "x" to .5f, "y" to .5f),
            effect(TextPVEffectKind.MotionBrackets, TextPVLayer.Overlay, "color" to "\$text", "alpha" to .7f, "lineWidth" to 1f, "showNodes" to true),
            effect(TextPVEffectKind.BalancingCircles, TextPVLayer.Decoration, "count" to 5, "blueColor" to "#0028B4", "whiteColor" to "#ffffff", "glowAlpha" to .4f),
            effect(TextPVEffectKind.FormulaText, TextPVLayer.Text, "color" to "\$text", "count" to 18, "formulaRatio" to .65f),
            effect(TextPVEffectKind.GlowTextCards, TextPVLayer.Text, "cardColor" to "#ffffff", "textColor" to "\$text", "fontSize" to 72f, "glowAlpha" to .5f, "charsPerRow" to 5, "x" to .5f, "y" to .45f),
        ),
    )

    val cyberGrunge = TextPVTemplate(
        style = MeloXTextPVStyle.CyberGrunge,
        palette = TextPVPalette("#000000", "#ffffff", "#888888", "#cccccc", "#ffffff"),
        effects = listOf(
            effect(TextPVEffectKind.DotScreen, TextPVLayer.Overlay, "spacing" to 6f, "dotRadius" to 2f, "color" to "#ffffff", "alpha" to .18f, "angle" to 22f),
            effect(TextPVEffectKind.Scanlines, TextPVLayer.Overlay, "color" to "#000000", "alpha" to .2f, "spacing" to 3f),
            effect(TextPVEffectKind.FilmGrain, TextPVLayer.Overlay, "alpha" to .14f, "mono" to true),
            effect(TextPVEffectKind.DataMonitors, TextPVLayer.Decoration, "count" to 4, "borderColor" to "#ffffff", "dataColor" to "#ffffff", "alpha" to .65f),
            effect(TextPVEffectKind.NoiseText, TextPVLayer.Decoration, "count" to 10, "color" to "#ffffff"),
            effect(TextPVEffectKind.DiagonalHatch, TextPVLayer.Decoration, "color" to "#ffffff", "alpha" to .06f, "spacing" to 12f),
            effect(TextPVEffectKind.ScatteredShapes, TextPVLayer.Decoration, "color" to "#ffffff", "alpha" to .08f),
            effect(TextPVEffectKind.GlitchBars, TextPVLayer.Overlay, "color" to "#ffffff", "alpha" to .4f),
            effect(TextPVEffectKind.GlowTextCards, TextPVLayer.Text, "cardColor" to "#ffffff", "textColor" to "#000000", "fontSize" to 64f, "glowAlpha" to .5f, "charsPerRow" to 5),
            effect(TextPVEffectKind.Vignette, TextPVLayer.Overlay, "color" to "#000000", "alpha" to .7f),
        ),
    )

    val geometric = TextPVTemplate(
        style = MeloXTextPVStyle.Geometric,
        palette = TextPVPalette("#ffee00", "#1a1a1a", "#ffffff", "#888888", "#ffffff"),
        effects = listOf(
            effect(TextPVEffectKind.DiagonalHatch, TextPVLayer.Background, "spacing" to 8f, "lineWidth" to .7f, "color" to "\$accent", "alpha" to .14f),
            effect(TextPVEffectKind.ScreenBorder, TextPVLayer.Decoration, "color" to "\$primary", "lineWidth" to 1.5f, "alpha" to .6f, "margin" to 22f, "gap" to 6f, "starSize" to 5f),
            effect(TextPVEffectKind.CenteredSquares, TextPVLayer.Decoration, "outerSize" to 320f, "midSize" to 240f, "innerSize" to 170f, "borderColor" to "\$primary", "innerColor" to "\$secondary"),
            effect(TextPVEffectKind.WaveText, TextPVLayer.Text, "color" to "#ffffff", "fontSize" to 52f, "charSpreadFrac" to .5f, "staggerY" to 18f),
            effect(TextPVEffectKind.Vignette, TextPVLayer.Overlay, "intensity" to .35f),
        ),
    )

    val rainCity = TextPVTemplate(
        style = MeloXTextPVStyle.RainCity,
        palette = TextPVPalette("#000000", "#003b00", "#005500", "#00ff41", "#00ff41"),
        effects = listOf(
            effect(TextPVEffectKind.GradientOverlay, TextPVLayer.Background, "colorTop" to "#003838", "colorMid" to "#004848", "colorBottom" to "#001020", "alpha" to .5f),
            effect(TextPVEffectKind.FallingText, TextPVLayer.Decoration, "color" to "\$accent", "count" to 24, "minSize" to 24f, "maxSize" to 62f),
            effect(TextPVEffectKind.ChromaticAberration, TextPVLayer.Overlay, "offset" to 4f),
            effect(TextPVEffectKind.Vignette, TextPVLayer.Overlay, "color" to "#000000", "alpha" to .7f, "radius" to .6f),
        ),
    )

    val cyberpunkHUD = TextPVTemplate(
        style = MeloXTextPVStyle.CyberpunkHUD,
        palette = TextPVPalette("#0a0a0a", "#FFFF00", "#323200", "#FF0000", "#FFFFFF"),
        effects = listOf(
            effect(TextPVEffectKind.TriangleGrid, TextPVLayer.Background, "color" to "\$secondary", "alpha" to .3f, "cols" to 9),
            effect(TextPVEffectKind.Scanlines, TextPVLayer.Overlay, "color" to "#001414", "alpha" to .15f, "spacing" to 4f),
            effect(TextPVEffectKind.HudCorners, TextPVLayer.Decoration, "color" to "\$primary", "alpha" to .9f, "margin" to 20f, "armLength" to 40f, "lineWidth" to 2f),
            effect(TextPVEffectKind.HudStatusText, TextPVLayer.Text, "textColor" to "\$text", "alertColor" to "\$accent", "centerText" to "SYSTEM ONLINE", "rightText" to "NETWATCH // 07", "fontSize" to 13f),
            effect(TextPVEffectKind.MotionBrackets, TextPVLayer.Overlay, "color" to "\$accent", "alpha" to .85f, "lineWidth" to 2f),
            effect(TextPVEffectKind.HudInfoPanel, TextPVLayer.Overlay, "primaryColor" to "\$primary", "alertColor" to "\$accent", "textColor" to "\$text", "panelWidth" to 240f, "panelHeight" to 420f),
            effect(TextPVEffectKind.HeroText, TextPVLayer.Text, "fontSize" to 36f, "x" to .5f, "y" to .92f, "color" to "\$primary", "animation" to "breathe", "animationSpeed" to .5f, "animationAmount" to .03f),
        ),
        bpm = 140f,
    )

    val emotionCinema = TextPVTemplate(
        style = MeloXTextPVStyle.EmotionCinema,
        palette = TextPVPalette("#0d1018", "#c8c8d0", "#4455aa", "#7788cc", "#e0e0e8"),
        effects = listOf(
            effect(TextPVEffectKind.GradientOverlay, TextPVLayer.Background, "colorTop" to "#141828", "colorBottom" to "#080c14", "alpha" to .7f),
            effect(TextPVEffectKind.FlowingLines, TextPVLayer.Decoration, "count" to 4, "color" to "\$secondary", "alpha" to .15f, "strokeWidth" to .5f, "amplitude" to 80f, "speed" to .08f),
            effect(TextPVEffectKind.ScatteredText, TextPVLayer.Text, "count" to 6, "color" to "\$secondary", "minSize" to 12f, "maxSize" to 20f, "chars" to "SCENE MEMORY LIGHT"),
            effect(TextPVEffectKind.MotionBrackets, TextPVLayer.Overlay, "color" to "\$primary", "alpha" to .5f, "lineWidth" to 1f),
            effect(TextPVEffectKind.HeroText, TextPVLayer.Text, "fontSize" to 60f, "x" to .5f, "y" to .5f, "color" to "\$text", "animation" to "breathe", "animationSpeed" to .15f, "animationAmount" to .02f),
            effect(TextPVEffectKind.Vignette, TextPVLayer.Overlay, "color" to "#000000", "alpha" to .8f),
            effect(TextPVEffectKind.ColorMask, TextPVLayer.Overlay, "color" to "#1a2040", "alpha" to .15f),
        ),
    )

    val hystericNight = TextPVTemplate(
        style = MeloXTextPVStyle.HystericNight,
        palette = TextPVPalette("#ffffff", "#1133aa", "#2244cc", "#cccc00", "#1a1a1a"),
        effects = listOf(
            effect(TextPVEffectKind.RadialRectangles, TextPVLayer.Decoration, "count" to 14, "baseColor" to "#1133aa", "edgeColor" to "#cccc00", "x" to .47f, "y" to .48f, "rotSpeed" to .08f),
            effect(TextPVEffectKind.GradientOverlay, TextPVLayer.Overlay, "mode" to "radial", "colorTop" to "#0a1535", "colorBottom" to "#000000", "alpha" to .55f),
            effect(TextPVEffectKind.GlowTextCards, TextPVLayer.Text, "cardColor" to "#ffffff", "textColor" to "#1a1a1a", "glowColor" to "#ffffff", "glowAlpha" to .6f, "fontSize" to 68f, "charsPerRow" to 5, "x" to .47f, "y" to .48f),
            effect(TextPVEffectKind.VerticalSubText, TextPVLayer.Text, "color" to "#ffffff", "fontSize" to 13f, "x" to .65f, "y" to .33f),
            effect(TextPVEffectKind.Vignette, TextPVLayer.Overlay, "color" to "#000000", "alpha" to .45f),
            effect(TextPVEffectKind.ScatteredShapes, TextPVLayer.Decoration, "count" to 30, "color" to "#4466cc", "shape" to "circle", "minSize" to 1f, "maxSize" to 3f, "alpha" to .3f),
        ),
        bpm = 130f,
    )

    val spiderWeb = TextPVTemplate(
        style = MeloXTextPVStyle.SpiderWeb,
        palette = TextPVPalette("#000000", "#ff2222", "#cc0000", "#ff4444", "#ffffff"),
        effects = listOf(
            effect(TextPVEffectKind.Scanlines, TextPVLayer.Overlay, "color" to "#cc0000", "alpha" to .18f, "spacing" to 3f),
            effect(TextPVEffectKind.DotScreen, TextPVLayer.Overlay, "spacing" to 7f, "dotRadius" to 1.8f, "color" to "#ff2222", "alpha" to .1f, "angle" to 30f),
            effect(TextPVEffectKind.WebLines, TextPVLayer.Decoration, "count" to 24, "color" to "#ff2222", "focalX" to .5f, "focalY" to .45f, "spread" to .2f),
            effect(TextPVEffectKind.WebLines, TextPVLayer.Decoration, "count" to 8, "color" to "#cc0000", "focalX" to .5f, "focalY" to .5f, "spread" to .6f),
            effect(TextPVEffectKind.ChromaticAberration, TextPVLayer.Overlay, "amount" to 4f),
            effect(TextPVEffectKind.GlitchBars, TextPVLayer.Overlay, "color" to "#ff1111", "alpha" to .35f),
            effect(TextPVEffectKind.HeroText, TextPVLayer.Text, "color" to "#ffffff", "fontSize" to 52f, "strokeColor" to "#000000", "strokeWidth" to 2f, "letterSpacing" to 8f),
            effect(TextPVEffectKind.Vignette, TextPVLayer.Overlay, "color" to "#000000", "alpha" to .5f),
        ),
    )

    val staggeredText = TextPVTemplate(
        style = MeloXTextPVStyle.StaggeredText,
        palette = TextPVPalette("#1a2a6c", "#ffffff", "#8899cc", "#4466dd", "#ffffff"),
        effects = listOf(
            effect(TextPVEffectKind.StaggeredText, TextPVLayer.Text, "color" to "#ffffff", "fontSize" to 68f, "modeDuration" to 3.5f, "transition" to .5f, "colChars" to 5),
            effect(TextPVEffectKind.ChromaticAberration, TextPVLayer.Overlay, "amount" to 3f),
            effect(TextPVEffectKind.Vignette, TextPVLayer.Overlay, "color" to "#0a1440", "alpha" to .4f),
        ),
        animationSpeed = 3.4f,
    )

    val calmVillain = TextPVTemplate(
        style = MeloXTextPVStyle.CalmVillain,
        palette = TextPVPalette("#f5c6d0", "#2255cc", "#1a3388", "#3377ff", "#1a2266"),
        effects = listOf(
            effect(TextPVEffectKind.TextureBackground, TextPVLayer.Background, "pattern" to "dots", "color" to "\$secondary", "alpha" to .6f),
            effect(TextPVEffectKind.Checkerboard, TextPVLayer.Background, "cellSize" to 64f, "color1" to "#f0b8c4", "color2" to "#e8a0b0", "alpha" to .08f),
            effect(TextPVEffectKind.PerspectiveGrid, TextPVLayer.Decoration, "color" to "\$line", "alpha" to .5f, "lineWidth" to 1.5f, "mode" to "floor", "scrollSpeed" to .25f),
            effect(TextPVEffectKind.BurstLines, TextPVLayer.Decoration, "color" to "\$line", "alpha" to .35f, "rayCount" to 20, "lineWidth" to 2.5f),
            effect(TextPVEffectKind.DiagonalHatch, TextPVLayer.Decoration, "color" to "\$line", "alpha" to .28f, "spacing" to 18f, "lineWidth" to 1.2f),
            effect(TextPVEffectKind.CompositionGuides, TextPVLayer.Decoration, "color" to "\$line", "alpha" to .35f),
            effect(TextPVEffectKind.ScreenBorder, TextPVLayer.Decoration, "color" to "\$line", "lineWidth" to 2f, "alpha" to .75f, "margin" to 20f, "gap" to 6f, "starSize" to 4f),
            effect(TextPVEffectKind.FormulaText, TextPVLayer.Text, "color" to "\$line", "count" to 14, "alpha" to .4f),
            effect(TextPVEffectKind.GlowTextCards, TextPVLayer.Text, "cardColor" to "#ffffff", "textColor" to "\$text", "glowColor" to "\$accent", "glowAlpha" to .6f, "fontSize" to 68f, "charsPerRow" to 5, "x" to .47f, "y" to .48f),
            effect(TextPVEffectKind.VerticalSubText, TextPVLayer.Text, "color" to "\$line", "fontSize" to 12f, "x" to .7f, "y" to .3f),
            effect(TextPVEffectKind.LightSpot, TextPVLayer.Overlay, "color" to "#ffffff", "x" to .5f, "y" to .08f, "alpha" to .5f, "radius" to .4f),
            effect(TextPVEffectKind.DotScreen, TextPVLayer.Overlay, "spacing" to 10f, "dotRadius" to 1f, "color" to "\$line", "alpha" to .07f, "angle" to 20f),
            effect(TextPVEffectKind.Scanlines, TextPVLayer.Overlay, "color" to "#002244", "alpha" to .18f, "spacing" to 4f),
            effect(TextPVEffectKind.GlitchBars, TextPVLayer.Overlay, "color" to "\$accent", "alpha" to .28f),
            effect(TextPVEffectKind.Vignette, TextPVLayer.Overlay, "color" to "#d898a8", "alpha" to .5f),
        ),
    )

    val girlyClouds = TextPVTemplate(
        style = MeloXTextPVStyle.GirlyClouds,
        palette = TextPVPalette("#fef5f8", "#fbbdbe", "#f8d7da", "#ff9eb5", "#5a3a3f"),
        effects = listOf(
            effect(TextPVEffectKind.PinkStripes, TextPVLayer.Background, "pinkColor" to "#fbbdbe", "stripeWidth" to 150f, "speed" to .3f, "angle" to -45f),
            effect(TextPVEffectKind.EdgeClouds, TextPVLayer.Decoration, "color" to "#ffffff", "cloudCount" to 5, "baseRadius" to 100f),
            effect(TextPVEffectKind.HeroText, TextPVLayer.Text, "color" to "#5a3a3f", "fontSize" to 72f),
        ),
        animationSpeed = 1.5f,
    )

    val sweetPink = TextPVTemplate(
        style = MeloXTextPVStyle.SweetPink,
        palette = TextPVPalette("#fef5f8", "#fab2b5", "#f8c7ca", "#ecbfc0", "#fab2b5"),
        effects = listOf(
            effect(TextPVEffectKind.PinkGrid, TextPVLayer.Background, "color" to "#f8c7ca", "cellSize" to 50f, "lineColor" to "#ffffff", "lineWidth" to 2f),
            effect(TextPVEffectKind.PulsingCircle, TextPVLayer.Background, "strokeColor" to "#ffffff", "strokeWidth" to 8f, "outerStrokeColor" to "#ecbfc0", "radius" to 250f, "x" to .5f, "y" to .5f),
            effect(TextPVEffectKind.ScalloppedBorder, TextPVLayer.Decoration, "color" to "#ffffff", "shadowColor" to "#ecbfc0", "circleRadius" to 80f),
            effect(TextPVEffectKind.CuteOutlineText, TextPVLayer.Text, "fillColor" to "#fab2b5", "strokeColor" to "#ffffff", "fontSize" to 80f, "strokeWidth" to 8f, "letterSpacing" to 4f),
        ),
    )

    val flyMeToTheMoon = TextPVTemplate(
        style = MeloXTextPVStyle.FlyMeToTheMoon,
        palette = TextPVPalette("#1122ee", "#c0c0d0", "#888899", "#6a6a8a", "#e0e0e0"),
        effects = listOf(
            effect(TextPVEffectKind.TextureBackground, TextPVLayer.Background),
            effect(TextPVEffectKind.GradientOverlay, TextPVLayer.Background, "colorTop" to "#0a0a18", "colorBottom" to "#000008", "alpha" to .55f, "mode" to "radial"),
            effect(TextPVEffectKind.ScatteredShapes, TextPVLayer.Decoration, "color" to "\$primary"),
            effect(TextPVEffectKind.Planet, TextPVLayer.Decoration, "color" to "#ffffff", "radius" to 120f, "coreRadius" to 12f),
            effect(TextPVEffectKind.VerticalSubText, TextPVLayer.Text, "color" to "#ffffff", "fontSize" to 13f),
            effect(TextPVEffectKind.Vignette, TextPVLayer.Overlay, "color" to "#000010", "alpha" to .35f, "radius" to .7f),
            effect(TextPVEffectKind.DotScreen, TextPVLayer.Overlay),
        ),
        animationSpeed = 3.7f,
        postFX = TextPVPostFX(zoom = .65f, tilt = -.52f),
    )

    val kawaiiPixel = TextPVTemplate(
        style = MeloXTextPVStyle.KawaiiPixel,
        palette = TextPVPalette("#fef0f5", "#ffb3d9", "#b3e5fc", "#c8f7dc", "#5a3a5a"),
        effects = listOf(
            effect(TextPVEffectKind.DotScreen, TextPVLayer.Background, "color" to "#ffb3d9", "dotRadius" to 4f, "spacing" to 12f, "alpha" to .15f),
            effect(TextPVEffectKind.Checkerboard, TextPVLayer.Background, "color1" to "#ffffff", "color2" to "#fef5f8", "cellSize" to 40f, "alpha" to .3f),
            effect(TextPVEffectKind.DesktopIcon, TextPVLayer.Decoration, "x" to 30f, "y" to 30f, "size" to 64f, "iconType" to "paint", "label" to "Paint", "labelColor" to "#5a3a5a"),
            effect(TextPVEffectKind.DesktopIcon, TextPVLayer.Decoration, "x" to 30f, "y" to 120f, "size" to 64f, "iconType" to "notes", "label" to "Notes", "labelColor" to "#5a3a5a"),
            effect(TextPVEffectKind.PixelWindow, TextPVLayer.Decoration, "x" to .22f, "y" to .18f, "width" to 320f, "height" to 260f, "bgColor" to "#ffffff", "borderColor" to "#ffb3d9", "borderWidth" to 4f, "titleBgColor" to "#ffb3d9", "titleColor" to "#ffffff", "title" to "PIXEL PAINT", "titleBarHeight" to 28f),
            effect(TextPVEffectKind.PixelWindow, TextPVLayer.Decoration, "x" to .75f, "y" to .15f, "width" to 280f, "height" to 180f, "bgColor" to "#f0f8ff", "borderColor" to "#b3e5fc", "borderWidth" to 4f, "titleBgColor" to "#b3e5fc", "titleColor" to "#ffffff", "title" to "WELCOME", "titleBarHeight" to 28f),
            effect(TextPVEffectKind.PixelWindow, TextPVLayer.Decoration, "x" to .82f, "y" to .78f, "width" to 300f, "height" to 200f, "bgColor" to "#fff5f8", "borderColor" to "#ffc0e0", "borderWidth" to 4f, "titleBgColor" to "#ffc0e0", "titleColor" to "#ffffff", "title" to "MESSAGES", "titleBarHeight" to 28f),
            effect(TextPVEffectKind.ScatteredShapes, TextPVLayer.Decoration, "shape" to "circle", "color" to "#ffb3d9", "count" to 6, "minSize" to 12f, "maxSize" to 20f, "alpha" to .5f),
            effect(TextPVEffectKind.PixelTypewriter, TextPVLayer.Text, "fillColor" to "#5a3a5a", "strokeColor" to "#ffffff", "fontSize" to 44f, "strokeWidth" to 6f, "leftX" to .2f, "rightX" to .6f, "y" to .5f, "maxCharsPerSide" to 5, "cursorColor" to "#ffb3d9"),
        ),
    )

    val crimeScene = TextPVTemplate(
        style = MeloXTextPVStyle.CrimeScene,
        palette = TextPVPalette("#0a0a0a", "#cccccc", "#666666", "#8b0000", "#ffffff"),
        effects = listOf(
            effect(TextPVEffectKind.VictimOutline, TextPVLayer.Background, "color" to "#ffffff", "alpha" to .88f, "scale" to 1.5f, "lineWidth" to 5f),
            effect(TextPVEffectKind.BloodSplatter, TextPVLayer.Background, "count" to 5, "color" to "#8b0000", "alpha" to .85f),
            effect(TextPVEffectKind.TextureBackground, TextPVLayer.Background, "pattern" to "dots"),
            effect(TextPVEffectKind.StaggeredText, TextPVLayer.Text, "color" to "#ffffff", "fontSize" to 64f, "modeDuration" to 3.5f),
            effect(TextPVEffectKind.CrimeTape, TextPVLayer.Decoration, "count" to 6, "tapeColor" to "#f5c800", "textColor" to "#000000", "tapeWidth" to 52f, "text" to "CRIME SCENE // DO NOT CROSS", "speed" to 70f),
            effect(TextPVEffectKind.Vignette, TextPVLayer.Overlay, "color" to "#000000", "alpha" to .72f, "radius" to .65f),
        ),
        animationSpeed = 2.5f,
    )

    val haruhikage = TextPVTemplate(
        style = MeloXTextPVStyle.Haruhikage,
        palette = TextPVPalette("#ABC5D2", "#5a3a5a", "#7faecc", "#d97a7a", "#5a3a5a"),
        effects = listOf(
            effect(TextPVEffectKind.BackgroundBlocks, TextPVLayer.Background, "count" to 7, "alpha" to .35f),
            effect(TextPVEffectKind.HalftoneBlocks, TextPVLayer.Background, "color" to "\$primary", "alpha" to .12f, "size" to 3f, "spacing" to 6f),
            effect(TextPVEffectKind.Scanlines, TextPVLayer.Overlay, "alpha" to .08f, "spacing" to 3f),
            effect(
                TextPVEffectKind.CrayonShatter, TextPVLayer.Text,
                "fontSize" to 115f, "charSpacingFrac" to 1.05f, "colSpacingFrac" to 1.4f,
                "colRowPhase" to .45f, "layoutJitter" to .06f, "baseColor" to "#5a3a5a",
                "colors" to listOf("#d97a7a", "#e0b96a", "#83b07c", "#7faecc", "#c89bba", "#d49d6f", "#9c89c4", "#7fb6c4"),
                "replaceProb" to .55f, "offsetProb" to .55f, "rotateProb" to .55f,
                "frameCount" to 4, "frameHoldSec" to .18f,
            ),
        ),
        bpm = 90f,
        animationSpeed = .8f,
    )
}
