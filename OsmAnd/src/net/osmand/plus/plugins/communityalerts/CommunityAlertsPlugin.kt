package net.osmand.plus.plugins.communityalerts

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.osmand.Location
import net.osmand.data.ValueHolder
import net.osmand.plus.OsmAndLocationProvider.OsmAndLocationListener
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.OsmandPlugin
import net.osmand.plus.routing.IRouteInformationListener

class CommunityAlertsPlugin(app: OsmandApplication) : OsmandPlugin(app) {

	private val repository = CommunityAlertsRepository()
	private val matcher = CommunityAlertMatcher()
	private val approachController = CommunityAlertApproachController()
	private val announcer = CommunityAlertAnnouncer.create(app)
	private val debugGenerator = CommunityAlertsDebugGenerator(matcher)
	private val debugProvider = DebugCommunityAlertsProvider(debugGenerator)
	private val debugRefreshPolicy = CommunityAlertsRefreshPolicy()
	private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
	private var debugRefreshJob: Job? = null
	private var alertsLayer: CommunityAlertsLayer? = null
	private var listenersRegistered = false
	private val locationListener = OsmAndLocationListener { location ->
		refreshDebugDemoAlerts(location)
	}
	private val routeListener = object : IRouteInformationListener {
		override fun newRouteIsCalculated(newRoute: Boolean, showToast: ValueHolder<Boolean>) {
			refreshDebugDemoAlerts(force = true)
		}

		override fun routeWasCancelled() {
			refreshDebugDemoAlerts(force = true)
		}

		override fun routeWasFinished() {
			refreshDebugDemoAlerts(force = true)
		}
	}

	override fun getId(): String = PLUGIN_ID

	override fun getName(): String = app.getString(R.string.community_alerts_plugin_name)

	override fun getDescription(linksEnabled: Boolean): CharSequence =
		app.getString(R.string.community_alerts_plugin_description)

	override fun getLogoResourceId(): Int = R.drawable.ic_action_alert

	override fun isEnableByDefault(): Boolean = false

	override fun init(app: OsmandApplication, activity: Activity?): Boolean {
		registerListeners()
		refreshDebugDemoAlerts(app.locationProvider.lastKnownLocation, force = true)
		return true
	}

	override fun disable(app: OsmandApplication) {
		unregisterListeners()
		debugRefreshJob?.cancel()
		debugRefreshPolicy.reset()
		super.disable(app)
	}

	override fun registerLayers(context: Context, mapActivity: MapActivity?) {
		if (!isActive) {
			return
		}
		val mapView = app.osmandMap.mapView
		val layer = alertsLayer ?: CommunityAlertsLayer(
			context,
			app,
			repository,
			matcher,
			debugGenerator,
			approachController,
			announcer
		).also {
			alertsLayer = it
		}
		if (!mapView.layers.contains(layer)) {
			mapView.addLayer(layer, ALERTS_LAYER_Z_ORDER)
		}
	}

	override fun updateLayers(context: Context, mapActivity: MapActivity?) {
		val mapView = app.osmandMap.mapView
		if (isActive) {
			registerLayers(context, mapActivity)
		} else {
			alertsLayer?.let(mapView::removeLayer)
			alertsLayer = null
		}
		mapView.refreshMap()
	}

	private fun refreshDebugDemoAlerts(location: Location? = null, force: Boolean = false) {
		val routingHelper = app.routingHelper
		val route = routingHelper.route
		val routeGeometry = if (routingHelper.isRouteCalculated && !route.isEmpty) {
			route.immutableAllLocations
		} else {
			null
		}
		val currentPosition = routingHelper.lastProjection
			?: location
			?: app.locationProvider.lastKnownLocation
		val bounds = createRequestedBounds(currentPosition)
		val now = System.currentTimeMillis()
		if (debugRefreshJob?.isActive == true ||
			!debugRefreshPolicy.shouldRefresh(bounds, now, force)) {
			return
		}
		if (routeGeometry != null) {
			val hasRouteAlerts = debugProvider.prepareRouteAlerts(
				routeGeometry = routeGeometry,
				currentRouteIndex = route.currentRoute,
				currentPosition = currentPosition,
				now = now
			)
			if (!hasRouteAlerts) {
				prepareLocationOrFallback(currentPosition, now)
			}
		} else {
			prepareLocationOrFallback(currentPosition, now)
		}

		// Keep the non-suspending debug provider on the route/location callback thread, as in phase 5.
		debugRefreshJob = refreshScope.launch(start = CoroutineStart.UNDISPATCHED) {
			repository.refresh(debugProvider, bounds)
			debugRefreshPolicy.recordSuccessfulRefresh(bounds, now)
		}
	}

	private fun prepareLocationOrFallback(location: Location?, now: Long) {
		if (location == null ||
			!debugProvider.prepareAlertNear(location.latitude, location.longitude, now)) {
			debugProvider.prepareFallbackAlert(now)
		}
	}

	private fun createRequestedBounds(location: Location?): CommunityAlertBounds {
		val latitude = location?.takeIf(::isValidLocation)?.latitude
			?: DebugCommunityAlertsProvider.FALLBACK_LATITUDE
		val longitude = location?.takeIf(::isValidLocation)?.longitude
			?: DebugCommunityAlertsProvider.FALLBACK_LONGITUDE
		return CommunityAlertBounds.around(latitude, longitude, ALERTS_LOAD_RADIUS_METERS)
	}

	private fun isValidLocation(location: Location): Boolean =
		location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0

	private fun registerListeners() {
		if (!listenersRegistered) {
			listenersRegistered = true
			app.locationProvider.addLocationListener(locationListener)
			app.routingHelper.addListener(routeListener)
		}
	}

	private fun unregisterListeners() {
		if (listenersRegistered) {
			listenersRegistered = false
			app.locationProvider.removeLocationListener(locationListener)
			app.routingHelper.removeListener(routeListener)
			app.routingHelper.transportRoutingHelper.removeListener(routeListener)
		}
	}

	companion object {
		const val PLUGIN_ID = "osmand.community.alerts"
		private const val ALERTS_LAYER_Z_ORDER = 3.6f
		private const val ALERTS_LOAD_RADIUS_METERS = 10_000.0
	}
}
