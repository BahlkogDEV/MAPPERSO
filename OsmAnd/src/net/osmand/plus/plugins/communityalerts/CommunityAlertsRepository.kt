package net.osmand.plus.plugins.communityalerts

import java.util.concurrent.CopyOnWriteArraySet

class CommunityAlertsRepository {
	fun interface Listener {
		fun onAlertsChanged(alerts: List<CommunityAlert>)
	}

	private val listeners = CopyOnWriteArraySet<Listener>()
	private val snapshotsBySource = linkedMapOf<String, LinkedHashMap<String, CommunityAlert>>()

	@Volatile
	private var alerts = emptyList<CommunityAlert>()

	fun getAlerts(): List<CommunityAlert> = alerts

	fun getAlerts(sourceId: String): List<CommunityAlert> = synchronized(this) {
		snapshotsBySource[sourceId]?.values?.toList().orEmpty()
	}

	fun getActiveAlerts(now: Long = System.currentTimeMillis()): List<CommunityAlert> =
		alerts.filter { it.expiresAt > now }

	suspend fun refresh(
		provider: CommunityAlertsProvider,
		bounds: CommunityAlertBounds
	): List<CommunityAlert> {
		val normalizedAlerts = provider.fetchAlerts(bounds)
		replaceSnapshot(provider.sourceId, normalizedAlerts)
		return normalizedAlerts
	}

	fun replaceSnapshot(sourceId: String, newAlerts: List<CommunityAlert>) {
		require(sourceId.isNotBlank()) { "Source id must not be blank" }
		val updatedAlerts = synchronized(this) {
			val snapshot = LinkedHashMap<String, CommunityAlert>()
			newAlerts.forEach { alert -> snapshot[alert.id] = alert }
			snapshotsBySource[sourceId] = snapshot
			alerts = snapshotsBySource.values.flatMap { it.values }.toList()
			alerts
		}
		notifyListeners(updatedAlerts)
	}

	fun removeSnapshot(sourceId: String) {
		val updatedAlerts = synchronized(this) {
			if (snapshotsBySource.remove(sourceId) == null) {
				return@synchronized null
			}
			alerts = snapshotsBySource.values.flatMap { it.values }.toList()
			alerts
		} ?: return
		notifyListeners(updatedAlerts)
	}

	fun clear() {
		val hadAlerts = synchronized(this) {
			val changed = snapshotsBySource.isNotEmpty() || alerts.isNotEmpty()
			snapshotsBySource.clear()
			alerts = emptyList()
			changed
		}
		if (hadAlerts) {
			notifyListeners(emptyList())
		}
	}

	fun addListener(listener: Listener) {
		listeners.add(listener)
	}

	fun removeListener(listener: Listener) {
		listeners.remove(listener)
	}

	private fun notifyListeners(updatedAlerts: List<CommunityAlert>) {
		val activeAlerts = updatedAlerts.filter { it.expiresAt > System.currentTimeMillis() }
		listeners.forEach { it.onAlertsChanged(activeAlerts) }
	}
}
