package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.AirportMapProjection
import com.example.domain.FlightCategory
import com.example.domain.UnitConverter
import com.example.model.AirportStation
import com.example.model.DecodedWeather
import com.example.model.PressureUnit
import com.example.model.WindSpeedUnit
import com.example.ui.theme.LocalMeteoPalette
import java.util.Locale
import kotlin.math.abs

@Composable
fun AirportMapSummaryCard(
  station: AirportStation,
  weather: DecodedWeather?,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
  isFavorite: Boolean,
  isActiveStation: Boolean,
  isLoadingPin: Boolean,
  onToggleFavorite: () -> Unit,
  onRefreshPin: () -> Unit,
  onOpenOnBoard: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  val category = AirportMapProjection.evaluateFlightCategory(weather)
  val categoryColor = flightCategoryColor(category)

  val windText = formatSummaryWind(weather, windUnit)
  val visibilityText = formatSummaryVisibility(weather)
  val tempText = formatSummaryTemperature(weather)
  val pressureText = formatSummaryPressure(weather, pressureUnit)
  val coordsText = formatCoordinates(station)

  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("map_selected_summary_card")
      .background(palette.panelSurface, RoundedCornerShape(6.dp))
      .border(1.2.dp, palette.borderStrong, RoundedCornerShape(6.dp))
      .padding(12.dp),
  ) {
    // Header: ICAO, Name, Flight Category badge, Favorite & Refresh actions
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = station.icao,
            color = palette.metarAmber,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.testTag("map_summary_icao"),
          )
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(categoryColor.copy(alpha = 0.16f))
              .border(1.dp, categoryColor, RoundedCornerShape(4.dp))
              .padding(horizontal = 7.dp, vertical = 2.dp),
          ) {
            Text(
              text = category.code,
              color = categoryColor,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.testTag("map_summary_flight_category"),
            )
          }
          if (isActiveStation) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(palette.buttonActiveBackground)
                .border(0.8.dp, palette.borderStrong, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
              Text(
                text = "На табло",
                color = palette.textPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }
        Text(
          text = "${station.city} · ${station.name}",
          color = palette.textPrimary,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = coordsText,
          color = palette.textMuted,
          fontSize = 11.sp,
          fontFamily = FontFamily.Monospace,
        )
      }

      IconButton(
        onClick = onRefreshPin,
        enabled = !isLoadingPin,
        modifier = Modifier
          .size(48.dp)
          .testTag("map_summary_refresh_pin"),
      ) {
        if (isLoadingPin) {
          CircularProgressIndicator(
            strokeWidth = 2.dp,
            color = palette.metarAmber,
            modifier = Modifier.size(18.dp),
          )
        } else {
          Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "Обновить погоду для ${station.icao}",
            tint = palette.textSecondary,
          )
        }
      }

      IconButton(
        onClick = onToggleFavorite,
        modifier = Modifier
          .size(48.dp)
          .testTag("map_summary_favorite_toggle"),
      ) {
        Icon(
          imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
          contentDescription = if (isFavorite) "Убрать ${station.icao} из избранного" else "Добавить ${station.icao} в избранное",
          tint = if (isFavorite) palette.metarAmber else palette.textMuted,
        )
      }
    }

    // Brief 2x2 Weather Summary Grid
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      SummaryMetricTile(
        icon = Icons.Filled.Air,
        label = "Ветер",
        value = windText,
        testTag = "map_summary_wind",
        modifier = Modifier.weight(1f),
      )
      SummaryMetricTile(
        icon = Icons.Filled.Visibility,
        label = "Видимость / Облака",
        value = visibilityText,
        testTag = "map_summary_visibility",
        modifier = Modifier.weight(1f),
      )
    }

    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      SummaryMetricTile(
        icon = Icons.Filled.Thermostat,
        label = "Темп. / Точка росы",
        value = tempText,
        testTag = "map_summary_temperature",
        modifier = Modifier.weight(1f),
      )
      SummaryMetricTile(
        icon = Icons.Filled.Compress,
        label = "Давление QNH",
        value = pressureText,
        testTag = "map_summary_pressure",
        modifier = Modifier.weight(1f),
      )
    }

    // Raw observation & phenomena snippet
    val obsLabel = if (weather != null && weather.observationTimeUtc != "--:--Z") {
      "METAR ${weather.observationTimeUtc} · ${weather.weatherPhenomena} (${weather.sourceLabel})"
    } else {
      "Нет загруженного METAR — нажмите обновление или откройте станцию"
    }
    Column(
      verticalArrangement = Arrangement.spacedBy(2.dp),
      modifier = Modifier
        .fillMaxWidth()
        .background(palette.secondaryFieldBackground, RoundedCornerShape(4.dp))
        .border(0.8.dp, palette.border, RoundedCornerShape(4.dp))
        .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
      Text(
        text = obsLabel,
        color = palette.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = weather?.rawMetar ?: "METAR ${station.icao} — ожидание данных",
        color = palette.metarAmber,
        fontSize = 12.5.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 16.5.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("map_summary_raw_metar"),
      )
    }

    Button(
      onClick = onOpenOnBoard,
      colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
      ),
      shape = RoundedCornerShape(4.dp),
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .testTag("map_open_on_board_button"),
    ) {
      Icon(
        imageVector = Icons.Filled.FlightTakeoff,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "Открыть ${station.icao} на табло",
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
      )
    }
  }
}

@Composable
private fun SummaryMetricTile(
  icon: ImageVector,
  label: String,
  value: String,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  Column(
    verticalArrangement = Arrangement.spacedBy(2.dp),
    modifier = modifier
      .background(palette.fieldBackground, RoundedCornerShape(4.dp))
      .border(0.8.dp, palette.border, RoundedCornerShape(4.dp))
      .padding(horizontal = 8.dp, vertical = 6.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = palette.textMuted,
        modifier = Modifier.size(13.dp),
      )
      Text(
        text = label,
        color = palette.textMuted,
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Text(
      text = value,
      color = palette.textPrimary,
      fontSize = 13.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.testTag(testTag),
    )
  }
}

@Composable
fun flightCategoryColor(category: FlightCategory): Color {
  val palette = LocalMeteoPalette.current
  return when (category) {
    FlightCategory.VFR -> palette.headwindPositive
    FlightCategory.MVFR -> palette.metarAmber
    FlightCategory.IFR -> palette.tailwindNegative
    FlightCategory.UNKNOWN -> palette.textMuted
  }
}

private fun formatSummaryWind(weather: DecodedWeather?, unit: WindSpeedUnit): String {
  if (weather == null || weather.observationTimeUtc == "--:--Z") return "—"
  val wind = weather.wind
  if (wind.isCalm) return "Штиль"
  val dir = when {
    wind.isVariable -> "VRB"
    wind.directionDeg != null -> String.format(Locale.US, "%03d°", wind.directionDeg)
    else -> "---"
  }
  val speedVal = UnitConverter.formatWindSpeedValue(wind.speedKt, unit)
  val gustVal = wind.gustKt?.let { UnitConverter.formatWindSpeedValue(it, unit) }
  val gustPart = if (gustVal != null && gustVal > speedVal) "G$gustVal" else ""
  return "$dir $speedVal$gustPart ${unit.label}"
}

private fun formatSummaryVisibility(weather: DecodedWeather?): String {
  if (weather == null || weather.observationTimeUtc == "--:--Z") return "—"
  if (weather.isCavok) return "CAVOK (>10 км)"
  val visStr = when (val m = weather.visibilityMeters) {
    null -> "—"
    in 0..999 -> "$m м"
    else -> "${m / 1000} км"
  }
  val cloudStr = weather.clouds.firstOrNull()?.let { "${it.cover} ${it.baseFeet} ft" } ?: weather.ceilingCode
  return if (cloudStr != null) "$visStr · $cloudStr" else visStr
}

private fun formatSummaryTemperature(weather: DecodedWeather?): String {
  if (weather == null || weather.temperatureC == null) return "—"
  val temp = weather.temperatureC
  val dew = weather.dewPointC
  return if (dew != null) "$temp°C / $dew°C" else "$temp°C"
}

private fun formatSummaryPressure(weather: DecodedWeather?, unit: PressureUnit): String {
  if (weather == null || (weather.qnhHpa == null && weather.qnhInHg == null)) return "—"
  val (primary, label) = UnitConverter.formatPressurePrimary(
    hpa = weather.qnhHpa,
    inHg = weather.qnhInHg,
    unit = unit,
  )
  return "$primary $label"
}

private fun formatCoordinates(station: AirportStation): String {
  val latDir = if (station.latitude >= 0) "N" else "S"
  val lonDir = if (station.longitude >= 0) "E" else "W"
  return String.format(
    Locale.US,
    "%.2f°%s · %.2f°%s · %d м",
    abs(station.latitude),
    latDir,
    abs(station.longitude),
    lonDir,
    station.elevationMeters,
  )
}
