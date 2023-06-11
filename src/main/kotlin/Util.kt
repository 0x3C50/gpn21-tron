fun Int.wrap(min: Int, max: Int): Int {
    var t = this
    while (t < min) t += (max - min)
    while (t >= max) t -= (max - min)
    return t
}

data class FloodFillResult(val free: Set<Position>, val walls: Set<Position>, val foundHeads: Set<Position>) {
}

/**
 * This looks a bit ugly, but it's the only real good way i found to do this kind of thing
 * Scans for gaps given a set of walls from a flood fill result. A "gap" is defined as any pair of Positions, that
 * have the same coordinate on one axis (X or Y), and the distance on the other axis is between [3..scanSize].
 * Additionally, all gaps must be connected by "free" blocks (from the same flood fill result).
 * This results in the resulting array containing gaps, that can technically be filled from the perspective of the flood fill.
 */
fun scanForHoles(
    walls: Set<Position>,
    free: Set<Position>,
    scanSize: Int,
    w: Int,
    h: Int
): Set<Pair<Position, Position>> {
    val l = mutableSetOf<Pair<Position, Position>>()
    for (wall in walls) {
        if (!walls.contains(Position((wall.x + 1).wrap(0, w), wall.y))) {
            for (xOffset in 3..scanSize) {
                val p = Position((wall.x + xOffset).wrap(0, w), wall.y)
                if (walls.contains(p)) {
                    l.add(Pair(wall, p))
                    break
                } else if (!free.contains(p)) break
            }
        }
        if (!walls.contains(Position((wall.x - 1).wrap(0, w), wall.y))) {
            for (xOffset in 3..scanSize) {
                val p = Position((wall.x - xOffset).wrap(0, w), wall.y)
                if (walls.contains(p)) {
                    l.add(Pair(wall, p))
                    break
                } else if (!free.contains(p)) break
            }
        }
        if (!walls.contains(Position(wall.x, (wall.y + 1).wrap(0, h)))) {
            for (yOffset in 3..scanSize) {
                val p = Position(wall.x, (wall.y + yOffset).wrap(0, h))
                if (walls.contains(p)) {
                    l.add(Pair(wall, p))
                    break
                } else if (!free.contains(p)) break
            }
        }
        if (!walls.contains(Position(wall.x, (wall.y - 1).wrap(0, h)))) {
            for (yOffset in 3..scanSize) {
                val p = Position(wall.x, (wall.y - yOffset).wrap(0, h))

                if (walls.contains(p)) {
                    l.add(Pair(wall, p))
                    break
                } else if (!free.contains(p)) break
            }
        }
    }
    return l
}

fun floodFill(game: Game, grid: Array<Array<Int>>, startX: Int, startY: Int): FloodFillResult {
    val free = mutableSetOf<Position>()
    val visited = mutableSetOf<Position>()
    val heads = mutableSetOf<Position>()
    val q = ArrayDeque<Position>()
    val walls = mutableSetOf<Position>()
    val snakeHeads = game.snakes.filter { it.id != game.ourId }.map { it.positions.last() }
    q.add(Position(startX, startY))
    while (!q.isEmpty()) {
        val currentPos = q.removeFirst()
        if (visited.contains(currentPos)) {
            continue
        }
        visited.add(currentPos)
        if (grid[currentPos.y][currentPos.x] != -1) {
            walls.add(currentPos)
            if (snakeHeads.contains(currentPos)) {
                heads.add(currentPos)
            }
            continue
        }
        free += currentPos
        q.addAll(currentPos.neighbors().map {
            val y = it.y.wrap(0, grid.size)
            val x = it.x.wrap(0, grid[y].size)
            Position(x, y)
        })
    }
    return FloodFillResult(free, walls, heads)
}

fun formatPacket(name: String, vararg args: Any): String {
    var s = "$name|"
    for (arg in args) {
        s += "$arg|"
    }
    return s.substring(0, s.length - 1)
}