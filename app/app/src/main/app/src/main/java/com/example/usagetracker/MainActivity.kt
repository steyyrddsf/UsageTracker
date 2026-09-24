package com.example.usagetracker

import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.Calendar

data class AppUsage(val name: String, val icon: Drawable?, val ms: Long)

class MainActivity : Activity() {
    private lateinit var list: ListView
    private lateinit var info: TextView
    private var days = 0 // 0 = aujourd'hui

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 40.dp(), 16.dp(), 8.dp())
        }
        root.addView(TextView(this).apply {
            text = "Temps d'utilisation"
            textSize = 22f
            setTextColor(Color.BLACK)
        })
        val bar = LinearLayout(this)
        listOf("Aujourd'hui" to 0, "7 jours" to 6, "30 jours" to 29).forEach { (label, d) ->
            bar.addView(Button(this).apply {
                text = label
                setOnClickListener { days = d; load() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(bar)
        info = TextView(this).apply { setPadding(0, 8.dp(), 0, 8.dp()) }
        root.addView(info)
        list = ListView(this)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun hasAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun load() {
        if (!hasAccess()) {
            info.text = "Autorisation requise : touche ici, puis active « Temps d'utilisation » pour cette app."
            info.setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            list.adapter = null
            return
        }
        info.setOnClickListener(null)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -days)
        }
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = usm.queryAndAggregateUsageStats(cal.timeInMillis, System.currentTimeMillis())
        val pm = packageManager
        val items = stats.values.filter { it.totalTimeInForeground > 0 }.map {
            try {
                val ai = pm.getApplicationInfo(it.packageName, 0)
                AppUsage(pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai), it.totalTimeInForeground)
            } catch (e: Exception) {
                AppUsage(it.packageName, null, it.totalTimeInForeground)
            }
        }.sortedByDescending { it.ms }
        info.text = if (items.isEmpty()) "Aucune donnée pour cette période." else "${items.size} applications"
        list.adapter = UsageAdapter(items)
    }

    private fun fmt(ms: Long): String {
        val m = ms / 60000
        return if (m >= 60) "${m / 60} h ${m % 60} min" else if (m > 0) "$m min" else "< 1 min"
    }

    inner class UsageAdapter(private val items: List<AppUsage>) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(p: Int) = items[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, v: View?, parent: ViewGroup?): View {
            val a = items[p]
            val max = items[0].ms.coerceAtLeast(1)
            val row = LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8.dp(), 0, 8.dp())
            }
            row.addView(ImageView(this@MainActivity).apply { setImageDrawable(a.icon) },
                LinearLayout.LayoutParams(44.dp(), 44.dp()))
            val mid = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12.dp(), 0, 12.dp(), 0)
            }
            mid.addView(TextView(this@MainActivity).apply {
                text = a.name; textSize = 16f; setTextColor(Color.BLACK); maxLines = 1
            })
            mid.addView(ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                this.max = 1000; progress = (a.ms * 1000 / max).toInt()
            })
            row.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(this@MainActivity).apply { text = fmt(a.ms) })
            return row
        }
    }
}
