package com.dipto.clify.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.dipto.clify.R
import com.dipto.clify.databinding.FragmentHomeBinding
import com.dipto.clify.model.PatchItem
import com.dipto.clify.patcher.PatchEngine
import kotlinx.coroutines.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isPatching = false

    private val defaultPatches = listOf(
        PatchItem("ads", R.string.patch_ads, R.string.patch_ads_desc),
        PatchItem("sponsorblock", R.string.patch_sponsorblock, R.string.patch_sponsorblock_desc),
        PatchItem("background", R.string.patch_background_play, R.string.patch_background_play_desc),
        PatchItem("quality", R.string.patch_remember_quality, R.string.patch_remember_quality_desc),
        PatchItem("dislike", R.string.patch_return_youtube_dislike, R.string.patch_return_youtube_dislike_desc),
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.patchButton.setOnClickListener {
            if (!isPatching) startPatching()
        }
    }

    private fun startPatching() {
        isPatching = true
        binding.patchButton.isEnabled = false
        binding.patchButton.text = getString(R.string.patching)
        binding.progressBar.visibility = View.VISIBLE
        binding.progressText.visibility = View.VISIBLE

        val engine = PatchEngine(requireContext())

        scope.launch {
            engine.startPatching(defaultPatches, object : PatchEngine.ProgressCallback {
                override fun onProgress(status: String, percent: Int) {
                    launch(Dispatchers.Main) {
                        binding.statusText.text = status
                        binding.progressBar.progress = percent
                        binding.progressText.text = "$percent%"
                    }
                }

                override fun onComplete(apkFile: java.io.File) {
                    launch(Dispatchers.Main) {
                        binding.statusText.text = getString(R.string.status_complete)
                        binding.patchButton.text = getString(R.string.done)
                        binding.progressBar.progress = 100
                        binding.progressText.text = "100%"
                        binding.patchButton.isEnabled = true
                        isPatching = false
                        engine.installApk(apkFile)
                    }
                }

                override fun onError(error: String) {
                    launch(Dispatchers.Main) {
                        binding.statusText.text = getString(R.string.status_error, error)
                        binding.patchButton.text = getString(R.string.patch_youtube)
                        binding.patchButton.isEnabled = true
                        isPatching = false
                    }
                }
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
        _binding = null
    }
}
