/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.mpi.annot.search.cqlsearch2;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import nl.mpi.corpusstructure.ArchiveObjectsDBImpl;
import nl.mpi.corpusstructure.CorpusStructureDBImpl;
import nl.mpi.corpusstructure.UnknownNodeException;
import nl.mpi.util.OurURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author olhsha
 */
public class CQLHelpers {

    // two fields below are used temporary to support temporary solution for VLO, untill http-accept-compatible facade service is in place
    public static final String formatcmdi = "@format=cmdi";
    private static final int formatcmdLength = formatcmdi.length();
    public static Logger _logger = LoggerFactory.getLogger(CQLHelpers.class);
    
    /**
     * 
     * @param archObjectDBImp reference to the corpus-structure database. Class {@link ArchiveObjectsDBImpl} is a superclass of {@link  CorpusStructureDBImpl}.
     * @param term node handle or node URI.
     * @return the ID of the node with the handle "term" or URI "term" in the database "archObjectDBImpl".
     */
    public static String handleOrURItoNodeId(ArchiveObjectsDBImpl archObjectDBImp, String term) {

        if (term == null) {
            _logger.error("The term " + term + " is null");
            return null;
        }
        String termTrimmed = term.trim();
        String termProcessed = termTrimmed.endsWith(formatcmdi) ? termTrimmed.substring(0, termTrimmed.length() - formatcmdLength) : termTrimmed;
        OurURL url=null;
        try {
            // term is a handle
            url = archObjectDBImp.getObjectURLForPid(termProcessed);
        } catch (UnknownNodeException e) {
            // is not a valid PID (handle), now I'm checking if it is an imdi-uri (for childes and talkbank) 
            try {
                url = new OurURL(URI.create(termProcessed).toURL());
            } catch (MalformedURLException murle) {
                _logger.error("The term " + termProcessed + " is neither a proper nodeId, nor a handle, nor an imdi-URI (for the deployed database)");
                return null;
            }
        }
        return archObjectDBImp.getObjectId(url);

    }
    
    /**
     * Calls  handleOrURItoNodeId(-,-) while iteration the list "termS".
     * 
     * @param archObjectDBImp reference to the corpus-structure database. Class {@link  ArchiveObjectsDBImpl} is a superclass of {@link CorpusstructureDBImpl}.
     * @param termS list of the node handles or URIs.
     * @return the list of node identifiers in the database "archObjectDBImp"  whose handles or URI-s are in the list "termS".
     * 
     * 
     */
    public static ArrayList<String> inListHandleOrURItoNodeId(ArchiveObjectsDBImpl archObjectDBImp, List<String> termS) {
        ArrayList<String> result = new ArrayList<String>(termS.size());
        for (String term : termS) {
            result.add(handleOrURItoNodeId(archObjectDBImp, term));
        }
        return result;
    }
    
    /**
     * 
     * @param archObjectDBImp reference to the corpus-structure database. Class {@link  ArchiveObjectsDBImpl} is a superclass of {@link  CorpusStructureDBImpl}.
     * @param nodeId the id of a node within "archObjectDBImp".
     * 
     * @return the PID of the node with "nodeId" within the database "archObjectDBImp".
     */
    public static String nodeIdToPID(ArchiveObjectsDBImpl archObjectDBImp, String nodeId, String handleResolverURI) {
        return handleResolverURI+"/"+archObjectDBImp.getObjectPID(nodeId);
    }
    
    /**
     * 
     * @param term a string which is supposed to be an iso-3 code of a language.
     * @return removes the first ISO_3_PREFIX-length symbols from "term". It does not check if the term is a correct ISO language code.
     */
    public static String removeISO369_3_prefix(String term) {
        return term.substring(CQLConstants.ISO_3_PREFIX.length());
    }

    /**
     * 
     * @param list a list of strings.
     * @param allowedElements a list of "allowed" strings.
     * @return "true" at least one of the elements of the "list" is not in "allowed", and false otherwise.
     */
    public static boolean isInvalidElementsInTheList(List<String> list, ArrayList<String> allowedElements) {
        if (list == null) {
            return false;
        }
        if (allowedElements == null) {
            return true;
        }
        for (String element : list) {
            if (!allowedElements.contains(element)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 
     * @param db corpus-structure database.
     * @param nodeId the id of a node in the database "db".
     * @return the handle of the node to which "@ view" is appended, or the open path of the node.
     */
    public static String constructHandleView(CorpusStructureDBImpl db, String nodeId, String handleResolverURL) {        
        String host = db.getObjectURI(nodeId).getHost();
        String nodeForOpenPath=(nodeId.replaceAll("MPI", "node:")).replaceAll("#","");
        
        if (host.startsWith("lux16")){
           return CQLConstants.openPathLux16 + nodeForOpenPath; 
        }       
        String nodePID = db.getObjectPID(nodeId);
        if (nodePID != null) {
            // yes, there is a handle for the node
            String handle = nodePID.startsWith(CQLConstants.HANDLE_HDL)
                    ? nodePID.substring(CQLConstants.HANDLE_HDL.length()) : nodePID;            
            return handleResolverURL+"/" + handle + "@view";
        }
        else {
            // no handle
            return CQLConstants.openPathCorpus1 + nodeForOpenPath;
        }
    }
    
    /**
     * 
     * @param languageId ISO or RFC language code, with the corresponding prefix.
     * @return the three-letter id of the language with the id "languageId". The method Must be rewritten once the language codes in imdi/cmdi are cleaned up
     */
    public static String toThreeLetterISOCode(String languageId, LanguageCodeToISO3 codeMap){
        String languageIdTrimmed = languageId.trim();
        if (languageIdTrimmed.startsWith(CQLConstants.ISO_3_PREFIX)) {
            String result = languageIdTrimmed.substring(CQLConstants.ISO_3_PREFIX.length());
            if (result.length() == 3) {
                return result.toLowerCase();
            }
        };
        String mapValue = codeMap.getCodeMap().get(languageIdTrimmed);
        if (mapValue != null) {
            return mapValue.substring(CQLConstants.ISO_3_PREFIX.length());
        };
        return "und";
    }

    /**
     * 
     * @param result
     * @param list
     * @return result with the elements of list added, where an element is added if and only if it is not in "list".
     */
    public static List<String> merge(List<String> result, List<String> list){
        if(list == null) {
            return result;
        }
        
        if (result == null) {
            result = list;
            return result;
        }
        
        for (String str:list){
            if (result.indexOf(str) == -1){
                result.add(str);
            }
        }
        
        return result;
    }
    
}
