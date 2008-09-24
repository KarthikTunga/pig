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
package org.apache.pig.impl.logicalLayer.schema;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collection;

import org.apache.pig.data.DataType;
import org.apache.pig.impl.logicalLayer.parser.ParseException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pig.impl.util.MultiMap;
import org.apache.pig.impl.plan.OperatorKey;
import org.apache.pig.impl.logicalLayer.FrontendException;


public class Schema implements Serializable, Cloneable {

    private static final long serialVersionUID = 2L;

    public static class FieldSchema implements Serializable, Cloneable {
        /**
         * 
         */
        private static final long serialVersionUID = 2L;

        /**
         * Alias for this field.
         */
        public String alias;

        /**
         * Datatype, using codes from {@link org.apache.pig.data.DataType}.
         */
        public byte type;

        /**
         * If this is a tuple itself, it can have a schema. Otherwise this field
         * must be null.
         */
        public Schema schema;

        /**
         * Canonical name.  This name uniquely identifies a field throughout
         * the query.  Unlike a an alias, it cannot be changed.  It will
         * change when the field is transformed in some way (such as being
         * used in an arithmetic expression or passed to a udf).  At that
         * point a new canonical name will be generated for the field.
         */
        public String canonicalName = null;

        /**
         * Map of canonical names used for this field in other sections of the
         * plan.  It can occur that a single field will have different
         * canonical names in different branches of a plan.  For example, 
         * C = cogroup A by x, B by y.  In subsequent statements, the grouping
         * column will have canonical name, say, of 'r'.  But in branches
         * above the cogroup it may have been known as 's' in the A branch and
         * 't' in the B branch.  This map preserves that.  The key is a
         * logical operator's key, and the value is the canonical name
         * associated with the field for that operator.
         */
        public Map<OperatorKey, String> canonicalMap = null;
        
        private static Log log = LogFactory.getLog(Schema.FieldSchema.class);

        /**
         * Constructor for any type.
         * 
         * @param a
         *            Alias, if known. If unknown leave null.
         * @param t
         *            Type, using codes from
         *            {@link org.apache.pig.data.DataType}.
         */
        public FieldSchema(String a, byte t) {
            alias = a;
            type = t;
            schema = null;            
        }

        /**
         * Constructor for tuple fields.
         * 
         * @param a
         *            Alias, if known. If unknown leave null.
         * @param s
         *            Schema of this tuple.
         */
        public FieldSchema(String a, Schema s) {
            alias = a;
            type = DataType.TUPLE;
            schema = s;
        }

        /**
         * Constructor for tuple fields.
         * 
         * @param a
         *            Alias, if known. If unknown leave null.
         * @param s
         *            Schema of this tuple.
         * @param t
         *            Type, using codes from
         *            {@link org.apache.pig.data.DataType}.
         * 
         */
        public FieldSchema(String a, Schema s, byte t)  throws FrontendException {
            alias = a;
            schema = s;
            log.debug("t: " + t + " Bag: " + DataType.BAG + " tuple: " + DataType.TUPLE);
            if ((null != s) && (t != DataType.BAG) && (t != DataType.TUPLE)) {
                throw new FrontendException("Only a BAG or TUPLE can have schemas. Got "
                        + DataType.findTypeName(t));
            }
            type = t;
        }

        /**
         * Copy Constructor.
         * 
         * @param fs
         *           Source FieldSchema
         * 
         */
        public FieldSchema(FieldSchema fs)  {
            if(null != fs) {
                alias = fs.alias;
                if(null != fs.schema) {
                    schema = new Schema(fs.schema);
                } else {
                    schema = null;
                }
                type = fs.type;
            } else {
                alias = null;
                schema = null;
                type = DataType.UNKNOWN;
            }
        }

        /***
         *  Two field schemas are equal if types and schemas
         *  are equal in all levels.
         *
         *  In order to relax alias equivalent requirement,
         *  instead use equals(FieldSchema fschema,
                               FieldSchema fother,
                               boolean relaxInner,
                               boolean relaxAlias)
          */

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof FieldSchema)) return false;
            FieldSchema otherfs = (FieldSchema)other;

            return FieldSchema.equals(this, otherfs, false, false) ;
        }


        @Override
        public int hashCode() {
            return (this.type * 17)
                    + ( (schema==null? 0:schema.hashCode()) * 23 )
                    + ( (alias==null? 0:alias.hashCode()) * 29 ) ;
        }

        /**
         * Recursively compare two schemas to check if the input schema 
         * can be cast to the cast schema
         * @param castFs schema of the cast operator
         * @param  inputFs schema of the cast input
         * @return true or falsew!
         */
        public static boolean castable(
                Schema.FieldSchema castFs,
                Schema.FieldSchema inputFs) {
            if(castFs == null && inputFs == null) {
                return false;
            }
            
            if (castFs == null) {
                return false ;
            }
    
            if (inputFs == null) {
                return false ;
            }
            byte inputType = inputFs.type;
            byte castType = castFs.type;
    
            if (DataType.isSchemaType(castFs.type)) {
                if(inputType == DataType.BYTEARRAY) {
                    //good
                } else if (inputType == castType) {
                    // Don't do the comparison if both embedded schemas are
                    // null.  That will cause Schema.equals to return false,
                    // even though we want to view that as true.
                    if (!(castFs.schema == null && inputFs.schema == null)) { 
                        // compare recursively using schema
                        if (!Schema.castable(castFs.schema, inputFs.schema)) {
                            return false ;
                        }
                    }
                } else {
                    return false;
                }
            } else {
                if (inputType == castType) {
                    //good
                }
                else if (DataType.isNumberType(inputType) &&
                    DataType.isNumberType(castType) ) {
                    //good
                }
                else if (inputType == DataType.BYTEARRAY) {
                    //good
                }
                else if (  ( DataType.isNumberType(inputType) || 
                             inputType == DataType.CHARARRAY 
                           )  &&
                           (  (castType == DataType.CHARARRAY) ||
                              (castType == DataType.BYTEARRAY)    
                           ) 
                        ) {
                    //good
                } else {
                    return false;
                }
            }
    
            return true ;
        }

        /***
         * Compare two field schema for equality
         * @param fschema
         * @param fother
         * @param relaxInner If true, we don't check inner tuple schemas
         * @param relaxAlias If true, we don't check aliases
         * @return true if FieldSchemas are equal, false otherwise
         */
        public static boolean equals(FieldSchema fschema,
                                     FieldSchema fother,
                                     boolean relaxInner,
                                     boolean relaxAlias) {
            if (fschema == null) {
                return false ;
            }

            if (fother == null) {
                return false ;
            }

            if (fschema.type != fother.type) {
                return false ;
            }


            if (!relaxAlias) {
                if ( (fschema.alias == null) &&
                     (fother.alias == null) ) {
                    // good
                }
                else if ( (fschema.alias != null) &&
                          (fother.alias == null) ) {
                    return false ;
                }
                else if ( (fschema.alias == null) &&
                          (fother.alias != null) ) {
                    return false ;
                }
                else if (!fschema.alias.equals(fother.alias)) {
                    return false ;
                }
            }

            if ( (!relaxInner) && (DataType.isSchemaType(fschema.type))) {
                // Don't do the comparison if both embedded schemas are
                // null.  That will cause Schema.equals to return false,
                // even though we want to view that as true.
                if (!(fschema.schema == null && fother.schema == null)) { 
                    // compare recursively using schema
                    if (!Schema.equals(fschema.schema, fother.schema, false, relaxAlias)) {
                        return false ;
                    }
                }
            }

            return true ;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (alias != null) {
                sb.append(alias);
                sb.append(": ");
            }
            sb.append(DataType.findTypeName(type));

            if (schema != null) {
                sb.append("(");
                sb.append(schema.toString());
                sb.append(")");
            }

            if (canonicalName != null) {
                sb.append(" cn: ");
                sb.append(canonicalName);
            }
            return sb.toString();
        }

        /**
         * Make a deep copy of this FieldSchema and return it.
         * @return clone of the this FieldSchema.
         * @throws CloneNotSupportedException
         */
        @Override
        public FieldSchema clone() throws CloneNotSupportedException {
            // Strings are immutable, so we don't need to copy alias.  Schemas
            // are mutable so we need to make a copy.
            try {
                FieldSchema fs = new FieldSchema(alias,
                    (schema == null ? null : schema.clone()), type);
                fs.canonicalName = canonicalName;
                if (canonicalMap != null) {
                    fs.canonicalMap =
                        new HashMap<OperatorKey, String>(canonicalMap);
                }
                return fs;
            } catch (FrontendException fe) {
                throw new RuntimeException(
                    "Should never fail to clone a FieldSchema", fe);
            }
        }

        /***
        * Recursively prefix merge two schemas
        * @param otherFs the other field schema to be merged with
        * @return the prefix merged field schema this can be null if one schema is null and
        *         allowIncompatibleTypes is true
        *
        * @throws SchemaMergeException if they cannot be merged
        */

        public Schema.FieldSchema mergePrefixFieldSchema(Schema.FieldSchema otherFs) throws SchemaMergeException {
            return mergePrefixFieldSchema(otherFs, true);
        }

        /***
        * Recursively prefix merge two schemas
        * @param otherFs the other field schema to be merged with
        * @param otherTakesAliasPrecedence true if aliases from the other
        *                                  field schema take precedence
        * @return the prefix merged field schema this can be null if one schema is null and
        *         allowIncompatibleTypes is true
        *
        * @throws SchemaMergeException if they cannot be merged
        */

        public Schema.FieldSchema mergePrefixFieldSchema(Schema.FieldSchema otherFs,
                                            boolean otherTakesAliasPrecedence)
                                                throws SchemaMergeException {
            Schema.FieldSchema myFs = this;
            Schema.FieldSchema mergedFs = null;
            byte mergedType = DataType.NULL;
    
            if(null == otherFs) {
                return myFs;
            }

            if((myFs.type == DataType.NULL || myFs.type == DataType.UNKNOWN)
                && (otherFs.type == DataType.NULL || otherFs.type == DataType.UNKNOWN)) {
                throw new SchemaMergeException("Type mismatch. No useful type for merging. Field Schema: " + myFs + ". Other Field Schema: " + otherFs);
            } else if(myFs.type == otherFs.type) {
                mergedType = myFs.type;
            } else if((myFs.type == DataType.NULL || myFs.type == DataType.UNKNOWN) 
                && (otherFs.type == DataType.NULL)) {
                mergedType = DataType.BYTEARRAY;
            } else if ((myFs.type != DataType.NULL && myFs.type != DataType.UNKNOWN)
                && (otherFs.type == DataType.NULL)) {
                mergedType = myFs.type;
            } else {
                throw new SchemaMergeException("Type mismatch. Field Schema: " + myFs + ". Other Field Schema: " + otherFs);
            }
    
            String mergedAlias = mergeAlias(myFs.alias,
                                            otherFs.alias,
                                            otherTakesAliasPrecedence) ;
    
            if (!DataType.isSchemaType(mergedType)) {
                // just normal merge
                mergedFs = new FieldSchema(mergedAlias, mergedType) ;
            }
            else {
                Schema mergedSubSchema = null;
                // merge inner schemas because both sides have schemas
                if(null != myFs.schema) {
                    mergedSubSchema = myFs.schema.mergePrefixSchema(otherFs.schema,
                                                     otherTakesAliasPrecedence);
                } else {
                    mergedSubSchema = otherFs.schema;
                }
                // create the merged field
                try {
                    mergedFs = new FieldSchema(mergedAlias, mergedSubSchema, mergedType) ;
                } catch (FrontendException fee) {
                    throw new SchemaMergeException(fee.getMessage());
                }
            }
            return mergedFs;
        }

    }

    private List<FieldSchema> mFields;
    private Map<String, FieldSchema> mAliases;
    private MultiMap<FieldSchema, String> mFieldSchemas;
    private static Log log = LogFactory.getLog(Schema.class);

    public Schema() {
        mFields = new ArrayList<FieldSchema>();
        mAliases = new HashMap<String, FieldSchema>();
        mFieldSchemas = new MultiMap<FieldSchema, String>();
    }

    /**
     * @param fields List of field schemas that describes the fields.
     */
    public Schema(List<FieldSchema> fields) {
        mFields = fields;
        mAliases = new HashMap<String, FieldSchema>(fields.size());
        mFieldSchemas = new MultiMap<FieldSchema, String>();
        for (FieldSchema fs : fields) {
            if (fs.alias != null) {
                mAliases.put(fs.alias, fs);
                if(null != fs) {
                    mFieldSchemas.put(fs, fs.alias);
                }
            }
        }
    }

    /**
     * Create a schema with only one field.
     * @param fieldSchema field to put in this schema.
     */
    public Schema(FieldSchema fieldSchema) {
        mFields = new ArrayList<FieldSchema>(1);
        mFields.add(fieldSchema);
        mAliases = new HashMap<String, FieldSchema>(1);
        mFieldSchemas = new MultiMap<FieldSchema, String>();
        if (fieldSchema.alias != null) {
            mAliases.put(fieldSchema.alias, fieldSchema);
            if(null != fieldSchema) {
                mFieldSchemas.put(fieldSchema, fieldSchema.alias);
            }
        }
    }

    /**
     * Copy Constructor.
     * @param s source schema
     */
    public Schema(Schema s) {

        if(null != s) {
            mFields = new ArrayList<FieldSchema>(s.size());
            mAliases = new HashMap<String, FieldSchema>();
            mFieldSchemas = new MultiMap<FieldSchema, String>();
            try {
                for (int i = 0; i < s.size(); ++i) {
                    FieldSchema fs = new FieldSchema(s.getField(i));
                    if(null != fs) {
                        mFields.add(fs);
                        if (fs.alias != null) {
                            mAliases.put(fs.alias, fs);
                            if(null != fs) {
                                mFieldSchemas.put(fs, fs.alias);
                            }
                        }
                    }
                }
            } catch (ParseException pe) {
                mFields = new ArrayList<FieldSchema>();
                mAliases = new HashMap<String, FieldSchema>();
                mFieldSchemas = new MultiMap<FieldSchema, String>();
            }
        } else {
            mFields = new ArrayList<FieldSchema>();
            mAliases = new HashMap<String, FieldSchema>();
            mFieldSchemas = new MultiMap<FieldSchema, String>();
        }
    }

    /**
     * Given an alias name, find the associated FieldSchema.
     * @param alias Alias to look up.
     * @return FieldSchema, or null if no such alias is in this tuple.
     */
    public FieldSchema getField(String alias) throws FrontendException {
        FieldSchema fs = mAliases.get(alias);
        if(null == fs) {
            String cocoPrefix = new String("::" + alias);
            Map<String, Integer> aliasMatches = new HashMap<String, Integer>();
            //build the map of aliases that have cocoPrefix as the suffix
            for(String key: mAliases.keySet()) {
                if(key.endsWith(cocoPrefix)) {
                    Integer count = aliasMatches.get(key);
                    if(null == count) {
                        aliasMatches.put(key, 1);
                    } else {
                        aliasMatches.put(key, ++count);
                    }
                }
            }
            //process the map to check if
            //1. are there multiple keys with count == 1
            //2. are there keys with count > 1 --> should never occur
            //3. if thers is a single key with count == 1 we have our match

            if(aliasMatches.keySet().size() == 0) {
                return null;
            }
            if(aliasMatches.keySet().size() == 1) {
                Object[] keys = aliasMatches.keySet().toArray();
                String key = (String)keys[0];
                if(aliasMatches.get(key) > 1) {
                    throw new FrontendException("Found duplicate aliases: " + key);
                }
                return mAliases.get(key);
            } else {
                // check if the multiple aliases obtained actually
                // point to the same field schema - then just return
                // that field schema
                Set<FieldSchema> set = new HashSet<FieldSchema>();
                for (String key: aliasMatches.keySet()) {
                    set.add(mAliases.get(key));
                }
                if(set.size() == 1) {
                    return set.iterator().next();
                }
                
                boolean hasNext = false;
                StringBuilder sb = new StringBuilder("Found more than one match: ");
                for (String key: aliasMatches.keySet()) {
                    if(hasNext) {
                        sb.append(", ");
                    } else {
                        hasNext = true;
                    }
                    sb.append(key);
                }
                throw new FrontendException(sb.toString());
            }
        } else {
            return fs;
        }
    }

    /**
     * Given a field number, find the associated FieldSchema.
     *
     * @param fieldNum
     *            Field number to look up.
     * @return FieldSchema for this field.
     * @throws ParseException
     *             if the field number exceeds the number of fields in the
     *             tuple.
     */
    public FieldSchema getField(int fieldNum) throws ParseException {
        if (fieldNum >= mFields.size()) {
            throw new ParseException("Attempt to fetch field " + fieldNum
                    + " from tuple of size " + mFields.size());
        }

        return mFields.get(fieldNum);
    }

    /**
     * Find the number of fields in the schema.
     *
     * @return number of fields.
     */
    public int size() {
        return mFields.size();
    }

    /**
     * Reconcile this schema with another schema.  The schema being reconciled
     * with should have the same number of columns.  The use case is where a
     * schema already exists but may not have alias and or type information.  If
     * an alias exists in this schema and a new one is given, then the new one
     * will be used.  Similarly with types, though this needs to be used
     * carefully, as types should not be lightly changed.
     * @param other Schema to reconcile with.
     * @throws ParseException if this cannot be reconciled.
     */
    public void reconcile(Schema other) throws ParseException {

        if (other != null) {
        
            if (other.size() != size()) {
                throw new ParseException("Cannot reconcile schemas with different "
                    + "sizes.  This schema has size " + size() + " other has size "
                    + "of " + other.size());
            }

            Iterator<FieldSchema> i = other.mFields.iterator();
            for (int j = 0; i.hasNext(); j++) {
                FieldSchema otherFs = i.next();
                FieldSchema ourFs = mFields.get(j);
                log.debug("ourFs: " + ourFs + " otherFs: " + otherFs);
                if (otherFs.alias != null) {
                    log.debug("otherFs.alias: " + otherFs.alias);
                    if (ourFs.alias != null) {
                        log.debug("Removing ourFs.alias: " + ourFs.alias);
                        mAliases.remove(ourFs.alias);
                        Collection<String> aliases = mFieldSchemas.get(ourFs);
                        if (aliases != null) {
                            List<String> listAliases = new ArrayList<String>();
                            for(String alias: aliases) {
                                listAliases.add(new String(alias));
                            }
                            for(String alias: listAliases) {
                                log.debug("Removing alias " + alias + " from multimap");
                                mFieldSchemas.remove(ourFs, alias);
                            }
                        }
                    }
                    ourFs.alias = otherFs.alias;
                    log.debug("Setting alias to: " + otherFs.alias);
                    mAliases.put(ourFs.alias, ourFs);
                    if(null != ourFs.alias) {
                        mFieldSchemas.put(ourFs, ourFs.alias);
                    }
                }
                if (otherFs.type != DataType.UNKNOWN) {
                    ourFs.type = otherFs.type;
                    log.debug("Setting type to: "
                            + DataType.findTypeName(otherFs.type));
                }
                if (otherFs.schema != null) {
                    ourFs.schema = otherFs.schema;
                    log.debug("Setting schema to: " + otherFs.schema);
                }

            }
        }
    }

    /***
     * For two schemas to be equal, they have to be deeply equal.
     * Use Schema.equals(Schema schema,
                         Schema other,
                         boolean relaxInner,
                         boolean relaxAlias)
       if relaxation of aliases is a requirement.
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Schema)) return false;

        Schema s = (Schema)other;
        return Schema.equals(this, s, false, false) ;

    }

    /**
     * Make a deep copy of a schema.
     * @throws CloneNotSupportedException
     */
    public Schema clone() throws CloneNotSupportedException {
        Schema s = new Schema();

        // Build a map between old and new field schemas, so we can properly
        // construct the new alias and field schema maps.  Populate the field
        // list with copies of the existing field schemas.
        Map<FieldSchema, FieldSchema> fsMap =
            new HashMap<FieldSchema, FieldSchema>(size());
        for (FieldSchema fs : mFields) {
            FieldSchema copy = fs.clone();
            s.mFields.add(copy);
            fsMap.put(fs, copy);
        }

        // Build the aliases map
        for (String alias : mAliases.keySet()) {
            FieldSchema oldFs = mAliases.get(alias);
            assert(oldFs != null);
            FieldSchema newFs = fsMap.get(oldFs);
            assert(newFs != null);
            s.mAliases.put(alias, newFs);
        }

        // Build the field schemas map
        for (FieldSchema oldFs : mFieldSchemas.keySet()) {
            FieldSchema newFs = fsMap.get(oldFs);
            assert(newFs != null);
            s.mFieldSchemas.put(newFs, mFieldSchemas.get(oldFs));
        }

        return s;
    }



    static int[] primeList = { 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37,
                               41, 43, 47, 53, 59, 61, 67, 71, 73, 79,
                               83, 89, 97, 101, 103, 107, 109, 1133} ;

    @Override
    public int hashCode() {
        int idx = 0 ;
        int hashCode = 0 ;
        for(FieldSchema fs: this.mFields) {
            hashCode += fs.hashCode() * (primeList[idx % primeList.length]) ;
            idx++ ;
        }
        return hashCode ;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        try {
            stringifySchema(sb, this, DataType.BAG) ;
        }
        catch (ParseException pe) {
            throw new RuntimeException("PROBLEM PRINTING SCHEMA")  ;
        }
        return sb.toString();
    }


    // This is used for building up output string
    // type can only be BAG or TUPLE
    public static void stringifySchema(StringBuilder sb,
                                       Schema schema,
                                       byte type)
                                            throws ParseException{

        if (type == DataType.TUPLE) {
            sb.append("(") ;
        }
        else if (type == DataType.BAG) {
            sb.append("{") ;
        }
        // TODO: Map Support

        if (schema == null) {
            sb.append("null") ;
        }
        else {
            boolean isFirst = true ;
            for (int i=0; i< schema.size() ;i++) {

                if (!isFirst) {
                    sb.append(",") ;
                }
                else {
                    isFirst = false ;
                }

                FieldSchema fs = schema.getField(i) ;

                if (fs.alias != null) {
                    sb.append(fs.alias);
                    sb.append(": ");
                }

                if (DataType.isAtomic(fs.type)) {
                    sb.append(DataType.findTypeName(fs.type)) ;
                }
                else if ( (fs.type == DataType.TUPLE) ||
                          (fs.type == DataType.BAG) ) {
                    // safety net
                    if (schema != fs.schema) {
                        stringifySchema(sb, fs.schema, fs.type) ;
                    }
                    else {
                        throw new AssertionError("Schema refers to itself "
                                                 + "as inner schema") ;
                    }
                } else if (fs.type == DataType.MAP) {
                    sb.append(DataType.findTypeName(fs.type) + "[ ]") ;
                }
                // TODO: Support Map
            }
        }

        if (type == DataType.TUPLE) {
            sb.append(")") ;
        }
        else if (type == DataType.BAG) {
            sb.append("}") ;
        }

    }

    public void add(FieldSchema f) {
        mFields.add(f);
        if (null != f.alias) {
            mAliases.put(f.alias, f);
        }
    }

    /**
     * Given an alias, find the associated position of the field schema.
     *
     * @param alias
     *            alias of the FieldSchema.
     * @return position of the FieldSchema.
     */
    public int getPosition(String alias) throws FrontendException{

        FieldSchema fs = getField(alias);

        if (null == fs) {
            return -1;
        }

        log.debug("fs: " + fs);
        int index = -1;
        for(int i = 0; i < mFields.size(); ++i) {
            log.debug("mFields(" + i + "): " + mFields.get(i) + " alias: " + mFields.get(i).alias);
            if(fs == mFields.get(i)) {index = i;}
        }

        log.debug("index: " + index);
        return index;
        //return mFields.indexOf(fs);
    }

    public void addAlias(String alias, FieldSchema fs) {
        if(null != alias) {
            mAliases.put(alias, fs);
            if(null != fs) {
                mFieldSchemas.put(fs, alias);
            }
        }
    }

    public Set<String> getAliases() {
        return mAliases.keySet();
    }

    public void printAliases() {
        Set<String> aliasNames = mAliases.keySet();
        for (String alias : aliasNames) {
            log.debug("Schema Alias: " + alias);
        }
    }

    public List<FieldSchema> getFields() {
        return mFields;
    }

    /**
     * Recursively compare two schemas to check if the input schema 
     * can be cast to the cast schema
     * @param cast schema of the cast operator
     * @param  input schema of the cast input
     * @return true or falsew!
     */
    public static boolean castable(Schema cast, Schema input) {

        // If both of them are null, they are castable
        if ((cast == null) && (input == null)) {
            return false ;
        }

        // otherwise
        if (cast == null) {
            return false ;
        }

        if (input == null) {
            return false ;
        }

        if (cast.size() > input.size()) return false;

        Iterator<FieldSchema> i = cast.mFields.iterator();
        Iterator<FieldSchema> j = input.mFields.iterator();

        while (i.hasNext()) {
        //iterate only for the number of fields in cast

            FieldSchema castFs = i.next() ;
            FieldSchema inputFs = j.next() ;

            // Compare recursively using field schema
            if (!FieldSchema.castable(castFs, inputFs)) {
                return false ;
            }

        }
        return true;
    }

    /**
     * Recursively compare two schemas for equality
     * @param schema
     * @param other
     * @param relaxInner if true, inner schemas will not be checked
     * @param relaxAlias if true, aliases will not be checked
     * @return true if schemas are equal, false otherwise
     */
    public static boolean equals(Schema schema,
                                 Schema other,
                                 boolean relaxInner,
                                 boolean relaxAlias) {

        // If both of them are null, they are equal
        if ((schema == null) && (other == null)) {
            return true ;
        }

        // otherwise
        if (schema == null) {
            return false ;
        }

        if (other == null) {
            return false ;
        }

        if (schema.size() != other.size()) return false;

        Iterator<FieldSchema> i = schema.mFields.iterator();
        Iterator<FieldSchema> j = other.mFields.iterator();

        while (i.hasNext()) {

            FieldSchema myFs = i.next() ;
            FieldSchema otherFs = j.next() ;

            if (!relaxAlias) {
                if ( (myFs.alias == null) &&
                     (otherFs.alias == null) ) {
                    // good
                }
                else if ( (myFs.alias != null) &&
                     (otherFs.alias == null) ) {
                    return false ;
                }
                else if ( (myFs.alias == null) && 
                     (otherFs.alias != null) ) {
                    return false ;
                }
                else if (!myFs.alias.equals(otherFs.alias)) {
                    return false ;
                }
            }

            if (myFs.type != otherFs.type) {
                return false ;
            }

            if (!relaxInner) {
                // Compare recursively using field schema
                if (!FieldSchema.equals(myFs, otherFs, false, relaxAlias)) {
                    return false ;
                }
            }

        }
        return true;
    }


    /***
     * Merge this schema with the other schema
     * @param other the other schema to be merged with
     * @param otherTakesAliasPrecedence true if aliases from the other
     *                                  schema take precedence
     * @return the merged schema, null if they are not compatible
     */
    public Schema merge(Schema other, boolean otherTakesAliasPrecedence) {
        return mergeSchema(this, other, otherTakesAliasPrecedence) ;
    }

    /***
     * Recursively merge two schemas
     * @param schema the initial schema
     * @param other the other schema to be merged with
     * @param otherTakesAliasPrecedence true if aliases from the other
     *                                  schema take precedence
     * @return the merged schema, null if they are not compatible
     */
    public static Schema mergeSchema(Schema schema, Schema other,
                               boolean otherTakesAliasPrecedence) {
        try {
            Schema newSchema = mergeSchema(schema,
                                        other,
                                        otherTakesAliasPrecedence,
                                        false,
                                        false) ;
            return newSchema;
        }
        catch(SchemaMergeException sme) {
            // just mean they are not compatible
        }
        return null ;
    }

    /***
     * Recursively merge two schemas
     * @param schema the initial schema
     * @param other the other schema to be merged with
     * @param otherTakesAliasPrecedence true if aliases from the other
     *                                  schema take precedence
     * @param allowDifferentSizeMerge allow merging of schemas of different types
     * @param allowIncompatibleTypes 1) if types in schemas are not compatible
     *                               they will be treated as ByteArray (untyped)
     *                               2) if schemas in schemas are not compatible
     *                               and allowIncompatibleTypes is true
     *                               those inner schemas in the output
     *                               will be null.
     * @return the merged schema this can be null if one schema is null and
     *         allowIncompatibleTypes is true
     *
     * @throws SchemaMergeException if they cannot be merged
     */

    public static Schema mergeSchema(Schema schema,
                               Schema other,
                               boolean otherTakesAliasPrecedence,
                               boolean allowDifferentSizeMerge,
                               boolean allowIncompatibleTypes)
                                    throws SchemaMergeException {
        if (schema == null) {
            if (allowIncompatibleTypes) {
                return null ;
            }
            else {
                throw new SchemaMergeException("One schema is null") ;
            }
        }

        if (other == null) {
            if (allowIncompatibleTypes) {
                return null ;
            }
            else {
                throw new SchemaMergeException("One schema is null") ;
            }
        }

        if ( (schema.size() != other.size()) &&
             (!allowDifferentSizeMerge) ) {
            throw new SchemaMergeException("Different schema size") ;
        }

        List<FieldSchema> outputList = new ArrayList<FieldSchema>() ;

        List<FieldSchema> mylist = schema.mFields ;
        List<FieldSchema> otherlist = other.mFields ;

        // We iterate up to the smaller one's size
        int iterateLimit = schema.mFields.size() > other.mFields.size()?
                            other.mFields.size() : schema.mFields.size() ;

        int idx = 0;
        for (; idx< iterateLimit ; idx ++) {

            // Just for readability
            FieldSchema myFs = mylist.get(idx) ;
            FieldSchema otherFs = otherlist.get(idx) ;

            byte mergedType = DataType.mergeType(myFs.type, otherFs.type) ;

            // If the types cannot be merged
            if (mergedType == DataType.ERROR) {
                // If  treatIncompatibleAsByteArray is true,
                // we will treat it as bytearray
                if (allowIncompatibleTypes) {
                    mergedType = DataType.BYTEARRAY ;
                }
                // otherwise the schemas cannot be merged
                else {
                    throw new SchemaMergeException("Incompatible types") ;
                }
            }

            String mergedAlias = mergeAlias(myFs.alias,
                                            otherFs.alias,
                                            otherTakesAliasPrecedence) ;

            FieldSchema mergedFs = null ;
            if (!DataType.isSchemaType(mergedType)) {
                // just normal merge
                mergedFs = new FieldSchema(mergedAlias, mergedType) ;
            }
            else {
                // merge inner tuple because both sides are tuples
                Schema mergedSubSchema = mergeSchema(myFs.schema,
                                                     otherFs.schema,
                                                     otherTakesAliasPrecedence,
                                                     allowDifferentSizeMerge,
                                                     allowIncompatibleTypes) ;
                // if they cannot be merged and we don't allow incompatible
                // types, just return null meaning cannot merge
                if ( (mergedSubSchema == null) &&
                     (!allowIncompatibleTypes) ) {
                    throw new SchemaMergeException("Incompatible inner schemas") ;
                }

                // create the merged field
                // the mergedSubSchema can be true if allowIncompatibleTypes
                mergedFs = new FieldSchema(mergedAlias, mergedSubSchema) ;

            }
            outputList.add(mergedFs) ;
        }

        // Handle different schema size
        if (allowDifferentSizeMerge) {
            
            // if the first schema has leftover, then append the rest
            for(int i=idx; i < mylist.size(); i++) {

                FieldSchema fs = mylist.get(i) ;

                // for non-schema types
                if (!DataType.isSchemaType(fs.type)) {
                    outputList.add(new FieldSchema(fs.alias, fs.type)) ;
                }
                // for TUPLE & BAG
                else {
                    FieldSchema tmp = new FieldSchema(fs.alias, fs.schema) ;
                    tmp.type = fs.type ;
                    outputList.add(tmp) ;
                }
            }

             // if the second schema has leftover, then append the rest
            for(int i=idx; i < otherlist.size(); i++) {

                FieldSchema fs = otherlist.get(i) ;

                // for non-schema types
                if (!DataType.isSchemaType(fs.type)) {
                    outputList.add(new FieldSchema(fs.alias, fs.type)) ;
                }
                // for TUPLE & BAG
                else {
                    FieldSchema tmp = new FieldSchema(fs.alias, fs.schema) ;
                    tmp.type = fs.type ;
                    outputList.add(tmp) ;
                }
            }

        }

        return new Schema(outputList) ;
    }

    /***
     * Merge two aliases. If one of aliases is null, return the other.
     * Otherwise check the precedence condition
     * @param alias
     * @param other
     * @param otherTakesPrecedence
     * @return
     */
    private static String mergeAlias(String alias, String other
                              ,boolean otherTakesPrecedence) {
        if (alias == null) {
            return other ;
        }
        else if (other == null) {
            return alias ;
        }
        else if (otherTakesPrecedence) {
            return other ;
        }
        else {
            return alias ;
        }
    }
    
    /**
     * 
     * @param topLevelType DataType type of the top level element
     * @param innerTypes DataType types of the inner level element
     * @return nested schema representing type of top level element at first level and inner schema
	 * representing types of inner element(s)
     */
    public static Schema generateNestedSchema(byte topLevelType, byte... innerTypes) throws FrontendException{
        
        Schema innerSchema = new Schema();
        for (int i = 0; i < innerTypes.length; i++) {
            innerSchema.add(new Schema.FieldSchema(null, innerTypes[i]));
        }
        
        Schema.FieldSchema outerSchema = new Schema.FieldSchema(null, innerSchema, topLevelType);
        return new Schema(outerSchema);
    }

    /***
     * Recursively prefix merge two schemas
     * @param other the other schema to be merged with
     * @param otherTakesAliasPrecedence true if aliases from the other
     *                                  schema take precedence
     * @return the prefix merged schema this can be null if one schema is null and
     *         allowIncompatibleTypes is true
     *
     * @throws SchemaMergeException if they cannot be merged
     */

    public Schema mergePrefixSchema(Schema other,
                               boolean otherTakesAliasPrecedence)
                                    throws SchemaMergeException {
        Schema schema = this;

        if (other == null) {
                return this ;
        }

        if (schema.size() < other.size()) {
            throw new SchemaMergeException("Schema size mismatch. Other schema size greater than schema size. Schema: " + this + ". Other schema: " + other);
        }

        List<FieldSchema> outputList = new ArrayList<FieldSchema>() ;

        List<FieldSchema> mylist = schema.mFields ;
        List<FieldSchema> otherlist = other.mFields ;

        // We iterate up to the smaller one's size
        int iterateLimit = other.mFields.size();

        int idx = 0;
        for (; idx< iterateLimit ; idx ++) {

            // Just for readability
            FieldSchema myFs = mylist.get(idx) ;
            FieldSchema otherFs = otherlist.get(idx) ;

            FieldSchema mergedFs = myFs.mergePrefixFieldSchema(otherFs, otherTakesAliasPrecedence);
            outputList.add(mergedFs) ;
        }
        // if the first schema has leftover, then append the rest
        for(int i=idx; i < mylist.size(); i++) {

            FieldSchema fs = mylist.get(i) ;

            // for non-schema types
            if (!DataType.isSchemaType(fs.type)) {
                outputList.add(new FieldSchema(fs.alias, fs.type)) ;
            }
            // for TUPLE & BAG
            else {
                try {
                    FieldSchema tmp = new FieldSchema(fs.alias, fs.schema, fs.type) ;
                    outputList.add(tmp) ;
                } catch (FrontendException fee) {
                    throw new SchemaMergeException(fee.getMessage());
                }
            }
        }

        return new Schema(outputList) ;
    }

}



