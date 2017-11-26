
package gov.noaa.nos.coops.opendap.webservices.activestations;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for stationV2 complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="stationV2">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="metadataV2" type="{https://opendap.co-ops.nos.noaa.gov/axis/webservices/activestations/wsdl}metadataV2"/>
 *         &lt;element name="parameter" type="{https://opendap.co-ops.nos.noaa.gov/axis/webservices/activestations/wsdl}parameter" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *       &lt;attribute name="ID" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="name" type="{http://www.w3.org/2001/XMLSchema}string" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "stationV2", propOrder = {
    "metadataV2",
    "parameter"
})
public class StationV2 {

    @XmlElement(required = true)
    protected MetadataV2 metadataV2;
    protected List<Parameter> parameter;
    @XmlAttribute(name = "ID")
    protected String id;
    @XmlAttribute(name = "name")
    protected String name;

    /**
     * Gets the value of the metadataV2 property.
     * 
     * @return
     *     possible object is
     *     {@link MetadataV2 }
     *     
     */
    public MetadataV2 getMetadataV2() {
        return metadataV2;
    }

    /**
     * Sets the value of the metadataV2 property.
     * 
     * @param value
     *     allowed object is
     *     {@link MetadataV2 }
     *     
     */
    public void setMetadataV2(MetadataV2 value) {
        this.metadataV2 = value;
    }

    /**
     * Gets the value of the parameter property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the parameter property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getParameter().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Parameter }
     * 
     * 
     */
    public List<Parameter> getParameter() {
        if (parameter == null) {
            parameter = new ArrayList<Parameter>();
        }
        return this.parameter;
    }

    /**
     * Gets the value of the id property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getID() {
        return id;
    }

    /**
     * Sets the value of the id property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setID(String value) {
        this.id = value;
    }

    /**
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setName(String value) {
        this.name = value;
    }

}
