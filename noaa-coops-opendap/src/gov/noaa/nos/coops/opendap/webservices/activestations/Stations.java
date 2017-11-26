
package gov.noaa.nos.coops.opendap.webservices.activestations;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for stations complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="stations">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="station" type="{https://opendap.co-ops.nos.noaa.gov/axis/webservices/activestations/wsdl}station" maxOccurs="unbounded"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "stations", propOrder = {
    "station"
})
public class Stations {

    @XmlElement(required = true)
    protected List<Station> station;

    /**
     * Gets the value of the station property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the station property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getStation().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Station }
     * 
     * 
     */
    public List<Station> getStation() {
        if (station == null) {
            station = new ArrayList<Station>();
        }
        return this.station;
    }

}
