package nl.tudelft.trustchain.detoks

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.tudelft.ipv8.android.IPv8Android


class VideosAdapter(
    private val torrentManager: TorrentManager,
    private val onPlaybackError: (() -> Unit)? = null,
    private val videoScaling: Boolean = false,
) :
    RecyclerView.Adapter<VideosAdapter.VideoViewHolder?>() {
    private val mVideoItems: List<VideoItem> =
        List(100) { VideoItem(torrentManager::provideContent) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        return VideoViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_video, parent, false),
            videoScaling,
        )
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        Log.i("DeToks", "onBindViewHolder: $position")
        holder.setVideoData(mVideoItems[position], position, onPlaybackError)
    }

    override fun getItemCount(): Int {
        return mVideoItems.size
    }

    class VideoViewHolder(itemView: View, private val videoScaling: Boolean = false) :
        RecyclerView.ViewHolder(itemView) {
        var mVideoView: VideoView
        var txtTitle: TextView
        var txtDesc: TextView
        var peerCount: TextView
        var mProgressBar: ProgressBar
        var like: Button

        init {
            mVideoView = itemView.findViewById(R.id.videoView)
            txtTitle = itemView.findViewById(R.id.txtTitle)
            txtDesc = itemView.findViewById(R.id.txtDesc)
            peerCount = itemView.findViewById(R.id.peerCount)
            mProgressBar = itemView.findViewById(R.id.progressBar)
            like = itemView.findViewById(R.id.like_button)
            like.setVisibility(View.GONE);

        }

        fun setVideoData(item: VideoItem, position: Int, onPlaybackError: (() -> Unit)? = null) {
            CoroutineScope(Dispatchers.Main).launch {
                val content = item.content(position, 10000)
                like.setVisibility(View.VISIBLE)
                like.setOnClickListener{
                    val community = IPv8Android.getInstance().getOverlay<DetoksCommunity>()!!
                    community.broadcastLike(content.fileName, content.torrentName, content.creator)
                }
                val community = IPv8Android.getInstance().getOverlay<DetoksCommunity>()!!
                txtTitle.text = content.creator
                txtDesc.text = content.torrentName
                peerCount.text = "Peers: " + community.getPeers().size.toString()
                mVideoView.setVideoPath(content.fileURI)
                Log.i("DeToks", "Received content: ${content.fileURI}")
                mVideoView.setOnPreparedListener { mp ->
                    mProgressBar.visibility = View.GONE
                    mp.start()
                    if (videoScaling) {
                        val videoRatio = mp.videoWidth / mp.videoHeight.toFloat()
                        val screenRatio = mVideoView.width / mVideoView.height.toFloat()
                        val scale = videoRatio / screenRatio
                        if (scale >= 1f) {
                            mVideoView.scaleX = scale
                        } else {
                            mVideoView.scaleY = 1f / scale

                        }
                    }
                }
                mVideoView.setOnCompletionListener { mp -> mp.start() }
                mVideoView.setOnErrorListener { p1, what, extra ->
                    Log.i("DeToks", "onError: $p1, $what, $extra")
                    if (onPlaybackError != null) {
                        onPlaybackError()
                        true
                    } else {
                        true
                    }
                }
            }
        }
    }
}

class VideoItem(val content: suspend (Int, Long) -> TorrentMediaInfo)
