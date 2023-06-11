fun reconstructPath(cameFrom: Map<Position, Position>, current: Position): Array<Position> {
    val totalPath = mutableListOf<Position>()
    var c = current
    while (cameFrom.containsKey(c)) {
        totalPath.add(c)
        c = cameFrom[c]!!
    }
    return totalPath.toTypedArray()
}

fun aStar(game: Game, start: Position, goal: Position): Array<Position>? {
    val openSet = mutableSetOf(start)
    val cameFrom = mutableMapOf<Position, Position>()
    val gScore = mutableMapOf<Position, Float>()
    gScore[start] = 0f
    val fScore = mutableMapOf<Position, Float>()
    fScore[start] = start.distanceTo(goal.x, goal.y).toFloat()
    while (openSet.isNotEmpty()) {
        val current = openSet.minBy { fScore[it] ?: Float.POSITIVE_INFINITY }
        if (current.distanceTo(goal.x, goal.y) <= 1.05) {
            return reconstructPath(cameFrom, current)
        }
        openSet.remove(current)
        for (neighbor in current.neighbors().map {
            Position(it.x.wrap(0, game.width), it.y.wrap(0, game.height))
        }) {
            if (game.snakes.any { it.positions.contains(neighbor) }) continue // can't go there
            val tentativeG = (gScore[current] ?: Float.POSITIVE_INFINITY) + 0.5
            if (tentativeG < (gScore[neighbor] ?: Float.POSITIVE_INFINITY)) {
                cameFrom[neighbor] = current
                gScore[neighbor] = tentativeG.toFloat()
                fScore[neighbor] = (tentativeG + neighbor.distanceTo(goal.x, goal.y)).toFloat()
                if (!openSet.contains(neighbor)) openSet.add(neighbor)
            }
        }
    }
    return null
}