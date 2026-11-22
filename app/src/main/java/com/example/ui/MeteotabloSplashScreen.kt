package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AuthorBrand
import com.example.ui.theme.SplashColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MeteotabloSplashScreen(
  onFinished: () -> Unit,
  modifier: Modifier = Modifier,
  durationMillis: Long = 1450L,
) {
  val logoScale = remember { Animatable(0.86f) }
  val progressAnim = remember { Animatable(0f) }

  val infiniteTransition = rememberInfiniteTransition(label = "splash_radar")
  val sweepAngle by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2600, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "radar_sweep",
  )

  LaunchedEffect(durationMillis) {
    launch {
      logoScale.animateTo(
        targetValue = 1f,
        animationSpec = spring(
          dampingRatio = Spring.DampingRatioMediumBouncy,
          stiffness = Spring.StiffnessMediumLow,
        ),
      )
    }
    launch {
      progressAnim.animateTo(
        targetValue = 1f,
        animationSpec = tween(
          durationMillis = durationMillis.coerceAtLeast(300L).toInt(),
          easing = FastOutSlowInEasing,
        ),
      )
    }
    delay(durationMillis)
    onFinished()
  }

  val tapHint = stringResource(R.string.splash_tap_to_continue)
  val interactionSource = remember { MutableInteractionSource() }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            SplashColors.backgroundTop,
            SplashColors.backgroundCenter,
            SplashColors.backgroundBottom,
          ),
        ),
      )
      .pointerInput(onFinished) {
        detectTapGestures(onTap = { onFinished() })
      }
      .testTag("splash_screen"),
  ) {
    // Subtle aviation radar range rings and crosshairs in background
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = Offset(size.width * 0.5f, size.height * 0.45f)
      val maxRadius = min(size.width, size.height) * 0.62f

      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            SplashColors.radarGlow.copy(alpha = 0.14f),
            SplashColors.windAmber.copy(alpha = 0.04f),
            Color.Transparent,
          ),
          center = center,
          radius = maxRadius,
        ),
        radius = maxRadius,
        center = center,
      )

      val ringFactors = listOf(0.32f, 0.54f, 0.78f, 1.0f)
      ringFactors.forEachIndexed { idx, factor ->
        drawCircle(
          color = SplashColors.radarGrid.copy(alpha = 0.42f - idx * 0.07f),
          radius = maxRadius * factor,
          center = center,
          style = Stroke(width = 1.dp.toPx()),
        )
      }

      drawLine(
        color = SplashColors.radarGrid.copy(alpha = 0.30f),
        start = Offset(0f, center.y),
        end = Offset(size.width, center.y),
        strokeWidth = 1.dp.toPx(),
      )
      drawLine(
        color = SplashColors.radarGrid.copy(alpha = 0.30f),
        start = Offset(center.x, 0f),
        end = Offset(center.x, size.height),
        strokeWidth = 1.dp.toPx(),
      )
    }

    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
      val isCompactHeight = maxHeight < 480.dp
      val logoSize: Dp = if (isCompactHeight) 116.dp else 164.dp
      val titleFontSize = if (isCompactHeight) 28.sp else 36.sp

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 520.dp)
          .align(Alignment.Center),
      ) {
        Spacer(modifier = Modifier.weight(1f))

        MeteotabloBrandLogo(
          sweepAngle = sweepAngle,
          modifier = Modifier
            .size(logoSize)
            .graphicsLayer {
              scaleX = logoScale.value
              scaleY = logoScale.value
            }
            .testTag("splash_logo"),
        )

        Spacer(modifier = Modifier.height(if (isCompactHeight) 16.dp else 26.dp))

        Text(
          text = stringResource(R.string.app_name),
          color = SplashColors.wordmarkPrimary,
          fontSize = titleFontSize,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.SansSerif,
          letterSpacing = 3.sp,
          modifier = Modifier.testTag("splash_app_name"),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
          modifier = Modifier
            .width(64.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
              Brush.horizontalGradient(
                colors = listOf(
                  SplashColors.skyAccent,
                  SplashColors.windAmber,
                ),
              ),
            ),
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = stringResource(R.string.splash_subtitle),
          color = SplashColors.subtitleText,
          fontSize = if (isCompactHeight) 13.sp else 15.sp,
          fontWeight = FontWeight.Medium,
          letterSpacing = 1.4.sp,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = AuthorBrand.COPYRIGHT_LINE,
          color = SplashColors.telemetryMuted,
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
          letterSpacing = 0.4.sp,
          modifier = Modifier.testTag("splash_author_credit"),
        )

        Spacer(modifier = Modifier.weight(1f))

        // Bottom telemetry initialization pill & progress bar
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 300.dp)
            .padding(bottom = if (isCompactHeight) 4.dp else 12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
              .clip(RoundedCornerShape(50))
              .background(SplashColors.pillSurface)
              .border(1.dp, SplashColors.pillBorder, RoundedCornerShape(50))
              .padding(horizontal = 14.dp, vertical = 6.dp),
          ) {
            Box(
              modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(SplashColors.windAmber),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.splash_initializing),
              color = SplashColors.subtitleText,
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              letterSpacing = 0.4.sp,
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(4.dp)
              .clip(RoundedCornerShape(2.dp))
              .background(SplashColors.pillSurface)
              .testTag("splash_progress"),
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth(progressAnim.value.coerceIn(0.05f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(
                  Brush.horizontalGradient(
                    colors = listOf(
                      SplashColors.skyAccent,
                      SplashColors.windAmber,
                    ),
                  ),
                ),
            )
          }

          Spacer(modifier = Modifier.height(4.dp))

          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .heightIn(min = 48.dp)
              .clip(RoundedCornerShape(50))
              .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = tapHint,
                onClick = onFinished,
              )
              .padding(horizontal = 16.dp, vertical = 8.dp)
              .testTag("splash_continue_button"),
          ) {
            Text(
              text = tapHint,
              color = SplashColors.telemetryMuted,
              fontSize = 11.sp,
              fontWeight = FontWeight.Normal,
            )
          }
        }
      }
    }
  }
}

@Composable
fun MeteotabloBrandLogo(
  sweepAngle: Float = 45f,
  modifier: Modifier = Modifier,
) {
  val logoDesc = stringResource(R.string.splash_logo_description)
  val textMeasurer = rememberTextMeasurer()

  Canvas(
    modifier = modifier.semantics {
      contentDescription = logoDesc
    },
  ) {
    val diameter = min(size.width, size.height)
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerRadius = diameter * 0.48f
    val dialRadius = diameter * 0.415f

    // Outer ambient glow
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(
          SplashColors.skyAccent.copy(alpha = 0.24f),
          Color.Transparent,
        ),
        center = center,
        radius = outerRadius * 1.15f,
      ),
      radius = outerRadius * 1.15f,
      center = center,
    )

    // Radar sweep arc on outer bezel
    rotate(degrees = sweepAngle, pivot = center) {
      drawArc(
        brush = Brush.sweepGradient(
          0.0f to Color.Transparent,
          0.72f to Color.Transparent,
          0.95f to SplashColors.skyAccent.copy(alpha = 0.65f),
          1.0f to SplashColors.windAmber,
          center = center,
        ),
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
        size = Size(outerRadius * 2f, outerRadius * 2f),
        style = Stroke(width = diameter * 0.018f, cap = StrokeCap.Round),
      )
    }

    // Instrument dial background & metallic rim
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(
          SplashColors.backgroundCenter,
          SplashColors.bezelInner,
        ),
        center = center,
        radius = dialRadius,
      ),
      radius = dialRadius,
      center = center,
    )
    drawCircle(
      color = SplashColors.bezelOuter,
      radius = dialRadius,
      center = center,
      style = Stroke(width = diameter * 0.016f),
    )

    // 36 Compass ticks (every 10 degrees)
    for (deg in 0 until 360 step 10) {
      val rad = Math.toRadians(deg.toDouble() - 90.0)
      val isCardinal = deg % 90 == 0
      val isMajor = deg % 30 == 0
      val innerFactor = when {
        isCardinal -> 0.80f
        isMajor -> 0.85f
        else -> 0.90f
      }
      val start = Offset(
        x = center.x + (dialRadius * innerFactor * cos(rad)).toFloat(),
        y = center.y + (dialRadius * innerFactor * sin(rad)).toFloat(),
      )
      val end = Offset(
        x = center.x + (dialRadius * 0.96f * cos(rad)).toFloat(),
        y = center.y + (dialRadius * 0.96f * sin(rad)).toFloat(),
      )
      drawLine(
        color = if (isMajor) SplashColors.compassTickMajor else SplashColors.compassTickMinor,
        start = start,
        end = end,
        strokeWidth = if (isCardinal) diameter * 0.016f else if (isMajor) diameter * 0.011f else diameter * 0.006f,
        cap = StrokeCap.Round,
      )
    }

    // Cardinal labels N, E, S, W
    val labelStyle = TextStyle(
      color = SplashColors.compassTickMajor,
      fontSize = (diameter * 0.072f).toSp(),
      fontWeight = FontWeight.Bold,
      fontFamily = FontFamily.SansSerif,
    )
    val labelRadius = dialRadius * 0.67f
    val cardinals = listOf(
      "N" to -90.0,
      "E" to 0.0,
      "S" to 90.0,
      "W" to 180.0,
    )
    cardinals.forEach { (label, angleDeg) ->
      val rad = Math.toRadians(angleDeg)
      val measured = textMeasurer.measure(label, style = labelStyle)
      val lx = center.x + (labelRadius * cos(rad)).toFloat() - measured.size.width / 2f
      val ly = center.y + (labelRadius * sin(rad)).toFloat() - measured.size.height / 2f
      drawText(
        textLayoutResult = measured,
        color = if (label == "N") SplashColors.windAmber else SplashColors.compassTickMajor,
        topLeft = Offset(lx, ly),
      )
    }

    // North index pointer triangle on bezel
    val northTip = Offset(center.x, center.y - outerRadius * 0.97f)
    val pointerHalf = diameter * 0.028f
    val pointerPath = Path().apply {
      moveTo(northTip.x, northTip.y)
      lineTo(northTip.x - pointerHalf, northTip.y - pointerHalf * 1.15f)
      lineTo(northTip.x + pointerHalf, northTip.y - pointerHalf * 1.15f)
      close()
    }
    drawPath(path = pointerPath, color = SplashColors.windAmber)

    // Wind-direction amber arrow behind/crossing runway (west-southwest -> east-northeast vector)
    rotate(degrees = -12f, pivot = center) {
      val arrowHalfLen = dialRadius * 0.64f
      val shaftHeight = diameter * 0.044f
      val headLen = diameter * 0.14f
      val headHalfW = diameter * 0.095f

      val arrowPath = Path().apply {
        moveTo(center.x - arrowHalfLen, center.y - shaftHeight / 2f)
        lineTo(center.x + arrowHalfLen - headLen, center.y - shaftHeight / 2f)
        lineTo(center.x + arrowHalfLen - headLen, center.y - headHalfW)
        lineTo(center.x + arrowHalfLen, center.y)
        lineTo(center.x + arrowHalfLen - headLen, center.y + headHalfW)
        lineTo(center.x + arrowHalfLen - headLen, center.y + shaftHeight / 2f)
        lineTo(center.x - arrowHalfLen, center.y + shaftHeight / 2f)
        close()
      }
      drawPath(
        path = arrowPath,
        brush = Brush.horizontalGradient(
          colors = listOf(SplashColors.windAmber, SplashColors.windAmberGlow),
          startX = center.x - arrowHalfLen,
          endX = center.x + arrowHalfLen,
        ),
      )
    }

    // Angled precision runway strip with centerline dashes
    rotate(degrees = 42f, pivot = center) {
      val runwayWidth = diameter * 0.135f
      val runwayLength = dialRadius * 1.16f
      val topLeft = Offset(center.x - runwayWidth / 2f, center.y - runwayLength / 2f)

      drawRoundRect(
        color = SplashColors.runwayFill,
        topLeft = topLeft,
        size = Size(runwayWidth, runwayLength),
        cornerRadius = CornerRadius(diameter * 0.018f, diameter * 0.018f),
      )
      drawRoundRect(
        color = SplashColors.runwayEdge,
        topLeft = topLeft,
        size = Size(runwayWidth, runwayLength),
        cornerRadius = CornerRadius(diameter * 0.018f, diameter * 0.018f),
        style = Stroke(width = diameter * 0.008f),
      )

      // Runway centerline dashes
      val dashCount = 5
      val usableLen = runwayLength * 0.78f
      val step = usableLen / dashCount
      val dashLen = step * 0.56f
      val startY = center.y - usableLen / 2f + (step - dashLen) / 2f
      for (i in 0 until dashCount) {
        val y0 = startY + i * step
        drawLine(
          color = SplashColors.runwayStripe,
          start = Offset(center.x, y0),
          end = Offset(center.x, y0 + dashLen),
          strokeWidth = diameter * 0.018f,
          cap = StrokeCap.Round,
        )
      }
    }
  }
}
