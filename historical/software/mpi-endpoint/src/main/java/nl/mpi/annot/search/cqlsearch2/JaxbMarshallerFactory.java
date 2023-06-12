/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.mpi.annot.search.cqlsearch2;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;

/**
 *
 * @author olhsha
 */
public class JaxbMarshallerFactory {
     
    private JAXBContext _context;
    private Marshaller _marshaller;
    private String _schemaLocation;
    
    public JaxbMarshallerFactory(){
        
    }
    
    /**
     * 
     * @return "_schemaLocation" field of *this* factory instance.
     */
    public String getSchemaLocation() {
        return _schemaLocation;
    }
    
    /**
     * Sets the schema-location string to "_schemaLocation" field of *this*.
     * @param schemaLocation a string (url).
     * @throws PropertyException.
     */
    public void setSchemaLocation(String schemaLocation) {
        _schemaLocation = schemaLocation;
    }
    
    /**
     * 
     * @param type
     * @return "marshaller" field value, after _marshaller" has been initialised with "_schemaLocation", Marshaller.JAXB_FORMATTED_OUTPUT and Marshaller.JAXB_FRAGMENT are set to "true".
     * @throws JAXBException. 
     * 
     */
    public Marshaller createMarshaller(Class<?> type) throws JAXBException{
        _context = JAXBContext.newInstance(type); 
        _marshaller = _context.createMarshaller();        
        _marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, _schemaLocation);
        _marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        _marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
        return _marshaller;
    }
    
   
    
}
