package com.github.exact7.xtra.util

import android.content.Context
import androidx.fragment.app.FragmentManager
import com.github.exact7.xtra.ui.common.RadioButtonDialogFragment

object FragmentUtils {

    /**
     * Use this when result should be a string resource id
     */
    fun showRadioButtonDialogFragment(context: Context, fragmentManager: FragmentManager, labels: List<Int>, checkedIndex: Int) {
        RadioButtonDialogFragment.newInstance(
                labels.map(context::getString),
                labels.toIntArray(),
                checkedIndex
        ).show(fragmentManager, null)
    }

    /**
     * Use this when result should be an checkedIndex
     */
    fun showRadioButtonDialogFragment(fragmentManager: FragmentManager, labels: Collection<CharSequence>, checkedIndex: Int) {
        RadioButtonDialogFragment.newInstance(
                labels,
                null,
                checkedIndex
        ).show(fragmentManager, null)
    }
}