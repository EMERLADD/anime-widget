package com.animewidget.maid

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** 桌面图标入口，显示从系统桌面添加 Widget 的说明。 */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            addView(TextView(context).apply {
                text = getString(R.string.launcher_title)
                textSize = 24f
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(TextView(context).apply {
                text = getString(R.string.launcher_instructions)
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, dp(24))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(Button(context).apply {
                text = getString(R.string.return_to_home)
                setOnClickListener { moveTaskToBack(true) }
            })
        }
        setContentView(content)
    }
}
