package ibd.query.binaryop.join;

import ibd.query.Operation;
import ibd.query.ReferedDataSource;
import ibd.query.binaryop.BinaryOperation;
import java.util.List;

public abstract class Join extends BinaryOperation {

    protected JoinPredicate joinPredicate;

    public Join(Operation leftOperation, Operation rightOperation, JoinPredicate joinPredicate) throws Exception {
        super(leftOperation, rightOperation);
        this.joinPredicate = cloneTerms(joinPredicate);
    }
    
    protected JoinPredicate cloneTerms(JoinPredicate joinPredicate){
        JoinPredicate newjoinPredicate = new JoinPredicate();
        for (JoinTerm term : joinPredicate.getTerms()) {
            newjoinPredicate.addTerm(term.getLeftTableAlias(), term.getLeftColumn(), 
                                           term.getRightTableAlias(), term.getRightColumn());
        }
        return newjoinPredicate;
    }

    public List<JoinTerm> getTerms() {
        return (List<JoinTerm>) joinPredicate.getTerms();
    }
    
    
    @Override
    public void prepare() throws Exception{
        super.prepare();
    }
    
    protected void prepareIndexes() throws Exception{
         setJoinTermsIndexes();
    }
    
    protected void setJoinTermsIndexes() throws Exception {
        for (JoinTerm term : joinPredicate.getTerms()) {
            leftOperation.setColumnLocation(term.getLeftColumnDescriptor());
            rightOperation.setColumnLocation(term.getRightColumnDescriptor());
        }
    }
    
   
    
    public abstract String getJoinAlgorithm();
    
    @Override
    public String toString(){
        return getJoinAlgorithm()+"("+joinPredicate+")";
    }
    
    public static JoinPredicate createJoinPredicate(List<String> arguments){
        JoinPredicate joinPredicate = new JoinPredicate();
       
        for (String term : arguments) {
            if (term.isBlank() || term.isEmpty()) continue;
            int index = term.indexOf("=", 0);
            String col1 = term.substring(0, index);
            String col2 = term.substring(index+1, term.length());
            try {
                joinPredicate.addTerm(col1, col2);
            } catch (Exception ex) {
            }
        }
        return joinPredicate;
    }

}
