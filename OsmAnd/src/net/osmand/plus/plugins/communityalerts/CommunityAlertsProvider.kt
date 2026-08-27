package net.osmand.plus.plugins.communityalerts

interface CommunityAlertsProvider {
	/** Stable, unique key used to replace only this provider's repository snapshot. */
	val sourceId: String

	/**
	 * Returns alerts already normalized as [CommunityAlert] instances. Alert IDs must remain stable
	 * and globally unique while the same real-world alert exists.
	 */
	suspend fun fetchAlerts(bounds: CommunityAlertBounds): List<CommunityAlert>
}
