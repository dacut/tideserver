
package gov.noaa.nos.coops.opendap.webservices.waterlevelrawsixmin;

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
 *         &lt;element name="data" type="{https://opendap.co-ops.nos.noaa.gov/axis/webservices/waterlevelrawsixmin/wsdl}ArrayOfData"/>
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
    "data"
})
@XmlRootElement(name = "WaterLevelRawSixMinMeasurements")
public class WaterLevelRawSixMinMeasurements {

    @XmlElement(required = true)
    protected ArrayOfData data;

    /**
     * Gets the value of the data property.
     * 
     * @return
     *     possible object is
     *     {@link ArrayOfData }
     *     
     */
    public ArrayOfData getData() {
        return data;
    }

    /**
     * Sets the value of the data property.
     * 
     * @param value
     *     allowed object is
     *     {@link ArrayOfData }
     *     
     */
    public void setData(ArrayOfData value) {
        this.data = value;
    }

}
