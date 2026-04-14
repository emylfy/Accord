package org.akanework.gramophone.ui.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.fragments.AdapterFragment

class ViewPager2Adapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val context: Context,
    private val viewPager2: ViewPager2,
    private val tabLayout: TabLayout?
) : FragmentStateAdapter(fragmentManager, lifecycle),
    SharedPreferences.OnSharedPreferenceChangeListener, DefaultLifecycleObserver {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private var tabs = mapSettingToTabList(prefs.getString("tabs", "") ?: "")
    private var mediator: TabLayoutMediator? = null

    init {
        prefs.registerOnSharedPreferenceChangeListener(this)
        lifecycle.addObserver(this)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    fun attachMediator() {
        mediator?.detach()
        tabLayout?.let { tl ->
            mediator = TabLayoutMediator(tl, viewPager2) { tab, position ->
                tab.text = context.getString(getLabelResId(position))
            }.also { it.attach() }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key != "tabs") return
        val currentItemId = tabs.getOrNull(viewPager2.currentItem)
        tabs = mapSettingToTabList(prefs.getString("tabs", "") ?: "")
        notifyDataSetChanged()
        attachMediator()
        if (currentItemId != null && tabs.contains(currentItemId)) {
            val newPosition = tabs.indexOfFirst { it == currentItemId }
            viewPager2.setCurrentItem(newPosition, false)
        }

        if (getItemCount() < 2) {
            tabLayout?.visibility = View.GONE
            viewPager2.isUserInputEnabled = false
        } else {
            tabLayout?.visibility = View.VISIBLE
            viewPager2.isUserInputEnabled = true
        }
    }

    fun getLabelResId(position: Int) = tabs[position]!!.label

    // Only tabs before the null separator are visible — null acts as the boundary
    // between shown and hidden tabs in the list.
    override fun getItemCount() = tabs.indexOf(null)
        .also { if (it == -1) throw IllegalStateException("indexOf null is -1 in tab list?") }

    override fun createFragment(position: Int): Fragment =
        AdapterFragment().apply {
            arguments = Bundle().apply {
                putInt("ID", tabs[position]!!.id)
            }
        }

    override fun getItemId(position: Int): Long {
        return tabs[position]!!.id.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return tabs.any { it?.id?.toLong() == itemId }
    }

    companion object {
        // Do not rename entries here, names are written to disk. Order is default tab order
        enum class Tab(val id: Int, val label: Int) {
            Songs(R.id.songs, R.string.category_songs),
            Albums(R.id.albums, R.string.category_albums),
            Artists(R.id.artists, R.string.category_artists),
            Genres(R.id.genres, R.string.category_genres),
            Dates(R.id.dates, R.string.category_dates),
            Folders(R.id.folders, R.string.filesystem),
            FileSystem(R.id.detailed_folders, R.string.folders),
            Playlist(R.id.playlists, R.string.category_playlists)
        }

        // Deserializes a comma-separated preference string into a tab list.
        // Format: "Songs,Albums,,Artists,Genres" — empty segment becomes null (the separator).
        // Tabs above the null separator are visible; tabs below are hidden.
        // Handles stale/unknown entries (silently dropped) and missing tabs (appended at end).
        // Deduplicates by removing all copies if a tab appears more than once.
        fun mapSettingToTabList(setting: String): List<Tab?> {
            val stList = if (setting.isNotEmpty())
                setting.split(",").flatMap {
                    if (it.isEmpty())
                        listOf(null)
                    else
                        try {
                            listOf(Tab.valueOf(it))
                        } catch (_: IllegalArgumentException) {
                            listOf()
                        }
                }.toMutableList()
            else mutableListOf()
            Tab.entries.forEach {
                if (stList.indexOf(it) != stList.lastIndexOf(it))
                    stList.removeAll { i -> i == it }
                if (!stList.contains(it))
                    stList.add(it)
            }
            if (!stList.contains(null))
                stList.add(null)
            return stList
        }

        fun mapTabListToSetting(tabList: List<Tab?>) = tabList.joinToString(",") { it?.name ?: "" }
    }
}
