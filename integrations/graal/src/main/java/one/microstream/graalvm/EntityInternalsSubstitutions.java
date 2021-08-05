package one.microstream.graalvm;

/*-
 * #%L
 * microstream-integrations-graalvm
 * %%
 * Copyright (C) 2019 - 2021 MicroStream Software
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;

@TargetClass(className = "one.microstream.persistence.binary.one.microstream.entity.EntityInternals")
public final class EntityInternalsSubstitutions {

	@Alias @RecomputeFieldValue(kind = Kind.FieldOffset, declClassName = "one.microstream.entity.EntityLayerVersioning", name = "context")
    private static long OFFSET_EntityLayerVersioning_context;
	
	@Alias @RecomputeFieldValue(kind = Kind.FieldOffset, declClassName = "one.microstream.entity.EntityLayerVersioning", name = "versions")
    private static long OFFSET_EntityLayerVersioning_versions;
}