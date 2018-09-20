package ru.curs.celesta.dbutils;

import ru.curs.celesta.score.AbstractScore;
import ru.curs.celesta.score.Grain;
import ru.curs.celesta.score.GrainElement;
import ru.curs.celesta.score.GrainElementReference;

import java.util.Comparator;

public class GrainElementUpdatingComparator implements Comparator<GrainElement> {


    private final AbstractScore score;

    public GrainElementUpdatingComparator(AbstractScore score) {
        this.score = score;
    }

    @Override
    public int compare(GrainElement o1, GrainElement o2) {

        if (firstDependsOnSecond(o1, o2)) {
            return 1;
        } else if (firstDependsOnSecond(o2, o1)) {
            return -1;
        }

        return 0;
    }


    private boolean firstDependsOnSecond(GrainElement first, GrainElement second) {

        for (GrainElementReference reference: first.getReferences()) {
            Grain grain = score.getGrain(reference.getGrainName());
            GrainElement ge = grain.getElement(reference.getName(), reference.getGrainElementClass());

            if (ge == second || this.firstDependsOnSecond(ge, second)) {
                return true;
            }
        }

        return false;
    }
}
