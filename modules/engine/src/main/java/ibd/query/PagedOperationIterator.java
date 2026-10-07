package ibd.query;

import java.util.Iterator;

public class PagedOperationIterator extends OperationIterator {

    Iterator<Tuple> delegateIterator;
    int tuplesToRead;
    int startingTuple;
    int tuplesRead;
    
    
    public PagedOperationIterator(Iterator<Tuple> delegateIterator,int startingTupleIndex, int tuplesToRead){
        this.delegateIterator = delegateIterator;
        this.startingTuple = startingTupleIndex;
        this.tuplesToRead = startingTupleIndex + tuplesToRead;
        tuplesRead = 0;
    }

    @Override
    protected Tuple findNextTuple(){
        if (tuplesRead>=tuplesToRead)
            return null;
        
        Tuple tuple = null;
        while (tuplesRead<startingTuple){
            tuple = delegateIterator.next();
            if (tuple==null) break;
            tuplesRead++;
        }
        tuple = delegateIterator.next();
        tuplesRead++;
        
        return tuple;
    }

}
