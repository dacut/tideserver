
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
 *         &lt;element name="stationsV2" type="{https://opendap.co-ops.nos.noaa.gov/axis/webservices/activestations/wsdl}stationsV2"/>
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
    "stationsV2"
})
@XmlRootElement(name = "ActiveStationsV2")
public class ActiveStationsV2 {

    @XmlElement(required = true)
    protected StationsV2 stationsV2;

    /**
     * Gets the value of the stationsV2 property.
     * 
     * @return
     *     possible object is
     *     {@link StationsV2 }
     *     
     */
    public StationsV2 getStationsV2() {
        return stationsV2;
    }

    /**
     * Sets the value of the stationsV2 property.
     * 
     * @param value
     *     allowed object is
     *     {@link StationsV2 }
     *     
     */
    public void setStationsV2(StationsV2 value) {
        this.stationsV2 = value;
    }

}
