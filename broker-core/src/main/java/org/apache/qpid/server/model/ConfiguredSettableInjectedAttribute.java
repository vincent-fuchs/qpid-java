/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.model;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfiguredSettableInjectedAttribute<C extends ConfiguredObject, T>
        extends ConfiguredObjectInjectedAttributeOrStatistic<C,T> implements ConfiguredSettableAttribute<C,T>, ConfiguredObjectInjectedAttribute<C,T>
{

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfiguredSettableInjectedAttribute.class);

    private final Method _validValuesMethod;
    private final Pattern _secureValuePattern;
    private final String _defaultValue;
    private final boolean _secure;
    private final boolean _persisted;
    private final boolean _immutable;
    private final boolean _oversized;
    private final String _oversizedAltText;
    private final String _description;
    private final String[] _validValues;

    public ConfiguredSettableInjectedAttribute(final String name,
                                        final Class<T> type,
                                        final Type genericType,
                                        final String defaultValue,
                                        final boolean secure,
                                        final boolean persisted,
                                        final boolean immutable,
                                        final String secureValueFilter,
                                        final boolean oversized,
                                        final String oversizedAltText,
                                        final String description,
                                        final String[] validValues,
                                        final TypeValidator typeValidator)
    {
        super(name, type, genericType, typeValidator);

        _defaultValue = defaultValue;
        _secure = secure;
        _persisted = persisted;
        _immutable = immutable;
        _oversized = oversized;
        _oversizedAltText = oversizedAltText;
        _description = description;
        _validValues = validValues;

        Method validValuesMethod = null;

        if(_validValues != null && _validValues.length == 1)
        {
            String validValue = _validValues[0];

            validValuesMethod = getValidValuesMethod(validValue);
        }
        _validValuesMethod = validValuesMethod;

        if (secureValueFilter == null || "".equals(secureValueFilter))
        {
            _secureValuePattern = null;
        }
        else
        {
            _secureValuePattern = Pattern.compile(secureValueFilter);
        }
    }

    private Method getValidValuesMethod(final String validValue)
    {
        if(validValue.matches("([\\w][\\w\\d_]+\\.)+[\\w][\\w\\d_\\$]*#[\\w\\d_]+\\s*\\(\\s*\\)"))
        {
            try
            {
                String className = validValue.split("#")[0].trim();
                String methodName = validValue.split("#")[1].split("\\(")[0].trim();
                Class<?> validValueCalculatingClass = Class.forName(className);
                Method method = validValueCalculatingClass.getMethod(methodName);
                if (Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers()))
                {
                    if (Collection.class.isAssignableFrom(method.getReturnType()))
                    {
                        if (method.getGenericReturnType() instanceof ParameterizedType)
                        {
                            Type parameterizedType =
                                    ((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments()[0];
                            if (parameterizedType == String.class)
                            {
                                return method;
                            }
                        }
                    }
                }

            }
            catch (ClassNotFoundException | NoSuchMethodException e)
            {
                LOGGER.warn("The validValues of the " + getName()
                            + " has value '" + validValue + "' which looks like it should be a method,"
                            + " but no such method could be used.", e );
            }
        }
        return null;
    }

    public boolean isAutomated()
    {
        return false;
    }

    public boolean isDerived()
    {
        return false;
    }

    public String defaultValue()
    {
        return _defaultValue;
    }

    public boolean isSecure()
    {
        return _secure;
    }

    public boolean isMandatory()
    {
        return false;
    }

    public boolean isImmutable()
    {
        return _immutable;
    }

    public boolean isPersisted()
    {
        return _persisted;
    }

    @Override
    public boolean isOversized()
    {
        return _oversized;
    }

    @Override
    public boolean updateAttributeDespiteUnchangedValue()
    {
        return false;
    }

    @Override
    public String getOversizedAltText()
    {
        return _oversizedAltText;
    }

    public String getDescription()
    {
        return _description;
    }

    public Pattern getSecureValueFilter()
    {
        return _secureValuePattern;
    }

    @Override
    public boolean isSecureValue(final Object value)
    {
        Pattern filter;
        return isSecure() &&
               ((filter = getSecureValueFilter()) == null || filter.matcher(String.valueOf(value)).matches());
    }

    public Collection<String> validValues()
    {
        if(_validValuesMethod != null)
        {
            try
            {
                return (Collection<String>) _validValuesMethod.invoke(null);
            }
            catch (InvocationTargetException | IllegalAccessException e)
            {
                LOGGER.warn("Could not execute the validValues generation method " + _validValuesMethod.getName(), e);
                return Collections.emptySet();
            }
        }
        else
        {
            return _validValues == null ? Collections.<String>emptySet() : Arrays.asList(_validValues);
        }
    }

    /** Returns true iff this attribute has valid values defined */
    public boolean hasValidValues()
    {
        return validValues() != null && validValues().size() > 0;
    }

    @Override
    public final T getValue(final C configuredObject)
    {
        Object value = configuredObject.getActualAttributes().get(getName());
        if(value == null)
        {
            value = defaultValue();
        }
        return convert(value, configuredObject);
    }

}
