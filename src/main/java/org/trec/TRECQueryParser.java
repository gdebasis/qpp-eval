/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.trec;

/**
 *
 * @author Debasis
 */
import java.io.FileReader;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import javax.xml.parsers.*;
import java.util.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.Query;
import org.experiments.QPPEvaluator;

public class TRECQueryParser extends DefaultHandler {
    StringBuffer        buff;      // Accumulation buffer for storing the current topic
    String              fileName;
    TRECQuery           query;
    Analyzer            analyzer;
    StandardQueryParser queryParser;
    QPPEvaluator retriever;

    static final String CONTENT_FIELD = "words";
    
    public List<TRECQuery>  queries;

    public TRECQueryParser(QPPEvaluator retriever, String fileName, Analyzer analyzer) throws SAXException {
        this.retriever = retriever;
        this.fileName = fileName;
        this.analyzer = analyzer;
        buff = new StringBuffer();
        queries = new ArrayList<>();
        queryParser = new StandardQueryParser(analyzer);
    }
    
    public TRECQueryParser(String fileName, Analyzer analyzer) throws SAXException {
        this(null, fileName, analyzer);
    }
    
    public StandardQueryParser getQueryParser() { return queryParser; }
    
    public void parse() throws Exception {
        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
        saxParserFactory.setValidating(false);
        SAXParser saxParser = saxParserFactory.newSAXParser();
        saxParser.parse(fileName, this);
    }

    public List<TRECQuery> getQueries() { return queries; }
    
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        try {
            if (qName.equalsIgnoreCase("top")) {
                query = new TRECQuery();
                queries.add(query);
            }
            else
                buff = new StringBuffer();
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
    
    public Query constructLuceneQueryObj(TRECQuery trecQuery) throws QueryNodeException {        
        String contentFiledName = retriever==null? CONTENT_FIELD: retriever.getContentFieldName();
        Query luceneQuery = queryParser.parse(trecQuery.title.replaceAll("/", " ")
            .replaceAll("\\?", " ").replaceAll("\"", " ").replaceAll("\\&", " "), contentFiledName);
        trecQuery.luceneQuery = luceneQuery;
        return luceneQuery;
    }

    static public Query constructLuceneQueryObj(TRECQuery trecQuery, String tdn, String contentFieldName) throws QueryNodeException {
        String content = trecQuery.title;

        if (tdn.equals("t")) {
            content = trecQuery.title;
        }
        else if (tdn.equals("td")) {
            content = trecQuery.title + " " + trecQuery.desc;
        }
        else if (tdn.equals("tdn")) {
            content = trecQuery.title + " " + trecQuery.desc + " " + trecQuery.narr;
        }

        Query luceneQuery = new StandardQueryParser(QPPEvaluator.englishAnalyzerWithSmartStopwords())
                .parse(content
                    .replaceAll("/", " ")
                    .replaceAll("\\?", " ")
                    .replaceAll("\"", " ")
                    .replaceAll("\\&", " "),
                contentFieldName
        );
        trecQuery.luceneQuery = luceneQuery;
        return luceneQuery;
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        try {
            if (qName.equalsIgnoreCase("title"))
                query.title = buff.toString();            
            else if (qName.equalsIgnoreCase("desc"))
                query.desc = buff.toString();
            else if (qName.equalsIgnoreCase("narr"))
                query.narr = buff.toString();
            else if (qName.equalsIgnoreCase("num"))
                query.id = buff.toString().trim();
            else if (qName.equalsIgnoreCase("top"))
                query.luceneQuery = constructLuceneQueryObj(query);            
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
    
    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        buff.append(new String(ch, start, length));
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "init.properties";
        }

        try {
            Properties prop = new Properties();
            prop.load(new FileReader(args[0]));
            String queryFile = prop.getProperty("query.file");
            
            TRECQueryParser parser = new TRECQueryParser(queryFile, new EnglishAnalyzer());
            parser.parse();
            for (TRECQuery q : parser.queries) {
                System.out.println(q);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}    
