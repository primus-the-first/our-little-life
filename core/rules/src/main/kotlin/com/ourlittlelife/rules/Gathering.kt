package com.ourlittlelife.rules

import com.ourlittlelife.rules.Need.ENERGY

/** Gathering wood, stone and fiber. Each spot gives a few times a day and grows back overnight. */
object Gathering {
    val chop = Action("chop_wood", "Chop wood", 60, mapOf(ENERGY to -6), mapOf(Resource.WOOD to 6))
    val breakRock = Action("break_rock", "Break the rock", 60, mapOf(ENERGY to -8), mapOf(Resource.STONE to 4))
    val pickFiber = Action("pick_fiber", "Pick fiber", 30, mapOf(ENERGY to -2), mapOf(Resource.FIBER to 5))

    /** How many times a day each kind of spot can be gathered from. */
    fun usesPerDay(kind: ObjectKind): Int = when (kind) {
        ObjectKind.TREE -> 4
        ObjectKind.ROCK -> 3
        ObjectKind.BUSH -> 2
        else -> Int.MAX_VALUE
    }
}

sealed interface UseResult {
    data class Done(val character: Character, val camp: Camp) : UseResult

    /** This spot has given all it can today. */
    data object UsedUp : UseResult

    data class Refused(val reason: ActionResult) : UseResult
}

/** The shared world on one plot: the land, the storage chest, and what has been gathered today. */
data class Camp(
    val plot: Plot,
    val storage: Storage = Storage(),
    private val usesToday: Map<String, Int> = emptyMap(),
) {

    fun usesLeft(obj: PlotObject): Int {
        val perDay = Gathering.usesPerDay(obj.kind)
        return if (perDay == Int.MAX_VALUE) perDay else perDay - (usesToday[obj.id] ?: 0)
    }

    /** A character uses an object on the plot. Nothing changes unless the action succeeds. */
    fun use(character: Character, obj: PlotObject, action: Action): UseResult {
        require(action in ObjectActions.forKind(obj.kind)) { "${obj.kind.label} doesn't offer ${action.name}" }
        val gathers = action.yields.isNotEmpty()
        if (gathers && usesLeft(obj) <= 0) return UseResult.UsedUp

        return when (val result = Rules.perform(character, action)) {
            is ActionResult.Done -> UseResult.Done(
                character = result.character,
                camp = if (gathers) {
                    copy(storage = storage.plus(action.yields), usesToday = usesToday + (obj.id to (usesToday[obj.id] ?: 0) + 1))
                } else {
                    this
                },
            )
            else -> UseResult.Refused(result)
        }
    }

    /** Overnight, every tree, rock and bush grows back. The chest keeps what's in it. */
    fun nextDay(): Camp = copy(usesToday = emptyMap())
}
