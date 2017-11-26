
package gov.noaa.nos.coops.opendap.webservices.activestations;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for metadataV2 complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="metadataV2">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="location" type="{https://opendap.co-ops.nos.noaa.gov/axis/webservices/activestations/wsdl}location"/>
 *         &lt;element name="date_established" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="shef_id" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="deployment_designation" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "metadataV2", propOrder = {
    "location",
    "dateEstablished",
    "shefId",
    "deploymentDesignation"
})
public class MetadataV2 {

    @XmlElement(required = true)
    protected Location location;
    @XmlElement(name = "date_established", required = true)
    protected String dateEstablished;
    @XmlElement(name = "shef_id", required = true)
    protected String shefId;
    @XmlElement(name = "deployment_designation", required = true)
    protected String deploymentDesignation;

    /**
     * Gets the value of the location property.
     * 
     * @return
     *     possible object is
     *     {@link Location }
     *     
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Sets the value of the location property.
     * 
     * @param value
     *     allowed object is
     *     {@link Location }
     *     
     */
    public void setLocation(Location value) {
        this.location = value;
    }

    /**
     * Gets the value of the dateEstablished property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDateEstablished() {
        return dateEstablished;
    }

    /**
     * Sets the value of the dateEstablished property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDateEstablished(String value) {
        this.dateEstablished = value;
    }

    /**
     * Gets the value of the shefId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getShefId() {
        return shefId;
    }

    /**
     * Sets the value of the shefId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setShefId(String value) {
        this.shefId = value;
    }

    /**
     * Gets the value of the deploymentDesignation property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDeploymentDesignation() {
        return deploymentDesignation;
    }

    /**
     * Sets the value of the deploymentDesignation property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDeploymentDesignation(String value) {
        this.deploymentDesignation = value;
    }

}
