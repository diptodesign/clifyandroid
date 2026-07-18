package com.dipto.clify.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.dipto.clify.R
import com.dipto.clify.databinding.FragmentPatchesBinding
import com.dipto.clify.model.PatchItem
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch

class PatchesFragment : Fragment() {

    private var _binding: FragmentPatchesBinding? = null
    private val binding get() = _binding!!

    private val features = listOf(
        PatchItem("ads", R.string.patch_ads, R.string.patch_ads_desc, builtIn = true),
        PatchItem("sponsorblock", R.string.patch_sponsorblock, R.string.patch_sponsorblock_desc, builtIn = true),
        PatchItem("background", R.string.patch_background_play, R.string.patch_background_play_desc, builtIn = true),
        PatchItem("quality", R.string.patch_remember_quality, R.string.patch_remember_quality_desc, builtIn = true),
        PatchItem("autoplay", R.string.patch_disable_autoplay, R.string.patch_disable_autoplay_desc, defaultEnabled = true, builtIn = true),
        PatchItem("shorts", R.string.patch_hide_shorts, R.string.patch_hide_shorts_desc, defaultEnabled = true, builtIn = true),
        PatchItem("dislike", R.string.patch_return_youtube_dislike, R.string.patch_return_youtube_dislike_desc, builtIn = true),
        PatchItem("minimal", R.string.patch_minimal_layout, R.string.patch_minimal_layout_desc, builtIn = true),
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPatchesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        populateFeatures()
    }

    private fun populateFeatures() {
        val container = binding.patchesList
        container.removeAllViews()

        for (feature in features) {
            val card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_patch, container, false) as MaterialCardView

            card.findViewById<TextView>(R.id.patchTitle).setText(feature.titleRes)
            card.findViewById<TextView>(R.id.patchDesc).setText(feature.descRes)

            val toggle = card.findViewById<MaterialSwitch>(R.id.patchToggle)
            toggle.isChecked = feature.enabled
            toggle.isEnabled = false
            toggle.alpha = 0.5f

            container.addView(card)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
