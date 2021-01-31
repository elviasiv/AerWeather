package com.elviva.aerweather

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.AnimationDrawable
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import com.elviva.aerweather.databinding.ActivityMainBinding
import com.elviva.models.WeatherResponse
import com.elviva.network.WeatherService
import com.github.matteobattilana.weather.PrecipType
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.jaeger.library.StatusBarUtil
import com.karumi.dexter.Dexter
import com.karumi.dexter.DexterBuilder
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding : ActivityMainBinding

    private lateinit var mFusedLocationClient : FusedLocationProviderClient // Required for getting location of LAT and LONG
    private var mProgressDialog : Dialog? = null

    lateinit var weatherType : PrecipType

    private lateinit var mSharedPreferences : SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        StatusBarUtil.setTransparent(this)

        weatherType = PrecipType.CLEAR

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE__NAME, Context.MODE_PRIVATE)

        setListeners()
        setupUI()
        getPermissions()
       // CallAPILoginAsyncTask("Kendrick", "Lmao").execute()
    }

    private fun getLocationWeatherDetails(name : String){
        if(Constants.isNetworkAvailable(this)){

            //Using retrofit2
            val retrofit : Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service : WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)

            val listCall : Call<WeatherResponse> = service.getWeatherByCityName(
                name, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showCustomProgressDialog()

            //We call it here in the background
            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful) {
                        dismissDialog()

                        val weatherList: WeatherResponse? = response.body()
                        Log.i("RESPONSE RESULT", "$weatherList")

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setupUI()
                    } else {
                        val responseCode = response.code()
                        when (responseCode) {
                            400 -> {
                                Log.e("ERROR 400", "BAD CONNECTION")
                                dismissDialog()
                                Toast.makeText(this@MainActivity, "Something wrong with Your internet connection. Try again.", Toast.LENGTH_LONG).show()
                            }
                            404 -> {
                                Log.e("ERROR 404", "NOT FOUND")
                                dismissDialog()
                                Toast.makeText(this@MainActivity, "The city You entered doesnt exist!", Toast.LENGTH_LONG).show()
                            }
                            else -> {
                                Log.e("ERROR", "$responseCode")
                                dismissDialog()
                                Toast.makeText(this@MainActivity, "Something went wrong...", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    dismissDialog()
                    Log.e("ERROR", t.message.toString())
                }

            })

        } else {
            Toast.makeText(this@MainActivity, "You dont have internet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getLocationWeatherDetails(latitude : Double, longitude : Double){
        if(Constants.isNetworkAvailable(this)){

            //Using retrofit2
            val retrofit : Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service : WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)

            val listCall : Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showCustomProgressDialog()

            //We call it here in the background
            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                   if(response.isSuccessful){
                       dismissDialog()

                       val weatherList : WeatherResponse? = response.body()
                       Log.i("RESPONSE RESULT", "$weatherList")

                       val weatherResponseJsonString = Gson().toJson(weatherList)
                       val editor = mSharedPreferences.edit()
                       editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                       editor.apply()

                       setupUI()
                   } else {
                       val responseCode = response.code()
                       when(responseCode){
                           400 -> Log.e("ERROR 400", "BAD CONNECTION")
                           404 -> Log.e("ERROR 404", "NOT FOUND")
                           else -> Log.e("ERROR", "$responseCode")
                       }
                   }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    dismissDialog()
                    Log.e("ERROR", t.message.toString())
                }

            })

        } else {
            Toast.makeText(this@MainActivity, "You dont have internet", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData(){

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
                mLocationRequest,
                mLocationCallback,
                Looper.myLooper()
        )
        binding.swipeRefresh.isRefreshing = false
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            val mLastLocation: Location = locationResult!!.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude", "$latitude")


            val longitude = mLastLocation.longitude
            Log.i("Current Longitude", "$longitude")

            getLocationWeatherDetails(latitude, longitude)
        }

    }

    //When user denies the permission that app needs to use necessary functionality
    private fun showRationalDialogForPermissions(){
        AlertDialog.Builder(this)
                .setMessage("It looks like you have turned off permissions which is required for this feature. It can be enabled under Application Settings")
                .setPositiveButton("GO TO SETTINGS") {_, _ ->  //Underscore because we do not use the given parameters (two underscores)
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", packageName, null)  // These two lines open the settings for this app.
                        intent.data = uri                                                       // Without them it would just open settings
                        startActivity(intent)
                    } catch (e : ActivityNotFoundException){
                        e.printStackTrace()
                    }
                }
                .setNegativeButton("Cancel") {dialog, _ -> dialog.dismiss()} // Here we use only one parameter - "dialog". The underscore is not used
                .show()
    }

    private fun isLocationEnabled(): Boolean{
        val locationManager : LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun getPermissions(){
        if(!isLocationEnabled()){
            Toast.makeText(this, "Turn on GPS", Toast.LENGTH_LONG).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS) //We send the user straight to Location settings
            startActivity(intent)
        } else {
            //Dexter
            Dexter.withContext(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(p0: MultiplePermissionsReport?) {
                        if (p0!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        }

                        if (p0.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(this@MainActivity, "You have denied location permission.", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(p0: MutableList<PermissionRequest>?, p1: PermissionToken?) {
                        showRationalDialogForPermissions()
                    }
                })
                .onSameThread()
                .check()
        }
    }

    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)

        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)

        mProgressDialog!!.show()
    }

    private fun dismissDialog(){
        if(mProgressDialog != null)
            mProgressDialog!!.dismiss()
    }

    private fun setupUI() {

        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")

        if (!weatherResponseJsonString.isNullOrEmpty()) {

            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)

            for (i in weatherList.weather.indices) {
                Log.i("Weather name", weatherList.weather.toString())

                binding.tvMain.text = weatherList.weather[i].main
                binding.tvMainDescription.text = weatherList.weather[i].description

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    binding.tvTemp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())

                } else {
                    binding.tvTemp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.toString())
                }

                binding.tvHumidity.text = weatherList.main.humidity.toString() + "%"

                binding.tvSpeed.text = weatherList.wind.speed.toString()
                if (Constants.METRIC_UNIT == "metric") {
                    binding.tvSpeedUnit.text = "KM/H"
                } else {
                    binding.tvSpeedUnit.text = "MP/H"
                }

                binding.tvMin.text = weatherList.main.temp_min.toString() + getUnit(application.resources.configuration.toString()) + " L"
                binding.tvMax.text = weatherList.main.temp_max.toString() + getUnit(application.resources.configuration.toString()) + " H"

                binding.tvName.text = weatherList.name
                binding.tvCountry.text = weatherList.sys.country

                binding.tvSunriseTime.text = unixTime(weatherList.sys.sunrise)
                binding.tvSunsetTime.text = unixTime(weatherList.sys.sunset)

                var weatherParticles = 0f
                var weatherSpeed = 0

                when (weatherList.weather[i].icon) {
                    "01d", "01n" -> {                                                       //clear sky
                        if(isNight(weatherList.weather[i].icon)){
                            binding.root.setBackgroundResource(R.drawable.gradient_night_animation)
                        } else {
                            binding.root.setBackgroundResource(R.drawable.gradient_day_animation)
                        }
                        startAnimation()
                        binding.ivMain.setImageResource(R.drawable.sunny)
                    }

                    "02d", "02n" -> {                                                       //few clouds
                        if(isNight(weatherList.weather[i].icon)){
                            binding.root.setBackgroundResource(R.drawable.gradient_night_animation)
                        } else {
                            binding.root.setBackgroundResource(R.drawable.gradient_day_animation)
                        }
                        startAnimation()
                        binding.ivMain.setImageResource(R.drawable.cloud)
                    }

                    "03d", "03n" -> {                                                       //scattered clouds
                        if(isNight(weatherList.weather[i].icon)){
                            binding.root.setBackgroundResource(R.drawable.gradient_night_animation)
                        } else {
                            binding.root.setBackgroundResource(R.drawable.gradient_cloudy_animation)
                        }
                        startAnimation()
                        binding.ivMain.setImageResource(R.drawable.cloud)
                    }

                    "04d", "04n" -> {                                                       //broken clouds
                        if(isNight(weatherList.weather[i].icon)){
                            binding.root.setBackgroundResource(R.drawable.gradient_night_animation)
                        } else {
                            binding.root.setBackgroundResource(R.drawable.gradient_cloudy_animation)
                        }
                        startAnimation()
                        binding.ivMain.setImageResource(R.drawable.cloud)
                    }

                    "09d", "09n" -> {                                                       //shower rain
                        if(isNight(weatherList.weather[i].icon)){
                            binding.root.setBackgroundResource(R.drawable.gradient_night_animation)
                        } else {
                            binding.root.setBackgroundResource(R.drawable.gradient_cloudy_animation)
                        }
                        startAnimation()
                        binding.ivMain.setImageResource(R.drawable.rain)
                        weatherType = PrecipType.RAIN
                        weatherParticles = 120f
                        weatherSpeed = 1000
                    }

                    "10d", "10n" -> {                                                       //rain
                        if(isNight(weatherList.weather[i].icon)){
                            binding.root.setBackgroundResource(R.drawable.gradient_night_animation)
                        } else {
                            binding.root.setBackgroundResource(R.drawable.gradient_cloudy_animation)
                        }
                        startAnimation()
                        binding.ivMain.setImageResource(R.drawable.rain)
                        weatherType = PrecipType.RAIN
                        weatherParticles = 100f
                        weatherSpeed = 800
                       // binding.root.setBackgroundResource(R.drawable.bg_night)
                    }

                    "11d", "11n" -> {                                                       //thunderstorm
                        if(isNight(weatherList.weather[i].icon)){
                            binding.root.setBackgroundResource(R.drawable.gradient_night_animation)
                        } else {
                            binding.root.setBackgroundResource(R.drawable.gradient_cloudy_animation)
                        }
                        startAnimation()
                        binding.ivMain.setImageResource(R.drawable.storm)
                        weatherType = PrecipType.RAIN
                        weatherParticles = 20f
                        weatherSpeed = 600
                    }

                    "13d", "13n" -> {                                                       //snow
                        if(isNight(weatherList.weather[i].icon)){
                            binding.root.setBackgroundResource(R.drawable.gradient_night_animation)
                        } else {
                            binding.root.setBackgroundResource(R.drawable.gradient_cloudy_animation)
                        }
                        startAnimation()
                        binding.ivMain.setImageResource(R.drawable.snowflake)
                        weatherType = PrecipType.SNOW
                        weatherParticles = 10f
                        weatherSpeed = 200
                    }
                }

                //Set the weather animation
                binding.wvWeatherView.apply {
                    setWeatherData(weatherType)
                    speed = weatherSpeed
                    emissionRate = weatherParticles
                    angle = (-45..45).random()
                    fadeOutPercent = .90f
                }
            }
        }
    }

    private fun isNight(value : String) : Boolean {
        return value.contains("n")
    }

    private fun startAnimation(){
        val animDrawable = binding.root.background as AnimationDrawable
        animDrawable.setEnterFadeDuration(10)
        animDrawable.setExitFadeDuration(5000)
        animDrawable.start()
    }

    private fun getUnit(value : String) : String? {
        var value = "°C"
        if("US" == value || "LR" == value || "MM" == value)
            value = "°F"

        return value
    }
    //convert timestamp to human date
    private fun unixTime(timex : Long) : String? {
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    private fun setListeners(){
        //Search field listener
        binding.etSearch.setOnEditorActionListener { _, keyCode, event ->
            if (((event?.action ?: -1) == KeyEvent.ACTION_DOWN) || keyCode == EditorInfo.IME_ACTION_DONE) {
                Log.i("KEYCODE", binding.etSearch.text.toString())
                getLocationWeatherDetails(binding.etSearch.text.toString())
                binding.etSearch.setText("")
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }

        //Pull down to refresh listener
        binding.swipeRefresh.setOnRefreshListener {
            Log.i("REFRESH", "refreshed")
            requestLocationData()

        }
    }

/*
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.i("MENU PRESSED", "You pressed the menu")
        return when(item.itemId){
            R.id.item_refresh -> {
                requestLocationData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

 */

/*
    private inner class CallAPILoginAsyncTask(val username : String, val password : String): AsyncTask<Any, Void, String>(){

        private lateinit var customProgressDialog : Dialog

        override fun onPreExecute() {
            super.onPreExecute()

            showProgressDialog()
        }

        override fun doInBackground(vararg p0: Any?): String {
            var result : String

            var connection : HttpURLConnection? = null
            try {
                val url = URL("https://run.mocky.io/v3/6c67998a-3806-4aec-b7b7-ee70af7f2f09")
                connection = url.openConnection() as HttpURLConnection
                connection.doInput = true //Do we get data? yes
                connection.doOutput = true //Do we send data? yes

                connection.instanceFollowRedirects = false

                //Post method
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("charset", "utf-8")
                connection.setRequestProperty("Accept", "application/json")

                connection.useCaches = false

                val writeDataOutputStream = DataOutputStream(connection.outputStream)
                val jsonRequest = JSONObject()
                jsonRequest.put("username", username)
                jsonRequest.put("password", password)

                writeDataOutputStream.writeBytes(jsonRequest.toString())

                writeDataOutputStream.flush()
                writeDataOutputStream.close()
                //----

                val httpResult : Int = connection.responseCode

                //If everything is OK we read the data in inputStream with reader
                if(httpResult == HttpURLConnection.HTTP_OK){
                    val inputStream = connection.inputStream

                    val reader = BufferedReader(InputStreamReader(inputStream))

                    val stringBuilder = StringBuilder()
                    var line : String?
                    try {
                        while (reader.readLine().also { line = it } != null) {//while streamreader has something to read
                            stringBuilder.appendLine(line + "\n")
                        }
                    } catch (e: IOException){
                        e.printStackTrace()
                    } finally {
                        try {
                            inputStream.close()
                        } catch (e: IOException){
                            e.printStackTrace()
                        }
                    }
                    result = stringBuilder.toString()
                } else{
                    result = connection.responseMessage
                }

            } catch (e: SocketTimeoutException){
                result = "Connection time out..."
                e.printStackTrace()
            } catch (e: Exception){
                result = "Error : " + e.message
            } finally {
                connection?.disconnect()
            }

            return result
        }


        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)

            cancelProgressDialog()

            Log.i("JSON RESPONSE RESULT", result)

            val responseData = Gson().fromJson(result, ResponseData::class.java) //GSON will map all the data from "result" to the data fields that we have in ResponseData class (awesome)
            Log.i("GSON_MESSAGE", responseData.message)
            Log.i("GSON_USER_ID", "${responseData.user_id}")
            Log.i("GSON_NAME   ", responseData.name)
            Log.i("GSON_EMAIL  ", responseData.email)
            Log.i("GSON_MOBILE ", "${responseData.mobile}")

            Log.i("GSON_PROFILE_DETAILS", "Is profile complete : ${responseData.profile_details.is_profile_complete}")
            Log.i("GSON_PROFILE_DETAILS", "Rating : ${responseData.profile_details.rating}")

            for(items in responseData.data_list.indices){
                Log.i("GSON_DATA_LIST", "${responseData.data_list.size}")

                val id = responseData.data_list[items].id
                val value = responseData.data_list[items].value

                Log.i("GSON_DATA_LIST_ID   ", "$id")
                Log.i("GSON_DATA_LIST_VALUE", value)
            }
/*
            //Picking apart JSON data
            val jsonObject = JSONObject(result)

            val message = jsonObject.optString("message")
            Log.i("MESSAGE", message)

            //Accessing JSONObject inside JSONObject
            val profileDetailsOBJ = jsonObject.optJSONObject("profile_details")
            val isProfileCompleted = profileDetailsOBJ.optBoolean("is_profile_completed")
            Log.i("IS_PROFILE_COMPLETE", isProfileCompleted.toString())

            //Accessing the list inside JSONObject
            val dataListArray = jsonObject.optJSONArray("data_list")
            Log.i("DATA_LIST_LENGTH", "${dataListArray.length()}")

            //Looping through the list
            for(item in 0 until dataListArray.length()){
                Log.i("DATA_LIST_ITEM", "${dataListArray[item]}")


                val dataItemObj : JSONObject = dataListArray[item] as JSONObject    //Getting the JSONObjects from the list
                val dataItemId = dataItemObj.optInt("id")                    //Getting id from the object
                val dataItemValue = dataItemObj.optString("value")

                Log.i("DATA_OBJECT_ID",  "$item : " + "${dataItemId}")
                Log.i("DATA_OBJECT_VALUE", "$item : " + "${dataItemValue}")
            }*/
        }

        //Show the dialog layout
        private fun showProgressDialog(){
            customProgressDialog = Dialog(this@MainActivity)
            customProgressDialog.setContentView(R.layout.dialog_custom_progress)
            customProgressDialog.show()
        }
        //Cancel the dialog
        private fun cancelProgressDialog(){
            customProgressDialog.dismiss()
        }
    }

 */
}

