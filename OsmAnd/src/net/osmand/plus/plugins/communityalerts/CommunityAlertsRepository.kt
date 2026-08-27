package net.osmand.plus.plugins.communityalerts

import java.util.concurrent.CopyOnWriteArraySet

class CommunityAlertsRepository(
	initialAlerts: List<CommunityAlert> = createFallbackDemoAlerts()
) {
	fun interface Listener {
		fun onAlertsChanged(alerts: List<CommunityAlert>)
	}

	private val listeners = CopyOnWriteArraySet<Listener>()

	@Volatile
	private var alerts = initialAlerts.toList()

	fun getAlerts(): List<CommunityAlert> = alerts

	fun getActiveAlerts(now: Long = System.currentTimeMillis()): List<CommunityAlert> =
		alerts.filter { it.expiresAt > now }

	fun replaceAlerts(newAlerts: List<CommunityAlert>) {
		alerts = newAlerts.toList()
		notifyListeners()
	}

	fun clearAlerts() {
		replaceAlerts(emptyList())
	}

	fun replaceWithDemoAlertNear(latitude: Double, longitude: Double) {
		if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
			return
		}
		val alertLatitude = (latitude + DEMO_LATITUDE_OFFSET).coerceIn(-90.0, 90.0)
		replaceAlerts(createDemoAlerts(alertLatitude, longitude, GPS_DEMO_SOURCE))
	}

	fun addListener(listener: Listener) {
		listeners.add(listener)
	}

	fun removeListener(listener: Listener) {
		listeners.remove(listener)
	}

	private fun notifyListeners() {
		val activeAlerts = getActiveAlerts()
		listeners.forEach { it.onAlertsChanged(activeAlerts) }
	}

	companion object {
		// Fallback location: central Paris. It is replaced by a nearby GPS alert when available.
		private const val FALLBACK_LATITUDE = 48.8566
		private const val FALLBACK_LONGITUDE = 2.3522
		// 0.00135 degrees of latitude is approximately 150 metres.
		private const val DEMO_LATITUDE_OFFSET = 0.00135
		private const val DEMO_DURATION_MS = 6 * 60 * 60 * 1000L
		private const val FALLBACK_DEMO_SOURCE = "local-demo-fixed-paris"
		private const val GPS_DEMO_SOURCE = "local-demo-gps"
		internal const val DEMO_ALERT_ID = "community-alert-demo"

		private fun createFallbackDemoAlerts(): List<CommunityAlert> =
			createDemoAlerts(FALLBACK_LATITUDE, FALLBACK_LONGITUDE, FALLBACK_DEMO_SOURCE)

		private fun createDemoAlerts(
			latitude: Double,
			longitude: Double,
			source: String
		): List<CommunityAlert> {
			val now = System.currentTimeMillis()
			return listOf(
				CommunityAlert(
					id = DEMO_ALERT_ID,
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
}
