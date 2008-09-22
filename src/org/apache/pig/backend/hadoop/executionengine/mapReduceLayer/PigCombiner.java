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
package org.apache.pig.backend.hadoop.executionengine.mapReduceLayer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;

import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.backend.hadoop.HDataType;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.PhysicalOperator;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.POStatus;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.Result;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.plans.PhysicalPlan;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators.POPackage;
import org.apache.pig.data.DataType;
import org.apache.pig.data.TargetedTuple;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.io.PigNullableWritable;
import org.apache.pig.impl.io.NullableTuple;
import org.apache.pig.impl.plan.VisitorException;
import org.apache.pig.impl.util.ObjectSerializer;
import org.apache.pig.impl.util.WrappedIOException;

public class PigCombiner {

    public static JobConf sJobConf = null;
    
    public static class Combine extends MapReduceBase
            implements
            Reducer<PigNullableWritable, NullableTuple, PigNullableWritable, Writable> {
        private final Log log = LogFactory.getLog(getClass());

        private byte keyType;
        
        //The reduce plan
        private PhysicalPlan cp;
        
        //The POPackage operator which is the
        //root of every Map Reduce plan is
        //obtained through the job conf. The portion
        //remaining after its removal is the reduce
        //plan
        private POPackage pack;
        
        ProgressableReporter pigReporter;
        
        /**
         * Configures the Reduce plan, the POPackage operator
         * and the reporter thread
         */
        @Override
        public void configure(JobConf jConf) {
            super.configure(jConf);
            sJobConf = jConf;
            try {
                cp = (PhysicalPlan) ObjectSerializer.deserialize(jConf
                        .get("pig.combinePlan"));
                pack = (POPackage)ObjectSerializer.deserialize(jConf.get("pig.combine.package"));
                // To be removed
                if(cp.isEmpty())
                    log.debug("Combine Plan empty!");
                else{
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    cp.explain(baos);
                    log.debug(baos.toString());
                }
                
                keyType = ((byte[])ObjectSerializer.deserialize(jConf.get("pig.map.keytype")))[0];
                // till here
                
                long sleepTime = jConf.getLong("pig.reporter.sleep.time", 10000);

                pigReporter = new ProgressableReporter();
            } catch (IOException e) {
                log.error(e.getMessage() + "was caused by:");
                log.error(e.getCause().getMessage());
            }
        }
        
        /**
         * The reduce function which packages the key and List &lt;Tuple&gt;
         * into key, Bag&lt;Tuple&gt; after converting Hadoop type key into Pig type.
         * The package result is either collected as is, if the reduce plan is
         * empty or after passing through the reduce plan.
         */
        public void reduce(PigNullableWritable key,
                Iterator<NullableTuple> tupIter,
                OutputCollector<PigNullableWritable, Writable> oc,
                Reporter reporter) throws IOException {
            
            pigReporter.setRep(reporter);
            
            pack.attachInput(key, tupIter);
            
            try {
                Tuple t=null;
                Result res = pack.getNext(t);
                if(res.returnStatus==POStatus.STATUS_OK){
                    Tuple packRes = (Tuple)res.result;
                    
                    if(cp.isEmpty()){
                        oc.collect(null, packRes);
                        return;
                    }
                    
                    cp.attachInput(packRes);

                    List<PhysicalOperator> leaves = cp.getLeaves();

                    PhysicalOperator leaf = leaves.get(0);
                    while(true){
                        Result redRes = leaf.getNext(t);
                        
                        if(redRes.returnStatus==POStatus.STATUS_OK){
                            Tuple tuple = (Tuple)redRes.result;
                            Byte index = (Byte)tuple.get(0);
                            PigNullableWritable outKey =
                                HDataType.getWritableComparableTypes(tuple.get(1), this.keyType);
                            NullableTuple val =
                                new NullableTuple((Tuple)tuple.get(2));
                            // Both the key and the value need the index.  The key needs it so
                            // that it can be sorted on the index in addition to the key
                            // value.  The value needs it so that POPackage can properly
                            // assign the tuple to its slot in the projection.
                            outKey.setIndex(index);
                            val.setIndex(index);
                            oc.collect(outKey, val);
                            continue;
                        }
                        
                        if(redRes.returnStatus==POStatus.STATUS_EOP)
                            return;
                        
                        if(redRes.returnStatus==POStatus.STATUS_NULL)
                            continue;
                        
                        if(redRes.returnStatus==POStatus.STATUS_ERR){
                            IOException ioe = new IOException("Received Error while " +
                                    "processing the reduce plan.");
                            throw ioe;
                        }
                    }
                }
                
                if(res.returnStatus==POStatus.STATUS_NULL)
                    return;
                
                if(res.returnStatus==POStatus.STATUS_ERR){
                    IOException ioe = new IOException("Packaging error while processing group");
                    throw ioe;
                }
                    
                
            } catch (ExecException e) {
                IOException ioe = new IOException(e.getMessage());
                ioe.initCause(e.getCause());
                throw ioe;
            }
        }
        
        
        /**
         * Will be called once all the intermediate keys and values are
         * processed. So right place to stop the reporter thread.
         */
        @Override
        public void close() throws IOException {
            super.close();
        }

        /**
         * @return the keyType
         */
        public byte getKeyType() {
            return keyType;
        }

        /**
         * @param keyType the keyType to set
         */
        public void setKeyType(byte keyType) {
            this.keyType = keyType;
        }
    }
    
}
