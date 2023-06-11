import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// const val ip = "gpn-tron.duckdns.org" // old live ip, now shut down
const val ip = "127.0.0.1"
const val port = 4000

// all offsets one could move to each tick, along with the position offset they'd induce
val offsets = arrayOf(
    Offset(1, 0, "right"),
    Offset(-1, 0, "left"),
    Offset(0, 1, "down"),
    Offset(0, -1, "up"),
)

var currentGoalStack: ArrayDeque<Position> = ArrayDeque()
var currentPath: Array<Position>? = null

fun main() {
    val sock = Socket()
    sock.tcpNoDelay = true
    sock.connect(InetSocketAddress(ip, port))
    val input = PacketInput(sock.getInputStream())
    val output = PacketOutput(sock.getOutputStream())

    var currentGame: Game? = null
    var ourCurrentPosX = 0
    var ourCurrentPosY = 0
    var died = false
    var endgame = false

    var lastTick = System.nanoTime()
    println("Connected, logging in. Assuming game is already running, we'll wait for a new one to start.")
    // I am aware that I am leaking my own credentials here, the live instance is already shut down. Good luck finding
    // any use in these :^)
    output.write("join", "bot.kt", "ktbot1234+-")

    while (true) {
        val readPacket = input.readPacket()
        when (readPacket.name) {
            "game" -> {
                // new game
                died = false
                val width = readPacket.args[0].toInt()
                val height = readPacket.args[1].toInt()
                currentGame = Game(width, height, readPacket.args[2].toInt())
                currentGame.snakes.add(Snake(currentGame.ourId))
                println("Game started: w: $width, h: $height, our id: ${currentGame.ourId}")
                endgame = false
                currentGoalStack.clear()
            }

            "pos" -> {
                currentGame!!
                val snakeId = readPacket.args[0].toInt()
                val movedToX = readPacket.args[1].toInt()
                val movedToY = readPacket.args[2].toInt()
                if (snakeId == currentGame.ourId) {
                    ourCurrentPosY = movedToY
                    ourCurrentPosX = movedToX
                }
                var foundSnake = currentGame.snakes.find { it.id == snakeId }
                if (foundSnake == null) {
                    foundSnake = Snake(snakeId)
                    currentGame.snakes.add(foundSnake)
                }
                foundSnake.positions.add(Position(movedToX, movedToY))
            }

            "tick" -> {
                val tickStart = System.nanoTime()
                val tickDelay = tickStart - lastTick
                lastTick = tickStart
                var postMessage = ""
                if (!died) {
                    currentGame!!
                    val freeMoves = mutableListOf<Move>()
                    val grid = currentGame.buildGrid()
                    val floodCache = mutableMapOf<Position, FloodFillResult>()
                    val snakeHeads = currentGame.snakes.filter { it.id != currentGame!!.ourId }.map { it.positions.last() }
                    for (offset in offsets) {
                        val nxp = (ourCurrentPosX + offset.x).wrap(0, currentGame.width)
                        val nyp = (ourCurrentPosY + offset.y).wrap(0, currentGame.height)
                        val posAround = arrayOf(
                            Position((nxp + 1).wrap(0, currentGame.width), nyp),
                            Position((nxp - 1).wrap(0, currentGame.width), nyp),
                            Position((nxp), (nyp + 1).wrap(0, currentGame.height)),
                            Position((nxp), (nyp - 1).wrap(0, currentGame.height)),
                        )
                        if (currentGame.snakes.map { it.positions }.flatten().none { it.x == nxp && it.y == nyp }) {
                            // this spot is free from any other snake, we could move here
                            // is any other snake **HEAD** adjacent to this square?
                            val snakeHeadNear = snakeHeads.any { posAround.contains(it) }
                            // distance from this point to the nearest snake head
                            // turns to 0 once the endgame state gets enabled, we want to fight
                            val minSnakeHeadDistance = if (!endgame) snakeHeads.minOf { it.distanceTo(nxp, nyp) } else 0.0

                            // flood fill from that position out
                            val flood = floodFill(currentGame, grid, nxp, nyp)
                            // score of that position
                            var score = flood.free.size + ceil(minSnakeHeadDistance).toInt()
                            if (currentPath != null && currentPath!!.contains(Position(nxp, nyp))) {
                                // this point is on the path, give a small score bonus to prefer this one if the others are indifferent
                                score += 5
                            }
                            floodCache[Position(nxp, nyp)] = flood
                            if (snakeHeadNear) {
                                // very bad position to be in as you might die going to it
                                // however it might be worth the risk if the other side has SIGNIFICANTLY more space
                                // (= one risk of death by adjacent snake is worth 150 spaces)
                                score -= 150
                            }
                            freeMoves.add(Move(nxp, nyp, offset.name, score))
                        }
                    }
                    if (freeMoves.isEmpty()) {
                        // we have literally nowhere to go, everything's blocked off.
                        // fuck it, take the bullet and commit suicide
                        freeMoves.add(Move(ourCurrentPosX, (ourCurrentPosY - 1).wrap(0, currentGame.height), "up", 0))
                    }
                    // the best move to make according to score
                    val bestMove = freeMoves.maxBy { it.score }
                    output.write("move", bestMove.name)
                    postMessage = "Sent move: ${bestMove.name}"
                    ourCurrentPosX = bestMove.toX
                    ourCurrentPosY = bestMove.toY
                    if (currentGoalStack.isNotEmpty() && currentGoalStack.first()
                            .distanceTo(ourCurrentPosX, ourCurrentPosY) <= 1.05
                    ) {
                        currentGoalStack.removeFirst() // we're there, next goal
                    }
                    val filled = floodCache[Position(ourCurrentPosX, ourCurrentPosY)] ?: floodFill(
                        currentGame,
                        grid,
                        ourCurrentPosX,
                        ourCurrentPosY
                    )
                    if (filled.foundHeads.isNotEmpty() && (filled.free.size < 200 || endgame)) {
                        // we're either in a mildly tight space with another person, or in the endgame with another person
                        val nearestSnakeHead = filled.foundHeads.minBy { it.distanceTo(ourCurrentPosX, ourCurrentPosY) }
                        val holes = scanForHoles(filled.walls, filled.free, 8, currentGame.width, currentGame.height)
                        val nearestHole = holes.minByOrNull {
                            min(
                                it.first.distanceTo(nearestSnakeHead.x, nearestSnakeHead.y),
                                it.second.distanceTo(nearestSnakeHead.x, nearestSnakeHead.y)
                            )
                        }
                        if (currentGoalStack.isEmpty() && nearestHole != null) {
                            // we have no goal, but we have a suggestion, go for it
                            currentGoalStack.add(nearestHole.first)
                            currentGoalStack.add(nearestHole.second)
                        }
                    } else {
                        currentGoalStack.clear()
                    }

                    val optimalPathToTarget = if (currentGoalStack.isNotEmpty()) aStar(
                        currentGame,
                        Position(ourCurrentPosX, ourCurrentPosY),
                        currentGoalStack.first()
                    ) else null
                    currentPath = optimalPathToTarget
                    if (currentGoalStack.isNotEmpty() && optimalPathToTarget == null) {
                        println("Can't reach target destination ${currentGoalStack.first()}, skipping")
                        currentGoalStack.removeFirst() // can't reach, remove
                    }
                }
                val now = System.nanoTime()
                currentGame!!.printSnakes()
                if (postMessage.isNotEmpty()) println(postMessage)
                println("Tick took: ${(now - tickStart) / 1e6f} ms, tick interval is ${tickDelay / 1e6f} ms")
            }

            "die" -> {
                if (readPacket.args.any { it == "${currentGame!!.ourId}" }) {
                    // loss packet arrives afterward, no point of logging it
                    died = true
                } else {
                    for (arg in readPacket.args) {
                        val toInt = arg.toInt()
                        currentGame!!.snakes.removeIf { it.id == toInt }
                    }
                    if (currentGame!!.snakes.size <= 4) {
                        endgame = true
                    }
                }
            }

            "error" -> {
                println("!! Error happened on server: ${readPacket.args.contentToString()}")
            }

            "lose" -> {
                val wins = readPacket.args[0].toInt()
                val losses = readPacket.args[1].toInt()
                println("We lost: Now at $wins wins / $losses losses. Win Ratio: ${wins.toFloat()/(wins+losses)}")
                died = true
            }

            "win" -> {
                val wins = readPacket.args[0].toInt()
                val losses = readPacket.args[1].toInt()
                println("We won! Now at $wins wins / $losses losses. Win Ratio: ${wins.toFloat()/(wins+losses)}")
                currentGame = null
            }
        }
    }
}

data class Position(val x: Int, val y: Int) {
    fun neighbors(): Array<Position> = arrayOf(
        Position(x - 1, y),
        Position(x + 1, y),
        Position(x, y - 1),
        Position(x, y + 1)
    )

    fun distanceTo(tx: Int, ty: Int): Double {
        // regular Pythagoras, f(x, y) = sqrt(x² + y²)
        return sqrt((x - tx).toDouble().pow(2.0) + (y - ty).toDouble().pow(2.0))
    }
}

data class Snake(
    val id: Int,
    val positions: MutableSet<Position> = mutableSetOf(),
    val color: Int = (Math.random() * 0xFFFFFF).toInt()
)

data class Offset(val x: Int, val y: Int, val name: String)
data class Move(val toX: Int, val toY: Int, val name: String, val score: Int)

data class Game(val width: Int, val height: Int, val ourId: Int) {
    val snakes = mutableListOf<Snake>()
    fun buildGrid(): Array<Array<Int>> {
        val grid = Array(height) { Array(width) { -1 } }
        for (snake in snakes) {
            for (position in snake.positions) {
                grid[position.y][position.x] = snake.id
            }
        }
        return grid
    }

    fun printSnakes() {
        val grid = buildGrid()
        val heads = snakes.map { it.positions.last() }
        var s = ""
        if (currentPath != null) {
            for (position in currentPath!!) {
                grid[position.y][position.x] = -2
            }
        }
        for (position in currentGoalStack) {
            grid[position.y][position.x] = -3
        }
        for (row in grid.withIndex()) {
            for (int in row.value.withIndex()) {
                val c = when (int.value) {
                    -3 -> "\u001B[33m!\u001B[0m"
                    -2 -> "\u001B[33m*\u001B[0m"
                    -1 -> "."
                    ourId -> if (heads.any { it.x == int.index && it.y == row.index }) "\u001B[32m#\u001B[0m" else "\u001b[31m#\u001b[0m"
                    else -> {
                        val sn = snakes.find { it.id == int.value }!!
                        val r = sn.color shr 16 and 0xFF
                        val g = sn.color shr 8 and 0xFF
                        val b = sn.color and 0xFF
                        ansiTruecolor(r, g, b, if (heads.any { it.x == int.index && it.y == row.index }) "S" else "s")
                    }
                }
                s += "$c "
            }
            s += "\n"
        }
        print(s)
    }
}