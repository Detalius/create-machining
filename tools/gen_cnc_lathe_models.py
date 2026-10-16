#!/usr/bin/env python3
"""
Generates every model for the CNC Lathe from ONE description of the machine.

Run from the repo root:   python3 tools/gen_cnc_lathe_models.py

The machine is described in "machine space", in pixels (16 per block), as seen from the front:
    L: along the bed, 0 (headstock end) .. 48 (tailstock end)
    H: up, 0 .. 32
    D: front to back, 0 (door side) .. 32
The script slices the static shell into the 12 block models (one per machine part), writes the
moving parts as separate "partial" models for the renderer, the blockstates, and a 1/3 scale
miniature of the whole machine for the inventory icon.

Models are written facing NORTH; blockstates rotate them for the other directions.
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "create_machining")

TEX = {
    "body": "create:block/industrial_iron_block",
    "base": "create:block/railway_casing",
    "head": "create:block/brass_casing",
    "glass": "minecraft:block/glass",
    "steel": "minecraft:block/iron_block",
    "brass": "create:block/brass_block",
    "screen": "minecraft:block/black_concrete",
    "lcd": "minecraft:block/lime_concrete",
    "copper": "minecraft:block/copper_block",
    "belt": "create:block/belt",
    "gold": "minecraft:block/gold_block",
    "dark": "minecraft:block/gray_concrete",
}

# (name, texture, (L0, L1), (H0, H1), (D0, D1))
STATIC = [
    # base cabinet and the chip trough (the gap between the front and back ledges)
    ("base",            "base",   (0, 48),  (0, 10),   (0, 32)),
    ("front_ledge",     "base",   (14, 48), (10, 12),  (0, 14)),
    ("back_ledge",      "base",   (14, 48), (10, 12),  (29, 32)),
    # headstock and its control panel
    ("headstock",       "head",   (0, 14),  (10, 32),  (0, 32)),
    ("panel",           "body",   (2, 12),  (15, 29),  (-1, 0)),
    ("screen",          "screen", (3, 11),  (17, 27),  (-1.3, -1)),
    ("screen_text_1",   "lcd",    (4, 9),   (24, 25),  (-1.4, -1.3)),
    ("screen_text_2",   "lcd",    (4, 10),  (21, 22),  (-1.4, -1.3)),
    ("screen_text_3",   "lcd",    (4, 7),   (18, 19),  (-1.4, -1.3)),
    ("bar_feeder_port", "steel",  (-1, 0),  (19, 29),  (3, 13)),
    # enclosure
    ("back_wall",       "body",   (14, 48), (12, 32),  (30, 32)),
    ("end_wall",        "body",   (46, 48), (12, 32),  (0, 30)),
    ("rail_top_back",   "body",   (14, 46), (30, 32),  (28, 30)),
    ("rail_top",        "body",   (14, 46), (30, 32),  (0, 2)),
    ("rail_bottom",     "body",   (14, 46), (12, 14),  (0, 2)),
    ("window_fixed",    "glass",  (32, 46), (14, 30),  (0.5, 1.5)),
    # tailstock and quill
    ("tailstock",       "body",   (39, 46), (12, 27),  (3, 13)),
    ("tailstock_cap",   "steel",   (39, 46), (27, 28),  (3, 13)),
    ("quill",           "steel",  (34, 39), (22, 26),  (6, 10)),
    # coolant manifold along the back wall
    ("coolant_pipe",    "copper", (16, 46), (27.5, 29), (27.5, 29)),
    # ports
    ("parts_chute",     "base",   (36, 46), (3, 10),   (-3, 0)),
    ("chip_chute",      "base",   (48, 51), (5, 10),   (16, 28)),
]

# Moving parts, authored around a pivot at model point (8, 8, 8) unless noted.
# Coordinates here are plain model space (x, y, z), already facing north.
PARTIALS = {
    # spins about x (the spindle axis); jaws face the tailstock (-x)
    "chuck": [
        ("body",  "steel", (6, 10), (1, 15), (1, 15)),
        ("jaw_1", "dark",  (4, 6), (11, 14), (7, 9)),
        ("jaw_2", "dark",  (4, 6), (2, 5),   (7, 9)),
        ("jaw_3", "dark",  (4, 6), (7, 9),   (11, 14)),
        ("jaw_4", "dark",  (4, 6), (7, 9),   (2, 5)),
    ],
    "workpiece_iron":  [("bar", "steel", (0, 16), (5, 11), (5, 11))],
    "workpiece_brass": [("bar", "brass", (0, 16), (5, 11), (5, 11))],
    # pivot is the TOOL TIP at (8, 8, 2); the tool points toward the spindle (-z)
    "turret": [
        ("disc",   "copper", (2, 14), (2, 14), (4, 10)),
        ("hub",    "head",  (5, 11), (5, 11), (10, 12)),
        ("holder", "steel", (6.5, 9.5), (6.5, 9.5), (2.5, 4)),
        ("insert", "gold",  (7, 9), (7.5, 8.5), (2, 2.5)),
    ],
    # pivot (8, 8, 0.5): middle of the door
    "door": [
        ("frame_top",    "body",  (-1, 17), (15, 16), (0, 1)),
        ("frame_bottom", "body",  (-1, 17), (0, 1),   (0, 1)),
        ("frame_left",   "body",  (16, 17), (1, 15),  (0, 1)),
        ("frame_right",  "body",  (-1, 0),  (1, 15),  (0, 1)),
        ("glass",        "glass", (0, 16),  (1, 15),  (0.3, 0.7)),
        ("handle",       "steel", (1, 2),   (5, 11),  (-0.8, 0)),
    ],
    # pivot (8, 0, 7.5): the belt lies in the chip trough
    "conveyor": [
        ("belt", "belt", (-8, 24), (0, 1), (0, 15)),
    ],
}


def face_dirs():
    return ["north", "south", "east", "west", "up", "down"]


def element(name, tex, x, y, z, skip=()):
    faces = {f: {"texture": "#" + tex} for f in face_dirs() if f not in skip}
    return {"name": name, "from": [x[0], y[0], z[0]], "to": [x[1], y[1], z[1]], "faces": faces}


def model(elements, parent="block/block"):
    used = sorted({f["texture"][1:] for e in elements for f in e["faces"].values()})
    textures = {t: TEX[t] for t in used}
    textures["particle"] = TEX["body"]
    return {"parent": parent, "render_type": "minecraft:cutout", "textures": textures, "elements": elements}


# Cells: (l, h, d) -> part name, matching CncLathePart.java
CELLS = {
    (0, 0, 1): "controller", (0, 0, 0): "head_front", (0, 1, 0): "chuck", (0, 1, 1): "head_top",
    (1, 0, 0): "bed_front", (1, 0, 1): "chip_tray", (1, 1, 0): "work_zone", (1, 1, 1): "turret",
    (2, 0, 0): "parts_catcher", (2, 0, 1): "chip_outlet", (2, 1, 0): "tailstock", (2, 1, 1): "tail_top",
}


def clip(lo, hi, cell_lo, cell_hi):
    a, b = max(lo, cell_lo), min(hi, cell_hi)
    return (a, b) if b - a > 1e-6 else None


def cell_model(l, h, d):
    """The static elements that fall inside one cell, in that cell's own model coordinates."""
    # Cell bounds in machine space, stretched outward on the machine's outside faces so things
    # that stick out (panel, chutes, ports) stay with the cell they're attached to.
    Lb = (16 * l - (16 if l == 0 else 0), 16 * l + 16 + (16 if l == 2 else 0))
    Hb = (16 * h - (16 if h == 0 else 0), 16 * h + 16 + (16 if h == 1 else 0))
    Db = (16 * d - (16 if d == 0 else 0), 16 * d + 16 + (16 if d == 1 else 0))
    out = []
    for name, tex, L, H, D in STATIC:
        cl, ch, cd = clip(*L, *Lb), clip(*H, *Hb), clip(*D, *Db)
        if not (cl and ch and cd):
            continue
        # Don't draw faces where the element was cut by a neighbouring cell (hidden anyway)
        skip = set()
        if cl[0] > L[0]: skip.add("east")   # L grows toward -x, so a cut at low L is the +x face
        if cl[1] < L[1]: skip.add("west")
        if ch[0] > H[0]: skip.add("down")
        if ch[1] < H[1]: skip.add("up")
        if cd[0] > D[0]: skip.add("north")
        if cd[1] < D[1]: skip.add("south")
        # machine space -> this cell's model space (facing north: x = 16 - L + 16l)
        x = (16 - cl[1] + 16 * l, 16 - cl[0] + 16 * l)
        y = (ch[0] - 16 * h, ch[1] - 16 * h)
        z = (cd[0] - 16 * d, cd[1] - 16 * d)
        out.append(element(name, tex, x, y, z, skip))
    return model(out)


def write(path, data):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def y_rot(facing):
    return {"north": 0, "east": 90, "south": 180, "west": 270}[facing]


def main():
    # 1. one static model per cell
    for (l, h, d), name in CELLS.items():
        write(f"models/block/cnc_lathe/{name}.json", cell_model(l, h, d))

    # 2. moving parts
    for name, elems in PARTIALS.items():
        write(f"models/block/cnc_lathe/moving/{name}.json", model([element(*e) for e in elems]))

    # 3. blockstates: the controller (facing only) and the structural blocks (facing x part)
    facings = ["north", "east", "south", "west"]
    variants = {f"facing={f}": {"model": "create_machining:block/cnc_lathe/controller", "y": y_rot(f)}
                for f in facings}
    write("blockstates/cnc_lathe.json", {"variants": variants})
    variants = {}
    for f in facings:
        for name in CELLS.values():
            if name == "controller":
                continue
            variants[f"facing={f},part={name}"] = {"model": f"create_machining:block/cnc_lathe/{name}", "y": y_rot(f)}
    write("blockstates/cnc_lathe_structural.json", {"variants": variants})

    # 4. inventory icon: the whole machine at 1/3 scale, front facing south, with the moving
    #    parts at their resting positions
    s = 1 / 3
    mini = []
    for name, tex, L, H, D in STATIC:
        mini.append(element(name, tex, (L[0] * s, L[1] * s), (H[0] * s, H[1] * s), ((32 - D[1]) * s, (32 - D[0]) * s)))
    rest = [  # (partial, pivot in machine space L, H, D, pivot in model space)
        ("chuck", (16, 24, 8), (8, 8, 8)),
        ("turret", (32, 24, 21), (8, 8, 2)),
        ("door", (23, 22, -0.1), (8, 8, 0.5)),
    ]
    for pname, (pl, ph, pd), (px, py, pz) in rest:
        for name, tex, x, y, z in PARTIALS[pname]:
            # model x maps to machine L reversed; model z maps to machine D
            L = (pl - (x[1] - px), pl - (x[0] - px))
            H = (ph + y[0] - py, ph + y[1] - py)
            D = (pd + z[0] - pz, pd + z[1] - pz)
            mini.append(element(name, tex, (L[0] * s, L[1] * s), (H[0] * s, H[1] * s),
                                ((32 - D[1]) * s, (32 - D[0]) * s)))
    for e in mini:  # center it in the item's 16x16x16 box
        e["from"] = [e["from"][0], e["from"][1] + 2, e["from"][2] + 2.5]
        e["to"] = [e["to"][0], e["to"][1] + 2, e["to"][2] + 2.5]
    item = model(mini)
    write("models/item/cnc_lathe.json", item)
    print(f"wrote {len(CELLS)} cell models, {len(PARTIALS)} partials, 2 blockstates, 1 item model")


if __name__ == "__main__":
    main()
