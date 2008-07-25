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
package org.apache.pig.impl.logicalLayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.pig.FuncSpec;
import org.apache.pig.impl.logicalLayer.FrontendException;
import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.apache.pig.impl.plan.OperatorKey;
import org.apache.pig.impl.plan.VisitorException;
import org.apache.pig.impl.plan.PlanVisitor;
import org.apache.pig.data.DataType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class LOSort extends LogicalOperator {
    private static final long serialVersionUID = 2L;

    private List<Boolean> mAscCols;
    private FuncSpec mSortFunc;
    private boolean mIsStar = false;
    private long limit;
    private List<LogicalPlan> mSortColPlans;
    private static Log log = LogFactory.getLog(LOSort.class);

    /**
     * @param plan
     *            LogicalPlan this operator is a part of.
     * @param key
     *            OperatorKey for this operator
     * @param sortColPlans
     *            Array of column numbers that will be used for sorting data.
     * @param ascCols
     *            Array of booleans. Should be same size as sortCols. True
     *            indicates sort ascending (default), false sort descending. If
     *            this array is null, then all columns will be sorted ascending.
     * @param sortFunc
     *            the user defined sorting function
     */
    public LOSort(
            LogicalPlan plan,
            OperatorKey key,
            List<LogicalPlan> sortColPlans,
            List<Boolean> ascCols,
            FuncSpec sortFunc) {
        super(plan, key);
        mSortColPlans = sortColPlans;
        mAscCols = ascCols;
        mSortFunc = sortFunc;
        limit = -1;
    }

    public LogicalOperator getInput() {
        return mPlan.getPredecessors(this).get(0);
    }
    
    public List<LogicalPlan> getSortColPlans() {
        return mSortColPlans;
    }

    public List<Boolean> getAscendingCols() {
        return mAscCols;
    }

    public FuncSpec getUserFunc() {
        return mSortFunc;
    }

    public void setUserFunc(FuncSpec func) {
        mSortFunc = func;
    }

    public boolean isStar() {
        return mIsStar;
    }

    public void setStar(boolean b) {
        mIsStar = b;
    }

    public void setLimit(long l)
    {
    	limit = l;
    }
    
    public long getLimit()
    {
    	return limit;
    }
    
    public boolean isLimited()
    {
    	return (limit!=-1);
    }

    @Override
    public String name() {
        return "SORT " + mKey.scope + "-" + mKey.id;
    }

    @Override
    public Schema getSchema() throws FrontendException {
        if (!mIsSchemaComputed) {
            // get our parent's schema
            Collection<LogicalOperator> s = mPlan.getPredecessors(this);
            ArrayList<Schema.FieldSchema> fss = new ArrayList<Schema.FieldSchema>();
            try {
                LogicalOperator op = s.iterator().next();
                if (null == op) {
                    throw new FrontendException("Could not find operator in plan");
                }
                if(op instanceof ExpressionOperator) {
                    Schema.FieldSchema fs = ((ExpressionOperator)op).getFieldSchema();
                    if(DataType.isSchemaType(fs.type)) {
                        mSchema = fs.schema;
                    } else {
                        fss.add(fs);
                        mSchema = new Schema(fss);
                    }
                } else {
                    mSchema = op.getSchema();
                }
                mIsSchemaComputed = true;
            } catch (FrontendException ioe) {
                mSchema = null;
                mIsSchemaComputed = false;
                throw ioe;
            }
        }
        return mSchema;
    }

    @Override
    public boolean supportsMultipleInputs() {
        return false;
    }

    public void visit(LOVisitor v) throws VisitorException {
        v.visit(this);
    }

    public byte getType() {
        return DataType.BAG ;
    }
}
