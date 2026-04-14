package org.akanework.gramophone.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener

class AboutFragment : BaseElevatedFragment(null) {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_about, container, false)
        val appBarLayout = rootView.findViewById<AppBarLayout>(R.id.appbarlayout)
        val tagTextView = rootView.findViewById<TextView>(R.id.version_tag)
        val versionTag = BuildConfig.MY_VERSION_NAME
        val materialToolbar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        val nestedScrollView = rootView.findViewById<NestedScrollView>(R.id.nested)
        val contributorCardView = rootView.findViewById<MaterialCardView>(R.id.contributor_frag)

        tagTextView.text = versionTag

        appBarLayout.enableEdgeToEdgePaddingListener()
        nestedScrollView.enableEdgeToEdgePaddingListener()

        materialToolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        fun openUrl(url: String) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        // Accord repo
        rootView.findViewById<View>(R.id.info_card).setOnClickListener {
            openUrl("https://github.com/emylfy/Accord")
        }
        // Main developers
        rootView.findViewById<View>(R.id.akane_cv).setOnClickListener { openUrl("https://github.com/AkaneTan") }
        rootView.findViewById<View>(R.id.lightsummer_cv).setOnClickListener { openUrl("https://github.com/lightsummer233") }
        rootView.findViewById<View>(R.id.duo3_cv).setOnClickListener { openUrl("https://github.com/123Duo3") }
        // Side developers
        rootView.findViewById<View>(R.id.lazar_frame).setOnClickListener { openUrl("https://github.com/lazrdev") }
        rootView.findViewById<View>(R.id.nick_frame).setOnClickListener { openUrl("https://github.com/nift4") }
        rootView.findViewById<View>(R.id.skyd_frame).setOnClickListener { openUrl("https://github.com/SkyD666") }
        rootView.findViewById<View>(R.id.luka_frame).setOnClickListener { openUrl("https://github.com/LukaLanczos") }
        // Fork maintainer
        rootView.findViewById<View>(R.id.fork_inner).setOnClickListener { openUrl("https://github.com/emylfy") }
        // Contributors
        contributorCardView.setOnClickListener {
            val supportFragmentManager = requireActivity().supportFragmentManager
            supportFragmentManager
                .beginTransaction()
                .addToBackStack(System.currentTimeMillis().toString())
                .hide(supportFragmentManager.fragments.let { it[it.size - 1] })
                .add(R.id.container, AboutContributorFragment())
                .commit()
        }

        return rootView
    }
}