package net.osmand.plus.plugins.communityalerts

data class CommunityAlertMatch(
	val alert: CommunityAlert,
	val distanceToRouteMeters: Double?,
	val distanceAheadMeters: Double?,
	val onRoute: Boolean,
	val ahead: Boolean,
	val activeRoute: Boolean
) {
	val status: Status
		get() = when {
			!activeRoute -> Status.NO_ROUTE
			!onRoute -> Status.OFF_ROUTE
			!ahead -> Status.BEHIND
			else -> Status.ON_ROUTE
		}

	enum class Status {
		NO_ROUTE,
		ON_ROUTE,
		OFF_ROUTE,
		BEHIND
	}
}
