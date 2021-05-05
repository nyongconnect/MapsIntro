package com.example.mapintro

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.PersistableBundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.JsonObject
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.geocoding.v5.GeocodingCriteria
import com.mapbox.api.geocoding.v5.MapboxGeocoding
import com.mapbox.api.geocoding.v5.models.CarmenFeature
import com.mapbox.api.geocoding.v5.models.GeocodingResponse
import com.mapbox.core.constants.Constants
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.places.autocomplete.PlaceAutocomplete
import com.mapbox.mapboxsdk.plugins.places.autocomplete.model.PlaceOptions
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.*
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.utils.BitmapUtils
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import com.microsoft.appcenter.AppCenter
import com.microsoft.appcenter.analytics.Analytics
import com.microsoft.appcenter.crashes.Crashes
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity(), Callback<DirectionsResponse>,  PermissionsListener {


    private lateinit var source: LatLng
    private var c: Int = 0
    private var mapView:MapView? = null
    private lateinit var mapboxMap:MapboxMap
    private lateinit var locationComponent: LocationComponent
    private var permissionManager: PermissionsManager? = null
    private var home: CarmenFeature? = null
    private var work: CarmenFeature? = null
    private var navigation: MapboxNavigation? = null
    private lateinit var client: MapboxDirections
    private var geoJsonSourceLayerId: String = "geoJsonSourceLayerId"
    private var symbolIconId: String = "symbolIconId"
    private var st: String = ""
    private lateinit var dv: TextView
    private var startLocation: String = ""
    private var endLocation: String = ""
    private var origin: Point = Point.fromLngLat(90.399452, 23.777176)
    private var destination: Point = Point.fromLngLat(90.3994, 23.777)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))

        setContentView(R.layout.activity_main)
        AppCenter.start(application, "6de77a9f-ab32-4191-817d-4732703338f8",
                Analytics::class.java, Crashes::class.java)

        navigation = MapboxNavigation(this, getString(R.string.mapbox_access_token))
        mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)
        dv = findViewById(R.id.distanceView)


        mapView?.getMapAsync { mapboxMap ->

            this.mapboxMap = mapboxMap
            mapboxMap.setStyle(Style.MAPBOX_STREETS) {

                enableLocationComponent(it) //function to show users location
                initSearchFab() // function to initialize location search

                addUserLocations() //function to add default location in auto complete search
                var drawable: Drawable? = ResourcesCompat.getDrawable(resources, R.drawable.ic_arrow_head, null)
                var mBitMap: Bitmap = BitmapUtils.getBitmapFromDrawable(drawable)!!
                it.addImage(symbolIconId, mBitMap)

                setUpSource(it) //create emppty geoJson source using emptyfeature collection
                setUpLayer(it) // set up new symbol layer for displaying search location

                initSource(it)
                initLayer(it)

                mapboxMap.addOnMapClickListener {
                    if (c == 0) {
                        origin = Point.fromLngLat(it.longitude, it.latitude)
                        //source = it
                        var markerOPtions: MarkerOptions = MarkerOptions()
                        markerOPtions.position(it)
                        markerOPtions.title("source")
                        mapboxMap.addMarker(markerOPtions)
                        reverseGeoCodeFun(it, c)

                    }

                    if (c == 1) {
                        destination = Point.fromLngLat(it.longitude, it.latitude)
                        getRoute(mapboxMap,origin, destination)
                        var markerOPtions2: MarkerOptions = MarkerOptions()
                        markerOPtions2.position(it)
                        markerOPtions2.title("destination")
                        mapboxMap.addMarker(markerOPtions2)
                        reverseGeoCodeFun(it, c)

                        getRoute(mapboxMap,origin, destination)

                    }

                    if (c > 1) {
                        c = 0
                        recreate()
                    }

                    c++
                 return@addOnMapClickListener true
                }
            }

        }
    }

    private fun enableLocationComponent(it: Style) {

        if (PermissionsManager.areLocationPermissionsGranted(this)) {

            locationComponent =mapboxMap.locationComponent

            locationComponent.activateLocationComponent(
                LocationComponentActivationOptions.builder(this, it).build()
            )

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

            locationComponent.isLocationComponentEnabled = true

            locationComponent.setCameraMode(CameraMode.TRACKING)

            locationComponent.renderMode = RenderMode.COMPASS
        }
        else {
            permissionManager = PermissionsManager(this)
            permissionManager!!.requestLocationPermissions(this)
        }
    }

    private fun setUpLayer(it: Style) {
        it.addLayer(SymbolLayer("SYMBOL_LAYER_ID", geoJsonSourceLayerId).withProperties(
            iconImage(symbolIconId),
            iconOffset(arrayOf(0f, -8f))
        ))
    }

    private fun setUpSource(it: Style) {
        it.addSource(GeoJsonSource(geoJsonSourceLayerId))
    }

    private fun addUserLocations() {
        home = CarmenFeature.builder().text("Mapbox sf office")
            .geometry(Point.fromLngLat(-122.3964485,37.7912561))
            .placeName("50 beal st, san francisco, CA")
            .id("mapbox-sf")
            .properties(JsonObject())
            .build()

        work = CarmenFeature.builder().text("Mapbox dc office")
            .geometry(Point.fromLngLat(-77.0338348,38.899750))
            .placeName("50 beal st, san francisco, CA")
            .id("mapbox-dc")
            .properties(JsonObject())
            .build()
    }

    private fun getRoute(mapboxMap: MapboxMap, origin: Point, destination: Point?) {
        client = MapboxDirections.builder()
            .origin(origin)
            .destination(destination!!)
            .overview(DirectionsCriteria.OVERVIEW_FULL)
            .profile(DirectionsCriteria.PROFILE_DRIVING)
            .accessToken(getString(R.string.mapbox_access_token))
            .build()
        client.enqueueCall(this)
    }

    private fun navigationRoute() {
        NavigationRoute.builder(this)
            .accessToken(getString(R.string.mapbox_access_token))
            .origin(origin)
            .destination(destination)
            .build()
            .getRoute(object : Callback<DirectionsResponse> {
                override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                }

                override fun onResponse(
                    call: Call<DirectionsResponse>,
                    response: Response<DirectionsResponse>
                ) {
                    if (response.body() == null) {
                        return
                    } else if (response.body()!!.routes().size < 1) {
                        return
                    }
                    else {
                        var route: DirectionsRoute = response.body()!!.routes().get(0)

                        var navigateLauncherOptions: NavigationLauncherOptions = NavigationLauncherOptions.builder()
                            .directionsRoute(route)
                            .shouldSimulateRoute(true)
                            .build()

                        NavigationLauncher.startNavigation(this@MainActivity, navigateLauncherOptions)
                    }
                }

            })
    }

    private fun initLayer(it: Style) {
        var routeLayer: LineLayer = LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID)
        routeLayer.setProperties(
            lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            lineWidth(5f),
        lineColor(Color.parseColor(getString(R.string.line_color))))
        it.addLayer(routeLayer)

        it.addImage(RED_PIN_ICON_ID,
            BitmapUtils.getBitmapFromDrawable(getResources().getDrawable(R.drawable.ic_arrow_head))!!)


//        it.addLayer(SymbolLayer(ICON_LAYER_ID, ICON_SOURCE_ID).withProperties(
//            PropertyFactory.iconImage(RED_PIN_ICON_ID),
//            PropertyFactory.iconIgnorePlacement(true),
//            PropertyFactory.iconAllowOverlap(true),
//            PropertyFactory.iconOffset(arrayOf(0f, -9f))
//        ))

    }

    private fun initSource(it: Style) {
        it.addSource(GeoJsonSource(ROUTE_SOURCE_ID))
        var iconGeoJsonSource: GeoJsonSource = GeoJsonSource(ICON_SOURCE_ID, FeatureCollection.fromFeatures(
            arrayOf(
                Feature.fromGeometry(Point.fromLngLat(origin.longitude(),origin.latitude())),
                Feature.fromGeometry(Point.fromLngLat(destination.longitude(),destination.latitude()))
            )))
        it.addSource(iconGeoJsonSource)
    }

    private fun reverseGeoCodeFun(point: LatLng, c: Int) {
        var geoCoding: MapboxGeocoding = MapboxGeocoding.builder()
            .accessToken(getString(R.string.mapbox_access_token))
            .query(Point.fromLngLat(point.longitude,point.latitude))
            .geocodingTypes(GeocodingCriteria.TYPE_POI)
            .build()


        geoCoding.enqueueCall(object : Callback<GeocodingResponse> {


            override fun onResponse(call: Call<GeocodingResponse>, response: Response<GeocodingResponse>) {
              var results: List<CarmenFeature>? = response.body()?.features()
                if (results?.size!! > 0) {

                    var firstResultPoint: Point? = results[0].center()
                    var feature: CarmenFeature = results[0]



                    if (c==0) {
                        startLocation +=feature.placeName()
                        val tv:TextView = findViewById(R.id.s)
                        tv.text = startLocation
                    }

                    if (c==1) {
                        endLocation+=feature.placeName()
                        val tv2:TextView = findViewById(R.id.d)
                        tv2.text = endLocation
                    }


                }
                else {
                    Toast.makeText(this@MainActivity, "not found", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<GeocodingResponse>, t: Throwable) {
                t.printStackTrace()            }

        })

    }

    fun confirmed(view: View) {
        navigationRoute()
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        TODO("Not yet implemented")
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            mapboxMap.getStyle {
                enableLocationComponent(it)
            }
        }
        else {
            finish()

        }

    }

override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {

    }

    override fun onResponse(
        call: Call<DirectionsResponse>,
        response: Response<DirectionsResponse>
    ) {
        if (response.body() == null) {
            return
        }

        else if( response.body()!!.routes().size < 1) {
            return
        }

        val currentRoute: DirectionsRoute = response.body()!!.routes().get(0)
        var distance = currentRoute.distance()?.div(1000)
        st = String.format("%.2f k.m", distance)
        dv.text = st

        if (mapboxMap != null) {
            mapboxMap.getStyle(object: Style.OnStyleLoaded {
                override fun onStyleLoaded(style: Style) {
                    var geoJsonSource: GeoJsonSource? = style.getSourceAs(ROUTE_SOURCE_ID)
                    if (geoJsonSource != null) {
                        geoJsonSource.setGeoJson(LineString.fromPolyline(currentRoute.geometry()!!, Constants.PRECISION_6))

                    }
                }

            })
        }

    }

    private fun initSearchFab() {
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
          var intent =   PlaceAutocomplete.IntentBuilder()
                .accessToken(Mapbox.getAccessToken()?: getString(R.string.mapbox_access_token))
                .placeOptions(PlaceOptions.builder()
                    .backgroundColor(Color.parseColor("#EEEEEE"))
                    .limit(10)
                    .addInjectedFeature(home)
                    .addInjectedFeature(work)
                    .build(PlaceOptions.MODE_CARDS))
                .build(this)
            startActivityForResult(intent, REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode== Activity.RESULT_OK  && requestCode == REQUEST_CODE) {

            var selectedCarmenFeature = PlaceAutocomplete.getPlace(data)

            if (mapboxMap != null) {
                var style = mapboxMap.getStyle()

                if (style != null) {
                    var newSource = style.getSourceAs<GeoJsonSource>(geoJsonSourceLayerId)
                    if(newSource != null) {
                        newSource.setGeoJson(FeatureCollection.fromFeature(
                            Feature.fromJson(selectedCarmenFeature.toJson())
                        ))

                    }

                    mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(
                                LatLng((selectedCarmenFeature.geometry() as Point).latitude(), (selectedCarmenFeature.geometry() as Point).longitude()))
                            .zoom(14.0)
                            .build()), 4000)

                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }

    companion object {
        private const val REQUEST_CODE: Int = 5678
        private const val ROUTE_LAYER_ID: String = "route-layer-id"
        private const val ROUTE_SOURCE_ID: String = "route-source-id"
        private const val ICON_LAYER_ID: String = "icon-layer-id"
        private const val ICON_SOURCE_ID: String = "icon-source-id"
        private const val RED_PIN_ICON_ID: String = "red-pin-icon-id"
    }



}