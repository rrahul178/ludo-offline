package com.example.ludo

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import kotlin.random.Random

class GameActivity : AppCompatActivity(), NetworkManager.Listener {

    private lateinit var boardView: LudoBoardView
    private lateinit var tvStatus: TextView
    private lateinit var tvDice: TextView
    private lateinit var btnRoll: Button

    private lateinit var net: NetworkManager
    private var isHost = false
    private var myPlayerIndex = 0
    private var numPlayers = 4
    private var connectedClients = 0

    private var state = GameLogic.State(numPlayers)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        boardView = findViewById(R.id.boardView)
        tvStatus = findViewById(R.id.tvStatus)
        tvDice = findViewById(R.id.tvDice)
        btnRoll = findViewById(R.id.btnRoll)

        net = NetworkManager(this)

        val mode = intent.getStringExtra("mode") ?: "host"
        if (mode == "host") {
            isHost = true
            myPlayerIndex = 0
            numPlayers = intent.getIntExtra("numPlayers", 4)
            state = GameLogic.State(numPlayers)
            net.startServer()
            tvStatus.text = "অপেক্ষা করুন... আরও ${numPlayers - 1} জন প্লেয়ার Hotspot-এ join করবে।"
            btnRoll.isEnabled = false
        } else {
            isHost = false
            val ip = intent.getStringExtra("hostIp") ?: ""
            net.connectToHost(ip)
            tvStatus.text = "হোস্টের সাথে কানেক্ট হচ্ছে..."
            btnRoll.isEnabled = false
        }

        boardView.state = state

        btnRoll.setOnClickListener { onRollClicked() }
        boardView.onTokenTapped = { p, t -> onTokenTapped(p, t) }
    }

    // ----------------------------------------------------------------
    // UI actions
    // ----------------------------------------------------------------

    private fun onRollClicked() {
        if (state.winner != -1) return
        if (state.currentTurn != myPlayerIndex) {
            Toast.makeText(this, "এখন আপনার turn না।", Toast.LENGTH_SHORT).show()
            return
        }
        if (state.diceRolled) return

        if (isHost) {
            val dice = Random.nextInt(1, 7)
            processRoll(myPlayerIndex, dice)
        } else {
            val msg = JSONObject()
            msg.put("type", "action")
            msg.put("action", "roll")
            msg.put("player", myPlayerIndex)
            net.sendToHost(msg)
        }
    }

    private fun onTokenTapped(player: Int, tokenIdx: Int) {
        if (state.winner != -1) return
        if (state.currentTurn != myPlayerIndex) return
        if (player != myPlayerIndex) return
        if (!state.diceRolled) return

        val movable = GameLogic.movableTokens(state, myPlayerIndex, state.lastDice)
        if (!movable.contains(tokenIdx)) {
            Toast.makeText(this, "এই টোকেন এখন move করা যাবে না।", Toast.LENGTH_SHORT).show()
            return
        }

        if (isHost) {
            processMove(myPlayerIndex, tokenIdx)
        } else {
            val msg = JSONObject()
            msg.put("type", "action")
            msg.put("action", "move")
            msg.put("tokenIdx", tokenIdx)
            msg.put("player", myPlayerIndex)
            net.sendToHost(msg)
        }
    }

    // ----------------------------------------------------------------
    // HOST-SIDE GAME LOGIC (authoritative)
    // ----------------------------------------------------------------

    /** Process a dice roll for `player`. Only valid on host. */
    private fun processRoll(player: Int, dice: Int) {
        if (player != state.currentTurn || state.diceRolled) return

        state.lastDice = dice
        state.diceRolled = true

        val movable = GameLogic.movableTokens(state, player, dice)
        if (movable.isEmpty()) {
            state.message = "${GameLogic.COLOR_NAMES[player]} এর কোনো চাল নেই। পরের প্লেয়ার।"
            advanceTurn(false)
        } else {
            state.message = "${GameLogic.COLOR_NAMES[player]} dice = $dice"
        }
        broadcastState()
    }

    /** Process a token move for `player`. Only valid on host. */
    private fun processMove(player: Int, tokenIdx: Int) {
        if (player != state.currentTurn || !state.diceRolled) return
        val movable = GameLogic.movableTokens(state, player, state.lastDice)
        if (!movable.contains(tokenIdx)) return

        val (extraTurn, captured, finished) = GameLogic.applyMove(state, player, tokenIdx, state.lastDice)

        if (GameLogic.hasWon(state, player)) {
            state.winner = player
            state.message = "${GameLogic.COLOR_NAMES[player]} জিতেছে! 🎉"
            broadcastState()
            return
        }

        var msg = "${GameLogic.COLOR_NAMES[player]} টোকেন সরিয়েছে।"
        if (captured) msg += " একটি প্রতিপক্ষের টোকেন কাটা পড়েছে!"
        if (finished) msg += " একটি টোকেন ঘরে পৌঁছেছে!"
        state.message = msg

        advanceTurn(extraTurn)
        broadcastState()
    }

    /** Advance currentTurn to next player unless extraTurn is true. */
    private fun advanceTurn(extraTurn: Boolean) {
        state.diceRolled = false
        state.lastDice = 0
        if (!extraTurn) {
            var next = state.currentTurn
            for (i in 1..state.numPlayers) {
                next = (next + 1) % state.numPlayers
                break
            }
            state.currentTurn = next
        }
    }

    private fun broadcastState() {
        val msg = JSONObject()
        msg.put("type", "state")
        msg.put("state", state.toJson())
        net.broadcast(msg)
        runOnUiThread { refreshUi() }
    }

    // ----------------------------------------------------------------
    // NetworkManager.Listener callbacks (runs on background threads)
    // ----------------------------------------------------------------

    override fun onMessage(json: JSONObject) {
        when (json.optString("type")) {
            "welcome" -> {
                myPlayerIndex = json.getInt("playerIndex")
                runOnUiThread {
                    tvStatus.text = "কানেক্টেড! আপনি ${GameLogic.COLOR_NAMES.getOrElse(myPlayerIndex){"?"}} (Player ${myPlayerIndex + 1})"
                }
            }
            "state" -> {
                val newState = GameLogic.State.fromJson(json.getJSONObject("state"))
                state = newState
                runOnUiThread { refreshUi() }
            }
            "action" -> {
                // Only the host processes action requests from clients
                if (!isHost) return
                // Determine which player this action belongs to. We track that via
                // the connection order: client writers are added in order, so the
                // Nth connected client corresponds to playerIndex N. Since this
                // callback doesn't carry the sender's socket, the message itself
                // must include the player index.
                val player = json.optInt("player", -1)
                if (player == -1) return
                when (json.optString("action")) {
                    "roll" -> {
                        if (player == state.currentTurn && !state.diceRolled) {
                            val dice = Random.nextInt(1, 7)
                            processRoll(player, dice)
                        }
                    }
                    "move" -> {
                        val tokenIdx = json.optInt("tokenIdx", -1)
                        if (tokenIdx in 0..3) processMove(player, tokenIdx)
                    }
                }
            }
        }
    }

    override fun onClientCountChanged(count: Int) {
        connectedClients = count
        runOnUiThread {
            if (isHost) {
                if (connectedClients >= numPlayers - 1) {
                    tvStatus.text = "সব প্লেয়ার যুক্ত হয়েছে! খেলা শুরু হচ্ছে..."
                    state.currentTurn = 0
                    state.diceRolled = false
                    state.message = "${GameLogic.COLOR_NAMES[0]} প্রথমে খেলবে।"
                    broadcastState()
                    refreshUi()
                } else {
                    tvStatus.text = "অপেক্ষা করুন... (${connectedClients}/${numPlayers - 1} জন যুক্ত হয়েছে)"
                }
            }
        }
    }

    override fun onError(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    override fun onConnected() {
        runOnUiThread {
            tvStatus.text = "হোস্টের সাথে কানেক্ট হয়েছে। অপেক্ষা করুন..."
        }
    }

    // ----------------------------------------------------------------
    // UI refresh
    // ----------------------------------------------------------------

    private fun refreshUi() {
        boardView.state = state
        boardView.invalidate()

        tvDice.text = if (state.lastDice > 0) state.lastDice.toString() else "-"

        if (state.winner != -1) {
            tvStatus.text = state.message
            btnRoll.isEnabled = false
            return
        }

        val turnName = GameLogic.COLOR_NAMES.getOrElse(state.currentTurn) { "?" }
        val myTurn = state.currentTurn == myPlayerIndex
        val base = if (myTurn) "আপনার (${GameLogic.COLOR_NAMES.getOrElse(myPlayerIndex){"?"}}) turn" else "$turnName এর turn"
        tvStatus.text = if (state.message.isNotEmpty()) "$base — ${state.message}" else base

        btnRoll.isEnabled = myTurn && !state.diceRolled
    }

    override fun onDestroy() {
        super.onDestroy()
        net.close()
    }
}
