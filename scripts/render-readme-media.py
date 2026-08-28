#!/usr/bin/env python3
"""Render README diagrams, not device recordings. Requires an existing Pillow install.

Run from any directory: python3 scripts/render-readme-media.py
Optional ROOMFLOW_DOC_FONT / ROOMFLOW_DOC_MONO select local TrueType fonts.
No Android dependency, network request, or test-source generation.
"""

import os
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs" / "assets"
SIZE = (1120, 480)
BG = "#101923"
PANEL = "#172532"
LINE = "#334657"
WHITE = "#edf4f7"
MUTED = "#a7bac9"
MINT = "#70e1bd"
BLUE = "#8abfff"
AMBER = "#ffd08a"


def font(size, mono=False):
    candidates = [os.environ.get("ROOMFLOW_DOC_MONO" if mono else "ROOMFLOW_DOC_FONT")]
    candidates += (["/System/Library/Fonts/Menlo.ttc", "DejaVuSansMono.ttf"] if mono else [
        "/System/Library/Fonts/Supplemental/Arial.ttf", "DejaVuSans.ttf"
    ])
    for candidate in filter(None, candidates):
        try:
            return ImageFont.truetype(candidate, size)
        except OSError:
            pass
    raise RuntimeError("Set ROOMFLOW_DOC_FONT and ROOMFLOW_DOC_MONO to available font files")


TITLE, LABEL, BODY, CODE = font(34), font(22), font(17), font(18, mono=True)


def label(draw, xy, value, face=BODY, color=WHITE, max_width=None):
    # Keep every label measurable, so regeneration cannot silently clip text.
    if max_width is not None:
        assert draw.textlength(value, font=face) <= max_width, value
    draw.text(xy, value, font=face, fill=color)


def frame(kind, stage, progress=1.0, static=False):
    canvas = Image.new("RGB", SIZE, BG)
    draw = ImageDraw.Draw(canvas)
    query = kind == "typed-query"
    label(draw, (40, 25), "room-flow  /  " + ("Typed query" if query else "Write lifecycle"), TITLE)
    label(draw, (40, 72), "CONCEPT DIAGRAM  /  NOT A DEVICE RECORDING OR BENCHMARK", color=MUTED)
    label(draw, (40, 112), "BUILD TIME" if query else "ONE ACCEPTED TASK", color=BLUE)
    label(draw, (572, 112), "RUNTIME" if query else "TRANSACTION OUTCOME", color=MINT)
    draw.line((558, 110, 558, 256), fill=LINE, width=1)

    if query:
        cards = [("01", "Room entity", "@RoomFlowEntity"), ("02", "KSP metadata", "UserTable.name"),
                 ("03", "Query + bindings", "QuerySpec / SQL"), ("04", "Room / SQLite", "List<User>")]
        descriptions = ["Annotate an existing entity. Room still owns schema and migrations.",
                        "KSP generates typed columns and row mapping, not every possible query.",
                        "Compose conditions at runtime. Values remain bound parameters.",
                        "list() executes the query, closes the Cursor, and returns mapped rows."]
        codes = [["@RoomFlowEntity", "data class User(...)"] ,
                 ["UserTable.name", "EntityColumn<User, String>"],
                 [".where(UserTable.age.greaterThanOrEqual(18))", ".page(1, 20)"],
                 ["db.select(definition).list()", "// Bind -> execute -> map -> close"]]
    else:
        cards = [("01", "Accepted", "Deferred: pending"), ("02", "Executing", "Room transaction"),
                 ("03", "Commit / rollback", "Database outcome"), ("04", "Result", "await / exception")]
        descriptions = ["An accepted task is not yet committed. Queue capacity is bounded.",
                        "Tasks run serially. Only busy/locked errors retry the transaction.",
                        "Success commits. A transaction error rolls back database changes.",
                        "await() succeeds after commit; transaction failures reach the caller."]
        codes = [["val result = queue.submit { dao.upsertList(users) }", "// Acceptance alone is not success"],
                 ["Room.withTransaction { ... }", "// Keep external side effects outside retries"],
                 ["success -> COMMIT", "transaction error -> ROLLBACK"],
                 ["commit -> complete(value)", "transaction failure -> exception"]]

    for index, (number, title, subtitle) in enumerate(cards):
        x = 40 + index * 266
        active = static or index <= stage
        color = (BLUE if query and index < 2 else MINT) if active else LINE
        draw.rounded_rectangle((x, 147, x + 242, 245), radius=12, fill=PANEL, outline=color, width=2)
        label(draw, (x + 14, 157), number, color=color)
        label(draw, (x + 14, 181), title, LABEL, max_width=218)
        label(draw, (x + 14, 215), subtitle, color=MUTED, max_width=218)
        if index < 3:
            draw.line((x + 244, 196, x + 262, 196), fill=LINE, width=2)
            draw.polygon([(x + 263, 196), (x + 257, 192), (x + 257, 200)], fill=MUTED)
            if not static and index == stage and stage < 3:
                dot = x + 245 + progress * 12
                draw.ellipse((dot - 3, 193, dot + 3, 199), fill=color)

    draw.rounded_rectangle((40, 270, 1080, 375), radius=12, fill=PANEL)
    for row, line in enumerate(codes[stage]):
        label(draw, (60, 288 + row * 32), line, CODE, AMBER if "ROLLBACK" in line else MINT, max_width=1000)
    label(draw, (40, 397), descriptions[stage], color=WHITE, max_width=1040)
    footer = ("Schema / transactions belong to Room. No automatic data deletion." if query else
              "Cancellation cannot undo a commit. This is an in-memory queue.")
    label(draw, (40, 438), footer, color=MUTED)
    if static:
        label(draw, (920, 438), "STATIC VIEW", color=MUTED)
    else:
        for index in range(4):
            x = 944 + index * 34
            draw.rounded_rectangle((x, 442, x + 24, 447), radius=2, fill=MINT if index <= stage else LINE)
    return canvas


def render(kind):
    still = frame(kind, 3, static=True)
    still.save(OUTPUT / f"{kind}.png", optimize=True)
    # ponytail: fixed four-stage diagrams, not a general animation engine.
    frames = [frame(kind, stage, tick / 15) for stage in range(4) for tick in range(16)]
    palette = still.quantize(colors=128)
    frames = [item.quantize(palette=palette, dither=Image.Dither.NONE) for item in frames]
    durations = [100] * len(frames)
    durations[-1] = 1600
    path = OUTPUT / f"{kind}.gif"
    # One repeat means two plays; no infinite loop or flashing transition.
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=durations,
                   loop=1, optimize=False, disposal=1)
    with Image.open(path) as result:
        assert result.size == SIZE and result.n_frames > 4
        assert result.info["loop"] == 1
        total_ms = 0
        for index in range(result.n_frames):
            result.seek(index)
            expected = frames[min(total_ms // 100, len(frames) - 1)].convert("RGB")
            assert ImageChops.difference(result.convert("RGB"), expected).getbbox() is None
            total_ms += result.info["duration"]
        assert total_ms == sum(durations)
    assert path.stat().st_size < 1_500_000, "Keep README media lightweight"
    print(f"{kind}: GIF + static PNG checked; {path.stat().st_size:,} bytes, {total_ms} ms/play")


if __name__ == "__main__":
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for diagram in ("typed-query", "write-queue"):
        render(diagram)
