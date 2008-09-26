package org.apache.pig.backend.local.executionengine.physicalLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.LogToPhyTranslationVisitor;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.PhysicalOperator;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.plans.PhysicalPlan;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators.POLocalRearrange;
import org.apache.pig.backend.local.executionengine.physicalLayer.relationalOperators.POCogroup;
import org.apache.pig.backend.local.executionengine.physicalLayer.relationalOperators.POSplit;
import org.apache.pig.backend.local.executionengine.physicalLayer.relationalOperators.POSplitOutput;
import org.apache.pig.data.DataType;
import org.apache.pig.impl.logicalLayer.LOCogroup;
import org.apache.pig.impl.logicalLayer.LOSplit;
import org.apache.pig.impl.logicalLayer.LOSplitOutput;
import org.apache.pig.impl.logicalLayer.LogicalOperator;
import org.apache.pig.impl.logicalLayer.LogicalPlan;
import org.apache.pig.impl.plan.OperatorKey;
import org.apache.pig.impl.plan.PlanException;
import org.apache.pig.impl.plan.PlanWalker;
import org.apache.pig.impl.plan.VisitorException;


public class LocalLogToPhyTranslationVisitor extends LogToPhyTranslationVisitor {

    private Log log = LogFactory.getLog(getClass());
    
    public LocalLogToPhyTranslationVisitor(LogicalPlan plan) {
	super(plan);
	// TODO Auto-generated constructor stub
    }
    
    public Map<LogicalOperator, PhysicalOperator> getLogToPhyMap() {
	return LogToPhyMap;
    }
    
    @Override
    public void visit(LOCogroup cg) throws VisitorException {
	String scope = cg.getOperatorKey().scope;
        List<LogicalOperator> inputs = cg.getInputs();
        
        POCogroup poc = new POCogroup(new OperatorKey(scope, nodeGen.getNextNodeId(scope)), cg.getRequestedParallelism());
        
        currentPlan.add(poc);
        
        int count = 0;
        Byte type = null;
        for(LogicalOperator lo : inputs) {
            List<LogicalPlan> plans = (List<LogicalPlan>) cg.getGroupByPlans().get(lo);
            
            POLocalRearrange physOp = new POLocalRearrange(new OperatorKey(
                    scope, nodeGen.getNextNodeId(scope)), cg
                    .getRequestedParallelism());
            List<PhysicalPlan> exprPlans = new ArrayList<PhysicalPlan>();
            currentPlans.push(currentPlan);
            for (LogicalPlan lp : plans) {
                currentPlan = new PhysicalPlan();
                PlanWalker<LogicalOperator, LogicalPlan> childWalker = mCurrentWalker
                        .spawnChildWalker(lp);
                pushWalker(childWalker);
                mCurrentWalker.walk(this);
                exprPlans.add((PhysicalPlan) currentPlan);
                popWalker();

            }
            currentPlan = currentPlans.pop();
            physOp.setPlans(exprPlans);
            physOp.setIndex(count++);
            if (plans.size() > 1) {
                type = DataType.TUPLE;
                physOp.setKeyType(type);
            } else {
                type = exprPlans.get(0).getLeaves().get(0).getResultType();
                physOp.setKeyType(type);
            }
            physOp.setResultType(DataType.TUPLE);

            currentPlan.add(physOp);

            try {
                currentPlan.connect(LogToPhyMap.get(lo), physOp);
                currentPlan.connect(physOp, poc);
            } catch (PlanException e) {
                log.error("Invalid physical operators in the physical plan"
                        + e.getMessage());
                throw new VisitorException(e);
            }
            
        }
        LogToPhyMap.put(cg, poc);
    }
    
    @Override
    public void visit(LOSplit split) throws VisitorException {
	String scope = split.getOperatorKey().scope;
        PhysicalOperator physOp = new POSplit(new OperatorKey(scope, nodeGen
                .getNextNodeId(scope)), split.getRequestedParallelism());
        
        LogToPhyMap.put(split, physOp);

        currentPlan.add(physOp);
        PhysicalOperator from = LogToPhyMap.get(split.getPlan()
                .getPredecessors(split).get(0));
        try {
            currentPlan.connect(from, physOp);
        } catch (PlanException e) {
            log.error("Invalid physical operator in the plan" + e.getMessage());
            throw new VisitorException(e);
        }
    }
    
    @Override
    public void visit(LOSplitOutput split) throws VisitorException {
	String scope = split.getOperatorKey().scope;
        PhysicalOperator physOp = new POSplitOutput(new OperatorKey(scope, nodeGen
                .getNextNodeId(scope)), split.getRequestedParallelism());
        LogToPhyMap.put(split, physOp);

        currentPlan.add(physOp);
        currentPlans.push(currentPlan);
        currentPlan = new PhysicalPlan();
        PlanWalker<LogicalOperator, LogicalPlan> childWalker = mCurrentWalker
                .spawnChildWalker(split.getConditionPlan());
        pushWalker(childWalker);
        mCurrentWalker.walk(this);
        popWalker();

        ((POSplitOutput) physOp).setPlan((PhysicalPlan) currentPlan);
        currentPlan = currentPlans.pop();
        currentPlan.add(physOp);
        PhysicalOperator from = LogToPhyMap.get(split.getPlan()
                .getPredecessors(split).get(0));
        try {
            currentPlan.connect(from, physOp);
        } catch (PlanException e) {
            log.error("Invalid physical operator in the plan" + e.getMessage());
            throw new VisitorException(e);
        }
    }

}
