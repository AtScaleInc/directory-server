/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.directory.server.config;


import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.directory.shared.ldap.name.DN;


/**
 * An annotation used to specify that the qualified field is configuration element.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigurationElement
{
    /**
     * Returns the attribute type.
     *
     * @return
     *      the attribute type
     */
    String attributeType() default "";


    /**
     * Returns the object class.
     *
     * @return
     *      the object class
     */
    String objectClass() default "";


    /**
     * Returns true if of the qualified field (attribute type and value) 
     * is the RDN of the entry.
     *
     * @return
     *      <code>true</code> if of the qualified field (attribute type and value) 
     * is the RDN of the entry,
     *      <code>false</code> if not.
     */
    boolean isRDN() default false;


    /**
     * Returns true if the qualified field contains multiple values.
     *
     * @return
     *      <code>true</code> if the qualified field contains multiple values,
     *      <code>false</code> if not.
     */
    boolean isMultiple() default false;


    /**
     * Returns the string value of the DN of the container.
     *
     * @return
     *      the string value of the DN of the container.
     */
    String container() default "";
}
