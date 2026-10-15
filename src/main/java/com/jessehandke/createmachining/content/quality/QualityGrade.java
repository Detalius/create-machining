package com.jessehandke.createmachining.content.quality;

import java.util.Locale;

/**
 * The quality grade an inspected part gets, decided from its gage reading (0-100%).
 *
 * An enum is a fixed list of named values, like a C enum, except each value is a full object that
 * can carry data (here: its minimum gage) and methods.
 *
 * Dispositions (what each grade is good for):
 *  - PERFECT: anything, including precision recipes
 *  - IN_TOLERANCE: any standard recipe
 *  - OUT_OF_TOLERANCE: "use as-is": standard recipes accept it, with a chance the assembly scraps
 *  - NEEDS_REWORK: back to the lathe for a skim cut
 *  - SCRAP: crush it for the metal
 */
public enum QualityGrade {
    // Order matters: highest first, so fromGage() can stop at the first match.
    PERFECT(95),
    IN_TOLERANCE(85),
    OUT_OF_TOLERANCE(75),
    NEEDS_REWORK(60),
    SCRAP(0);

    /** Lowest gage reading (inclusive) that earns this grade. */
    public final int minGage;

    QualityGrade(int minGage) {
        this.minGage = minGage;
    }

    public static QualityGrade fromGage(int gage) {
        for (QualityGrade grade : values())
            if (gage >= grade.minGage)
                return grade;
        return SCRAP;
    }

    /** "perfect", "in_tolerance", ... : the name used in JSON recipes and save data. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
