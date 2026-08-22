package com.theveloper.pixelplay.data.listentogether

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class ListenTogetherRole { DISCONNECTED, HOST, GUEST }

data class ListenTogetherState(
    val role: ListenTogetherRole = ListenTogetherRole.DISCONNECTED,
    val inviteCode: String = "",
    val peerCount: Int = 0,
    val error: String? = null,
)

data class SharedPlaybackSnapshot(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val positionMs: Long,
    val isPlaying: Boolean,
    val sentAtMs: Long = System.currentTimeMillis(),
)

/**
 * Backend-free Listen Together for devices on the same Wi-Fi/LAN. A random room token is
 * required before a peer can receive playback metadata. The host is authoritative.
 */
@Singleton
class ListenTogetherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private data class Peer(val socket: Socket, val writer: BufferedWriter)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val peers = CopyOnWriteArrayList<Peer>()
    private val _state = MutableStateFlow(ListenTogetherState())
    val state: StateFlow<ListenTogetherState> = _state.asStateFlow()
    private val _incomingSnapshots = MutableSharedFlow<SharedPlaybackSnapshot>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val incomingSnapshots: SharedFlow<SharedPlaybackSnapshot> = _incomingSnapshots.asSharedFlow()

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var guestSocket: Socket? = null
    @Volatile private var roomToken: String = ""
    @Volatile private var lastSnapshot: SharedPlaybackSnapshot? = null

    fun host() {
        stop()
        scope.launch {
            runCatching {
                check(isWifiConnected()) { "Connect this device to Wi-Fi before hosting a room" }
                val server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(0))
                }
                serverSocket = server
                roomToken = randomToken()
                val address = localIpv4Address()
                    ?: error("Connect this device to Wi-Fi before hosting a room")
                _state.value = ListenTogetherState(
                    role = ListenTogetherRole.HOST,
                    inviteCode = "$address:${server.localPort}#$roomToken",
                )
                while (!server.isClosed) {
                    val socket = server.accept().apply { tcpNoDelay = true }
                    scope.launch { authorizeAndServePeer(socket) }
                }
            }.onFailure { error ->
                if (serverSocket != null) Log.w(TAG, "Listen Together host stopped", error)
                closeSockets()
                if (_state.value.role == ListenTogetherRole.HOST) {
                    _state.value = ListenTogetherState(error = error.message ?: "Could not host the room")
                }
            }
        }
    }

    fun join(inviteCode: String) {
        val parsed = parseInvite(inviteCode)
        if (parsed == null) {
            _state.value = ListenTogetherState(error = "Enter a valid room code")
            return
        }
        stop()
        scope.launch {
            runCatching {
                val (host, port, token) = parsed
                val socket = Socket().apply {
                    tcpNoDelay = true
                    connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                }
                guestSocket = socket
                socket.getOutputStream().bufferedWriter().apply {
                    write("AUTH $token\n")
                    flush()
                }
                _state.value = ListenTogetherState(
                    role = ListenTogetherRole.GUEST,
                    inviteCode = inviteCode.trim(),
                    peerCount = 1,
                )
                val reader = socket.getInputStream().bufferedReader()
                while (!socket.isClosed) {
                    val line = reader.readLine() ?: break
                    decodeSnapshot(line)?.let { _incomingSnapshots.emit(it) }
                }
                error("The host ended the room")
            }.onFailure { error ->
                closeSockets()
                if (_state.value.role == ListenTogetherRole.GUEST) {
                    _state.value = ListenTogetherState(error = error.message ?: "Could not join the room")
                }
            }
        }
    }

    fun publish(snapshot: SharedPlaybackSnapshot) {
        if (_state.value.role != ListenTogetherRole.HOST) return
        lastSnapshot = snapshot
        if (peers.isEmpty()) return
        val payload = encodeSnapshot(snapshot) + "\n"
        scope.launch {
            peers.toList().forEach { peer ->
                runCatching {
                    synchronized(peer.writer) {
                        peer.writer.write(payload)
                        peer.writer.flush()
                    }
                }.onFailure { removePeer(peer) }
            }
        }
    }

    fun stop() {
        closeSockets()
        _state.value = ListenTogetherState()
    }

    private fun authorizeAndServePeer(socket: Socket) {
        val reader = socket.getInputStream().bufferedReader()
        if (reader.readLine() != "AUTH $roomToken") {
            socket.close()
            return
        }
        val peer = Peer(socket, socket.getOutputStream().bufferedWriter())
        peers += peer
        updatePeerCount()
        lastSnapshot?.let { snapshot ->
            synchronized(peer.writer) {
                peer.writer.write(encodeSnapshot(snapshot) + "\n")
                peer.writer.flush()
            }
        }
        try {
            while (!socket.isClosed) {
                if (reader.readLine() == null) break
            }
        } finally {
            removePeer(peer)
        }
    }

    private fun removePeer(peer: Peer) {
        peers.remove(peer)
        runCatching { peer.socket.close() }
        updatePeerCount()
    }

    private fun updatePeerCount() {
        val current = _state.value
        if (current.role == ListenTogetherRole.HOST) {
            _state.value = current.copy(peerCount = peers.size)
        }
    }

    private fun closeSockets() {
        runCatching { serverSocket?.close() }
        runCatching { guestSocket?.close() }
        serverSocket = null
        guestSocket = null
        peers.toList().forEach(::removePeer)
        peers.clear()
        lastSnapshot = null
    }

    private fun localIpv4Address(): String? = NetworkInterface.getNetworkInterfaces()?.toList()
        ?.asSequence()
        ?.filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
        ?.sortedByDescending { it.name.startsWith("wlan") || it.name.startsWith("wifi") }
        ?.flatMap { it.inetAddresses.toList().asSequence() }
        ?.filterIsInstance<Inet4Address>()
        ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
        ?.hostAddress

    private fun isWifiConnected(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private fun randomToken(): String = ByteArray(4).also(SecureRandom()::nextBytes)
        .joinToString("") { "%02X".format(it) }

    private fun parseInvite(value: String): Triple<String, Int, String>? {
        val match = INVITE_REGEX.matchEntire(value.trim()) ?: return null
        val port = match.groupValues[2].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        return Triple(match.groupValues[1], port, match.groupValues[3].uppercase())
    }

    private fun encodeSnapshot(value: SharedPlaybackSnapshot) = JSONObject()
        .put("songId", value.songId)
        .put("title", value.title)
        .put("artist", value.artist)
        .put("album", value.album)
        .put("artworkUrl", value.artworkUrl)
        .put("durationMs", value.durationMs)
        .put("positionMs", value.positionMs)
        .put("isPlaying", value.isPlaying)
        .put("sentAtMs", value.sentAtMs)
        .toString()

    private fun decodeSnapshot(payload: String): SharedPlaybackSnapshot? = runCatching {
        val json = JSONObject(payload)
        SharedPlaybackSnapshot(
            songId = json.getString("songId"),
            title = json.getString("title"),
            artist = json.optString("artist"),
            album = json.optString("album", "Listen Together"),
            artworkUrl = json.optString("artworkUrl").takeIf { it.isNotBlank() && it != "null" },
            durationMs = json.optLong("durationMs"),
            positionMs = json.optLong("positionMs"),
            isPlaying = json.optBoolean("isPlaying"),
            sentAtMs = json.optLong("sentAtMs", System.currentTimeMillis()),
        )
    }.getOrNull()

    private companion object {
        const val TAG = "ListenTogether"
        const val CONNECT_TIMEOUT_MS = 8_000
        val INVITE_REGEX = Regex("""([^:#\s]+):(\d{1,5})#([A-Fa-f0-9]{8})""")
    }
}
