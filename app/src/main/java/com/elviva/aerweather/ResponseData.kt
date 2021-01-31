package com.elviva.aerweather

//For each JSONObject and JSONArray inside we create corresponding data classes

data class ResponseData(
    val message : String,
    val user_id : Int,
    val name : String,
    val email : String,
    val mobile : Long,
    val profile_details : ProfileDetails, //JSONObject
    val data_list: List<DataListDetail> //JSONArray (list)
)

data class ProfileDetails(
    val is_profile_complete : Boolean,
    val rating : Double
)

data class DataListDetail(
    val id : Int,
    val value : String
)