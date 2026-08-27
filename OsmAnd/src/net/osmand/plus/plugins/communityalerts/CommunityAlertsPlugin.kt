package net.osmand.plus.plugins.communityalerts

import android.app.Activity
import android.content.Context
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
	// TEMPORARY DEBUG/DEMO MODE: replace with real repository data in a later phase.
	private val debugGenerator = CommunityAlertsDebugGenerator(matcher)
	private var alertsLayer: CommunityAlertsLayer? = null
	private var demoAlertUsesGps = false
	private var showingRouteDemoAlerts = false
	private var listenersRegistered = false
	private val locationListener = OsmAndLocationListener { location ->
		refreshDebugDemoAlerts(location)
	}
	private val routeListener = object : IRouteInformationListener {
		override fun newRouteIsCalculated(newRoute: Boolean, showToast: ValueHolder<Boolean>) {
			refreshDebugDemoAlerts()
		}

		override fun routeWasCancelled() {
			refreshDebugDemoAlerts()
		}

		override fun routeWasFinished() {
			refreshDebugDemoAlerts()
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
		refreshDebugDemoAlerts(app.locationProvider.lastKnownLocation)
		return true
	}

	override fun disable(app: OsmandApplication) {
		unregisterListeners()
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

	override fun updateLocation(location: Location?) {
		if (!showingRouteDemoAlerts) {
			location?.let(::updateDemoAlertLocation)
		}
	}

	private fun updateDemoAlertLocation(location: Location) {
		if (!demoAlertUsesGps && isValidLocation(location)) {
			demoAlertUsesGps = true
			repository.replaceWithDemoAlertNear(location.latitude, location.longitude)
		}
	}

	private fun refreshDebugDemoAlerts(location: Location? = null) {
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
		if (routeGeometry != null) {
			val debugAlerts = debugGenerator.createRouteDemoAlerts(
				routeGeometry = routeGeometry,
				currentRouteIndex = route.currentRoute,
				currentPosition = currentPosition
			)
			if (debugAlerts.isNotEmpty()) {
				showingRouteDemoAlerts = true
				repository.replaceAlerts(debugAlerts)
				return
			}
		}

		if (showingRouteDemoAlerts) {
			showingRouteDemoAlerts = false
			if (currentPosition != null && isValidLocation(currentPosition)) {
				demoAlertUsesGps = true
				repository.replaceWithDemoAlertNear(currentPosition.latitude, currentPosition.longitude)
			} else {
				demoAlertUsesGps = false
				repository.replaceWithFallbackDemoAlert()
			}
		} else if (currentPosition != null) {
			updateDemoAlertLocation(currentPosition)
		}
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
	}
}
