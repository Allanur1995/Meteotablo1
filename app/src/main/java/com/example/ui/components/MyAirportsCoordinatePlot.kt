package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AirportCatalog
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
import kotlin.math.roundToInt

@Composable
fun MyAirportsCoordinatePlot(
  myAirports: List<String>,
  currentIcao: String,
  weatherByIcao: Map<String, DecodedWeather>,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
  isNightMode: Boolean,
  onSelectStation: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  val density = LocalDensity.current

  val knownStations = remember(myAirports) {
    myAirports
      .map { it.trim().uppercase() }
      .distinct()
      .mapNotNull { code -> AirportCatalog.findKnown(code) }
  }
  val unknownIcaos = remember(myAirports, knownStations) {
    val knownSet = knownStations.map { it.icao }.toSet()
    myAirports
      .map { it.trim().uppercase() }
      .distinct()
      .filter { it.length == 4 && it !in knownSet }
  }

  var inspectedIcao by remember(currentIcao, myAirports) {
    mutableStateOf(
      knownStations.firstOrNull { it.icao.equals(currentIcao, ignoreCase = true) }?.icao
        ?: knownStations.firstOrNull()?.icao
        ?: currentIcao.uppercase(),
    )
  }

  Column(
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("my_airports_coordinate_plot")
      .background(palette.fieldBackground, RoundedCornerShape(6.dp))
      .border(1.dp, palette.borderStrong, RoundedCornerShape(6.dp))
      .padding(8.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.MyLocation,
          contentDescription = "Координатная карта Моих аэропортов",
          tint = palette.metarAmber,
          modifier = Modifier.size(15.dp),
        )
        Text(
          text = "Координатная карта «Моих аэропортов»",
          color = palette.textPrimary,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
        )
      }

      Text(
        text = "${knownStations.size} на сетке",
        color = palette.metarAmber,
        fontSize = 10.5.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
      )
    }

    if (knownStations.isEmpty()) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("my_airports_plot_empty")
          .background(palette.secondaryFieldBackground, RoundedCornerShape(4.dp))
          .border(0.8.dp, palette.border, RoundedCornerShape(4.dp))
          .padding(12.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.Place,
          contentDescription = null,
          tint = palette.textMuted,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = if (myAirports.isEmpty()) {
            "Добавьте аэропорты в «Мои аэропорты», чтобы увидеть их расположение по координатам."
          } else {
            "Для добавленных кодов (${unknownIcaos.joinToString(", ")}) нет координат в локальном каталоге."
          },
          color = palette.textSecondary,
          fontSize = 11.5.sp,
        )
      }
      return@Column
    }

    val bounds = remember(knownStations) {
      AirportMapProjection.computeCoordinatePlotBounds(knownStations)
    }
    val bgTop = if (isNightMode) Color(0xFF0A1421) else Color(0xFFF0F4F8)
    val bgBottom = if (isNightMode) Color(0xFF122338) else Color(0xFFE2E9F1)
    val gridColor = if (isNightMode) Color(0xFF213854) else Color(0xFFB7C5D6)
    val axisAccentColor = if (isNightMode) Color(0xFF39618C) else Color(0xFF7D95B2)

    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .height(210.dp)
        .clip(RoundedCornerShape(5.dp))
        .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
        .border(1.dp, palette.border, RoundedCornerShape(5.dp))
        .clipToBounds()
        .testTag("my_airports_plot_canvas"),
    ) {
      val widthPx = constraints.maxWidth.toFloat()
      val heightPx = constraints.maxHeight.toFloat()

      val latSteps = remember(bounds) {
        val step = (bounds.maxLat - bounds.minLat) / 3.0
        listOf(
          bounds.minLat + step * 0.5,
          bounds.minLat + step * 1.5,
          bounds.minLat + step * 2.5,
        )
      }
      val lonSteps = remember(bounds) {
        val step = (bounds.maxLon - bounds.minLon) / 3.0
        listOf(
          bounds.minLon + step * 0.5,
          bounds.minLon + step * 1.5,
          bounds.minLon + step * 2.5,
        )
      }

      Canvas(modifier = Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

        // Longitude vertical grid lines
        lonSteps.forEach { lon ->
          val p = AirportMapProjection.project(bounds.minLat, lon, bounds, size.width, size.height)
          drawLine(
            color = gridColor,
            start = Offset(p.x, 0f),
            end = Offset(p.x, size.height),
            strokeWidth = 1f,
            pathEffect = dash,
          )
        }

        // Latitude horizontal grid lines
        latSteps.forEach { lat ->
          val p = AirportMapProjection.project(lat, bounds.minLon, bounds, size.width, size.height)
          drawLine(
            color = gridColor,
            start = Offset(0f, p.y),
            end = Offset(size.width, p.y),
            strokeWidth = 1f,
            pathEffect = dash,
          )
        }

        // Reference axes (Equator & Prime Meridian if inside bounds)
        if (0.0 in bounds.minLat..bounds.maxLat) {
          val eq = AirportMapProjection.project(0.0, bounds.minLon, bounds, size.width, size.height)
          drawLine(axisAccentColor, Offset(0f, eq.y), Offset(size.width, eq.y), strokeWidth = 1.4f)
        }
        if (0.0 in bounds.minLon..bounds.maxLon) {
          val pm = AirportMapProjection.project(bounds.minLat, 0.0, bounds, size.width, size.height)
          drawLine(axisAccentColor, Offset(pm.x, 0f), Offset(pm.x, size.height), strokeWidth = 1.4f)
        }

        // Route legs connecting inspected/active station to all other saved airports
        val anchorStation = knownStations.firstOrNull { it.icao == inspectedIcao } ?: knownStations.first()
        val anchorPt = AirportMapProjection.project(
          anchorStation.latitude,
          anchorStation.longitude,
          bounds,
          size.width,
          size.height,
        )
        val routeDash = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f)
        knownStations.filter { it.icao != anchorStation.icao }.forEach { other ->
          val otherPt = AirportMapProjection.project(
            other.latitude,
            other.longitude,
            bounds,
            size.width,
            size.height,
          )
          drawLine(
            color = palette.metarAmber.copy(alpha = 0.48f),
            start = Offset(anchorPt.x, anchorPt.y),
            end = Offset(otherPt.x, otherPt.y),
            strokeWidth = 1.5f,
            pathEffect = routeDash,
          )
        }

        // Target range ring around inspected station
        drawCircle(
          color = palette.metarAmber.copy(alpha = 0.35f),
          radius = 34f,
          center = Offset(anchorPt.x, anchorPt.y),
          style = Stroke(width = 1.5f),
        )
      }

      // Latitude axis labels along left edge
      latSteps.forEach { lat ->
        val pt = AirportMapProjection.project(lat, bounds.minLon, bounds, widthPx, heightPx)
        val yOffset = (pt.y - with(density) { 7.dp.toPx() }).roundToInt().coerceIn(4, (heightPx - 24f).toInt().coerceAtLeast(4))
        Text(
          text = formatLatShort(lat),
          color = palette.textMuted,
          fontSize = 9.sp,
          fontFamily = FontFamily.Monospace,
          modifier = Modifier
            .offset { IntOffset(x = 6, y = yOffset) }
            .background(palette.panelSurface.copy(alpha = 0.72f), RoundedCornerShape(2.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp),
        )
      }

      // Longitude axis labels along bottom edge
      lonSteps.forEach { lon ->
        val pt = AirportMapProjection.project(bounds.minLat, lon, bounds, widthPx, heightPx)
        val xOffset = (pt.x - with(density) { 16.dp.toPx() }).roundToInt().coerceIn(32, (widthPx - 64f).toInt().coerceAtLeast(32))
        Text(
          text = formatLonShort(lon),
          color = palette.textMuted,
          fontSize = 9.sp,
          fontFamily = FontFamily.Monospace,
          modifier = Modifier
            .align(Alignment.BottomStart)
            .offset { IntOffset(x = xOffset, y = -6) }
            .background(palette.panelSurface.copy(alpha = 0.72f), RoundedCornerShape(2.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp),
        )
      }

      // Interactive Airport Pins
      val halfPinW = with(density) { 30.dp.toPx() }
      val halfPinH = with(density) { 24.dp.toPx() }
      knownStations.forEach { station ->
        val pt = AirportMapProjection.project(
          lat = station.latitude,
          lon = station.longitude,
          bounds = bounds,
          widthPx = widthPx,
          heightPx = heightPx,
        )
        val isInspected = station.icao == inspectedIcao
        val isCurrentBoard = station.icao.equals(currentIcao, ignoreCase = true)
        val weather = weatherByIcao[station.icao]
        val category = AirportMapProjection.evaluateFlightCategory(weather)
        val dotColor = if (category == FlightCategory.UNKNOWN) palette.metarAmber else flightCategoryColor(category)

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
          modifier = Modifier
            .offset {
              IntOffset(
                x = (pt.x - halfPinW).roundToInt(),
                y = (pt.y - halfPinH).roundToInt(),
              )
            }
            .sizeIn(minWidth = 60.dp, minHeight = 48.dp)
            .testTag("coordinate_plot_pin_${station.icao}")
            .clip(RoundedCornerShape(6.dp))
            .clickable {
              inspectedIcao = station.icao
              onSelectStation(station.icao)
            }
            .padding(2.dp),
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .size(if (isInspected || isCurrentBoard) 18.dp else 13.dp)
              .clip(CircleShape)
              .background(if (isCurrentBoard) palette.metarAmber.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.2f))
              .border(
                width = if (isCurrentBoard) 2.dp else 1.2.dp,
                color = if (isCurrentBoard) palette.metarAmber else Color.White,
                shape = CircleShape,
              ),
          ) {
            Box(
              modifier = Modifier
                .size(if (isInspected || isCurrentBoard) 10.dp else 8.dp)
                .clip(CircleShape)
                .background(dotColor),
            )
          }

          Spacer(modifier = Modifier.height(2.dp))

          Text(
            text = station.icao,
            color = if (isCurrentBoard) palette.metarAmber else palette.textPrimary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
              .clip(RoundedCornerShape(3.dp))
              .background(if (isCurrentBoard) palette.buttonActiveBackground else palette.panelSurface.copy(alpha = 0.92f))
              .border(
                width = if (isCurrentBoard) 1.2.dp else 0.8.dp,
                color = if (isCurrentBoard) palette.metarAmber else palette.borderStrong,
                shape = RoundedCornerShape(3.dp),
              )
              .padding(horizontal = 4.dp, vertical = 1.dp),
          )
        }
      }
    }

    // Selected / Active Station Coordinate Readout Bar
    val selectedStation = knownStations.firstOrNull { it.icao == inspectedIcao } ?: knownStations.first()
    val selectedWeather = weatherByIcao[selectedStation.icao]
    val activeStation = AirportCatalog.findKnown(currentIcao)
    val distanceNote = if (activeStation != null && activeStation.icao != selectedStation.icao) {
      val km = AirportMapProjection.haversineKm(
        activeStation.latitude,
        activeStation.longitude,
        selectedStation.latitude,
        selectedStation.longitude,
      )
      val brg = AirportMapProjection.bearingDeg(
        activeStation.latitude,
        activeStation.longitude,
        selectedStation.latitude,
        selectedStation.longitude,
      )
      " · $km км (${String.format(Locale.US, "%03d°", brg)} от ${activeStation.icao})"
    } else {
      ""
    }
    val wxSnippet = if (selectedWeather != null && selectedWeather.observationTimeUtc != "--:--Z") {
      val spd = UnitConverter.formatWindSpeedValue(selectedWeather.wind.speedKt, windUnit)
      val (qnh, qnhU) = UnitConverter.formatPressurePrimary(
        selectedWeather.qnhHpa,
        selectedWeather.qnhInHg,
        pressureUnit,
      )
      " · Ветер $spd ${windUnit.label}, QNH $qnh $qnhU"
    } else {
      ""
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) {}
        .testTag("coordinate_plot_selected_info")
        .background(palette.secondaryFieldBackground, RoundedCornerShape(4.dp))
        .border(0.8.dp, palette.border, RoundedCornerShape(4.dp))
        .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
      Text(
        text = "${selectedStation.icao} (${selectedStation.city}) · ${formatLatShort(selectedStation.latitude, decimals = 2)} ${formatLonShort(selectedStation.longitude, decimals = 2)}$distanceNote$wxSnippet",
        color = palette.textPrimary,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      if (unknownIcaos.isNotEmpty()) {
        Text(
          text = "+${unknownIcaos.size} без коорд.",
          color = palette.textMuted,
          fontSize = 10.sp,
          modifier = Modifier.padding(start = 6.dp),
        )
      }
    }
  }
}

private fun formatLatShort(lat: Double, decimals: Int = 1): String {
  val hemi = if (lat >= 0) "N" else "S"
  return String.format(Locale.US, "%.${decimals}f°%s", abs(lat), hemi)
}

private fun formatLonShort(lon: Double, decimals: Int = 1): String {
  val hemi = if (lon >= 0) "E" else "W"
  return String.format(Locale.US, "%.${decimals}f°%s", abs(lon), hemi)
}
