package net.jadoth.persistence.binary.types;

import static net.jadoth.Jadoth.notNull;

import java.lang.reflect.Field;

import net.jadoth.collections.HashEnum;
import net.jadoth.collections.X;
import net.jadoth.collections.types.XGettingEnum;
import net.jadoth.persistence.binary.internal.BinaryHandlerNativeArrayObject;
import net.jadoth.persistence.binary.internal.BinaryHandlerStateless;
import net.jadoth.persistence.exceptions.PersistenceExceptionTypeNotPersistable;
import net.jadoth.persistence.types.PersistenceFieldLengthResolver;
import net.jadoth.persistence.types.PersistenceTypeAnalyzer;
import net.jadoth.persistence.types.PersistenceTypeDescriptionMemberField;
import net.jadoth.persistence.types.PersistenceTypeHandler;
import net.jadoth.persistence.types.PersistenceTypeHandlerEnsurer;
import net.jadoth.swizzling.types.SwizzleTypeManager;


/**
 * Called "ensurer", because depending on the case, if creates new type handler or it just initializes
 * already existing, pre-registered ones. So "ensuring" is the most suited common denominator.
 * 
 * @author TM
 */
public interface BinaryTypeHandlerEnsurer extends PersistenceTypeHandlerEnsurer<Binary>
{
	@Override
	public <T> PersistenceTypeHandler<Binary, T> ensureTypeHandler(
		Class<T>           type       ,
		long               typeId     ,
		SwizzleTypeManager typeManager
	) throws PersistenceExceptionTypeNotPersistable;



	public class Implementation implements BinaryTypeHandlerEnsurer
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields  //
		/////////////////////

		private final PersistenceTypeAnalyzer        typeAnalyzer  ;
		private final PersistenceFieldLengthResolver lengthResolver;



		///////////////////////////////////////////////////////////////////////////
		// constructors     //
		/////////////////////

		public Implementation(
			final PersistenceTypeAnalyzer        typeAnalyzer  ,
			final PersistenceFieldLengthResolver lengthResolver
		)
		{
			super();
			this.typeAnalyzer   = notNull(typeAnalyzer);
			this.lengthResolver = notNull(lengthResolver); // must be provided, may not be null
		}



		///////////////////////////////////////////////////////////////////////////
		// declared methods //
		/////////////////////

		@Override
		public <T> PersistenceTypeHandler<Binary, T> ensureTypeHandler(
			final Class<T>           type       ,
			final long               typeId     ,
			final SwizzleTypeManager typeManager
		)
			throws PersistenceExceptionTypeNotPersistable
		{
			if(type.isPrimitive())
			{
				// (29.04.2017 TM)EXCP: proper exception
				throw new RuntimeException("Primitive type values cannot be handled as instances.");
			}
			if(type.isArray())
			{
				// array special cases
				if(type.getComponentType().isPrimitive())
				{
					// (01.04.2013)EXCP: proper exception
					throw new RuntimeException("Primitive component type arrays must be covered by default handler implementations.");
				}
				
				// array types can never change and therefore can never have obsolete types.
				return new BinaryHandlerNativeArrayObject<>(type).initialize(typeId, X.emptyTable());
			}

			final HashEnum<PersistenceTypeDescriptionMemberField> fieldDescriptions = HashEnum.New();

			final XGettingEnum<Field> persistableFields =
				this.typeAnalyzer.collectPersistableFields(type, typeManager, fieldDescriptions)
			;

			if(persistableFields.isEmpty())
			{
				// required for a) sparing unnecessary overhead and b) validation reasons
				return new BinaryHandlerStateless<>(type).initialize(typeId, X.emptyTable());
			}
			
			if(type.isEnum())
			{
				/* (09.06.2017 TM)TODO: enum BinaryHandler special case implementation once completed
				 * (10.06.2017 TM)NOTE: not sure if handling enums (constants) in an entity graph
				 * makes sense in the first place. The whole enum concept is just to wacky for an entity graph.
				 */
//				return this.createEnumHandler(type, typeId, persistableFields, this.lengthResolver);
			}

			// default implementation simply always uses a blank memory instantiator
			return new BinaryHandlerGeneric<>(
				type                                           ,
				typeId                                         ,
				BinaryPersistence.blankMemoryInstantiator(type),
				persistableFields                              ,
				this.lengthResolver
			);
		}
		
		@SuppressWarnings("unchecked") // required generics crazy sh*t tinkering
		final <T, E extends Enum<E>> PersistenceTypeHandler<Binary, T> createEnumHandler(
			final Class<?>                       type          ,
			final long                           tid           ,
			final XGettingEnum<Field>            allFields     ,
			final PersistenceFieldLengthResolver lengthResolver
		)
		{
			return (PersistenceTypeHandler<Binary, T>)new BinaryHandlerEnum<>(
				(Class<E>)type     ,
				tid                ,
				allFields          ,
				this.lengthResolver
			);
		}

	}

}