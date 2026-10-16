package com.jessehandke.createmachining.content.cnc;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/**
 * The 12 block positions that make up a CNC Lathe, with their place in the machine.
 *
 * Machine-local coordinates, as seen standing at the front (door side) of the machine:
 *   length (l): 0 = headstock (your left) .. 2 = tailstock (your right)
 *   height (h): 0 = base .. 1 = top
 *   depth  (d): 0 = front row (door side) .. 1 = back row
 *
 *            l=0 headstock   l=1 bed       l=2 tailstock
 *   h=1 d=0  CHUCK          WORK_ZONE     TAILSTOCK        <- door slides along this row
 *   h=1 d=1  HEAD_TOP       TURRET        TAIL_TOP
 *   h=0 d=0  HEAD_FRONT     BED_FRONT     PARTS_CATCHER
 *   h=0 d=1  CONTROLLER     CHIP_TRAY     CHIP_OUTLET
 *
 * StringRepresentable is a Minecraft interface that gives each value a lowercase name, which
 * blockstate JSON files use (e.g. "part=chuck").
 */
public enum CncLathePart implements StringRepresentable {
    CONTROLLER(0, 0, 1),    // the drive: the one "real" block, takes the power shaft from behind
    HEAD_FRONT(0, 0, 0),
    CHUCK(0, 1, 0),         // load stock / pull parts; bar-feeder port on its headstock end face
    HEAD_TOP(0, 1, 1),
    BED_FRONT(1, 0, 0),
    CHIP_TRAY(1, 0, 1),     // right-click a Mechanical Belt here to install the chip conveyor
    WORK_ZONE(1, 1, 0),     // the door: same interactions as the chuck
    TURRET(1, 1, 1),        // load / remove carbide inserts
    PARTS_CATCHER(2, 0, 0), // finished parts come out here
    CHIP_OUTLET(2, 0, 1),   // chips leave here (once the conveyor is installed)
    TAILSTOCK(2, 1, 0),
    TAIL_TOP(2, 1, 1);

    public final int l, h, d;

    CncLathePart(int l, int h, int d) {
        this.l = l;
        this.h = h;
        this.d = d;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
