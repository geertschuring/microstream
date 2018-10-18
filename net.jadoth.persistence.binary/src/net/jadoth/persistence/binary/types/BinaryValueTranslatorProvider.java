package net.jadoth.persistence.binary.types;

import static net.jadoth.X.mayNull;
import static net.jadoth.X.notNull;

import net.jadoth.collections.XUtilsCollection;
import net.jadoth.collections.types.XGettingMap;
import net.jadoth.collections.types.XGettingSequence;
import net.jadoth.persistence.types.PersistenceTypeDefinition;
import net.jadoth.persistence.types.PersistenceTypeDefinitionMember;
import net.jadoth.persistence.types.PersistenceTypeDescriptionMember;
import net.jadoth.persistence.types.PersistenceTypeHandler;
import net.jadoth.typing.TypeMappingLookup;


public interface BinaryValueTranslatorProvider
{
	/**
	 * Normal translator to translate a value from binary form to a target instance.
	 * 
	 * @param sourceMember
	 * @param targetMember
	 * @return
	 */
	public BinaryValueSetter provideTargetValueTranslator(
		PersistenceTypeDefinition         sourceLegacyType ,
		PersistenceTypeDefinitionMember   sourceMember     ,
		PersistenceTypeHandler<Binary, ?> targetCurrentType,
		PersistenceTypeDefinitionMember   targetMember
	);
	
	/**
	 * Special translator to translate a value from binary form to an intermediate binary form.
	 * 
	 * @param sourceMember
	 * @param targetMember
	 * @return
	 */
	public BinaryValueSetter provideBinaryValueTranslator(
		PersistenceTypeDefinition         sourceLegacyType ,
		PersistenceTypeDefinitionMember   sourceMember     ,
		PersistenceTypeHandler<Binary, ?> targetCurrentType,
		PersistenceTypeDefinitionMember   targetMember
	);
	
	
	
	public static BinaryValueTranslatorProvider New(
		final XGettingMap<String, BinaryValueSetter>                      customTranslatorLookup  ,
		final XGettingSequence<? extends BinaryValueTranslatorKeyBuilder> translatorKeyBuilders   ,
		final BinaryValueTranslatorLookupProvider                         translatorLookupProvider
	)
	{
		return new BinaryValueTranslatorProvider.Implementation(
			mayNull(customTranslatorLookup),
			unwrapKeyBuilders(translatorKeyBuilders),
			notNull(translatorLookupProvider)
		);
	}
	
	static BinaryValueTranslatorKeyBuilder[] unwrapKeyBuilders(
		final XGettingSequence<? extends BinaryValueTranslatorKeyBuilder> translatorKeyBuilders
	)
	{
		return translatorKeyBuilders == null || translatorKeyBuilders.isEmpty()
			? null
			: XUtilsCollection.toArray(translatorKeyBuilders, BinaryValueTranslatorKeyBuilder.class)
		;
	}
	
	public final class Implementation implements BinaryValueTranslatorProvider
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final XGettingMap<String, BinaryValueSetter> customTranslatorLookup  ;
		private final BinaryValueTranslatorKeyBuilder[]      translatorKeyBuilders   ;
		private final BinaryValueTranslatorLookupProvider    translatorLookupProvider;
		
		private transient TypeMappingLookup<BinaryValueSetter> translatorLookup;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Implementation(
			final XGettingMap<String, BinaryValueSetter> customTranslatorLookup  ,
			final BinaryValueTranslatorKeyBuilder[]      translatorKeyBuilders   ,
			final BinaryValueTranslatorLookupProvider    translatorLookupProvider
		)
		{
			super();
			this.customTranslatorLookup   = customTranslatorLookup  ;
			this.translatorKeyBuilders    = translatorKeyBuilders   ;
			this.translatorLookupProvider = translatorLookupProvider;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		private TypeMappingLookup<BinaryValueSetter> translatorLookup()
		{
			if(this.translatorLookup == null)
			{
				this.translatorLookup = this.translatorLookupProvider.mapping();
			}
			
			return this.translatorLookup;
		}
		
		private BinaryValueSetter provideValueSkipper(final PersistenceTypeDefinitionMember sourceMember)
		{
			if(sourceMember.isReference())
			{
				// skip the long-typed OID value
				return BinaryValueTranslators::skip_long;
			}

			/* (27.09.2018 TM)TODO: Legacy Type Mapping: implement skipping a variable length type.
			 * This even already exists in BinaryReferenceTraverser
			 */

			return resolvePrimitiveSkipper(sourceMember);
		}
		
		private static void validateIsPrimitiveType(final PersistenceTypeDefinitionMember member)
		{
			final Class<?> memberType = member.type();
			if(memberType == null || !memberType.isPrimitive())
			{
				// (27.09.2018 TM)EXCP: proper exception
				throw new RuntimeException("Unhandled type \"" + toTypedIdentifier(member) + ".");
			}
		}
		
		private static BinaryValueSetter resolvePrimitiveSkipper(
			final PersistenceTypeDefinitionMember sourceMember
		)
		{
			validateIsPrimitiveType(sourceMember);
			
			final Class<?> sourceType = sourceMember.type();
			return sourceType == byte.class
				? BinaryValueTranslators::skip_byte
				: sourceType == boolean.class
				? BinaryValueTranslators::skip_boolean
				: sourceType == short.class
				? BinaryValueTranslators::skip_short
				: sourceType == char.class
				? BinaryValueTranslators::skip_char
				: sourceType == int.class
				? BinaryValueTranslators::skip_int
				: sourceType == float.class
				? BinaryValueTranslators::skip_float
				: sourceType == long.class
				? BinaryValueTranslators::skip_long
				: sourceType == double.class
				? BinaryValueTranslators::skip_double
				: throwUnhandledPrimitiveException(sourceMember)
			;
		}
		
		private static BinaryValueSetter throwUnhandledPrimitiveException(
			final PersistenceTypeDescriptionMember sourceMember
		)
		{
			// (27.09.2018 TM)EXCP: proper exception
			throw new RuntimeException(
				"Unhandled primitive type \"" + toTypedIdentifier(sourceMember) + "."
			);
		}
		
		private BinaryValueSetter provideValueTranslator(final Class<?> sourceType, final Class<?> targetType)
		{
			final BinaryValueSetter translator = this.translatorLookup().lookup(sourceType, targetType);
			if(translator != null)
			{
				return translator;
			}
			
			validateIsReferenceType(sourceType);
			validateIsReferenceType(targetType);
			
			/*
			 * In case none of the other mapping tools (explicit mapping and member matching and translator registration)
			 * Covered the current case, it is essential to check the target type compatibility, since it is
			 * too dangerous to arbitrarily copy references to instances with one type into fields with another type.
			 */
			validateCompatibleTargetType(sourceType, targetType);
			
			return this.provideReferenceResolver();
		}
		
		private static void validateCompatibleTargetType(final Class<?> sourceType, final Class<?> targetType)
		{
			if(targetType.isAssignableFrom(sourceType))
			{
				return;
			}
			
			// (27.09.2018 TM)EXCP: proper exception
			throw new RuntimeException(
				"Incompatible types: " + sourceType.getName() + " -> " + targetType.getName()
			);
		}
		
		private BinaryValueSetter provideReferenceResolver(
			final PersistenceTypeDescriptionMember sourceMember,
			final PersistenceTypeDescriptionMember targetMember
		)
		{
			validateIsReferenceType(sourceMember);
			validateIsReferenceType(targetMember);
			
			return this.provideReferenceResolver();
		}
		
		private BinaryValueSetter provideReferenceResolver()
		{
			return BinaryPersistence.getSetterReference();
		}
		
		private static void validateIsReferenceType(final PersistenceTypeDescriptionMember member)
		{
			if(member.isReference())
			{
				return;
			}
			
			// (27.09.2018 TM)EXCP: proper exception
			throw new RuntimeException(
				"Non-reference type \"" + toTypedIdentifier(member) + "\" cannot be handled generically."
			);
		}
		
		private static void validateIsReferenceType(final Class<?> type)
		{
			if(!type.isPrimitive())
			{
				return;
			}
			
			// (27.09.2018 TM)EXCP: proper exception
			throw new RuntimeException("Unhandled primitive type: \"" + type.getName() + ".");
		}
		
		private static String toTypedIdentifier(final PersistenceTypeDescriptionMember member)
		{
			return member.typeName() + "\" of "
				+ PersistenceTypeDescriptionMember.class.getSimpleName() + " " + member.uniqueName()
			;
		}
		
		private BinaryValueSetter lookupCustomValueTranslator(
			final PersistenceTypeDefinition         sourceLegacyType ,
			final PersistenceTypeDescriptionMember  sourceMember     ,
			final PersistenceTypeHandler<Binary, ?> targetCurrentType,
			final PersistenceTypeDescriptionMember  targetMember
		)
		{
			if(this.translatorKeyBuilders == null || this.customTranslatorLookup == null)
			{
				return null;
			}
			
			final XGettingMap<String, BinaryValueSetter> customTranslatorLookup = this.customTranslatorLookup;
			final BinaryValueTranslatorKeyBuilder[]      translatorKeyBuilders  = this.translatorKeyBuilders ;
			
			for(final BinaryValueTranslatorKeyBuilder keyBuilder : translatorKeyBuilders)
			{
				final String key = keyBuilder.buildTranslatorLookupKey(
					sourceLegacyType ,
					sourceMember     ,
					targetCurrentType,
					targetMember
				);
				
				final BinaryValueSetter customValueSetter = customTranslatorLookup.get(key);
				if(customValueSetter != null)
				{
					return customValueSetter;
				}
			}
			
			return null;
		}
		
		@Override
		public BinaryValueSetter provideTargetValueTranslator(
			final PersistenceTypeDefinition         sourceLegacyType ,
			final PersistenceTypeDefinitionMember   sourceMember     ,
			final PersistenceTypeHandler<Binary, ?> targetCurrentType,
			final PersistenceTypeDefinitionMember   targetMember
		)
		{
			if(targetMember == null)
			{
				return this.provideValueSkipper(sourceMember);
			}
			
			// check for potential custom value translator
			final BinaryValueSetter customValueSetter = this.lookupCustomValueTranslator(
				sourceLegacyType ,
				sourceMember     ,
				targetCurrentType,
				targetMember
			);
			if(customValueSetter != null)
			{
				return customValueSetter;
			}
			
			// note: see #validateCompatibleTargetType for target field type compatability validation.
			
			// check for generically handleable types on both sides
			final Class<?> sourceType = sourceMember.type();
			final Class<?> targetType = targetMember.type();
			if(sourceType != null && targetType != null)
			{
				return this.provideValueTranslator(sourceType, targetType);
			}
						
			// generic fallback: for two reference fields, simply resolve the OID to a reference/instance.
			return provideReferenceResolver(sourceMember, targetMember);
		}
		
		@Override
		public final BinaryValueSetter provideBinaryValueTranslator(
			final PersistenceTypeDefinition         sourceLegacyType ,
			final PersistenceTypeDefinitionMember   sourceMember     ,
			final PersistenceTypeHandler<Binary, ?> targetCurrentType,
			final PersistenceTypeDefinitionMember   targetMember
		)
		{
			if(sourceMember.isReference())
			{
				return BinaryValueTranslators.provideReferenceValueBinaryTranslator(sourceMember, targetMember);
			}
			
			validateIsPrimitiveType(sourceMember);
			
			// target may be null (meaning the source member/field value shall be skipped)
			if(targetMember != null)
			{
				validateIsPrimitiveType(targetMember);
			}

			// primitives can be handled the normal way: copy/translate the bytes from source to target.
			return this.provideTargetValueTranslator(sourceLegacyType, sourceMember, targetCurrentType, targetMember);
		}
		
	}
	
}