package net.osmand.test.junit

import kotlinx.coroutines.runBlocking
import net.osmand.plus.plugins.communityalerts.CommunityAlert
import net.osmand.plus.plugins.communityalerts.CommunityAlertBounds
import net.osmand.plus.plugins.communityalerts.CommunityAlertMatcher
import net.osmand.plus.plugins.communityalerts.CommunityAlertsDebugGenerator
import net.osmand.plus.plugins.communityalerts.CommunityAlertsProvider
import net.osmand.plus.plugins.communityalerts.CommunityAlertsRepository
import net.osmand.plus.plugins.communityalerts.DebugCommunityAlertsProvider
import net.osmand.plus.plugins.communityalerts.ExternalCommunityAlertsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityAlertsRepositoryTest {

	private val bounds = CommunityAlertBounds(
		north = 49.0,
		south = 48.0,
		east = 3.0,
		west = 2.0
	)

	@Test
	fun providerRefreshPopulatesRepository() = runBlocking {
		val alert = alert("external-1")
		val provider = SequencedProvider(EXTERNAL_SOURCE, listOf(listOf(alert)))
		val repository = CommunityAlertsRepository()

		repository.refresh(provider, bounds)

		assertEquals(listOf(alert), repository.getAlerts())
		assertEquals(listOf(alert), repository.getAlerts(EXTERNAL_SOURCE))
		assertEquals(listOf(bounds), provider.requestedBounds)
	}

	@Test
	fun refreshReplacesOnlyProviderSnapshot() = runBlocking {
		val provider = SequencedProvider(
			EXTERNAL_SOURCE,
			listOf(
				listOf(alert("external-1"), alert("external-2")),
				listOf(alert("external-2"), alert("external-3"))
			)
		)
		val repository = CommunityAlertsRepository()

		repository.refresh(provider, bounds)
		repository.refresh(provider, bounds)

		assertEquals(
			listOf("external-2", "external-3"),
			repository.getAlerts(EXTERNAL_SOURCE).map { it.id }
		)
	}

	@Test
	fun emptyProviderResponseReplacesItsSnapshot() = runBlocking {
		val provider = SequencedProvider(
			EXTERNAL_SOURCE,
			listOf(listOf(alert("external-1")), emptyList())
		)
		val repository = CommunityAlertsRepository()

		repository.refresh(provider, bounds)
		repository.refresh(provider, bounds)

		assertTrue(repository.getAlerts(EXTERNAL_SOURCE).isEmpty())
		assertTrue(repository.getAlerts().isEmpty())
	}

	@Test
	fun sameIdsRemainStableAcrossRefreshes() = runBlocking {
		val first = alert("stable-id", timestamp = NOW)
		val updated = alert("stable-id", timestamp = NOW + 1)
		val provider = SequencedProvider(EXTERNAL_SOURCE, listOf(listOf(first), listOf(updated)))
		val repository = CommunityAlertsRepository()

		repository.refresh(provider, bounds)
		val firstId = repository.getAlerts().single().id
		repository.refresh(provider, bounds)

		assertEquals(firstId, repository.getAlerts().single().id)
		assertEquals(NOW + 1, repository.getAlerts().single().timestamp)
	}

	@Test
	fun expiredAlertsAreExcludedFromActiveAlerts() {
		val repository = CommunityAlertsRepository()
		repository.replaceSnapshot(
			EXTERNAL_SOURCE,
			listOf(
				alert("expired", expiresAt = NOW),
				alert("active", expiresAt = NOW + 1)
			)
		)

		assertEquals(listOf("active"), repository.getActiveAlerts(NOW).map { it.id })
		assertTrue(repository.getActiveAlerts(NOW + 1).isEmpty())
	}

	@Test
	fun externalEmptySnapshotDoesNotRemoveDebugAlerts() = runBlocking {
		val repository = CommunityAlertsRepository()
		val debugProvider = DebugCommunityAlertsProvider(
			generator = CommunityAlertsDebugGenerator(CommunityAlertMatcher()),
			clock = { NOW }
		)
		repository.refresh(debugProvider, bounds)
		val debugAlert = repository.getAlerts(DebugCommunityAlertsProvider.SOURCE_ID).single()

		repository.refresh(ExternalCommunityAlertsProvider(), bounds)

		assertEquals(DebugCommunityAlertsProvider.DEMO_ALERT_ID_PREFIX, debugAlert.id)
		assertEquals(listOf(debugAlert), repository.getAlerts(DebugCommunityAlertsProvider.SOURCE_ID))
		assertTrue(repository.getAlerts(ExternalCommunityAlertsProvider.SOURCE_ID).isEmpty())
		assertEquals(listOf(debugAlert), repository.getAlerts())
	}

	private fun alert(
		id: String,
		timestamp: Long = NOW,
		expiresAt: Long = NOW + 1_000,
		source: String = "external-test"
	) = CommunityAlert(
		id = id,
		type = CommunityAlert.Type.HAZARD,
		latitude = 48.5,
		longitude = 2.5,
		timestamp = timestamp,
		expiresAt = expiresAt,
		source = source
	)

	private class SequencedProvider(
		override val sourceId: String,
		responses: List<List<CommunityAlert>>
	) : CommunityAlertsProvider {
		private val remainingResponses = ArrayDeque(responses)
		val requestedBounds = mutableListOf<CommunityAlertBounds>()

		override suspend fun fetchAlerts(bounds: CommunityAlertBounds): List<CommunityAlert> {
			requestedBounds.add(bounds)
			return remainingResponses.removeFirst()
		}
	}

	companion object {
		private const val NOW = 1_000_000L
		private const val EXTERNAL_SOURCE = "external-test-provider"
	}
}
