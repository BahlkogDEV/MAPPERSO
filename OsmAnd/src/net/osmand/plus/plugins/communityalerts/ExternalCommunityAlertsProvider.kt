package net.osmand.plus.plugins.communityalerts

/**
 * Reserved boundary for a future external source. Phase 6 deliberately performs no network I/O.
 */
class ExternalCommunityAlertsProvider : CommunityAlertsProvider {
	override val sourceId: String = SOURCE_ID

	override suspend fun fetchAlerts(bounds: CommunityAlertBounds): List<CommunityAlert> = emptyList()

	companion object {
		const val SOURCE_ID = "community-alerts-external"
	}
}
