
package gov.noaa.nos.coops.opendap.webservices.highlowtidepred;

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
 *         &lt;element name="HighLowValues" type="{https://opendap.co-ops.nos.noaa.gov/axis/webservices/highlowtidepred/wsdl}ArrayOfData"/>
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
    "highLowValues"
})
@XmlRootElement(name = "HighLowValues")
public class HighLowValues {

    @XmlElement(name = "HighLowValues", required = true, nillable = true)
    protected ArrayOfData highLowValues;

    /**
     * Gets the value of the highLowValues property.
     * 
     * @return
     *     possible object is
     *     {@link ArrayOfData }
     *     
     */
    public ArrayOfData getHighLowValues() {
        return highLowValues;
    }

    /**
     * Sets the value of the highLowValues property.
     * 
     * @param value
     *     allowed object is
     *     {@link ArrayOfData }
     *     
     */
    public void setHighLowValues(ArrayOfData value) {
        this.highLowValues = value;
    }

}
