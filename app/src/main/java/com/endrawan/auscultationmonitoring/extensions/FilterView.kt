package com.endrawan.auscultationmonitoring.extensions

import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import com.endrawan.auscultationmonitoring.R

fun ImageView.filterNormal() {
    this.background = ResourcesCompat.getDrawable(resources, R.drawable.filter_normal, null)
    this.setColorFilter(ResourcesCompat.getColor(resources, R.color.black, null), android.graphics.PorterDuff.Mode.SRC_IN)
}

fun ImageView.filterDisabled() {
    this.background = ResourcesCompat.getDrawable(resources, R.drawable.filter_disabled, null)
    this.setColorFilter(ResourcesCompat.getColor(resources, R.color.black, null), android.graphics.PorterDuff.Mode.SRC_IN)
}

fun ImageView.filterSelected() {
    this.background = ResourcesCompat.getDrawable(resources, R.drawable.filter_selected, null)
    this.setColorFilter(ResourcesCompat.getColor(resources, R.color.teal_200, null), android.graphics.PorterDuff.Mode.SRC_IN)
}