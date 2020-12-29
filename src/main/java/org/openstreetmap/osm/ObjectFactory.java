/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2018.03.21 at 02:35:20 PM CET 
//


package org.openstreetmap.osm;

import javax.xml.bind.annotation.XmlRegistry;


/**
 * This object contains factory methods for each
 * Java content interface and Java element interface
 * generated in the org.openstreetmap.osm._0 package.
 * <p>An ObjectFactory allows you to programatically
 * construct new instances of the Java representation
 * for XML content. The Java representation of XML
 * content can consist of schema derived interfaces
 * and classes representing the binding of schema
 * type definitions, element declarations and model
 * groups.  Factory methods for each of these are
 * provided in this class.
 */
@XmlRegistry
public class ObjectFactory {


    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: org.openstreetmap.osm._0
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link Node }
     */
    public Node createNode() {
        return new Node();
    }

    /**
     * Create an instance of {@link Tag }
     */
    public Tag createTag() {
        return new Tag();
    }

    /**
     * Create an instance of {@link Nd }
     */
    public Nd createNd() {
        return new Nd();
    }

    /**
     * Create an instance of {@link Osm }
     */
    public Osm createOsm() {
        return new Osm();
    }

    /**
     * Create an instance of {@link Bounds }
     */
    public Bounds createBounds() {
        return new Bounds();
    }

    /**
     * Create an instance of {@link Way }
     */
    public Way createWay() {
        return new Way();
    }

    /**
     * Create an instance of {@link Relation }
     */
    public Relation createRelation() {
        return new Relation();
    }

    /**
     * Create an instance of {@link Member }
     */
    public Member createMember() {
        return new Member();
    }

}
