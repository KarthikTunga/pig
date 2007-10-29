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
package org.apache.pig.builtin;

import java.io.DataInputStream;
import java.io.IOException;

import org.apache.pig.LoadFunc;
import org.apache.pig.data.DataAtom;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.io.BufferedPositionedInputStream;


/**
 * This load function simply creates a tuple for each line of text that has a single field that
 * contains the line of text.
 */
public class TextLoader implements LoadFunc{
	BufferedPositionedInputStream in;
	private DataInputStream inData = null;
    
	long                end;

	public void bindTo(String fileName, BufferedPositionedInputStream in, long offset, long end) throws IOException {
        this.in = in;
        inData = new DataInputStream(in);
        this.end = end;
        // Since we are not block aligned we throw away the first
        // record and cound on a different instance to read it
        if (offset != 0)
            getNext();
    }

	public Tuple getNext() throws IOException {
        if (in == null || in.getPosition() > end)
            return null;
        String line;
        if ((line = inData.readLine()) != null) {
            Tuple t = new Tuple(1);
            t.setField(0, new DataAtom(line));
            return t;
        }
        return null;
    }

}
