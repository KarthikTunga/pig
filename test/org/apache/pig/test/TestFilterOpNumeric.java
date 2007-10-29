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
package org.apache.pig.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Iterator;

import org.junit.Test;

import org.apache.pig.PigServer;
import org.apache.pig.builtin.PigStorage;
import org.apache.pig.data.Tuple;

import junit.framework.TestCase;

public class TestFilterOpNumeric extends TestCase {

    
    private static int LOOP_COUNT = 1024;
    private String initString = "mapreduce";
    
    @Test
    public void testNumericEq() throws Exception {
        PigServer pig = new PigServer(initString);
        File tmpFile = File.createTempFile("test", "txt");
        PrintStream ps = new PrintStream(new FileOutputStream(tmpFile));
        for(int i = 0; i < LOOP_COUNT; i++) {
            if(i % 5 == 0) {
                ps.println(i + ":" + (double)i);
            } else {
                ps.println(i + ":" + (i-1));
            }
        }
        ps.close();
        pig.registerQuery("A=load 'file:" + tmpFile + "' using "+PigStorage.class.getName() +"(':');");
        String query = "A = filter A by $0 == $1;";
        System.out.println(query);
        pig.registerQuery(query);
        Iterator it = pig.openIterator("A");
        tmpFile.delete();
        while(it.hasNext()) {
            Tuple t = (Tuple)it.next();
            Double first = t.getAtomField(0).numval();
            Double second = t.getAtomField(1).numval();
            assertTrue(first.equals(second));
        
            String sfirst = t.getAtomField(0).strval();
            String ssecond = t.getAtomField(1).strval();
            assertFalse(sfirst.equals(ssecond));
        }
    }

    @Test
    public void testNumericNeq() throws Exception {
        PigServer pig = new PigServer(initString);
        File tmpFile = File.createTempFile("test", "txt");
        PrintStream ps = new PrintStream(new FileOutputStream(tmpFile));
        for(int i = 0; i < LOOP_COUNT; i++) {
            if(i % 5 == 0) {
                ps.println("1:1");
            } else {
                ps.println("2:3");
            }
        }
        ps.close();
        pig.registerQuery("A=load 'file:" + tmpFile + "' using " + PigStorage.class.getName() + "(':');");
        String query = "A = filter A by $0 != $1;";
        System.out.println(query);
        pig.registerQuery(query);
        Iterator it = pig.openIterator("A");
        tmpFile.delete();
        while(it.hasNext()) {
            Tuple t = (Tuple)it.next();
            Double first = t.getAtomField(0).numval();
            Double second = t.getAtomField(1).numval();
            assertFalse(first.equals(second));
        }
    }

    @Test
    public void testNumericGt() throws Exception {
        PigServer pig = new PigServer(initString);
        File tmpFile = File.createTempFile("test", "txt");
        PrintStream ps = new PrintStream(new FileOutputStream(tmpFile));
        for(int i = 0; i < LOOP_COUNT; i++) {
            if(i % 5 == 0) {
                ps.println(i + ":" + (double)i);
            } else {
                ps.println(i+1 + ":" + (double)(i));
            }
        }
        ps.close();
        pig.registerQuery("A=load 'file:" + tmpFile + "' using " + PigStorage.class.getName() + "(':');");
        String query = "A = filter A by $0 > $1;";

        System.out.println(query);
        pig.registerQuery(query);
        Iterator it = pig.openIterator("A");
        tmpFile.delete();
        while(it.hasNext()) {
            Tuple t = (Tuple)it.next();
            Double first = t.getAtomField(0).numval();
            Double second = t.getAtomField(1).numval();
            assertTrue(first.compareTo(second) > 0);
        }
    }

    @Test
    public void testBinCond() throws Exception {
        PigServer pig = new PigServer(initString);
        File tmpFile = File.createTempFile("test", "txt");
        PrintStream ps = new PrintStream(new FileOutputStream(tmpFile));
        for(int i = 0; i < LOOP_COUNT; i++) {
            ps.println(i + "\t" + i + "\t1");            
        }
        ps.close();
        pig.registerQuery("A=load 'file:" + tmpFile + "';");
        String query = "A = foreach A generate ($1 >= '"+ LOOP_COUNT+"'-'10'?'1':'0');";
        System.out.println(query);
        pig.registerQuery(query);
        Iterator it = pig.openIterator("A");
        tmpFile.delete();
        int count =0;
        while(it.hasNext()) {
            Tuple t = (Tuple)it.next();
            Double first = t.getAtomField(0).numval();
            if (first == 1)
            	count++;
            else
            	assertTrue(first == 0);
            
        }
        assertTrue(count == 10);
    }
    
    
    @Test
    public void testNestedBinCond() throws Exception {
        PigServer pig = new PigServer(initString);
        File tmpFile = File.createTempFile("test", "txt");
        PrintStream ps = new PrintStream(new FileOutputStream(tmpFile));
        for(int i = 0; i < LOOP_COUNT; i++) {
            ps.println(i + "\t" + i + "\t1");            
        }
        ps.close();
        pig.registerQuery("A=load 'file:" + tmpFile + "';");
        String query = "A = foreach A generate ($0 < '10'?($1 >= '5' ? '2': '1') : '0');";
        System.out.println(query);
        pig.registerQuery(query);
        Iterator it = pig.openIterator("A");
        tmpFile.delete();
        int count =0;
        while(it.hasNext()) {
            Tuple t = (Tuple)it.next();
            Double first = t.getAtomField(0).numval();
            count+=first;
           	assertTrue(first == 1 || first == 2 || first == 0);
            
        }
        assertTrue(count == 15);
    }
    
    
    
    @Test 
    public void testNumericLt() throws Exception {
    	PigServer pig = new PigServer(initString);
    	File tmpFile = File.createTempFile("test", "txt");
    	PrintStream ps = new PrintStream(new FileOutputStream(tmpFile));
        for(int i = 0; i < LOOP_COUNT; i++) {
            if(i % 5 == 0) {
                ps.println(i + ":" + (double)i);
            } else {
                ps.println(i + ":" + (double)(i+1));
            }
        }
        ps.close();
        pig.registerQuery("A=load 'file:" + tmpFile + "' using " + PigStorage.class.getName() + "(':');");
        String query = "A = filter A by $0 < $1;";

        System.out.println(query);
        pig.registerQuery(query);
        Iterator it = pig.openIterator("A");
        tmpFile.delete();
        while(it.hasNext()) {
            Tuple t = (Tuple)it.next();
            Double first = t.getAtomField(0).numval();
            Double second = t.getAtomField(1).numval();
            assertTrue(first.compareTo(second) < 0);
        }
    	
    }

    
    @Test
    public void testNumericGte() throws Exception {
        PigServer pig = new PigServer(initString);
        File tmpFile = File.createTempFile("test", "txt");
        PrintStream ps = new PrintStream(new FileOutputStream(tmpFile));
        for(int i = 0; i < LOOP_COUNT; i++) {
            if(i % 5 == 0) {
                ps.println(i + ":" + (double)i);
            } else if(i % 3 == 0){
                ps.println(i-1 + ":" + (double)(i));
            } else {
                ps.println(i+1 + ":" + (double)(i));
            }
        }
        ps.close();
        pig.registerQuery("A=load 'file:" + tmpFile + "' using " + PigStorage.class.getName() + "(':');");
        String query = "A = filter A by $0 >= $1;";

        System.out.println(query);
        pig.registerQuery(query);
        Iterator it = pig.openIterator("A");
        tmpFile.delete();
        while(it.hasNext()) {
            Tuple t = (Tuple)it.next();
            Double first = t.getAtomField(0).numval();
            Double second = t.getAtomField(1).numval();
            assertTrue(first.compareTo(second) >= 0);
        }
    }

    @Test
    public void testNumericLte() throws Exception {
        PigServer pig = new PigServer(initString);
        File tmpFile = File.createTempFile("test", "txt");
        PrintStream ps = new PrintStream(new FileOutputStream(tmpFile));
        for(int i = 0; i < LOOP_COUNT; i++) {
            if(i % 5 == 0) {
                ps.println(i + ":" + (double)i);
            } else if(i % 3 == 0){
                ps.println(i-1 + ":" + (double)(i));
            } else {
                ps.println(i+1 + ":" + (double)(i));
            }
        }
        ps.close();
        pig.registerQuery("A=load 'file:" + tmpFile + "' using " + PigStorage.class.getName() + "(':');");
        String query = "A = filter A by $0 <= $1;";

        System.out.println(query);
        pig.registerQuery(query);
        Iterator it = pig.openIterator("A");
        tmpFile.delete();
        while(it.hasNext()) {
            Tuple t = (Tuple)it.next();
            Double first = t.getAtomField(0).numval();
            Double second = t.getAtomField(1).numval();
            assertTrue(first.compareTo(second) <= 0);
        }
    }
    
}
