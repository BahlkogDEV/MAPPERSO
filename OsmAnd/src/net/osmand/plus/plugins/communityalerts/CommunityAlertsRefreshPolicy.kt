package net.osmand.plus.plugins.communityalerts

class CommunityAlertsRefreshPolicy(
	private val periodicRefreshIntervalMs: Long = DEFAULT_REFRESH_INTERVAL_MS,
	private val significantExitInsetRatio: Double = DEFAULT_SIGNIFICANT_EXIT_INSET_RATIO
) {
	private var lastLoadedBounds: CommunityAlertBounds? = null
	private var lastRefreshAt: Long? = null

	init {
		require(periodicRefreshIntervalMs > 0L) { "Refresh interval must be positive" }
		require(significantExitInsetRatio in 0.0..0.5) {
			"Significant exit inset ratio must be between 0 and 0.5"
		}
	}

	@Synchronized
	fun shouldRefresh(
		requestedBounds: CommunityAlertBounds,
		now: Long = System.currentTimeMillis(),
		force: Boolean = false
	): Boolean {
		if (force) {
			return true
		}
		val loadedBounds = lastLoadedBounds ?: return true
		val refreshedAt = lastRefreshAt ?: return true
		if (now - refreshedAt >= periodicRefreshIntervalMs) {
			return true
		}
		return !loadedBounds.contains(
			requestedBounds.centerLatitude,
			requestedBounds.centerLongitude,
			insetRatio = significantExitInsetRatio
		)
	}

	@Synchronized
	fun recordSuccessfulRefresh(bounds: CommunityAlertBounds, refreshedAt: Long = System.currentTimeMillis()) {
		lastLoadedBounds = bounds
		lastRefreshAt = refreshedAt
	}

	@Synchronized
	fun reset() {
		lastLoadedBounds = null
		lastRefreshAt = null
	}

	companion object {
		const val DEFAULT_REFRESH_INTERVAL_MS = 15 * 60 * 1000L
		const val DEFAULT_SIGNIFICANT_EXIT_INSET_RATIO = 0.25
	}
}
