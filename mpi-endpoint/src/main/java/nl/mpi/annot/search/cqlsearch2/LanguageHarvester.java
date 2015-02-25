/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.mpi.annot.search.cqlsearch2;

import de.clarin.fcs.schema.EndpointDescriptionResource;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import nl.mpi.corpusstructure.CorpusStructureDBImpl;
import nl.mpi.corpusstructure.Node;
import nl.mpi.imdi.api.IMDIDom;
import nl.mpi.imdi.api.IMDIElement;
import nl.mpi.util.OurURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

/**
 *
 * @author olhsha
 */
public class LanguageHarvester {
    
   private Logger _logger = LoggerFactory.getLogger(LanguageHarvester.class);
   
   private List<String> _mainCorpora;
   private HashMap<String, List<String>> _corpora_subcorpora;
   private List<String> _subcorpora;
   private HashMap<String, List<String>> _subcorpora_sessions;
   private List<String> _sessions;
   private HashMap<String, List<String>> _sessions_languages;
   private HashMap<String, List<String>> _corpora_sessions;
   private LanguageCodeToISO3 _languageCodeMap;
   
  /**
   * The constructor generates a number of maps according to the outline "corpus --> subcorpus --> session --> language".
   * @param db corpus-structure database.
   * @param searchableMainCorpora the list of main ("root") searchable corpora. 
   */ 
   public LanguageHarvester(CorpusStructureDBImpl db, List<String> searchableMainCorpora, String imdiLanguageMapURL, String imdiNamespaceURL){
       
       _languageCodeMap = new LanguageCodeToISO3(imdiLanguageMapURL, imdiNamespaceURL);
       _mainCorpora  = searchableMainCorpora;
       _corpora_subcorpora = this.retrieveCorporaSubcorpora(db, _mainCorpora);
       _subcorpora = this.getValuesWithCheckDuplicates(_corpora_subcorpora); 
       _subcorpora_sessions = this.retrieveSubcorporaSessions(db, _subcorpora);
       _corpora_sessions = this.retrieveSubcorporaSessions(db, _mainCorpora);
       _sessions = this.getValues(_corpora_sessions);
       _sessions_languages = this.retrieveSessionLanguages(db, _sessions);
   }
   
   /**
    * 
    * @param map a map from strings to lists of strings.
    * @return the list of elements (with no repetitions) of all the lists which are values of the map.
    */
   private List<String> getValuesWithCheckDuplicates(HashMap<String, List<String>> map){
     List<String> result = new ArrayList(); 
     Collection<List<String>> values = map.values();
       for (List<String> list:  values) {
           result = CQLHelpers.merge(result, list);
       }
       return result;
   }
   
   /**
    * 
    * @param map a map from strings to lists of strings
    * @return the list of elements (possible repetitions) of all the lists which are values of the map.
    */
    private List<String> getValues(HashMap<String, List<String>> map){
     List<String> result = new ArrayList(); 
     Collection<List<String>> values = map.values();
       for (List<String> list:  values) {
           result.addAll(list);
       }
       return result;
   }
   
   /**
    * 
    * @param db corpus-structure database.
    * @param searchableMainCorpora the list of corpora
    * @return the mapping from corpus-node id-s to lists of their corresponding direct subcorpus-node id-s
    */ 
   private HashMap<String, List<String>> retrieveCorporaSubcorpora(CorpusStructureDBImpl db, List<String> searchableMainCorpora){
      HashMap<String, List<String>> result = new HashMap<String, List<String>>();
       for (String nodeId: searchableMainCorpora) {
           try {
               Node[] kids = db.getChildrenNodes(nodeId);
               List<String> kidIds = new ArrayList<String>();
               for (Node kid : kids) {
                   kidIds.add(kid.getNodeId());
               }
               result.put(nodeId, kidIds);
               //_logger.info(nodeId);
           } catch (Exception e) {
               _logger.error("Exception in node " + nodeId + ": " + e.getMessage());
           }
       }
       return result;
   }
   
   /**
    * 
    * @param db corpus-structure database.
    * @param corpora list of corpus-node identifiers.
    * @return the mapping from corpus-node identifiers to lists of corresponding session-node identifiers.
    */
   private HashMap<String, List<String>> retrieveSubcorporaSessions(CorpusStructureDBImpl db, List<String> corpora){
      HashMap<String, List<String>> result = new HashMap<String, List<String>>();
       for (String nodeId: corpora) {
           try {
           String[] tmp = db.getSessions(nodeId);
           List<String> sessions = Arrays.asList(tmp); 
           result.put(nodeId, sessions);
           } catch (Exception e) {
               _logger.error("Eception on getting sessions of node "+nodeId+": "+e.getMessage());
           }
       }
       return result;
   }
   
   /**
    * 
    * @param db corpus-structure database.
    * @param sessions lists of session-node identifiers.
    * @return a map from session-node  identifiers to the lists of corresponding language ISO-3 codes.
    */
   private HashMap<String, List<String>> retrieveSessionLanguages(CorpusStructureDBImpl db, List<String> sessions){
       HashMap result = new HashMap<String, List<String>>(); 
       for (String id: sessions) {
           result.put(id, getLanguagesForSession(db, id));
       }
       return result;
   }
   
   
   
   /**
    * 
    * @param nodeId a corpus-node identifier.
    * @return the instance of {@link Languages} containing the list of ISO-3 codes of the languages spoken and referred to in the node with "nodeId". 
    */
   public EndpointDescriptionResource.Languages getLanguages(String nodeId) {
        EndpointDescriptionResource.Languages result = new EndpointDescriptionResource.Languages();
        List<String> languages = result.getLanguage();
        List<String> sessions  = (_mainCorpora.contains(nodeId)) ?
                _corpora_sessions.get(nodeId) : _subcorpora_sessions.get(nodeId);
        for (String sessionId : sessions) {
            List<String> sessionLanguages = _sessions_languages.get(sessionId);
            CQLHelpers.merge(languages, sessionLanguages);
        }
        return result;
    }

   /**
    * 
    * @param db a corpus-structure database.
    * @param nodeId a session-node identifier.
    * @return a list of the languages (iso-3 codes), spoken and referred to in the node with "nodeId".
    */
    private List<String> getLanguagesForSession(CorpusStructureDBImpl db, String nodeId) {
        List<String> result = new ArrayList<String>();
        try {
            URL sessionURLtmp = db.getObjectURI(nodeId).toURL();
            OurURL sessionURL = new OurURL(sessionURLtmp);
            IMDIDom dom = new IMDIDom();
            Document doc = dom.loadIMDIDocument(sessionURL, false);
            IMDIElement elem = dom.getIMDIElement(doc, "Session.Content.Languages.Language(1).Id");
            int elementNumber = 1;
            while (elem != null) {
                String langaugeCode = CQLHelpers.toThreeLetterISOCode(elem.getValue(), _languageCodeMap);
                if (langaugeCode != null && result.indexOf(langaugeCode) == -1) {
                    result.add(langaugeCode);
                }
                elementNumber++;
                elem = dom.getIMDIElement(doc, "Session.Content.Languages.Language(" + elementNumber + ").Id");
            }
        } catch (Exception e) {
            _logger.error(e.getMessage());
        }
        return result;
    }
}
