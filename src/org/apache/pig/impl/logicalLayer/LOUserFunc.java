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

import java.util.List;

import org.apache.pig.EvalFunc;
import org.apache.pig.FuncSpec;
import org.apache.pig.impl.PigContext;
import org.apache.pig.impl.plan.VisitorException;
import org.apache.pig.impl.logicalLayer.parser.ParseException;
import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.apache.pig.data.DataType;
import org.apache.pig.impl.plan.OperatorKey;
import org.apache.pig.impl.plan.PlanVisitor;

public class LOUserFunc extends ExpressionOperator {
    private static final long serialVersionUID = 2L;

    private FuncSpec mFuncSpec;
    private List<ExpressionOperator> mArgs;

    /**
     * @param plan
     *            LogicalPlan this operator is a part of.
     * @param k
     *            OperatorKey for this operator.
     * @param funcSpec
     *            name of the user defined function.
     * @param args
     *            List of expressions that form the arguments for this function.
     * @param returnType
     *            return type of this function.
     */
    public LOUserFunc(LogicalPlan plan, OperatorKey k, FuncSpec funcSpec,
            List<ExpressionOperator> args, byte returnType) {
        super(plan, k, -1);
        mFuncSpec = funcSpec;
        mArgs = args;
        mType = returnType;
    }

    public FuncSpec getFuncSpec() {
        return mFuncSpec;
    }

    public List<ExpressionOperator> getArguments() {
        return mArgs;
    }

    @Override
    public boolean supportsMultipleInputs() {
        return true;
    }

    @Override
    public String name() {
        return "UserFunc " + mKey.scope + "-" + mKey.id + " function: " + mFuncSpec;
    }

    @Override
    public Schema getSchema() {
        return mSchema;
    }

    @Override
    public Schema.FieldSchema getFieldSchema() throws FrontendException {
        Schema inputSchema = new Schema();
        for(ExpressionOperator op: mArgs) {
            if (!DataType.isUsableType(op.getType())) {
                String msg = "Problem with input: " + op + " of User-defined function: " + this ;
                mFieldSchema = null;
                mIsFieldSchemaComputed = false;
                throw new FrontendException(msg) ;
            }
            inputSchema.add(op.getFieldSchema());    
        }

        EvalFunc<?> ef = (EvalFunc<?>) PigContext.instantiateFuncFromSpec(mFuncSpec);
        Schema udfSchema = ef.outputSchema(inputSchema);

        if (null != udfSchema) {
            Schema.FieldSchema fs;
            try {
                fs = new Schema.FieldSchema(udfSchema.getField(0));
            } catch (ParseException pe) {
                throw new FrontendException(pe.getMessage());
            }
            setType(fs.type);
            mFieldSchema = fs;
            mIsFieldSchemaComputed = true;
        } else {
            byte returnType = DataType.findType(ef.getReturnType());
            setType(returnType);
            mFieldSchema = new Schema.FieldSchema(null, null, returnType);
            mIsFieldSchemaComputed = true;
        }
        return mFieldSchema;
    }


    @Override
    public void visit(LOVisitor v) throws VisitorException {
        v.visit(this);
    }

    /**
     * @param funcSpec the FuncSpec to set
     */
    public void setFuncSpec(FuncSpec funcSpec) {
        mFuncSpec = funcSpec;
    }

}
