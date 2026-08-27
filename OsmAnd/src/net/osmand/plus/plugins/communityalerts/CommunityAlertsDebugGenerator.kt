package net.osmand.plus.plugins.communityalerts

import net.osmand.Location
import net.osmand.PlatformUtil
import net.osmand.shared.util.KMapUtils
import net.osmand.util.MapUtils

/**
 * Temporary debug/demo-only generator used to validate CommunityAlertMatcher visually.
 * Remove or gate this class when CommunityAlerts starts receiving real repository data.
 */
class CommunityAlertsDebugGenerator(
	private val matcher: CommunityAlertMatcher
) {
	@Volatile
	var lastBehindDiagnostic: BehindDiagnostic? = null
		private set

	fun createRouteDemoAlerts(
		routeGeometry: List<Location>,
		currentRouteIndex: Int,
		currentPosition: Location?,
		now: Long = System.currentTimeMillis()
	): List<CommunityAlert> {
		if (routeGeometry.size < 2 || currentRouteIndex !in routeGeometry.indices) {
			publishBehindDiagnostic(
				BehindDiagnostic(
					routeProgressMeters = null,
					behindAvailableMeters = null,
					generated = false,
					omissionReason = "invalid route geometry or progress index " +
						"(points=${routeGeometry.size}, currentRouteIndex=$currentRouteIndex)"
				)
			)
			return emptyList()
		}

		val route = DebugRoute(routeGeometry)
		val currentDistance = route.getDistanceAlongRoute(currentRouteIndex, currentPosition)
		val aheadDistance = (currentDistance + TEST_AHEAD_DISTANCE_METERS)
			.coerceAtMost(route.totalDistanceMeters)
		val aheadPoint = route.getPointAtDistance(aheadDistance)

		val alerts = mutableListOf(
			createAlert(
				id = TEST_ON_ALERT_ID,
				point = aheadPoint,
				source = TEST_ON_SOURCE,
				now = now
			),
			createOffRouteAlert(
				aheadPoint = aheadPoint,
				routeGeometry = routeGeometry,
				currentRouteIndex = currentRouteIndex,
				currentPosition = currentPosition,
				now = now
			)
		)

		val distanceAvailableBehind = currentDistance
		val availableBehindDistance = currentDistance.coerceAtMost(TEST_BEHIND_DISTANCE_METERS)
		if (availableBehindDistance >= MIN_DISTINCT_BEHIND_DISTANCE_METERS) {
			alerts.add(
				createAlert(
					id = TEST_BEHIND_ALERT_ID,
					point = route.getPointAtDistance(currentDistance - availableBehindDistance),
					source = TEST_BEHIND_SOURCE,
					now = now
				)
			)
			publishBehindDiagnostic(
				BehindDiagnostic(
					routeProgressMeters = currentDistance,
					behindAvailableMeters = distanceAvailableBehind,
					generated = true,
					placedBehindMeters = availableBehindDistance
				)
			)
		} else {
			publishBehindDiagnostic(
				BehindDiagnostic(
					routeProgressMeters = currentDistance,
					behindAvailableMeters = distanceAvailableBehind,
					generated = false,
					omissionReason = "available distance behind is below " +
						"$MIN_DISTINCT_BEHIND_DISTANCE_METERS m"
				)
			)
		}
		return alerts
	}

	private fun publishBehindDiagnostic(diagnostic: BehindDiagnostic) {
		lastBehindDiagnostic = diagnostic
		LOG.info(diagnostic.toLogMessage())
	}

	private fun createOffRouteAlert(
		aheadPoint: RoutePoint,
		routeGeometry: List<Location>,
		currentRouteIndex: Int,
		currentPosition: Location?,
		now: Long
	): CommunityAlert {
		val candidates = listOf(-90.0, 90.0).map { perpendicularOffset ->
			val offsetPoint = MapUtils.rhumbDestinationPoint(
				aheadPoint.latitude,
				aheadPoint.longitude,
				TEST_OFF_ROUTE_OFFSET_METERS,
				aheadPoint.segmentBearingDegrees + perpendicularOffset
			)
			createAlert(
				id = TEST_OFF_ALERT_ID,
				latitude = offsetPoint.latitude,
				longitude = offsetPoint.longitude,
				source = TEST_OFF_SOURCE,
				now = now
			)
		}
		return candidates.maxBy { candidate ->
			matcher.match(
				alert = candidate,
				routeGeometry = routeGeometry,
				currentRouteIndex = currentRouteIndex,
				currentPosition = currentPosition,
				now = now
			)?.distanceToRouteMeters ?: Double.NEGATIVE_INFINITY
		}
	}

	private fun createAlert(
		id: String,
		point: RoutePoint,
		source: String,
		now: Long
	): CommunityAlert = createAlert(
		id = id,
		latitude = point.latitude,
		longitude = point.longitude,
		source = source,
		now = now
	)

	private fun createAlert(
		id: String,
		latitude: Double,
		longitude: Double,
		source: String,
		now: Long
	) = CommunityAlert(
		id = id,
		type = CommunityAlert.Type.HAZARD,
		latitude = latitude,
		longitude = longitude,
		timestamp = now,
		expiresAt = now + DEBUG_ALERT_DURATION_MS,
		source = source
	)

	private class DebugRoute(
		private val locations: List<Location>
	) {
		private val cumulativeDistancesMeters = DoubleArray(locations.size).also { distances ->
			for (index in 1 until locations.size) {
				distances[index] = distances[index - 1] + locations[index - 1].distanceTo(locations[index])
			}
		}

		val totalDistanceMeters: Double
			get() = cumulativeDistancesMeters.last()

		fun getDistanceAlongRoute(currentRouteIndex: Int, currentPosition: Location?): Double {
			val segmentEndIndex = currentRouteIndex.coerceAtLeast(1)
			val segmentStartIndex = segmentEndIndex - 1
			if (currentPosition == null) {
				return cumulativeDistancesMeters[segmentStartIndex]
			}
			val segmentStart = locations[segmentStartIndex]
			val segmentEnd = locations[segmentEndIndex]
			val coefficient = MapUtils.getProjectionCoeff(
				currentPosition.latitude,
				currentPosition.longitude,
				segmentStart.latitude,
				segmentStart.longitude,
				segmentEnd.latitude,
				segmentEnd.longitude
			)
			return cumulativeDistancesMeters[segmentStartIndex] +
				segmentStart.distanceTo(segmentEnd) * coefficient
		}

		fun getPointAtDistance(distanceMeters: Double): RoutePoint {
			val targetDistance = distanceMeters.coerceIn(0.0, totalDistanceMeters)
			var segmentEndIndex = 1
			while (segmentEndIndex < cumulativeDistancesMeters.lastIndex &&
				cumulativeDistancesMeters[segmentEndIndex] < targetDistance) {
				segmentEndIndex++
			}
			val segmentStartIndex = segmentEndIndex - 1
			val segmentStart = locations[segmentStartIndex]
			val segmentEnd = locations[segmentEndIndex]
			val segmentDistance = segmentStart.distanceTo(segmentEnd).toDouble()
			val fraction = if (segmentDistance > 0.0) {
				((targetDistance - cumulativeDistancesMeters[segmentStartIndex]) / segmentDistance)
					.coerceIn(0.0, 1.0)
			} else {
				0.0
			}
			val interpolated = KMapUtils.interpolateLatLon(
				segmentStart.latitude,
				segmentStart.longitude,
				segmentEnd.latitude,
				segmentEnd.longitude,
				fraction
			)
			return RoutePoint(
				latitude = interpolated.latitude,
				longitude = interpolated.longitude,
				segmentBearingDegrees = segmentStart.bearingTo(segmentEnd).toDouble()
			)
		}
	}

	private data class RoutePoint(
		val latitude: Double,
		val longitude: Double,
		val segmentBearingDegrees: Double
	)

	data class BehindDiagnostic(
		val routeProgressMeters: Double?,
		val behindAvailableMeters: Double?,
		val generated: Boolean,
		val placedBehindMeters: Double? = null,
		val omissionReason: String? = null
	) {
		fun toLogMessage(): String = buildString {
			append("CommunityAlerts debug TEST BEHIND: generated=")
			append(generated)
			append(", progressMeters=")
			append(routeProgressMeters ?: "unavailable")
			append(", availableBehindMeters=")
			append(behindAvailableMeters ?: "unavailable")
			placedBehindMeters?.let {
				append(", placedBehindMeters=")
				append(it)
			}
			omissionReason?.let {
				append(", reason=")
				append(it)
			}
		}
	}

	companion object {
		private val LOG = PlatformUtil.getLog(CommunityAlertsDebugGenerator::class.java)
		private const val TEST_AHEAD_DISTANCE_METERS = 300.0
		private const val TEST_OFF_ROUTE_OFFSET_METERS = 100.0
		private const val TEST_BEHIND_DISTANCE_METERS = 150.0
		private const val MIN_DISTINCT_BEHIND_DISTANCE_METERS = 25.0
		private const val DEBUG_ALERT_DURATION_MS = 6 * 60 * 60 * 1000L
		private const val TEST_ON_ALERT_ID = "community-alert-demo-on-route"
		private const val TEST_OFF_ALERT_ID = "community-alert-demo-off-route"
		private const val TEST_BEHIND_ALERT_ID = "community-alert-demo-behind"
		private const val TEST_ON_SOURCE = "local-debug-route-on"
		private const val TEST_OFF_SOURCE = "local-debug-route-off"
		private const val TEST_BEHIND_SOURCE = "local-debug-route-behind"
	}
}
