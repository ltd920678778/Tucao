package me.sweetll.tucao.business.download.adapter

import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import com.chad.library.adapter.base.BaseMultiItemQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.chad.library.adapter.base.entity.MultiItemEntity
import io.reactivex.android.schedulers.AndroidSchedulers
import me.sweetll.tucao.R
import me.sweetll.tucao.business.download.DownloadActivity
import me.sweetll.tucao.business.download.model.Part
import me.sweetll.tucao.business.download.model.StateController
import me.sweetll.tucao.business.download.model.Video
import me.sweetll.tucao.business.video.VideoActivity
import me.sweetll.tucao.extension.DownloadHelpers
import me.sweetll.tucao.extension.load
import me.sweetll.tucao.extension.logD
import me.sweetll.tucao.extension.toast
import me.sweetll.tucao.rxdownload.RxDownload
import me.sweetll.tucao.rxdownload.entity.DownloadEvent
import me.sweetll.tucao.rxdownload.entity.DownloadStatus
import java.util.concurrent.TimeUnit

class DownloadingVideoAdapter(val downloadActivity: DownloadActivity, data: MutableList<MultiItemEntity>?): BaseMultiItemQuickAdapter<MultiItemEntity, BaseViewHolder>(data) {
    companion object {
        const val TYPE_VIDEO = 0
        const val TYPE_PART = 1
    }

    val rxDownload: RxDownload by lazy {
        RxDownload.getInstance(mContext)
    }

    init {
        addItemType(TYPE_VIDEO, R.layout.item_downloaded_video)
        addItemType(TYPE_PART, R.layout.item_downloaded_part)
    }

    override fun convert(helper: BaseViewHolder, item: MultiItemEntity) {
        when (helper.itemViewType) {
            TYPE_VIDEO -> {
                val video = item as Video
                helper.setText(R.id.text_title, video.title)
                helper.setVisible(R.id.text_size, false)
                val thumbImg = helper.getView<ImageView>(R.id.img_thumb)
                thumbImg.load(mContext, video.thumb)

                helper.setVisible(R.id.checkbox, video.checkable)
                val checkBox = helper.getView<CheckBox>(R.id.checkbox)
                checkBox.isChecked = video.checked
                checkBox.setOnCheckedChangeListener {
                    compoundButton, checked ->
                    video.checked = checked
                    updateMenu()
                }

                helper.itemView.setOnClickListener {
                    if (video.checkable) {
                        checkBox.isChecked = !checkBox.isChecked
                        video.subItems.forEach {
                            it.checked = checkBox.isChecked
                        }
                        if (video.isExpanded) {
                            notifyItemRangeChanged(helper.adapterPosition + 1, video.subItems.size)
                        }
                        updateMenu()
                    } else {
                        if (video.isExpanded) {
                            collapse(helper.adapterPosition)
                        } else {
                            expand(helper.adapterPosition)
                        }
                    }
                }
                helper.getView<LinearLayout>(R.id.linear_detail).setOnClickListener {
                    VideoActivity.intentTo(mContext, video.hid)
                }
            }
            TYPE_PART -> {
                val part = item as Part
                part.stateController = StateController(helper.getView(R.id.text_size), helper.getView(R.id.img_status), helper.getView(R.id.progress))
                helper.setText(R.id.text_title, part.title)
                part.durls.forEach {
                    durl ->
                    rxDownload.receive(durl.url)
                            .sample(500, TimeUnit.MILLISECONDS)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({
                                (status, downloadSize, totalSize) ->

                                durl.flag = status
                                durl.downloadSize = downloadSize
                                durl.totalSize = totalSize

                                part.update()

                                val newEvent = DownloadEvent(part.flag, part.downloadSize, part.totalSize)

                                if (part.flag == DownloadStatus.COMPLETED) {
//                                    DownloadHelpers.saveDownloadPart(part)
                                } else {
                                    part.stateController?.setEvent(newEvent)
                                }
                            }, {
                                error ->
                                error.printStackTrace()
                                error.message?.toast()
                            })
                }

                helper.setVisible(R.id.checkbox, part.checkable)
                val checkBox = helper.getView<CheckBox>(R.id.checkbox)
                checkBox.isChecked = part.checked
                checkBox.setOnCheckedChangeListener {
                    compoundButton, checked ->
                    part.checked = checked
                    updateMenu()
                }

                helper.itemView.setOnClickListener {
                    view ->
                    if (part.checkable) {
                        checkBox.isChecked = !checkBox.isChecked
                        val parentVideo = data.find {
                            video ->
                            (video is Video) && video.subItems.any { it.vid == part.vid }
                        } as Video
                        val currentPosition = parentVideo.subItems.indexOf(part)
                        val newParentChecked = parentVideo.subItems.all(Part::checked)
                        if (newParentChecked != parentVideo.checked) {
                            parentVideo.checked = newParentChecked
                            notifyItemChanged(helper.adapterPosition - 1 - currentPosition)
                        }
                        updateMenu()
                    } else {
                        val parentVideo = data.find {
                            video ->
                            (video is Video) && video.subItems.any { it.vid == part.vid }
                        } as Video
                        val callback = object : DownloadHelpers.Callback {
                            override fun startDownload() {
                                DownloadHelpers.startDownload(parentVideo, part)
                            }

                            override fun pauseDownload() {
                                DownloadHelpers.pauseDownload(part)
                            }
                        }
                        part.stateController?.handleClick(callback)
                    }
                }
            }
        }
    }

    fun updateMenu() {
        val deleteEnabled = data.any {
            when (it) {
                is Video -> it.checked
                is Part -> it.checked
                else -> false
            }
        }
        val isPickAll = data.all {
            when (it) {
                is Video -> it.checked
                is Part -> it.checked
                else -> false
            }
        }
        downloadActivity.updateBottomMenu(deleteEnabled, isPickAll)
    }

}
