package com.example.ludo

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Simple line-delimited JSON over TCP for offline multiplayer on a local hotspot.
 * Host: runs a ServerSocket, accepts up to (numPlayers-1) clients, assigns each
 *        client a playerIndex (1,2,3...) and broadcasts game state to everyone.
 * Client: connects to the host's IP, sends action requests, receives state updates.
 */
class NetworkManager(private val listener: Listener) {

    interface Listener {
        fun onMessage(json: JSONObject)
        fun onClientCountChanged(count: Int)
        fun onError(msg: String)
        fun onConnected()
    }

    companion object {
        const val PORT = 8988
    }

    private val pool = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null
    private val clientWriters = CopyOnWriteArrayList<PrintWriter>()

    private var clientSocket: Socket? = null
    private var clientWriter: PrintWriter? = null

    var isHost = false
        private set

    // ---------------- HOST ----------------
    fun startServer() {
        isHost = true
        pool.execute {
            try {
                val server = ServerSocket(PORT)
                serverSocket = server
                while (!server.isClosed) {
                    val socket = server.accept()
                    handleNewClient(socket)
                }
            } catch (e: Exception) {
                listener.onError("Server error: ${e.message}")
            }
        }
    }

    private fun handleNewClient(socket: Socket) {
        val writer = PrintWriter(socket.getOutputStream(), true)
        // assign player index: host=0, first client=1, second=2, third=3
        val assignedIndex = clientWriters.size + 1
        val welcome = JSONObject()
        welcome.put("type", "welcome")
        welcome.put("playerIndex", assignedIndex)
        try { writer.println(welcome.toString()) } catch (e: Exception) { }

        clientWriters.add(writer)
        listener.onClientCountChanged(clientWriters.size)
        pool.execute {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (true) {
                    val line = reader.readLine() ?: break
                    val json = JSONObject(line)
                    listener.onMessage(json)
                }
            } catch (e: Exception) {
                // client disconnected
            } finally {
                clientWriters.remove(writer)
                listener.onClientCountChanged(clientWriters.size)
            }
        }
    }

    /** Host: send to all connected clients. */
    fun broadcast(json: JSONObject) {
        val line = json.toString()
        for (w in clientWriters) {
            try { w.println(line) } catch (e: Exception) { }
        }
    }

    // ---------------- CLIENT ----------------
    fun connectToHost(ip: String) {
        isHost = false
        pool.execute {
            try {
                val socket = Socket(ip, PORT)
                clientSocket = socket
                clientWriter = PrintWriter(socket.getOutputStream(), true)
                listener.onConnected()
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (true) {
                    val line = reader.readLine() ?: break
                    val json = JSONObject(line)
                    listener.onMessage(json)
                }
            } catch (e: Exception) {
                listener.onError("Connect failed: ${e.message}")
            }
        }
    }

    /** Client: send action to host. */
    fun sendToHost(json: JSONObject) {
        try {
            clientWriter?.println(json.toString())
        } catch (e: Exception) { }
    }

    fun close() {
        try { serverSocket?.close() } catch (e: Exception) { }
        try { clientSocket?.close() } catch (e: Exception) { }
        for (w in clientWriters) { try { w.close() } catch (e: Exception) { } }
        clientWriters.clear()
    }
}
