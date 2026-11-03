package io.agora.examples.karaoke_view_ex

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.agora.examples.karaoke_view_ex.agora.MccManager
import io.agora.examples.karaoke_view_ex.agora.ServiceManager
import io.agora.examples.karaoke_view_ex.databinding.SheetSongSelectionBinding
import io.agora.examples.utils.LyricsResourcePool
import io.agora.examples.utils.ServiceType

class SongSelectionSheet : BottomSheetDialogFragment() {
    interface Host {
        fun onTmeSongSelected(song: MccManager.TmeSong): Boolean
        fun onMccSongSelected(position: Int): Boolean
    }

    private var binding: SheetSongSelectionBinding? = null
    private var songs: List<MccManager.TmeSong> = emptyList()
    private val isTme get() = requireArguments().getInt("service") == ServiceType.TME.type
    private val listListener = ServiceManager.TmeListListener { items, loading, error ->
        activity?.runOnUiThread {
            val views = binding ?: return@runOnUiThread
            songs = items.toList()
            views.songList.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, songs.map { it.name })
            views.songList.isEnabled = !loading
            views.songListRetry.isEnabled = !loading
            views.songListStatus.text = when {
                loading -> getString(R.string.tme_loading_songs)
                error != null -> error
                else -> getString(R.string.tme_choose_song)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val views = SheetSongSelectionBinding.inflate(inflater, container, false)
        binding = views
        views.songSelectionTitle.setText(if (isTme) R.string.tme_song_selection_title else R.string.mcc_song_selection_title)
        views.closeSongSelection.setOnClickListener { dismiss() }
        views.songListRetry.setOnClickListener { ServiceManager.retryTmeSongs() }
        if (!isTme) {
            views.songListRetry.visibility = View.GONE
            views.songList.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1,
                LyricsResourcePool.asMusicList().map { it.songName })
        }
        views.songList.setOnItemClickListener { _, _, position, _ ->
            val host = activity as? Host ?: return@setOnItemClickListener
            val selected = if (isTme) songs.getOrNull(position)?.let(host::onTmeSongSelected) ?: false
                else host.onMccSongSelected(position)
            if (selected) dismiss()
        }
        return views.root
    }

    override fun onStart() {
        super.onStart()
        resizeSheet()
        if (isTme) ServiceManager.attachTmeList(listListener)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        resizeSheet()
    }

    private fun resizeSheet() {
        val sheetDialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = sheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val screenHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requireActivity().windowManager.currentWindowMetrics.bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION") requireActivity().windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.heightPixels
        }
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply { height = (screenHeight * .8f).toInt() }
        sheetDialog.behavior.apply {
            isFitToContents = true
            skipCollapsed = true
            isDraggable = false
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onStop() {
        if (isTme) ServiceManager.detachTmeList(listListener)
        super.onStop()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    companion object {
        @JvmStatic fun show(manager: FragmentManager, service: ServiceType) {
            if (manager.isStateSaved || manager.findFragmentByTag("song-selection") != null) return
            SongSelectionSheet().apply { arguments = Bundle().apply { putInt("service", service.type) } }
                .showNow(manager, "song-selection")
        }
    }
}
