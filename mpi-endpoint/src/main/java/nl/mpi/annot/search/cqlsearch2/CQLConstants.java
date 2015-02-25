/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.mpi.annot.search.cqlsearch2;

/**
 * The collection of constants, e.g. standard prefixes or url-s, used throughout this endpoint implementation.
 * Consider moving these constants to web.xml or so to avoid recompilation once some of the constants is changed.
 * @author olhsha
 * 
 */
public class CQLConstants {
    
    public static final String HANDLE_HDL = "hdl:";
    public static final String X_FCS_ENDPOINT_DESCRIPTION ="x-fcs-endpoint-description";
    public static final String X_FCS_CONTEXT ="x-fcs-context";
    public static final String X_FCS_DATAVIEWS ="x-fcs-dataviews";
    public static final String hits_type ="application/x-clarin-fcs-hits+xml";
    public static final String hits ="hits";
    public static final String send_by_default ="send-by-default";
    public static final String need_to_request ="need-to-request";
    
    
    
    public static final char LOZENGE = (char) (Integer.parseInt("2666", 16)); // black lozenge (diamondsuit)
    public static final char SPANISHQUESTION = (char) (Integer.parseInt("191"));
    public static final char SPANISHBANG = (char) (Integer.parseInt("161"));
    public static final String SEPARATORS = "[ \\[\\].,!?\"#$%&()*+/<>=:;^_~{}|''`-]";
    
    private static final String FCS_DIAGNOSTIC_URI_PREFIX = "http://clarin.eu/fcs/diagnostic/";
    public static final String FCS_DIAGNOSTIC_PERSISTENT_IDENTIFIER_INVALID =
            FCS_DIAGNOSTIC_URI_PREFIX + 1;
    public static final String FCS_DIAGNOSTIC_RESOURCE_TOO_LARGE_CONTEXT_ADJUSTED =
            FCS_DIAGNOSTIC_URI_PREFIX + 2;
    public static final String FCS_DIAGNOSTIC_RESOURCE_TOO_LARGE_CANNOT_PERFORM_QUERY =
            FCS_DIAGNOSTIC_URI_PREFIX + 3;
    public static final String FCS_DIAGNOSTIC_REQUESTED_DATA_VIEW_INVALID =
            FCS_DIAGNOSTIC_URI_PREFIX + 4;
   
    
    public static final String ISO_3_PREFIX="ISO639-3:";
    
    public static final String openPathLux16 = "https://lux16.mpi.nl/ds/asv/?openpath=";
    public static final String openPathCorpus1 = "https://corpus1.mpi.nl/ds/asv/?openpath=";
    
}

