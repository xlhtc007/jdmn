/**
 * Copyright 2016 Goldman Sachs.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.gs.dmn.feel.lib.type.context;

import com.gs.dmn.feel.lib.type.BaseType;
import com.gs.dmn.feel.lib.type.BooleanType;
import com.gs.dmn.feel.lib.type.ContextType;
import com.gs.dmn.feel.lib.type.logic.DefaultBooleanType;
import org.slf4j.Logger;

public class DefaultContextType extends BaseType implements ContextType {
    private final BooleanType booleanType;

    public DefaultContextType(Logger logger) {
        super(logger);
        this.booleanType = new DefaultBooleanType(logger);
    }

    @Override
    public Boolean contextEqual(Object c1, Object c2) {
        if (c1 == null && c2 == null) {
            return true;
        } else if (c1 == null) {
            return false;
        } else if (c2 == null) {
            return false;
        } else {
            return c1.equals(c2);
        }
    }

    @Override
    public Boolean contextNotEqual(Object c1, Object c2) {
        return booleanType.booleanNot(contextEqual(c1, c2));
    }
}
