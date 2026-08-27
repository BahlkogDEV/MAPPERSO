package net.osmand.plus.plugins.communityalerts

import net.osmand.Location

/**
 * Debug-only source. Route TEST alerts and the fallback marker stay isolated here and are never
 * part of an external provider implementation.
 */
class DebugCommunityAlertsProvider(
	private val generator: CommunityAlertsDebugGenerator,
	private val clock: () -> Long = System::currentTimeMillis
) : CommunityAlertsProvider {
	@Volatile
	private var preparedAlerts: List<CommunityAlert> = createFallbackAlerts(clock())

	override val sourceId: String = SOURCE_ID

	override suspend fun fetchAlerts(bounds: CommunityAlertBounds): List<CommunityAlert> =
		preparedAlerts.toList()

	fun prepareRouteAlerts(
		routeGeometry: List<Location>,
		currentRouteIndex: Int,
		currentPosition: Location?,
		now: Long = clock()
	): Boolean {
		val routeAlerts = generator.createRouteDemoAlerts(
			routeGeometry = routeGeometry,
			currentRouteIndex = currentRouteIndex,
			currentPosition = currentPosition,
			now = now
		)
		if (routeAlerts.isEmpty()) {
			return false
		}
		preparedAlerts = routeAlerts.toList()
		return true
	}

	fun prepareAlertNear(latitude: Double, longitude: Double, now: Long = clock()): Boolean {
		if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
			return false
		}
		val alertLatitude = (latitude + DEMO_LATITUDE_OFFSET).coerceIn(-90.0, 90.0)
		preparedAlerts = createDemoAlerts(alertLatitude, longitude, GPS_DEMO_SOURCE, now)
		return true
	}

	fun prepareFallbackAlert(now: Long = clock()) {
		preparedAlerts = createFallbackAlerts(now)
	}

	companion object {
		const val SOURCE_ID = "community-alerts-debug"
		const val DEMO_ALERT_ID_PREFIX = "community-alert-demo"
		const val FALLBACK_LATITUDE = 48.8566
		const val FALLBACK_LONGITUDE = 2.3522
		private const val DEMO_LATITUDE_OFFSET = 0.00135
		private const val DEMO_DURATION_MS = 6 * 60 * 60 * 1000L
		private const val FALLBACK_DEMO_SOURCE = "local-debug-fixed-paris"
		private const val GPS_DEMO_SOURCE = "local-debug-gps"

		fun isDebugAlert(alert: CommunityAlert): Boolean =
			alert.id.startsWith(DEMO_ALERT_ID_PREFIX)

		private fun createFallbackAlerts(now: Long): List<CommunityAlert> =
			createDemoAlerts(FALLBACK_LATITUDE, FALLBACK_LONGITUDE, FALLBACK_DEMO_SOURCE, now)

		private fun createDemoAlerts(
			latitude: Double,
			longitude: Double,
			source: String,
			now: Long
		): List<CommunityAlert> = listOf(
			CommunityAlert(
				id = DEMO_ALERT_ID_PREFIX,
				type = CommunityAlert.Type.HAZARD,
				latitude = latitude,
				longitude = longitude,
				timestamp = now,
				expiresAt = now + DEMO_DURATION_MS,
				source = source
			)
		)
	}
}
