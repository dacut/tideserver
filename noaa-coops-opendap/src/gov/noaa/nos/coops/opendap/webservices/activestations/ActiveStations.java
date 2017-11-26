
package gov.noaa.nos.coops.opendap.webservices.activestations;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="stations" type="{https://opendap.co-ops.nos.noaa.gov/axis/webservices/activestations/wsdl}stations"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "stations"
})
@XmlRootElement(name = "ActiveStations")
public class ActiveStations {

    @XmlElement(required = true)
    protected Stations stations;

    /**
     * Gets the value of the stations property.
     * 
     * @return
     *     possible object is
     *     {@link Stations }
     *     
     */
    public Stations getStations() {
        return stations;
    }

    /**
     * Sets the value of the stations property.
     * 
     * @param value
     *     allowed object is
     *     {@link Stations }
     *     
     */
    public void setStations(Stations value) {
        this.stations = value;
    }

}
