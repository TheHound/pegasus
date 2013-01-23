/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.brekka.pegasus.core.services.impl;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.brekka.pegasus.core.PegasusErrorCode;
import org.brekka.pegasus.core.PegasusException;
import org.brekka.pegasus.core.model.Accessor;
import org.brekka.pegasus.core.model.AccessorContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A non-serializable context for retaining expensive-to-calculate values for a session user.
 * 
 * @author Andrew Taylor (andrew@brekka.org)
 */
public class AccessorContextImpl implements Serializable, AccessorContext {

    /**
     * Serial UID
     */
    private static final long serialVersionUID = 1769337117320776327L;

    /**
     * Map that holds the objects.
     */
    private transient Map<Serializable, Object> map;

    /*
     * (non-Javadoc)
     * 
     * @see org.brekka.pegasus.core.model.AccessorContext#retain(java.io.Serializable, java.lang.Object)
     */
    @Override
    public synchronized void retain(Serializable key, Object value) {
        map().put(key, value);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.brekka.pegasus.core.model.AccessorContext#retrieve(java.io.Serializable, java.lang.Class)
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized <V> V retrieve(Serializable key, Class<V> expectedType) {
        Object object = map().get(key);
        if (object == null) {
            return null;
        }
        if (expectedType.isAssignableFrom(object.getClass()) == false) {
            throw new PegasusException(PegasusErrorCode.PG500, "Expected value of type '%s', actual '%s'",
                    expectedType.getName(), object.getClass().getName());
        }
        return (V) object;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.brekka.pegasus.core.model.AccessorContext#remove(java.io.Serializable)
     */
    @Override
    public synchronized void remove(Serializable key) {
        map().remove(key);
    }

    /**
     * Map is lazy initialised so all operations should obtain a reference using this method.
     */
    private synchronized Map<Serializable, Object> map() {
        if (map == null) {
            map = new HashMap<>();
        }
        return map;
    }

    /**
     * Retrieve the current {@link AccessorContext} from the security context user (assuming there is one). If no user
     * is present then a {@link PegasusException} will be thrown.
     * 
     * @return the {@link AccessorContext} bound to the current security context.
     * @throws PegasusException
     *             if there is no {@link AccessorContext} available.
     */
    public static AccessorContext getCurrent() {
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication authentication = context.getAuthentication();
        if (authentication instanceof Accessor) {
            return ((Accessor) authentication).getContext();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Accessor) {
            return ((Accessor) principal).getContext();
        }
        throw new PegasusException(PegasusErrorCode.PG623, 
                "No AccessorContext available for the current security context");
    }
}
