/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.data.BagFactory;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.DataType;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;
import org.apache.pig.impl.io.NullableTuple;
import org.apache.pig.impl.io.PigNullableWritable;
import org.apache.pig.impl.plan.OperatorKey;
import org.apache.pig.impl.plan.NodeIdGenerator;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.PhysicalOperator;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.POStatus;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.Result;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.plans.PhyPlanVisitor;
import org.apache.pig.impl.plan.VisitorException;
/**
 * The package operator that packages
 * the globally rearranged tuples into
 * output format as required by co-group.
 * This is last stage of processing co-group.
 * This operator has a slightly different
 * format than other operators in that, it
 * takes two things as input. The key being 
 * worked on and the iterator of bags that
 * contain indexed tuples that just need to
 * be packaged into their appropriate output
 * bags based on the index.
 */
public class POPackage extends PhysicalOperator {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    //The iterator of indexed Tuples
    //that is typically provided by
    //Hadoop
    Iterator<NullableTuple> tupIter;

    //The key being worked on
    Object key;
    
    //key's type
    byte keyType;

    //The number of inputs to this
    //co-group.  0 indicates a distinct, which means there will only be a
    //key, no value.
    int numInputs;
    
    //Denotes if inner is specified
    //on a particular input
    boolean[] inner;
    
    private final Log log = LogFactory.getLog(getClass());

    protected static BagFactory mBagFactory = BagFactory.getInstance();
    protected static TupleFactory mTupleFactory = TupleFactory.getInstance();

    public POPackage(OperatorKey k) {
        this(k, -1, null);
    }

    public POPackage(OperatorKey k, int rp) {
        this(k, rp, null);
    }

    public POPackage(OperatorKey k, List<PhysicalOperator> inp) {
        this(k, -1, inp);
    }

    public POPackage(OperatorKey k, int rp, List<PhysicalOperator> inp) {
        super(k, rp, inp);
        numInputs = -1;
    }

    @Override
    public String name() {
        return "Package" + "[" + DataType.findTypeName(resultType) + "]" + "{" + DataType.findTypeName(keyType) + "}" +" - " + mKey.toString();
    }

    @Override
    public boolean supportsMultipleInputs() {
        return false;
    }

    @Override
    public void visit(PhyPlanVisitor v) throws VisitorException {
        v.visitPackage(this);
    }

    @Override
    public boolean supportsMultipleOutputs() {
        return false;
    }
    
    /**
     * Attaches the required inputs
     * @param k - the key being worked on
     * @param inp - iterator of indexed tuples typically
     *              obtained from Hadoop
     */
    public void attachInput(PigNullableWritable k, Iterator<NullableTuple> inp) {
        tupIter = inp;
        key = k.getValueAsPigType();
    }

    /**
     * attachInput's better half!
     */
    public void detachInput() {
        tupIter = null;
        key = null;
    }

    public int getNumInps() {
        return numInputs;
    }

    public void setNumInps(int numInps) {
        this.numInputs = numInps;
    }
    
    public boolean[] getInner() {
        return inner;
    }

    public void setInner(boolean[] inner) {
        this.inner = inner;
    }

    /**
     * From the inputs, constructs the output tuple
     * for this co-group in the required format which
     * is (key, {bag of tuples from input 1}, {bag of tuples from input 2}, ...)
     */
    @Override
    public Result getNext(Tuple t) throws ExecException {
        //Create numInputs bags
        DataBag[] dbs = null;
        if (numInputs > 0) {
            dbs = new DataBag[numInputs];
            for (int i = 0; i < numInputs; i++) {
                dbs[i] = mBagFactory.newDefaultBag();
            }
        }
        
        //For each indexed tup in the inp, sort them
        //into their corresponding bags based
        //on the index
        while (tupIter.hasNext()) {
            NullableTuple ntup = tupIter.next();
            // Need to make a copy of the value, as hadoop uses the same ntup
            // to represent each value.
            Tuple val = (Tuple)ntup.getValueAsPigType();
            /*
            Tuple copy = mTupleFactory.newTuple(val.size());
            for (int i = 0; i < val.size(); i++) {
                copy.set(i, val.get(i));
            }
            */
            Tuple copy = mTupleFactory.newTuple(val.getAll());
            if (numInputs > 0) dbs[ntup.getIndex()].add(copy);
            if(reporter!=null) reporter.progress();
        }
        
        //Construct the output tuple by appending
        //the key and all the above constructed bags
        //and return it.
        Tuple res;
        res = mTupleFactory.newTuple(numInputs+1);
        res.set(0,key);
        if (numInputs > 0) {
            int i=-1;
            for (DataBag bag : dbs) {
                if(inner[++i]){
                    if(bag.size()==0){
                        detachInput();
                        Result r = new Result();
                        r.returnStatus = POStatus.STATUS_NULL;
                        return r;
                    }
                }
                res.set(i+1,bag);
            }
        }
        detachInput();
        Result r = new Result();
        r.result = res;
        r.returnStatus = POStatus.STATUS_OK;
        return r;
    }

    public byte getKeyType() {
        return keyType;
    }

    public void setKeyType(byte keyType) {
        this.keyType = keyType;
    }

    /**
     * Make a deep copy of this operator.  
     * @throws CloneNotSupportedException
     */
    @Override
    public POPackage clone() throws CloneNotSupportedException {
        POPackage clone = new POPackage(new OperatorKey(mKey.scope,
            NodeIdGenerator.getGenerator().getNextNodeId(mKey.scope)));
        clone.resultType = resultType;
        clone.keyType = keyType;
        clone.numInputs = numInputs;
        clone.inner = new boolean[inner.length];
        for (int i = 0; i < inner.length; i++) {
            clone.inner[i] = inner[i];
        }
        return clone;
    }


}
