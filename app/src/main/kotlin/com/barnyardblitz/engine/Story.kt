package com.barnyardblitz.engine

/**
 * Chapters, renovation tasks and the dialogue between them.
 *
 * Coins earned on the merge board pay for renovation tasks; finishing a
 * chapter's tasks plays its closing scene and opens the next one.
 */
const val PLAYER_PORTRAIT = -1

data class Line(val speaker: String, val portrait: Int, val text: String)

data class Task(val key: String, val title: String, val detail: String, val cost: Int)

data class Chapter(
    val key: String,
    val title: String,
    val blurb: String,
    val intro: List<Line>,
    val tasks: List<Task>,
    val outro: List<Line>,
)

val CHAPTERS: List<Chapter> = listOf(
    Chapter(
        key = "homecoming",
        title = "Back to Hollow Creek",
        blurb = "Gran left you the farm. She did not leave you a tidy one.",
        intro = listOf(
            Line("You", PLAYER_PORTRAIT, "Gran's farm. Smaller than I remember. Muddier, too."),
            Line("Henrietta", 2, "Well, well. The city one came back after all."),
            Line("Hamlet", 1, "I TOLD everyone you'd come. Nobody listens to me."),
            Line("Buttercup", 0, "Ignore him. Start with the yard, love. Then the gate."),
            Line("You", PLAYER_PORTRAIT, "Right. Yard, gate, roof. How hard can it be."),
        ),
        tasks = listOf(
            Task("yard", "Clear the yard", "Twelve years of brambles and one very smug goat.", 120),
            Task("gate", "Fix the front gate", "It swings open on its own. Allegedly.", 260),
            Task("roof", "Patch the barn roof", "Buttercup has opinions about the drips.", 450),
        ),
        outro = listOf(
            Line("Buttercup", 0, "Would you look at that. It's a farm again."),
            Line("Hamlet", 1, "About the gate. It doesn't swing open on its own."),
            Line("Hamlet", 1, "Someone opens it. Every night. I've SEEN them."),
            Line("You", PLAYER_PORTRAIT, "...Seen who, exactly?"),
            Line("Hamlet", 1, "I've said too much. I've said exactly enough."),
        ),
    ),
    Chapter(
        key = "coop",
        title = "The Coop Committee",
        blurb = "Henrietta calls a meeting. Attendance is not optional.",
        intro = listOf(
            Line("Henrietta", 2, "Emergency session of the Coop Committee. That's me."),
            Line("Henrietta", 2, "The henhouse is a disgrace and the path is a swamp."),
            Line("Woolliam", 3, "Is this about the gate? It sounds like it's about the gate."),
            Line("Henrietta", 2, "It is NOT about the gate, Woolliam."),
            Line("Buttercup", 0, "It's a bit about the gate."),
        ),
        tasks = listOf(
            Task("henhouse", "Rebuild the henhouse", "Nesting boxes to committee specification.", 700),
            Task("path", "Lay a new path", "Henrietta refuses to campaign through mud.", 1000),
            Task("beds", "Plant the flower beds", "Purely for morale. Mostly for Hamlet.", 1400),
        ),
        outro = listOf(
            Line("Henrietta", 2, "Motion carried. The coop looks marvellous."),
            Line("Woolliam", 3, "So, um. About the duck by the creek."),
            Line("Woolliam", 3, "He's been out there all week with a clipboard."),
            Line("You", PLAYER_PORTRAIT, "A clipboard."),
            Line("Woolliam", 3, "And a tiny hat. It's the hat that worries me."),
        ),
    ),
    Chapter(
        key = "creek",
        title = "Whispers by the Creek",
        blurb = "There's a duck at the water's edge, measuring things.",
        intro = listOf(
            Line("Drake", 4, "Afternoon! Lovely meadow. Really lovely."),
            Line("Drake", 4, "Purely professional interest, of course."),
            Line("Clementine", 5, "He's been saying 'of course' for six days straight."),
            Line("Clementine", 5, "Your gran fixed that footbridge every spring, you know."),
            Line("You", PLAYER_PORTRAIT, "Then I suppose it's my turn."),
        ),
        tasks = listOf(
            Task("bridge", "Repair the footbridge", "Gran's plank is still there. Just the one.", 2000),
            Task("mill", "Restore the mill wheel", "It hasn't turned since before you were born.", 2900),
            Task("creekpath", "Clear the creek path", "So the whole farm can see what's out there.", 3800),
        ),
        outro = listOf(
            Line("Clementine", 5, "The wheel's turning. Listen to that."),
            Line("Drake", 4, "Yes. About that. I should probably mention -"),
            Line("Hamlet", 1, "HE WANTS TO BUY THE MEADOW."),
            Line("Drake", 4, "I was GETTING there."),
            Line("Buttercup", 0, "Farm meeting. Barn. Now."),
        ),
    ),
    Chapter(
        key = "meadow",
        title = "The Meadow Offer",
        blurb = "Everyone has an opinion. Only one of you has a hammer.",
        intro = listOf(
            Line("Drake", 4, "It's a good offer. Genuinely. My firm doesn't do bad ones."),
            Line("Buttercup", 0, "And what happens to the meadow?"),
            Line("Drake", 4, "...Parking, mostly."),
            Line("Hamlet", 1, "PARKING. I said he was trouble. I said it."),
            Line("You", PLAYER_PORTRAIT, "Then we make the meadow worth more to us than to him."),
            Line("Clementine", 5, "Now that's your gran talking."),
        ),
        tasks = listOf(
            Task("fence", "Raise the new fence", "Marking what's ours, politely but firmly.", 5000),
            Task("stall", "Build the market stall", "If we're selling, we sell our own eggs.", 6800),
            Task("lanterns", "Light the harvest lanterns", "One for every field. Visible from the road.", 8500),
        ),
        outro = listOf(
            Line("Drake", 4, "Well. That's the prettiest 'no' I've ever had."),
            Line("Drake", 4, "Withdrawing the offer. Keeping the hat."),
            Line("Henrietta", 2, "The committee votes to let him stay. Provisionally."),
            Line("Hamlet", 1, "I brokered this. Everyone saw me broker this."),
            Line("Buttercup", 0, "Nobody saw that, Hamlet."),
            Line("Clementine", 5, "Harvest fair on Saturday, then. Like the old days."),
            Line("You", PLAYER_PORTRAIT, "Like the old days. Only muddier."),
        ),
    ),
)

class StoryProgress(
    chapter: Int = 0,
    done: Set<String> = emptySet(),
    seenIntro: Set<String> = emptySet(),
    seenOutro: Set<String> = emptySet(),
) {
    var chapter: Int = chapter
        private set
    val done: MutableSet<String> = done.toMutableSet()
    val seenIntro: MutableSet<String> = seenIntro.toMutableSet()
    val seenOutro: MutableSet<String> = seenOutro.toMutableSet()

    val finished: Boolean get() = chapter >= CHAPTERS.size

    val current: Chapter? get() = CHAPTERS.getOrNull(chapter)

    fun tasks(): List<Task> = current?.tasks ?: emptyList()

    fun isDone(task: Task): Boolean = task.key in done

    fun nextTask(): Task? = tasks().firstOrNull { !isDone(it) }

    fun complete(task: Task): Boolean = done.add(task.key)

    val chapterComplete: Boolean
        get() = current?.tasks?.all { isDone(it) } ?: false

    fun advance() {
        if (!finished) chapter++
    }

    val totalTasks: Int get() = CHAPTERS.sumOf { it.tasks.size }

    fun toJson(): Map<String, Any?> = mapOf(
        "chapter" to chapter,
        "done" to done.sorted(),
        "seen_intro" to seenIntro.sorted(),
        "seen_outro" to seenOutro.sorted(),
    )

    companion object {
        fun fromJson(data: Map<String, Any?>): StoryProgress = StoryProgress(
            chapter = data.int("chapter").coerceIn(0, CHAPTERS.size),
            done = data.strings("done").toSet(),
            seenIntro = data.strings("seen_intro").toSet(),
            seenOutro = data.strings("seen_outro").toSet(),
        )
    }
}
