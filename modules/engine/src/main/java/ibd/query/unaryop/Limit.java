package ibd.query.unaryop;

import ibd.query.Operation;
import ibd.query.Tuple;
import java.util.Iterator;
import java.util.List;

public class Limit extends UnaryOperation {

    
    public Limit(Operation op, int tuplesToRead, int startingTuple) throws Exception {
        super(op);
        this.tuplesToRead = tuplesToRead;
        this.startingTuple = startingTuple;
    }


    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        Iterator<Tuple> tuples =  childOperation.lookUp(processedTuples, withFilterDelegation);
        return tuples;
    }

}
