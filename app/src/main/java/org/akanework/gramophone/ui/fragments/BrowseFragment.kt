package org.akanework.gramophone.ui.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.activityViewModels
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.applyGeneralMenuItem
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.ui.LibraryViewModel
import org.akanework.gramophone.ui.adapters.ViewPager2Adapter

@androidx.annotation.OptIn(UnstableApi::class)
class BrowseFragment : BaseFragment(null) {
    private val libraryViewModel: LibraryViewModel by activityViewModels()
    lateinit var appBarLayout: AppBarLayout
        private set

    @SuppressLint("StringFormatInvalid", "StringFormatMatches")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_browse, container, false)
        val tabLayout = rootView.findViewById<TabLayout>(R.id.tab_layout)
        val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        val viewPager2 = rootView.findViewById<ViewPager2>(R.id.fragment_viewpager)

        appBarLayout = rootView.findViewById(R.id.appbarlayout)
        appBarLayout.enableEdgeToEdgePaddingListener()
        topAppBar.overflowIcon = AppCompatResources.getDrawable(
            requireContext(), R.drawable.ic_more_vert_bold
        )!!.apply {
            setTint(
                resources.getColor(R.color.contrast_themeColor, null)
            )
        }

        topAppBar.applyGeneralMenuItem(this, libraryViewModel)

        viewPager2.offscreenPageLimit = 9999
        val adapter = ViewPager2Adapter(
            childFragmentManager, viewLifecycleOwner.lifecycle,
            requireContext(), viewPager2, tabLayout
        )
        viewPager2.adapter = adapter
        adapter.attachMediator()

        if (adapter.itemCount < 2) {
            tabLayout.visibility = View.GONE
            viewPager2.isUserInputEnabled = false
        }

        return rootView
    }
}
