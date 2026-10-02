package com.kekitemkekifalta.core

/** One aisle of a market, in route order. */
data class AisleSpec(val id: String, val name: String, val sectors: List<Sector>)

/** Items that fall in one aisle; [aisle] null is the "sem corredor" bucket at the end. */
data class RouteGroup<T>(val aisle: AisleSpec?, val items: List<T>)

object RouteGrouping {

    /**
     * Places each item in an aisle: the per-item override (if it points to an existing aisle),
     * else the first aisle in route order holding the item's sector. Items with no aisle go to a
     * trailing group with `aisle == null` (only present when non-empty).
     * Every aisle gets a group, even empty ones, so the map can show them collapsed.
     */
    fun <T> group(
        aisles: List<AisleSpec>,
        items: List<T>,
        sectorOf: (T) -> Sector,
        overrideAisleOf: (T) -> String? = { null },
        sortKey: (T) -> String,
    ): List<RouteGroup<T>> {
        val aisleIds = aisles.map { it.id }.toSet()
        val bySector = HashMap<Sector, String>()
        aisles.forEach { aisle -> aisle.sectors.forEach { bySector.putIfAbsent(it, aisle.id) } }
        val buckets = items.groupBy { item ->
            overrideAisleOf(item)?.takeIf { it in aisleIds } ?: bySector[sectorOf(item)]
        }
        val groups = aisles.map { aisle ->
            RouteGroup(aisle, buckets[aisle.id].orEmpty().sortedBy(sortKey))
        }
        val loose = buckets[null].orEmpty().sortedBy(sortKey)
        return if (loose.isEmpty()) groups else groups + RouteGroup(null, loose)
    }

    /** Fallback grouping when no market is chosen: by sector, in the default order. */
    fun <T> bySector(items: List<T>, sectorOf: (T) -> Sector, sortKey: (T) -> String): List<Pair<Sector, List<T>>> =
        items.groupBy(sectorOf).toList()
            .sortedBy { it.first.ordinal }
            .map { (sector, list) -> sector to list.sortedBy(sortKey) }
}

object RouteLearning {

    /**
     * Compares the order in which aisles were actually visited with the registered route.
     *
     * @param route aisle ids in the registered order.
     * @param checkedAisles the aisle of each item, in the order the items were checked (repeats ok).
     * @return the proposed route, or null when the visit agrees with the route (or there is too
     * little information). Unvisited aisles keep their slots; visited ones are reordered among the
     * slots they occupy. An aisle's visit time is the median of its checks, so one item picked up
     * late does not drag the whole aisle.
     */
    fun proposeRoute(route: List<String>, checkedAisles: List<String>): List<String>? {
        val routeIndex = route.withIndex().associate { it.value to it.index }
        val positions = LinkedHashMap<String, MutableList<Int>>()
        checkedAisles.forEachIndexed { index, aisle ->
            if (aisle in routeIndex) positions.getOrPut(aisle) { mutableListOf() }.add(index)
        }
        if (positions.size < 2) return null

        val observed = positions.entries
            .sortedWith(compareBy<Map.Entry<String, MutableList<Int>>> { median(it.value) }.thenBy { routeIndex.getValue(it.key) })
            .map { it.key }
        val registered = route.filter { it in positions }
        if (observed == registered) return null

        val queue = ArrayDeque(observed)
        return route.map { id -> if (id in positions) queue.removeFirst() else id }
    }

    private fun median(values: List<Int>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid].toDouble() else (sorted[mid - 1] + sorted[mid]) / 2.0
    }
}
