package net.osmand.plus.plugins.communityalerts

import net.osmand.Location
import net.osmand.util.MapUtils

class CommunityAlertMatcher(
	private val onRouteThresholdMeters: Double = ON_ROUTE_THRESHOLD_METERS
) {
	private var cachedRouteGeometry: List<Location>? = null
	private var cachedRoute: PreparedRoute? = null
	private val cachedAlertProjections = mutableMapOf<CommunityAlert, AlertProjection>()

	@Synchronized
	fun match(
		alert: CommunityAlert,
		routeGeometry: List<Location>?,
		currentRouteIndex: Int,
		currentPosition: Location?,
		now: Long = System.currentTimeMillis()
	): CommunityAlertMatch? = matchAll(
		alerts = listOf(alert),
		routeGeometry = routeGeometry,
		currentRouteIndex = currentRouteIndex,
		currentPosition = currentPosition,
		now = now
	).firstOrNull()

	@Synchronized
	fun matchAll(
		alerts: List<CommunityAlert>,
		routeGeometry: List<Location>?,
		currentRouteIndex: Int,
		currentPosition: Location?,
		now: Long = System.currentTimeMillis()
	): List<CommunityAlertMatch> {
		val activeAlerts = alerts.filter { it.expiresAt > now }
		val preparedRoute = prepareRoute(routeGeometry, currentRouteIndex)
		if (preparedRoute == null) {
			return activeAlerts.map(::createNoRouteMatch)
		}

		cachedAlertProjections.keys.retainAll(activeAlerts.toSet())
		val currentDistanceAlongRoute = preparedRoute.getDistanceAlongRoute(
			currentRouteIndex,
			currentPosition
		)
		return activeAlerts.map { alert ->
			val projection = cachedAlertProjections.getOrPut(alert) {
				preparedRoute.project(alert)
			}
			val distanceAhead = projection.distanceAlongRouteMeters - currentDistanceAlongRoute
			val onRoute = projection.distanceToRouteMeters <= onRouteThresholdMeters
			CommunityAlertMatch(
				alert = alert,
				distanceToRouteMeters = projection.distanceToRouteMeters,
				distanceAheadMeters = distanceAhead,
				onRoute = onRoute,
				ahead = distanceAhead >= 0.0,
				activeRoute = true
			)
		}
	}

	private fun prepareRoute(
		routeGeometry: List<Location>?,
		currentRouteIndex: Int
	): PreparedRoute? {
		if (routeGeometry == null || routeGeometry.size < 2 || currentRouteIndex !in routeGeometry.indices) {
			clearRouteCache()
			return null
		}
		if (cachedRouteGeometry !== routeGeometry) {
			cachedRouteGeometry = routeGeometry
			cachedRoute = PreparedRoute(routeGeometry.toList())
			cachedAlertProjections.clear()
		}
		return cachedRoute
	}

	private fun clearRouteCache() {
		cachedRouteGeometry = null
		cachedRoute = null
		cachedAlertProjections.clear()
	}

	private fun createNoRouteMatch(alert: CommunityAlert) = CommunityAlertMatch(
		alert = alert,
		distanceToRouteMeters = null,
		distanceAheadMeters = null,
		onRoute = false,
		ahead = false,
		activeRoute = false
	)

	private class PreparedRoute(
		private val locations: List<Location>
	) {
		private val cumulativeDistancesMeters = DoubleArray(locations.size).also { distances ->
			for (index in 1 until locations.size) {
				distances[index] = distances[index - 1] + locations[index - 1].distanceTo(locations[index])
			}
		}

		fun project(alert: CommunityAlert): AlertProjection {
			var closestProjection: AlertProjection? = null
			for (segmentEndIndex in 1 until locations.size) {
				val segmentStart = locations[segmentEndIndex - 1]
				val segmentEnd = locations[segmentEndIndex]
				val coefficient = getProjectionCoefficient(
					alert.latitude,
					alert.longitude,
					segmentStart,
					segmentEnd
				)
				val projectedPoint = MapUtils.getProjection(
					alert.latitude,
					alert.longitude,
					segmentStart.latitude,
					segmentStart.longitude,
					segmentEnd.latitude,
					segmentEnd.longitude
				)
				val distanceToRoute = MapUtils.getDistance(
					alert.latitude,
					alert.longitude,
					projectedPoint.latitude,
					projectedPoint.longitude
				)
				if (closestProjection == null || distanceToRoute < closestProjection.distanceToRouteMeters) {
					val segmentDistance = segmentStart.distanceTo(segmentEnd).toDouble()
					closestProjection = AlertProjection(
						distanceToRouteMeters = distanceToRoute,
						distanceAlongRouteMeters = cumulativeDistancesMeters[segmentEndIndex - 1] +
							segmentDistance * coefficient
					)
				}
			}
			return checkNotNull(closestProjection)
		}

		fun getDistanceAlongRoute(currentRouteIndex: Int, currentPosition: Location?): Double {
			val segmentEndIndex = currentRouteIndex.coerceAtLeast(1)
			val segmentStartIndex = segmentEndIndex - 1
			if (currentPosition == null) {
				return cumulativeDistancesMeters[segmentStartIndex]
			}
			val segmentStart = locations[segmentStartIndex]
			val segmentEnd = locations[segmentEndIndex]
			val coefficient = getProjectionCoefficient(
				currentPosition.latitude,
				currentPosition.longitude,
				segmentStart,
				segmentEnd
			)
			return cumulativeDistancesMeters[segmentStartIndex] +
				segmentStart.distanceTo(segmentEnd) * coefficient
		}

		private fun getProjectionCoefficient(
			latitude: Double,
			longitude: Double,
			segmentStart: Location,
			segmentEnd: Location
		): Double = MapUtils.getProjectionCoeff(
			latitude,
			longitude,
			segmentStart.latitude,
			segmentStart.longitude,
			segmentEnd.latitude,
			segmentEnd.longitude
		)
	}

	private data class AlertProjection(
		val distanceToRouteMeters: Double,
		val distanceAlongRouteMeters: Double
	)

	companion object {
		const val ON_ROUTE_THRESHOLD_METERS = 50.0
	}
}
