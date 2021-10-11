package com.example.covidtrackerapp

import com.google.gson.annotations.SerializedName
import java.util.*


//For optimization, can delete SerializedName and parameter

data class CovidData(

    @SerializedName("dateChecked") val dateChecked : Date,
    @SerializedName("positiveIncrease") val positiveIncrease : Int,
    @SerializedName("negativeIncrease") val negativeIncrease : Int,
    @SerializedName("deathIncrease") val deathIncrease : Int,
    @SerializedName("state") val state : String
)