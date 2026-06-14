package com.example.ludo

import org.json.JSONArray
import org.json.JSONObject

/**
 * Core Ludo board geometry and rules.
 * Board is a 15x15 grid (rows 0-14, cols 0-14).
 * Each token has a "path position":
 *   -1            => in yard (not yet started)
 *   0..50         => on the shared 52-cell main track (51 cells used per player)
 *   51..55        => in player's private home column
 *   56            => finished (reached home)
 */
object GameLogic {

    const val NUM_PLAYERS_MAX = 4
    const val TOKENS_PER_PLAYER = 4
    const val PATH_LEN = 57 // positions 0..56
    const val FINISH = 56

    // Player colors in fixed order
    val COLOR_NAMES = arrayOf("Red", "Green", "Yellow", "Blue")
    val COLOR_HEX = arrayOf("#E53935", "#43A047", "#FBC02D", "#1E88E5")

    // The 52-cell shared main track, as (row, col) on a 15x15 grid.
    val MAIN_PATH: Array<IntArray> = buildMainPath()

    // Start index (entry point into MAIN_PATH) for each player.
    val START_INDEX = intArrayOf(0, 13, 26, 39)

    // "Safe" cells (no capture) = each player's entry square.
    val SAFE_CELLS: Set<Int> = START_INDEX.toHashSet()

    // Home column cells (6 cells) for each player, leading to center (7,7).
    val HOME_COLUMNS: Array<Array<IntArray>> = arrayOf(
        // Red: row 7, cols 1..6
        arrayOf(intArrayOf(7,1), intArrayOf(7,2), intArrayOf(7,3), intArrayOf(7,4), intArrayOf(7,5), intArrayOf(7,6)),
        // Green: col 7, rows 1..6
        arrayOf(intArrayOf(1,7), intArrayOf(2,7), intArrayOf(3,7), intArrayOf(4,7), intArrayOf(5,7), intArrayOf(6,7)),
        // Yellow: row 7, cols 13..8
        arrayOf(intArrayOf(7,13), intArrayOf(7,12), intArrayOf(7,11), intArrayOf(7,10), intArrayOf(7,9), intArrayOf(7,8)),
        // Blue: col 7, rows 13..8
        arrayOf(intArrayOf(13,7), intArrayOf(12,7), intArrayOf(11,7), intArrayOf(10,7), intArrayOf(9,7), intArrayOf(8,7))
    )

    // Yard positions (4 token slots) for each player - within their 6x6 corner.
    val YARD_CELLS: Array<Array<IntArray>> = arrayOf(
        // Red top-left
        arrayOf(intArrayOf(1,1), intArrayOf(1,4), intArrayOf(4,1), intArrayOf(4,4)),
        // Green top-right
        arrayOf(intArrayOf(1,10), intArrayOf(1,13), intArrayOf(4,10), intArrayOf(4,13)),
        // Yellow bottom-right
        arrayOf(intArrayOf(10,10), intArrayOf(10,13), intArrayOf(13,10), intArrayOf(13,13)),
        // Blue bottom-left
        arrayOf(intArrayOf(10,1), intArrayOf(10,4), intArrayOf(13,1), intArrayOf(13,4))
    )

    private fun buildMainPath(): Array<IntArray> {
        val list = mutableListOf<IntArray>()
        // Segment 1: (6,1) -> (6,5)
        for (c in 1..5) list.add(intArrayOf(6, c))
        // Segment 2: (5,6) -> (0,6)
        for (r in 5 downTo 0) list.add(intArrayOf(r, 6))
        // Segment 3: (0,7)
        list.add(intArrayOf(0, 7))
        // Segment 4: (0,8) -> (5,8)
        for (r in 0..5) list.add(intArrayOf(r, 8))
        // Segment 5: (6,9) -> (6,14)
        for (c in 9..14) list.add(intArrayOf(6, c))
        // Segment 6: (7,14)
        list.add(intArrayOf(7, 14))
        // Segment 7: (8,14) -> (8,9)
        for (c in 14 downTo 9) list.add(intArrayOf(8, c))
        // Segment 8: (9,8) -> (14,8)
        for (r in 9..14) list.add(intArrayOf(r, 8))
        // Segment 9: (14,7)
        list.add(intArrayOf(14, 7))
        // Segment 10: (14,6) -> (9,6)
        for (r in 14 downTo 9) list.add(intArrayOf(r, 6))
        // Segment 11: (8,5) -> (8,0)
        for (c in 8 downTo 0) list.add(intArrayOf(8, c))
        // Segment 12: (7,0)
        list.add(intArrayOf(7, 0))
        // Segment 13: (6,0)
        list.add(intArrayOf(6, 0))
        return list.toTypedArray()
    }

    /** Convert a token's path position to a (row,col) cell for drawing. -1 => use yard cell. */
    fun cellForPosition(player: Int, tokenIdx: Int, pos: Int): IntArray {
        return when {
            pos < 0 -> YARD_CELLS[player][tokenIdx]
            pos in 0..50 -> {
                val idx = (START_INDEX[player] + pos) % 52
                MAIN_PATH[idx]
            }
            pos in 51..55 -> HOME_COLUMNS[player][pos - 51]
            else -> intArrayOf(7, 7) // finished -> center
        }
    }

    /** Returns the global MAIN_PATH index for a player's path position (0..50), or -1 if not on main track. */
    fun globalIndex(player: Int, pos: Int): Int {
        if (pos < 0 || pos > 50) return -1
        return (START_INDEX[player] + pos) % 52
    }

    fun isSafe(globalIdx: Int): Boolean = SAFE_CELLS.contains(globalIdx)

    // ----------------- Game State -----------------
    // positions[player][token] = path position (-1..56)
    class State(
        var numPlayers: Int,
        var positions: Array<IntArray> = Array(NUM_PLAYERS_MAX) { IntArray(TOKENS_PER_PLAYER) { -1 } },
        var currentTurn: Int = 0,
        var lastDice: Int = 0,
        var diceRolled: Boolean = false,
        var winner: Int = -1,
        var message: String = ""
    ) {
        fun toJson(): JSONObject {
            val o = JSONObject()
            o.put("numPlayers", numPlayers)
            val posArr = JSONArray()
            for (p in 0 until NUM_PLAYERS_MAX) {
                val pa = JSONArray()
                for (t in 0 until TOKENS_PER_PLAYER) pa.put(positions[p][t])
                posArr.put(pa)
            }
            o.put("positions", posArr)
            o.put("currentTurn", currentTurn)
            o.put("lastDice", lastDice)
            o.put("diceRolled", diceRolled)
            o.put("winner", winner)
            o.put("message", message)
            return o
        }

        companion object {
            fun fromJson(o: JSONObject): State {
                val numPlayers = o.getInt("numPlayers")
                val posArr = o.getJSONArray("positions")
                val positions = Array(NUM_PLAYERS_MAX) { p ->
                    val pa = posArr.getJSONArray(p)
                    IntArray(TOKENS_PER_PLAYER) { t -> pa.getInt(t) }
                }
                val s = State(numPlayers, positions)
                s.currentTurn = o.getInt("currentTurn")
                s.lastDice = o.getInt("lastDice")
                s.diceRolled = o.getBoolean("diceRolled")
                s.winner = o.getInt("winner")
                s.message = o.optString("message", "")
                return s
            }
        }
    }

    /** Returns list of token indices for `player` that can legally move with `dice`. */
    fun movableTokens(state: State, player: Int, dice: Int): List<Int> {
        val result = mutableListOf<Int>()
        for (t in 0 until TOKENS_PER_PLAYER) {
            val pos = state.positions[player][t]
            if (pos == -1) {
                if (dice == 6) result.add(t)
            } else if (pos < FINISH) {
                val newPos = pos + dice
                if (newPos <= FINISH) result.add(t)
            }
        }
        return result
    }

    /**
     * Apply a move. Returns Triple(extraTurn, captured, justFinished)
     */
    fun applyMove(state: State, player: Int, tokenIdx: Int, dice: Int): Triple<Boolean, Boolean, Boolean> {
        val pos = state.positions[player][tokenIdx]
        var captured = false
        var justFinished = false

        val newPos = if (pos == -1) 0 else pos + dice
        state.positions[player][tokenIdx] = newPos

        if (newPos == FINISH) {
            justFinished = true
        } else if (newPos in 0..50) {
            val g = globalIndex(player, newPos)
            if (!isSafe(g)) {
                for (op in 0 until NUM_PLAYERS_MAX) {
                    if (op == player || op >= state.numPlayers) continue
                    for (ot in 0 until TOKENS_PER_PLAYER) {
                        val opPos = state.positions[op][ot]
                        if (opPos in 0..50 && globalIndex(op, opPos) == g) {
                            state.positions[op][ot] = -1
                            captured = true
                        }
                    }
                }
            }
        }

        val extraTurn = dice == 6 || captured || justFinished
        return Triple(extraTurn, captured, justFinished)
    }

    /** Check if all 4 tokens of `player` have finished. */
    fun hasWon(state: State, player: Int): Boolean {
        return state.positions[player].all { it == FINISH }
    }
}
