package com.example.covidtrackerapp

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.covidtrackerapp.Metric.NEGATIVE
import com.example.covidtrackerapp.databinding.ActivityMainBinding
import com.google.gson.GsonBuilder
import com.robinhood.spark.SparkAdapter
import com.robinhood.spark.SparkView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.robinhood.ticker.TickerUtils
import org.angmarch.views.NiceSpinner


private const val BASE_URL = "https://covidtracking.com/api/v1/"
class MainActivity : AppCompatActivity() {
    private lateinit var sparkView: SparkView
    private lateinit var adapter: SparkAdapter
    private lateinit var perStateDailyData: Map<String, List<CovidData>>
    private lateinit var nationalDailyData: List<CovidData>
    private lateinit var binding: ActivityMainBinding
    var metric = Metric.POSITIVE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create()
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        val covidService = retrofit.create(CovidService::class.java)

        //Fetch National data
        covidService.getNationalData().enqueue(object : Callback<List<CovidData>> {
            override fun onResponse(
                call: Call<List<CovidData>>,
                response: Response<List<CovidData>>
            ) {
                Log.i(TAG, "onResponse $response")
                val nationalData = response.body()
                if (nationalData == null) {
                    Log.w(TAG, "Did not receive a valid response body")
                    return
                }

                //Starting with the oldest data first
                setupEventListeners()
                nationalDailyData = nationalData.reversed()
                Log.i(TAG, "Update graph with national data")
                updateDisplayWithData(nationalDailyData)
            }

            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG, "onFailure $t")
            }
        })


        //Fetch State data
        covidService.getStatesData().enqueue(object : Callback<List<CovidData>> {
            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG, "onFailure $t")
            }


            override fun onResponse(
                call: Call<List<CovidData>>,
                response: Response<List<CovidData>>
            ) {
                Log.i(TAG, "onResponse $response")
                val statesData = response.body()
                if (statesData == null) {
                    Log.w(TAG, "Did not receive a valid response body")
                    return
                }

                //Starting with the oldest data first
                perStateDailyData = statesData
                    .filter { it.dateChecked != null }
                    .map {
                        CovidData(
                            it.dateChecked,
                            it.positiveIncrease.coerceAtLeast(0),
                            it.negativeIncrease.coerceAtLeast(0),
                            it.deathIncrease.coerceAtLeast(0),
                            it.state
                        )}
                    .reversed()
                    .groupBy {it.state}
                Log.i(TAG, "Update spinner with state data")
                updateSpinnerWithStateData(perStateDailyData.keys)
            }
        })
    }

    private fun updateSpinnerWithStateData(stateNames: Set<String>) {
        val stateAbbreviationList = stateNames.toMutableList()
        stateAbbreviationList.sort()
        stateAbbreviationList.add(0, ALL_STATES)
        spinnerSelect.attachDataSource(stateAbbreviationList)
        spinnerSelect.setOnSpinnerITemSelectedLIstener {parent, _, position, _ ->
            val selectedState = parent.getItemAtPosition(position) as String
            val selectedData = perStateDailyData[selectedState] ?: nationalDailyData
            updateDisplayWithData(selectedData)
        }
    }

    private fun setupEventListeners() {
        //Add listener for user scrubbing on chart
        sparkView.isScrubEnabled = true
        sparkView.setScrubListener { itemData ->
            if (itemData is CovidData) {
                updateInfoForDate(itemData)
            }
        }
        //Respond to radio button selected events
        binding.radioGroupTimeSelection.setOnCheckedChangeListener { _, checkedId ->
            adapter.daysAgo = when (checkedId) {
                R.id.radioButtonWeek -> TimeScale.WEEK
                R.id.radioButtonMonth -> TimeScale.MONTH
                else -> TimeScale.MAX
            }
            adapter.notifyDataSetChanged()
        }
        binding.radioGroupMetricSelection.setOnCheckedChangeListener {_, checkedId ->
            when(checkedId) {
                R.id.radioButtonPositive -> updateDisplayMetric(Metric.POSITIVE)
                R.id.radioButtonNegative -> updateDisplayMetric(NEGATIVE)
                else -> updateDisplayMetric(Metric.DEATH)
            }
        }
    }

    private fun updateDisplayMetric(metric: Metric): Any {
        adapter.metric = metric
        adapter.notifyDataSetChanged()
    }

    private fun updateDisplayWithData(dailyData: List<CovidData>) {
        //Create new SparkAdapter with data
        adapter = CovidSparkAdapter(dailyData)
        SparkView.adapter = adapter
        //Update radio buttons to select positive cases and max time by default
        binding.radioButtonPositive.isChecked = true
        binding.radioButtonMax.isChecked = true
        //Display metric for most recent date

        updateInfoForDate(dailyData.last())

    }

    private fun updateInfoForDate(covidData: CovidData) {
        val numCases = when (adapter.metric) {
            Metric.NEGATIVE -> covidData.negativeIncrease
            Metric.POSITIVE -> covidData.positiveIncrease
            else -> covidData.deathIncrease
        }
        binding.tvMetricLabel.text = NumberFormat.getInstance().format(numCases)
        val outputDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        binding.tvDateLabel.text = outputDateFormat.format(covidData.dateChecked)
    }
}