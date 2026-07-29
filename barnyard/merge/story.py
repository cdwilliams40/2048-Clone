"""Chapters, renovation tasks and the dialogue between them.

Coins earned on the merge board pay for renovation tasks; finishing a
chapter's tasks plays its closing scene and opens the next one. This is the
reason to keep merging once the novelty of the board wears off.
"""

from __future__ import annotations

from dataclasses import dataclass, field

PLAYER = -1  # portrait id for the player character


@dataclass(frozen=True)
class Line:
    speaker: str
    portrait: int
    text: str


@dataclass(frozen=True)
class Task:
    key: str
    title: str
    detail: str
    cost: int


@dataclass(frozen=True)
class Chapter:
    key: str
    title: str
    blurb: str
    intro: tuple[Line, ...]
    tasks: tuple[Task, ...]
    outro: tuple[Line, ...]


CHAPTERS: tuple[Chapter, ...] = (
    Chapter(
        key="homecoming",
        title="Back to Hollow Creek",
        blurb="Gran left you the farm. She did not leave you a tidy one.",
        intro=(
            Line("You", PLAYER,
                 "Gran's farm. Smaller than I remember. Muddier, too."),
            Line("Henrietta", 2,
                 "Well, well. The city one came back after all."),
            Line("Hamlet", 1,
                 "I TOLD everyone you'd come. Nobody listens to me."),
            Line("Buttercup", 0,
                 "Ignore him. Start with the yard, love. Then the gate."),
            Line("You", PLAYER,
                 "Right. Yard, gate, roof. How hard can it be."),
        ),
        tasks=(
            Task("yard", "Clear the yard",
                 "Twelve years of brambles and one very smug goat.", 120),
            Task("gate", "Fix the front gate",
                 "It swings open on its own. Allegedly.", 260),
            Task("roof", "Patch the barn roof",
                 "Buttercup has opinions about the drips.", 450),
        ),
        outro=(
            Line("Buttercup", 0, "Would you look at that. It's a farm again."),
            Line("Hamlet", 1,
                 "About the gate. It doesn't swing open on its own."),
            Line("Hamlet", 1, "Someone opens it. Every night. I've SEEN them."),
            Line("You", PLAYER, "...Seen who, exactly?"),
            Line("Hamlet", 1, "I've said too much. I've said exactly enough."),
        ),
    ),
    Chapter(
        key="coop",
        title="The Coop Committee",
        blurb="Henrietta calls a meeting. Attendance is not optional.",
        intro=(
            Line("Henrietta", 2,
                 "Emergency session of the Coop Committee. That's me."),
            Line("Henrietta", 2,
                 "The henhouse is a disgrace and the path is a swamp."),
            Line("Woolliam", 3, "Is this about the gate? It sounds like it's"
                                " about the gate."),
            Line("Henrietta", 2, "It is NOT about the gate, Woolliam."),
            Line("Buttercup", 0, "It's a bit about the gate."),
        ),
        tasks=(
            Task("henhouse", "Rebuild the henhouse",
                 "Nesting boxes to committee specification.", 700),
            Task("path", "Lay a new path",
                 "Henrietta refuses to campaign through mud.", 1000),
            Task("beds", "Plant the flower beds",
                 "Purely for morale. Mostly for Hamlet.", 1400),
        ),
        outro=(
            Line("Henrietta", 2, "Motion carried. The coop looks marvellous."),
            Line("Woolliam", 3, "So, um. About the duck by the creek."),
            Line("Woolliam", 3,
                 "He's been out there all week with a clipboard."),
            Line("You", PLAYER, "A clipboard."),
            Line("Woolliam", 3, "And a tiny hat. It's the hat that worries me."),
        ),
    ),
    Chapter(
        key="creek",
        title="Whispers by the Creek",
        blurb="There's a duck at the water's edge, measuring things.",
        intro=(
            Line("Drake", 4, "Afternoon! Lovely meadow. Really lovely."),
            Line("Drake", 4, "Purely professional interest, of course."),
            Line("Clementine", 5,
                 "He's been saying 'of course' for six days straight."),
            Line("Clementine", 5,
                 "Your gran fixed that footbridge every spring, you know."),
            Line("You", PLAYER, "Then I suppose it's my turn."),
        ),
        tasks=(
            Task("bridge", "Repair the footbridge",
                 "Gran's plank is still there. Just the one.", 2000),
            Task("mill", "Restore the mill wheel",
                 "It hasn't turned since before you were born.", 2900),
            Task("creekpath", "Clear the creek path",
                 "So the whole farm can see what's out there.", 3800),
        ),
        outro=(
            Line("Clementine", 5, "The wheel's turning. Listen to that."),
            Line("Drake", 4, "Yes. About that. I should probably mention -"),
            Line("Hamlet", 1, "HE WANTS TO BUY THE MEADOW."),
            Line("Drake", 4, "I was GETTING there."),
            Line("Buttercup", 0, "Farm meeting. Barn. Now."),
        ),
    ),
    Chapter(
        key="meadow",
        title="The Meadow Offer",
        blurb="Everyone has an opinion. Only one of you has a hammer.",
        intro=(
            Line("Drake", 4,
                 "It's a good offer. Genuinely. My firm doesn't do bad ones."),
            Line("Buttercup", 0, "And what happens to the meadow?"),
            Line("Drake", 4, "...Parking, mostly."),
            Line("Hamlet", 1, "PARKING. I said he was trouble. I said it."),
            Line("You", PLAYER,
                 "Then we make the meadow worth more to us than to him."),
            Line("Clementine", 5, "Now that's your gran talking."),
        ),
        tasks=(
            Task("fence", "Raise the new fence",
                 "Marking what's ours, politely but firmly.", 5000),
            Task("stall", "Build the market stall",
                 "If we're selling, we sell our own eggs.", 6800),
            Task("lanterns", "Light the harvest lanterns",
                 "One for every field. Visible from the road.", 8500),
        ),
        outro=(
            Line("Drake", 4, "Well. That's the prettiest 'no' I've ever had."),
            Line("Drake", 4, "Withdrawing the offer. Keeping the hat."),
            Line("Henrietta", 2,
                 "The committee votes to let him stay. Provisionally."),
            Line("Hamlet", 1, "I brokered this. Everyone saw me broker this."),
            Line("Buttercup", 0, "Nobody saw that, Hamlet."),
            Line("Clementine", 5,
                 "Harvest fair on Saturday, then. Like the old days."),
            Line("You", PLAYER, "Like the old days. Only muddier."),
        ),
    ),
)

CHAPTER_INDEX = {c.key: i for i, c in enumerate(CHAPTERS)}


@dataclass
class StoryProgress:
    chapter: int = 0
    done: set[str] = field(default_factory=set)
    seen_intro: set[str] = field(default_factory=set)
    seen_outro: set[str] = field(default_factory=set)

    @property
    def finished(self) -> bool:
        return self.chapter >= len(CHAPTERS)

    @property
    def current(self) -> Chapter | None:
        return None if self.finished else CHAPTERS[self.chapter]

    def tasks(self) -> tuple[Task, ...]:
        chapter = self.current
        return chapter.tasks if chapter else ()

    def is_done(self, task: Task) -> bool:
        return task.key in self.done

    def next_task(self) -> Task | None:
        for task in self.tasks():
            if not self.is_done(task):
                return task
        return None

    def complete(self, task: Task) -> bool:
        if self.is_done(task):
            return False
        self.done.add(task.key)
        return True

    @property
    def chapter_complete(self) -> bool:
        chapter = self.current
        return chapter is not None and all(self.is_done(t)
                                           for t in chapter.tasks)

    def advance(self) -> None:
        if not self.finished:
            self.chapter += 1

    @property
    def total_tasks(self) -> int:
        return sum(len(c.tasks) for c in CHAPTERS)

    def to_dict(self) -> dict:
        return {"chapter": self.chapter, "done": sorted(self.done),
                "seen_intro": sorted(self.seen_intro),
                "seen_outro": sorted(self.seen_outro)}

    @classmethod
    def from_dict(cls, data: dict) -> "StoryProgress":
        return cls(
            chapter=max(0, min(len(CHAPTERS), int(data.get("chapter", 0)))),
            done=set(data.get("done", [])),
            seen_intro=set(data.get("seen_intro", [])),
            seen_outro=set(data.get("seen_outro", [])),
        )
