package ibd.query.binaryop.join.outer;

import ibd.query.Operation;
import ibd.query.binaryop.join.JoinPredicate;
import ibd.query.binaryop.join.JoinTerm;

public class NestedLoopRightJoin extends NestedLoopLeftJoin {

    public NestedLoopRightJoin(Operation leftOperation, Operation rightOperation, JoinPredicate terms) throws Exception {
        super(rightOperation, leftOperation, terms);
    }

    @Override
    protected JoinPredicate cloneTerms(JoinPredicate terms) {
        JoinPredicate newTerms = new JoinPredicate();
        for (JoinTerm term : terms.getTerms()) {
            newTerms.addTerm(term.getRightTableAlias(), term.getRightColumn(),
                    term.getLeftTableAlias(), term.getLeftColumn());
        }
        return newTerms;
    }

    @Override
    public String getJoinAlgorithm() {
        return "Nested Loop Right Join";
    }

}
