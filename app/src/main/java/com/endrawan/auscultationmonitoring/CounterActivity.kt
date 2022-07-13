package com.endrawan.auscultationmonitoring

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.endrawan.auscultationmonitoring.configs.Config
import com.endrawan.auscultationmonitoring.configs.Config.AUDIO_FREQUENCY
import com.endrawan.auscultationmonitoring.databinding.ActivityCounterBinding
import com.endrawan.auscultationmonitoring.helpers.CounterHelper
import com.endrawan.auscultationmonitoring.utils.SmoothedZScore
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlin.collections.ArrayList

class CounterActivity : AppCompatActivity() {

    private val TAG = "CounterActivity"
    private val ADD_DATA_INTERVAL: Long = 25
    private val CHART_X_RANGE_VISIBILITY = 100
    private val CHART_Y_RANGE_VISIBILITY = 8f

    private lateinit var binding: ActivityCounterBinding

    private val lag = 30
    private val threshold = 5f
    private val influence = 0.0

    private val smoothedZScore = SmoothedZScore(lag, threshold.toDouble(), influence)
    private val counterHelper = CounterHelper(AUDIO_FREQUENCY, CounterHelper.HEART_TYPE)

    private val rawDataSet = LineDataSet(ArrayList<Entry>(), "Raw Signal")
    private val avgFilterDataSet = LineDataSet(ArrayList<Entry>(), "AvgFilter")
    private val positiveStdDataSet = LineDataSet(ArrayList<Entry>(), "AvgFilter + Std")
    private val negativeStdDataSet = LineDataSet(ArrayList<Entry>(), "AvgFilter - Std")
    private val peaksDataSet = LineDataSet(ArrayList<Entry>(), "Peaks")

    private val rawChartData = LineData(listOf(
        rawDataSet, avgFilterDataSet, positiveStdDataSet, negativeStdDataSet
    ))

    private val peaksChartData = LineData(peaksDataSet)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCounterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        styleLineDataSet()
        binding.rawChart.data = rawChartData
        binding.peakChart.data = peaksChartData
        binding.rawChart.invalidate()
        binding.peakChart.invalidate()

        addNewDataPeriodically()
    }

    private fun addNewDataPeriodically() {
        val addDataHandler = Handler(Looper.getMainLooper())
        val addDataCode = object: Runnable {
            override fun run() {
                val n = rawDataSet.entryCount
                val newData = Config.dummySignal[n]
                rawDataSet.addEntry(Entry(n.toFloat(), newData.toFloat()))

                val result = smoothedZScore.update(newData.toDouble())
//                val peak = result.first.toFloat()
//                val avgFilter = result.second.toFloat()
//                val stdFilter = result.third.toFloat()
//
//                val peaksIndexes = counterHelper.update(result.first)

//                Log.d(TAG, "Peak detected: ${peaksIndexes.size}")
//                peaksIndexes.forEachIndexed { index, value ->
//                    Log.d(TAG, "Peak-$index = $value")
//                }

                avgFilterDataSet.addEntry(Entry(avgFilterDataSet.entryCount.toFloat(), result.average.toFloat()))
                negativeStdDataSet.addEntry(Entry(negativeStdDataSet.entryCount.toFloat(), result.getNegativeDeviation(threshold).toFloat()))
                positiveStdDataSet.addEntry(Entry(positiveStdDataSet.entryCount.toFloat(), result.getPositiveDeviation(threshold).toFloat()))

                peaksDataSet.addEntry(Entry(peaksDataSet.entryCount.toFloat(), result.peak.toFloat()))

                refreshGraph()
                if (n + 1 != Config.dummySignal.size) {
                    addDataHandler.postDelayed(this, ADD_DATA_INTERVAL)
                } else {
                    val bpm = counterHelper.getHeartBpm()
                    val hrv = counterHelper.getHrv()
                    val description = "Heart Rate: $bpm bpm \t HRV: $hrv ms"
                    Log.d(TAG, description)
                    Toast.makeText(this@CounterActivity, description, Toast.LENGTH_LONG).show()
//                    binding.peakChart.contentDescription = description
                }
            }
        }
        addDataHandler.post(addDataCode)
    }

    private fun refreshGraph() {
        rawChartData.notifyDataChanged()
        binding.rawChart.notifyDataSetChanged()
        binding.rawChart.setVisibleXRangeMaximum(CHART_X_RANGE_VISIBILITY.toFloat())
        binding.rawChart.moveViewTo(
            rawChartData.entryCount - CHART_X_RANGE_VISIBILITY - 1f,
            CHART_Y_RANGE_VISIBILITY,
            YAxis.AxisDependency.LEFT)

        peaksChartData.notifyDataChanged()
        binding.peakChart.notifyDataSetChanged()
        binding.peakChart.setVisibleXRangeMaximum(CHART_X_RANGE_VISIBILITY.toFloat())
        binding.peakChart.moveViewTo(
            peaksDataSet.entryCount - CHART_X_RANGE_VISIBILITY - 1f,
            CHART_Y_RANGE_VISIBILITY,
            YAxis.AxisDependency.LEFT)

    }

    private fun styleLineDataSet() {
        val colorYellow = Color.parseColor("#FFBD35")
        val colorGreen = Color.parseColor("#3FA796")
        val colorLightPurple = Color.parseColor("#3FA796")
        val colorDarkPurple = Color.parseColor("#502064")
        val colorRed = Color.parseColor("#781D42")

        rawDataSet.setDrawValues(false)
        rawDataSet.setDrawCircles(false)
        rawDataSet.color = colorYellow

        avgFilterDataSet.setDrawValues(false)
        avgFilterDataSet.setDrawCircles(false)
        avgFilterDataSet.color = colorGreen

        positiveStdDataSet.setDrawValues(false)
        positiveStdDataSet.setDrawCircles(false)
        positiveStdDataSet.color = colorLightPurple

        negativeStdDataSet.setDrawValues(false)
        negativeStdDataSet.setDrawCircles(false)
        negativeStdDataSet.color = colorDarkPurple

        peaksDataSet.setDrawValues(false)
        peaksDataSet.setDrawCircles(false)
        peaksDataSet.color = colorRed
    }
}