package org.akanework.gramophone.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener

class AboutContributorFragment : BaseElevatedFragment(null) {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_about_inner, container, false)
        val appBarLayout = rootView.findViewById<AppBarLayout>(R.id.appbarlayout)
        val materialToolbar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        val nestedScrollView = rootView.findViewById<NestedScrollView>(R.id.nested)

        appBarLayout.enableEdgeToEdgePaddingListener()
        nestedScrollView.enableEdgeToEdgePaddingListener()

        materialToolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        fun openUrl(url: String) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        // Acknowledgements → GitHub profiles
        rootView.findViewById<View>(R.id.ack1_frame).setOnClickListener { openUrl("https://github.com/maximiliaan") }
        rootView.findViewById<View>(R.id.ack2_frame).setOnClickListener { openUrl("https://github.com/NurKeinNeid") }
        rootView.findViewById<View>(R.id.ack3_frame).setOnClickListener { openUrl("https://github.com/nabpeepol") }

        // Libraries → URLs from strings
        val libs = listOf(
            R.string.lib1_desc, R.string.lib2_desc, R.string.lib3_desc, R.string.lib4_desc
        )
        val sourceFrame = rootView.findViewById<ViewGroup>(R.id.source_frame)
        val linearLayout = sourceFrame.getChildAt(0) as ViewGroup
        for (i in libs.indices) {
            linearLayout.getChildAt(i)?.setOnClickListener {
                openUrl(getString(libs[i]))
            }
        }

        return rootView
    }
}