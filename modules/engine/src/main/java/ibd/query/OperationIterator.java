package ibd.query;

import java.util.Iterator;

public abstract class OperationIterator implements Iterator<Tuple> {

    Tuple nextTuple = null;

    @Override
    public Tuple next() {

        ibd.query.QueryStats.NEXT_CALLS++;
        if (nextTuple != null) {
            Tuple next_ = nextTuple;
            nextTuple = null;
            return next_;
        }

        return findNextTuple();

    }

    @Override
    public boolean hasNext() {

        if (nextTuple != null) {
            return true;
        }
        nextTuple = findNextTuple();

        return (nextTuple != null);
    }

    protected abstract Tuple findNextTuple();

}
