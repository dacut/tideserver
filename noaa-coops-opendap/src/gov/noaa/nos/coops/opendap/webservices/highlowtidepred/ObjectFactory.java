
package gov.noaa.nos.coops.opendap.webservices.highlowtidepred;

import javax.xml.bind.annotation.XmlRegistry;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the gov.noaa.nos.coops.opendap.webservices.highlowtidepred package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {


    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: gov.noaa.nos.coops.opendap.webservices.highlowtidepred
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link HighLowValues }
     * 
     */
    public HighLowValues createHighLowValues() {
        return new HighLowValues();
    }

    /**
     * Create an instance of {@link ArrayOfData }
     * 
     */
    public ArrayOfData createArrayOfData() {
        return new ArrayOfData();
    }

    /**
     * Create an instance of {@link HighLowAndMetadata }
     * 
     */
    public HighLowAndMetadata createHighLowAndMetadata() {
        return new HighLowAndMetadata();
    }

    /**
     * Create an instance of {@link Parameters }
     * 
     */
    public Parameters createParameters() {
        return new Parameters();
    }

    /**
     * Create an instance of {@link Highlowtidepred }
     * 
     */
    public Highlowtidepred createHighlowtidepred() {
        return new Highlowtidepred();
    }

    /**
     * Create an instance of {@link Data }
     * 
     */
    public Data createData() {
        return new Data();
    }

}
