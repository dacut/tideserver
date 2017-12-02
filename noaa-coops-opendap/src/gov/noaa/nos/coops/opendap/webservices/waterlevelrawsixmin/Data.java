
package gov.noaa.nos.coops.opendap.webservices.waterlevelrawsixmin;

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
 *         &lt;element name="O" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="F" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="R" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="L" type="{http://www.w3.org/2001/XMLSchema}int"/>
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
    "o",
    "f",
    "r",
    "l"
})
public class Data {

    @XmlElement(required = true)
    protected String timeStamp;
    @XmlElement(name = "WL")
    protected double wl;
    protected double sigma;
    @XmlElement(name = "O")
    protected int o;
    @XmlElement(name = "F")
    protected int f;
    @XmlElement(name = "R")
    protected int r;
    @XmlElement(name = "L")
    protected int l;

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
     * Gets the value of the o property.
     * 
     */
    public int getO() {
        return o;
    }

    /**
     * Sets the value of the o property.
     * 
     */
    public void setO(int value) {
        this.o = value;
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
     * Gets the value of the l property.
     * 
     */
    public int getL() {
        return l;
    }

    /**
     * Sets the value of the l property.
     * 
     */
    public void setL(int value) {
        this.l = value;
    }

}
