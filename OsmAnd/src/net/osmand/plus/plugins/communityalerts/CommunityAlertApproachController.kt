package net.osmand.plus.plugins.communityalerts

class CommunityAlertApproachController(
	private val triggerDistanceMeters: Double = APPROACH_TRIGGER_DISTANCE_METERS
) {
	private val triggeredAlertExpirations = mutableMapOf<String, Long>()

	@Volatile
	var triggerCount: Int = 0
		private set

	@Volatile
	var lastEvent: CommunityAlertApproachEvent? = null
		private set

	@Synchronized
	fun evaluate(
		matches: List<CommunityAlertMatch>,
		now: Long = System.currentTimeMillis()
	): List<CommunityAlertApproachEvent> {
		triggeredAlertExpirations.entries.removeAll { (_, expiresAt) -> expiresAt <= now }

		val events = mutableListOf<CommunityAlertApproachEvent>()
		matches.forEach { match ->
			val alert = match.alert
			val triggeredUntil = triggeredAlertExpirations[alert.id]
			if (triggeredUntil != null) {
				triggeredAlertExpirations[alert.id] = maxOf(triggeredUntil, alert.expiresAt)
				return@forEach
			}
			val distanceAhead = match.distanceAheadMeters
			if (isEligible(match, distanceAhead, now)) {
				triggeredAlertExpirations[alert.id] = alert.expiresAt
				triggerCount++
				val event = CommunityAlertApproachEvent(
					alert = alert,
					distanceAheadMeters = checkNotNull(distanceAhead),
					triggerCount = triggerCount,
					triggeredAt = now
				)
				lastEvent = event
				events.add(event)
			}
		}
		return events
	}

	@Synchronized
	fun rearm(alertId: String): Boolean = triggeredAlertExpirations.remove(alertId) != null

	@Synchronized
	fun clear() {
		triggeredAlertExpirations.clear()
		triggerCount = 0
		lastEvent = null
	}

	private fun isEligible(
		match: CommunityAlertMatch,
		distanceAheadMeters: Double?,
		now: Long
	): Boolean = match.activeRoute &&
		match.alert.expiresAt > now &&
		match.onRoute &&
		match.ahead &&
		distanceAheadMeters != null &&
		distanceAheadMeters in 0.0..triggerDistanceMeters

	companion object {
		const val APPROACH_TRIGGER_DISTANCE_METERS = 500.0
	}
}

data class CommunityAlertApproachEvent(
	val alert: CommunityAlert,
	val distanceAheadMeters: Double,
	val triggerCount: Int,
	val triggeredAt: Long
)
