/*

Copyright 2010, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
    * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,           
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY           
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package com.google.refine.importers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import au.com.bytecode.opencsv.CSVParser;

import com.google.refine.ProjectMetadata;
import com.google.refine.importing.ImportingJob;
import com.google.refine.importing.ImportingUtilities;
import com.google.refine.model.Project;
import com.google.refine.util.JSONUtilities;

public class SeparatorBasedImporter extends TabularImportingParserBase {
    public SeparatorBasedImporter() {
        super(false);
    }
    
    @Override
    public JSONObject createParserUIInitializationData(ImportingJob job,
            List<JSONObject> fileRecords, String format) {
        JSONObject options = super.createParserUIInitializationData(job, fileRecords, format);
        
        JSONUtilities.safePut(options, "lineSeparator", "\n");
        
        String separator = guessSeparator(job, fileRecords);
        JSONUtilities.safePut(options, "separator", separator != null ? separator : "\t");
        
        JSONUtilities.safePut(options, "guessCellValueTypes", true);
        JSONUtilities.safePut(options, "processQuotes", true);

        return options;
    }
    
    @Override
    public void parseOneFile(
        Project project,
        ProjectMetadata metadata,
        ImportingJob job,
        String fileSource,
        Reader reader,
        int limit,
        JSONObject options,
        List<Exception> exceptions
    ) {
        // String lineSeparator = JSONUtilities.getString(options, "lineSeparator", "\n");
        String sep = JSONUtilities.getString(options, "separator", "\t");
        boolean processQuotes = JSONUtilities.getBoolean(options, "processQuotes", true);
        
        final CSVParser parser = new CSVParser(
            sep.toCharArray()[0],//HACK changing string to char - won't work for multi-char separators.
            CSVParser.DEFAULT_QUOTE_CHARACTER,
            (char) 0, // escape character
            CSVParser.DEFAULT_STRICT_QUOTES,
            CSVParser.DEFAULT_IGNORE_LEADING_WHITESPACE,
            !processQuotes);
        
        final LineNumberReader lnReader = new LineNumberReader(reader);
        
        TableDataReader dataReader = new TableDataReader() {
            long bytesRead = 0;
            
            @Override
            public List<Object> getNextRowOfCells() throws IOException {
                String line = lnReader.readLine();
                if (line == null) {
                    return null;
                } else {
                    bytesRead += line.length();
                    return getCells(line, parser, lnReader);
                }
            }
        };
        
        readTable(project, metadata, job, dataReader, fileSource, limit, options, exceptions);
    }
    
    static protected ArrayList<Object> getCells(String line, CSVParser parser, LineNumberReader lnReader)
        throws IOException{
        
        ArrayList<Object> cells = new ArrayList<Object>();
        String[] tokens = parser.parseLineMulti(line);
        for (String s : tokens){
            cells.add(s);
        }
        while (parser.isPending()) {
            tokens = parser.parseLineMulti(lnReader.readLine());
            for (String s : tokens) {
                cells.add(s);
            }
        }
        return cells;
    }
    
    static public String guessSeparator(ImportingJob job, List<JSONObject> fileRecords) {
        for (int i = 0; i < 5 && i < fileRecords.size(); i++) {
            JSONObject fileRecord = fileRecords.get(i);
            String encoding = ImportingUtilities.getEncoding(fileRecord);
            String location = JSONUtilities.getString(fileRecord, "location", null);
            
            if (location != null) {
                File file = new File(job.getRawDataDir(), location);
                Separator separator = guessSeparator(file, encoding);
                if (separator != null) {
                    return Character.toString(separator.separator);
                }
            }
        }
        return null;
    }
    
    static public class Separator {
        char separator;
        int totalCount = 0;
        int totalOfSquaredCount = 0;
        int currentLineCount = 0;
        
        double averagePerLine;
        double stddev;
    }
    
    static public Separator guessSeparator(File file, String encoding) {
        try {
            InputStream is = new FileInputStream(file);
            try {
                Reader reader = encoding != null ? new InputStreamReader(is, encoding) : new InputStreamReader(is);
                LineNumberReader lineNumberReader = new LineNumberReader(reader);
                
                List<Separator> separators = new ArrayList<SeparatorBasedImporter.Separator>();
                Map<Character, Separator> separatorMap = new HashMap<Character, SeparatorBasedImporter.Separator>();
                
                int totalBytes = 0;
                int lineCount = 0;
                String s;
                while (totalBytes < 64 * 1024 &&
                       lineCount < 100 &&
                       (s = lineNumberReader.readLine()) != null) {
                    
                    totalBytes += s.length() + 1; // count the new line character
                    if (s.length() == 0) {
                        continue;
                    }
                    lineCount++;
                    
                    for (int i = 0; i < s.length(); i++) {
                        char c = s.charAt(i);
                        if (!Character.isLetterOrDigit(c) &&
                            !"\"' .-".contains(s.subSequence(i, i + 1))) {
                            Separator separator = separatorMap.get(c);
                            if (separator == null) {
                                separator = new Separator();
                                separator.separator = c;
                                
                                separatorMap.put(c, separator);
                                separators.add(separator);
                            }
                            separator.currentLineCount++;
                        }
                    }
                    
                    for (Separator separator : separators) {
                        separator.totalCount += separator.currentLineCount;
                        separator.totalOfSquaredCount += separator.currentLineCount * separator.currentLineCount;
                        separator.currentLineCount = 0;
                    }
                }
                
                if (separators.size() > 0) {
                    for (Separator separator : separators) {
                        separator.averagePerLine = separator.totalCount / (double) lineCount;
                        separator.stddev = Math.sqrt(
                            separator.totalOfSquaredCount / (double) lineCount -
                            separator.averagePerLine * separator.averagePerLine);
                    }
                    
                    Collections.sort(separators, new Comparator<Separator>() {
                        @Override
                        public int compare(Separator sep0, Separator sep1) {
                            return Double.compare(sep0.stddev, sep1.stddev);
                        }
                    });
                    for (Separator separator : separators) {
                        if (separator.stddev / separator.averagePerLine < 0.1) {
                            return separator;
                        }
                    }
                }
            } finally {
                is.close();
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
