
package gov.noaa.nos.coops.opendap.webservices.waterlevelverifiedsixmin;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for Data complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="Data">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="timeStamp" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="WL" type="{http://www.w3.org/2001/XMLSchema}double"/>
 *         &lt;element name="sigma" type="{http://www.w3.org/2001/XMLSchema}double"/>
 *         &lt;element name="I" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="F" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="R" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="T" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Data", propOrder = {
    "timeStamp",
    "wl",
    "sigma",
    "i",
    "f",
    "r",
    "t"
})
public class Data {

    @XmlElement(required = true)
    protected String timeStamp;
    @XmlElement(name = "WL")
    protected double wl;
    protected double sigma;
    @XmlElement(name = "I")
    protected int i;
    @XmlElement(name = "F")
    protected int f;
    @XmlElement(name = "R")
    protected int r;
    @XmlElement(name = "T")
    protected int t;

    /**
     * Gets the value of the timeStamp property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTimeStamp() {
        return timeStamp;
    }

    /**
     * Sets the value of the timeStamp property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTimeStamp(String value) {
        this.timeStamp = value;
    }

    /**
     * Gets the value of the wl property.
     * 
     */
    public double getWL() {
        return wl;
    }

    /**
     * Sets the value of the wl property.
     * 
     */
    public void setWL(double value) {
        this.wl = value;
    }

    /**
     * Gets the value of the sigma property.
     * 
     */
    public double getSigma() {
        return sigma;
    }

    /**
     * Sets the value of the sigma property.
     * 
     */
    public void setSigma(double value) {
        this.sigma = value;
    }

    /**
     * Gets the value of the i property.
     * 
     */
    public int getI() {
        return i;
    }

    /**
     * Sets the value of the i property.
     * 
     */
    public void setI(int value) {
        this.i = value;
    }

    /**
     * Gets the value of the f property.
     * 
     */
    public int getF() {
        return f;
    }

    /**
     * Sets the value of the f property.
     * 
     */
    public void setF(int value) {
        this.f = value;
    }

    /**
     * Gets the value of the r property.
     * 
     */
    public int getR() {
        return r;
    }

    /**
     * Sets the value of the r property.
     * 
     */
    public void setR(int value) {
        this.r = value;
    }

    /**
     * Gets the value of the t property.
     * 
     */
    public int getT() {
        return t;
    }

    /**
     * Sets the value of the t property.
     * 
     */
    public void setT(int value) {
        this.t = value;
    }

}
