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
package org.apache.pig.data;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.hadoop.io.WritableComparable;

/**
 * an ordered list of Datums
 */
public class Tuple extends Datum implements WritableComparable {
    protected ArrayList<Datum> fields;
    static String              defaultDelimiter = "[,\t]";
    static String              NULL = "__PIG_NULL__";

    public Tuple() {
        this(0);
    }

    public Tuple(int numFields) {
        fields = new ArrayList<Datum>(numFields);
        for (int i = 0; i < numFields; i++) {
            fields.add(null);
        }
    }

    public Tuple(List<Datum> fieldsIn) {
        fields = new ArrayList<Datum>(fieldsIn.size());
        fields.addAll(fieldsIn);
    }
    
    /**
     * shortcut, if tuple only has one field
     */
    public Tuple(Datum fieldIn) {
        fields = new ArrayList<Datum>(1);
        fields.add(fieldIn);
    }

    /**
     * Creates a tuple from a delimited line of text
     * 
     * @param textLine
     *            the line containing fields of data
     * @param delimiter
     *            a regular expression of the form specified by String.split(). If null, the default
     *            delimiter "[,\t]" will be used.
     */
    public Tuple(String textLine, String delimiter) {
        if (delimiter == null) {
            delimiter = defaultDelimiter;
        }
        String[] splitString = textLine.split(delimiter, -1);
        fields = new ArrayList<Datum>(splitString.length);
        for (int i = 0; i < splitString.length; i++) {
            fields.add(new DataAtom(splitString[i]));
        }
    }

    /**
     * Creates a tuple from a delimited line of text. This will invoke Tuple(textLine, null)
     * 
     * @param textLine
     *            the line containing fields of data
     */
    public Tuple(String textLine) {
        this(textLine, defaultDelimiter);
    }

    public Tuple(Tuple[] otherTs) {
        fields = new ArrayList<Datum>(otherTs.length);
        for (int i = 0; i < otherTs.length; i++) {
                appendTuple(otherTs[i]);
        }
    }

    public void copyFrom(Tuple otherT) {
        this.fields = otherT.fields;
    }

    public int arity() {
        return fields.size();
    }

    @Override
	public String toString() {
    	StringBuffer sb = new StringBuffer();
        sb.append('(');
        for (Iterator<Datum> it = fields.iterator(); it.hasNext();) {
            Datum d = it.next();
            if(d != null) {
                sb.append(d.toString());
            } else {
                sb.append(NULL);
            }
            if (it.hasNext())
                sb.append(", ");
        }
        sb.append(')');
        String s = sb.toString();
        return s;
    }

    public void setField(int i, Datum val) throws IOException {
        getField(i); // throws exception if field doesn't exist

        fields.set(i, val);
    }

    public void setField(int i, int val) throws IOException {
        setField(i, new DataAtom(val));
    }

    public void setField(int i, double val) throws IOException {
        setField(i, new DataAtom(val));
    }

    public void setField(int i, String val) throws IOException {
        setField(i, new DataAtom(val));
    }

    public Datum getField(int i) throws IOException {
        if (fields.size() >= i + 1)
            return fields.get(i);
        else
            throw new IOException("Column number out of range: " + i + " -- " + toString());
    }

    // Get field i, if it is an Atom or can be coerced into an Atom
    public DataAtom getAtomField(int i) throws IOException {
        Datum field = getField(i); // throws exception if field doesn't exist

        if (field instanceof DataAtom) {
            return (DataAtom) field;
        } else if (field instanceof Tuple) {
            Tuple t = (Tuple) field;
            if (t.arity() == 1) {
            	System.err.println("Warning: Asked for an atom field but found a tuple with one field.");
                return t.getAtomField(0);
            }
        } else if (field instanceof DataBag) {
            DataBag b = (DataBag) field;
            if (b.cardinality() == 1) {
                Tuple t = b.content().next();
                if (t.arity() == 1) {
                    return t.getAtomField(0);
                }
            }
        }

        throw new IOException("Incompatible type for request getAtomField().");
    }

    // Get field i, if it is a Tuple or can be coerced into a Tuple
    public Tuple getTupleField(int i) throws IOException {
        Datum field = getField(i); // throws exception if field doesn't exist

        if (field instanceof Tuple) {
            return (Tuple) field;
        } else if (field instanceof DataBag) {
            DataBag b = (DataBag) field;
            if (b.cardinality() == 1) {
                return b.content().next();
            }
        }

        throw new IOException("Incompatible type for request getTupleField().");
    }

    // Get field i, if it is a Bag or can be coerced into a Bag
    public DataBag getBagField(int i) throws IOException {
        Datum field = getField(i); // throws exception if field doesn't exist

        if (field instanceof DataBag) {
            return (DataBag) field;
        }

        throw new IOException("Incompatible type for request getBagField().");
    }

    public void appendTuple(Tuple other){
        for (Iterator<Datum> it = other.fields.iterator(); it.hasNext();) {
            this.fields.add(it.next());
        }
    }

    public void appendField(Datum newField){
        this.fields.add(newField);
    }

    public String toDelimitedString(String delim) throws IOException {
        StringBuffer buf = new StringBuffer();
        for (Iterator<Datum> it = fields.iterator(); it.hasNext();) {
            Datum field = it.next();
            if (!(field instanceof DataAtom)) {
                throw new IOException("Unable to convert non-flat tuple to string.");
            }

            buf.append((DataAtom) field);
            if (it.hasNext())
                buf.append(delim);
        }
        return buf.toString();
    }

    public boolean lessThan(Tuple other) {
        return (this.compareTo(other) < 0);
    }

    public boolean greaterThan(Tuple other) {
        return (this.compareTo(other) > 0);
    }
    
    @Override
	public boolean equals(Object other){
    		return compareTo(other)==0;
    }	
    
    public int compareTo(Tuple other) {
        if (other.fields.size() != this.fields.size())
            return other.fields.size() < this.fields.size() ? 1 : -1;

        for (int i = 0; i < this.fields.size(); i++) {
            int c = this.fields.get(i).compareTo(other.fields.get(i));
            if (c != 0)
                return c;
        }
        return 0;
    }

    public int compareTo(Object other) {
        if (other instanceof DataAtom)
            return +1;
        else if (other instanceof DataBag)
            return +1;
        else if (other instanceof Tuple)
            return compareTo((Tuple) other);
        else
        	return -1;
    }

    @Override
	public int hashCode() {
        int hash = 1;
        for (Iterator<Datum> it = fields.iterator(); it.hasNext();) {
            hash = 31 * hash + it.next().hashCode();
        }
        return hash;
    }

    // WritableComparable methods:
   
    @Override
	public void write(DataOutput out) throws IOException {
        out.write(TUPLE);
        int n = arity();
        encodeInt(out, n);
        for (int i = 0; i < n; i++) {
        	Datum d = getField(i);
        	if (d!=null){
        		d.write(out);
        	}else{
        		throw new RuntimeException("Null field in tuple");
        	}
        }
    }

    //This method is invoked when the beginning 'TUPLE' is still on the stream
    public void readFields(DataInput in) throws IOException {
    	byte[] b = new byte[1];
        in.readFully(b);
        if (b[0]!=TUPLE)
        	throw new IOException("Unexpected data while reading tuple from binary file");
    	Tuple t = read(in);
    	fields = t.fields;
    }
    
    //This method is invoked when the beginning 'TUPLE' has been read off the stream
    public static Tuple read(DataInput in) throws IOException {
        // nuke the old contents of the tuple
        Tuple ret = new Tuple();
    	ret.fields = new ArrayList<Datum>();

    	int size = decodeInt(in);
        
        for (int i = 0; i < size; i++) {
            ret.appendField(readDatum(in));
        }
        
        return ret;

    }
    
    public static Datum readDatum(DataInput in) throws IOException{
    	byte[] b = new byte[1];
    	in.readFully(b);
    	switch (b[0]) {
	        case TUPLE:
	            return Tuple.read(in);
	        case BAG:
	        	return DataBag.read(in);
	        case MAP:
	        	return DataMap.read(in);
	        case ATOM:
	            return DataAtom.read(in);
	        default:
	        	throw new IOException("Invalid data while reading Datum from binary file");
    	}
    }

    //  Encode the integer so that the high bit is set on the last
    // byte
    static void encodeInt(DataOutput os, int i) throws IOException {
        if (i >> 28 != 0)
            os.write((i >> 28) & 0x7f);
        if (i >> 21 != 0)
            os.write((i >> 21) & 0x7f);
        if (i >> 14 != 0)
            os.write((i >> 14) & 0x7f);
        if (i >> 7 != 0)
            os.write((i >> 7) & 0x7f);
        os.write((i & 0x7f) | (1 << 7));
    }

    static int decodeInt(DataInput is) throws IOException {
        int i = 0;
        int c;
        while (true) {
            c = is.readUnsignedByte();
            if (c == -1)
                break;
            i <<= 7;
            i += c & 0x7f;
            if ((c & 0x80) != 0)
                break;
        }
        return i;
    }
}
