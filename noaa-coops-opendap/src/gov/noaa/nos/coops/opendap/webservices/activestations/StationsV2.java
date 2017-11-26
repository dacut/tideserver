
package gov.noaa.nos.coops.opendap.webservices.activestations;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for stationsV2 complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="stationsV2">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="stationV2" type="{https://opendap.co-ops.nos.noaa.gov/axis/webservices/activestations/wsdl}stationV2" maxOccurs="unbounded"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "stationsV2", propOrder = {
    "stationV2"
})
public class StationsV2 {

    @XmlElement(required = true)
    protected List<StationV2> stationV2;

    /**
     * Gets the value of the stationV2 property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the stationV2 property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getStationV2().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link StationV2 }
     * 
     * 
     */
    public List<StationV2> getStationV2() {
        if (stationV2 == null) {
            stationV2 = new ArrayList<StationV2>();
        }
        return this.stationV2;
    }

}
