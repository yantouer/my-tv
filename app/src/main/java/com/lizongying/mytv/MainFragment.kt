package com.lizongying.mytv

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ListRowPresenter.SelectItemViewHolderTask
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import com.lizongying.mytv.api.YSP
import com.lizongying.mytv.models.ProgramType
import com.lizongying.mytv.models.TVListViewModel
import com.lizongying.mytv.models.TVViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainFragment : BrowseSupportFragment() {

    private var itemPosition = 0

    private var rowsAdapter: ArrayObjectAdapter? = null

    var tvListViewModel = TVListViewModel()

    private var lastVideoUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        headersState = HEADERS_DISABLED
    }

//    override fun onCreateView(
//        inflater: LayoutInflater,
//        container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View? {
//        val rootView = super.onCreateView(inflater, container, savedInstanceState)
//        rootView?.setOnClickListener {
//            Log.i(TAG, "main on click")
//            fragmentManager!!.beginTransaction().hide(this).commit()
//        }
//        mainFragment.view?.setOnClickListener {
//            Log.i(TAG, "mainFragment on click")
//            fragmentManager!!.beginTransaction().hide(this).commit()
//        }
//        getRowsSupportFragment().view?.setOnClickListener {
//            Log.i(TAG, "getRowsSupportFragment on click")
//            fragmentManager!!.beginTransaction().hide(this).commit()
//        }
//
//
//        return rootView
//    }

    override fun onStart() {
        Log.i(TAG, "onStart")
        super.onStart()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        Log.i(TAG, "onActivityCreated start")
        super.onActivityCreated(savedInstanceState)

        Log.i(TAG, "init YSP")
        try {
            activity?.let { 
                Log.i(TAG, "call YSP.init")
                YSP.init(it)
                Log.i(TAG, "YSP.init success")
            }
        } catch (e: Exception) {
            Log.e(TAG, "YSP.init error: ${e.message}", e)
        }

        Log.i(TAG, "loadRows")
        try {
            loadRows()
            Log.i(TAG, "loadRows success")
        } catch (e: Exception) {
            Log.e(TAG, "loadRows error: ${e.message}", e)
        }

        Log.i(TAG, "setupEventListeners")
        try {
            setupEventListeners()
            Log.i(TAG, "setupEventListeners success")
        } catch (e: Exception) {
            Log.e(TAG, "setupEventListeners error: ${e.message}", e)
        }

        Log.i(TAG, "observe tvListViewModel")
        try {
            tvListViewModel.tvListViewModel.value?.forEach { tvViewModel ->
                tvViewModel.errInfo.observe(viewLifecycleOwner) { _ ->
                    if (tvViewModel.errInfo.value != null
                        && tvViewModel.getTV().id == itemPosition
                    ) {
                        Toast.makeText(context, tvViewModel.errInfo.value, Toast.LENGTH_SHORT).show()
                    }
                }
                tvViewModel.ready.observe(viewLifecycleOwner) { _ ->

                    // not first time && channel not change
                    if (tvViewModel.ready.value != null
                        && tvViewModel.getTV().id == itemPosition
                        && check(tvViewModel)
                    ) {
                        Log.i(TAG, "ready ${tvViewModel.getTV().title}")
                        (activity as? MainActivity)?.play(tvViewModel)
                    }
                }
                tvViewModel.change.observe(viewLifecycleOwner) { _ ->
                    if (tvViewModel.change.value != null) {
                        val title = tvViewModel.getTV().title
                        Log.i(TAG, "switch $title")
                        if (tvViewModel.getTV().pid != "") {
                            Log.i(TAG, "request $title")
                            lifecycleScope.launch(Dispatchers.IO) {
                                tvViewModel.let { Request.fetchData(it) }
                            }
                            (activity as? MainActivity)?.showInfoFragment(tvViewModel)
                            setSelectedPosition(
                                tvViewModel.getRowPosition(), true,
                                SelectItemViewHolderTask(tvViewModel.getItemPosition())
                            )
                        } else {
                            if (check(tvViewModel)) {
                                (activity as? MainActivity)?.play(tvViewModel)
                                (activity as? MainActivity)?.showInfoFragment(tvViewModel)
                                setSelectedPosition(
                                    tvViewModel.getRowPosition(), true,
                                    SelectItemViewHolderTask(tvViewModel.getItemPosition())
                                )
                            }
                        }
                    }
                }
            }
            Log.i(TAG, "observe tvListViewModel success")
        } catch (e: Exception) {
            Log.e(TAG, "observe tvListViewModel error: ${e.message}", e)
        }

        Log.i(TAG, "call fragmentReady")
        try {
            (activity as MainActivity).fragmentReady("MainFragment")
            Log.i(TAG, "fragmentReady success")
        } catch (e: Exception) {
            Log.e(TAG, "fragmentReady error: ${e.message}", e)
        }

        Log.i(TAG, "onActivityCreated end")
    }

    fun toLastPosition() {
        setSelectedPosition(
            selectedPosition, false,
            SelectItemViewHolderTask(tvListViewModel.maxNum[selectedPosition] - 1)
        )
    }

    fun toFirstPosition() {
        setSelectedPosition(
            selectedPosition, false,
            SelectItemViewHolderTask(0)
        )
    }

    override fun startHeadersTransition(withHeaders: Boolean) {
    }

    private fun loadRows() {
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        adapter = rowsAdapter
        
        // 从网络加载直播源
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val liveSourceUrl = "https://gt.ifree.fun/https://raw.githubusercontent.com/yantouer/IPTVzidong/refs/heads/main/live.txt"
                val liveSources = LiveSourceManager.loadLiveSources(liveSourceUrl)
                
                if (liveSources.isNotEmpty()) {
                    val cardPresenter = CardPresenter(requireContext())
                    var idx: Long = 0
                    
                    for ((k, v) in liveSources) {
                        val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                        for ((idx2, v1) in v.withIndex()) {
                            val tvViewModel = TVViewModel(v1)
                            tvViewModel.setRowPosition(idx.toInt())
                            tvViewModel.setItemPosition(idx2)
                            tvListViewModel.addTVViewModel(tvViewModel)
                            listRowAdapter.add(tvViewModel)
                        }
                        tvListViewModel.maxNum.add(v.size)
                        val header = HeaderItem(idx, k)
                        rowsAdapter!!.add(ListRow(header, listRowAdapter))
                        idx++
                    }
                    
                    itemPosition = SP.itemPosition
                    if (itemPosition >= tvListViewModel.size()) {
                        itemPosition = 0
                    }
                    tvListViewModel.setItemPosition(itemPosition)
                } else {
                    // 加载失败，使用默认频道
                    loadDefaultChannels()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load live sources: ${e.message}", e)
                // 加载失败，使用默认频道
                loadDefaultChannels()
            }
        }
    }
    
    /**
     * 加载默认频道（当网络直播源加载失败时使用）
     */
    private fun loadDefaultChannels() {
        val cardPresenter = CardPresenter(requireContext())

        var idx: Long = 0
        for ((k, v) in TVList.list) {
            val listRowAdapter = ArrayObjectAdapter(cardPresenter)
            for ((idx2, v1) in v.withIndex()) {
                val tvViewModel = TVViewModel(v1)
                tvViewModel.setRowPosition(idx.toInt())
                tvViewModel.setItemPosition(idx2)
                tvListViewModel.addTVViewModel(tvViewModel)
                listRowAdapter.add(tvViewModel)
            }
            tvListViewModel.maxNum.add(v.size)
            val header = HeaderItem(idx, k)
            rowsAdapter!!.add(ListRow(header, listRowAdapter))
            idx++
        }

        itemPosition = SP.itemPosition
        if (itemPosition >= tvListViewModel.size()) {
            itemPosition = 0
        }
        tvListViewModel.setItemPosition(itemPosition)
    }

    fun prevSource() {
        view?.post {
            val tvViewModel = tvListViewModel.getTVViewModel(itemPosition)
            if (tvViewModel != null) {
                if (tvViewModel.videoUrl.value!!.size > 1) {
                    val videoIndex = tvViewModel.videoIndex.value?.minus(1)
                    if (videoIndex == -1) {
                        tvViewModel.setVideoIndex(tvViewModel.videoUrl.value!!.size - 1)
                    }
                    tvViewModel.changed()
                }
            }
        }
    }

    fun nextSource() {
        view?.post {
            val tvViewModel = tvListViewModel.getTVViewModel(itemPosition)
            if (tvViewModel != null) {
                if (tvViewModel.videoUrl.value!!.size > 1) {
                    val videoIndex = tvViewModel.videoIndex.value?.plus(1)
                    if (videoIndex == tvViewModel.videoUrl.value!!.size) {
                        tvViewModel.setVideoIndex(0)
                    }
                    tvViewModel.changed()
                }
            }
        }
    }

    private fun setupEventListeners() {
        onItemViewClickedListener = ItemViewClickedListener()
        onItemViewSelectedListener = ItemViewSelectedListener()
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row
        ) {
            if (item is TVViewModel) {
                if (itemPosition != item.getTV().id) {
                    itemPosition = item.getTV().id
                    tvListViewModel.setItemPosition(itemPosition)
                    tvListViewModel.getTVViewModel(itemPosition)?.changed()
                }
                (activity as? MainActivity)?.switchMainFragment()
            }
        }
    }

    private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
        override fun onItemSelected(
            itemViewHolder: Presenter.ViewHolder?, item: Any?,
            rowViewHolder: RowPresenter.ViewHolder, row: Row
        ) {
            if (item is TVViewModel) {
                tvListViewModel.setItemPositionCurrent(item.getTV().id)
                (activity as MainActivity).mainActive()
            }
        }
    }

    fun check(tvViewModel: TVViewModel): Boolean {
        val title = tvViewModel.getTV().title
        val videoUrl = tvViewModel.videoIndex.value?.let { tvViewModel.videoUrl.value?.get(it) }
        if (videoUrl == null || videoUrl == "") {
            Log.e(TAG, "$title videoUrl is empty")
            return false
        }

        if (videoUrl == lastVideoUrl) {
            Log.e(TAG, "$title videoUrl is duplication")
            return false
        }

        return true
    }

    fun fragmentReady() {
        tvListViewModel.getTVViewModel(itemPosition)?.changed()

        tvListViewModel.tvListViewModel.value?.forEach { tvViewModel ->
            updateEPG(tvViewModel)
        }
    }

    fun play(itemPosition: Int) {
        view?.post {
            if (itemPosition > -1 && itemPosition < tvListViewModel.size()) {
                this.itemPosition = itemPosition
                tvListViewModel.setItemPosition(itemPosition)
                tvListViewModel.getTVViewModel(itemPosition)?.changed()
            } else {
                Toast.makeText(context, "频道不存在", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun prev() {
        view?.post {
            itemPosition--
            if (itemPosition == -1) {
                itemPosition = tvListViewModel.size() - 1
            }
            tvListViewModel.setItemPosition(itemPosition)
            tvListViewModel.getTVViewModel(itemPosition)?.changed()
        }
    }

    fun next() {
        view?.post {
            itemPosition++
            if (itemPosition == tvListViewModel.size()) {
                itemPosition = 0
            }
            tvListViewModel.setItemPosition(itemPosition)
            tvListViewModel.getTVViewModel(itemPosition)?.changed()
        }
    }

    private fun updateEPG(tvViewModel: TVViewModel) {
        when (tvViewModel.getTV().programType) {
            ProgramType.Y_PROTO -> {
                Request.fetchYProtoEPG(tvViewModel)
            }

            ProgramType.Y_JCE -> {
                Request.fetchYJceEPG(tvViewModel)
            }

            ProgramType.F -> {
                Request.fetchFEPG(tvViewModel)
            }
        }
    }

    override fun onResume() {
        Log.i(TAG, "onResume")
        super.onResume()
    }

    override fun onStop() {
        Log.i(TAG, "onStop")
        super.onStop()
        SP.itemPosition = itemPosition
        Log.i(TAG, "$POSITION $itemPosition saved")
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MainFragment"
        private const val POSITION = "position"
    }
}