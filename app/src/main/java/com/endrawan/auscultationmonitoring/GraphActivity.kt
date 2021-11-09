package com.endrawan.auscultationmonitoring

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.endrawan.auscultationmonitoring.databinding.ActivityGraphBinding
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

class GraphActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGraphBinding

    private val dummyObjects = listOf(
        1, 3, 5, 7, 9, 7, 5, 3, 1
    )

    private val ADD_DATA_INTERVAL: Long = 10
    private val REFRESH_DATA_INTERVAL: Long = 100 // 0.1 Seconds

    private val CHART_X_RANGE_VISIBILITY = 200

    private val lineDataSet = LineDataSet(ArrayList<Entry>(), "Suara terbaca")
    private val lineData = LineData(lineDataSet)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGraphBinding.inflate(layoutInflater)
        setContentView(binding.root)

        for (i in dummyObjects.indices) {
            lineDataSet.addEntry(Entry(i.toFloat(), dummyObjects[i].toFloat()))
        }
        styleLineDataSet()
        binding.chart.data = lineData
        binding.chart.invalidate()
        addNewDataPeriodically()
        refreshGraphPeriodically()
    }

    private fun addNewDataPeriodically() {
        val addDataHandler = Handler(Looper.getMainLooper())
        val addDataCode = object: Runnable {
            override fun run() {
                lineDataSet.addEntry(Entry(lineDataSet.entryCount.toFloat(), (0..50).random().toFloat()))
                addDataHandler.postDelayed(this, ADD_DATA_INTERVAL)
            }
        }
        addDataHandler.post(addDataCode)
    }

    private fun refreshGraphPeriodically() {
        val refreshDataHandler = Handler(Looper.getMainLooper())
        val refreshDataCode = object: Runnable {
            override fun run() {
                lineData.notifyDataChanged()
                binding.chart.notifyDataSetChanged()
                binding.chart.setVisibleXRangeMaximum(CHART_X_RANGE_VISIBILITY.toFloat())
                binding.chart.moveViewTo(
                    lineDataSet.entryCount - CHART_X_RANGE_VISIBILITY - 1f,
                    50f,
                    YAxis.AxisDependency.LEFT)

                refreshDataHandler.postDelayed(this, REFRESH_DATA_INTERVAL)
            }
        }
        refreshDataHandler.post(refreshDataCode)
    }

    private fun styleLineDataSet() {
        lineDataSet.setDrawValues(false)
        lineDataSet.setDrawCircles(false)
    }
}