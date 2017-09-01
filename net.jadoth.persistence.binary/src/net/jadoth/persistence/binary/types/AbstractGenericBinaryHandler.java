package net.jadoth.persistence.binary.types;

import static net.jadoth.functional.JadothPredicates.not;

import java.lang.reflect.Field;
import java.util.function.Predicate;

import net.jadoth.Jadoth;
import net.jadoth.collections.BulkList;
import net.jadoth.collections.EqConstHashEnum;
import net.jadoth.collections.EqHashEnum;
import net.jadoth.collections.types.XGettingEnum;
import net.jadoth.collections.types.XGettingSequence;
import net.jadoth.collections.types.XImmutableSequence;
import net.jadoth.exceptions.TypeCastException;
import net.jadoth.functional.JadothPredicates;
import net.jadoth.functional._longProcedure;
import net.jadoth.memory.Memory;
import net.jadoth.memory.objectstate.ObjectStateDescriptor;
import net.jadoth.memory.objectstate.ObjectStateHandlerLookup;
import net.jadoth.persistence.exceptions.PersistenceExceptionTypeConsistencyDefinitionValidationFieldMismatch;
import net.jadoth.persistence.types.PersistenceFieldLengthResolver;
import net.jadoth.persistence.types.PersistenceTypeDescriptionMember;
import net.jadoth.persistence.types.PersistenceTypeDescriptionMemberField;
import net.jadoth.reflect.JadothReflect;
import net.jadoth.swizzling.exceptions.SwizzleExceptionConsistency;
import net.jadoth.swizzling.types.SwizzleBuildLinker;
import net.jadoth.swizzling.types.SwizzleFunction;
import net.jadoth.swizzling.types.SwizzleStoreLinker;

public abstract class AbstractGenericBinaryHandler<T> extends BinaryTypeHandler.AbstractImplementation<T>
{
	// (21.05.2013)XXX: BinaryHandlerGeneric cleanup static handling massacre mess

	///////////////////////////////////////////////////////////////////////////
	// static methods    //
	/////////////////////

	@SafeVarargs
	protected static final EqConstHashEnum<Field> filter(
		final XGettingEnum<Field> allFields,
		final Predicate<Field>... predicates
	)
	{
		return allFields.filterTo(EqHashEnum.<Field>New(), JadothPredicates.all(predicates)).immure();
	}

	protected static long calculateOffsets(
		final Field[]                allFieldsDeclOrder,
		final Field[]                fieldsBinOrder    ,
		final long[]                 allBinOfs         ,
		final BinaryValueStorer[]    storers           ,
		final BinaryValueSetter[]    setters           //,
//		final ObjectValueCopier[]    copiers           ,
//		final BinaryValueEqualator[] equltrs
	)
	{
		final BinaryValueStorer[]    refStorers = new BinaryValueStorer[   storers.length];
		final BinaryValueSetter[]    refSetters = new BinaryValueSetter[   setters.length];
//		final ObjectValueCopier[]    refCopiers = new ObjectValueCopier[   copiers.length];
//		final BinaryValueEqualator[] refEqultrs = new BinaryValueEqualator[equltrs.length];
		final Field[]                refFields  = new Field[        fieldsBinOrder.length];
		final BinaryValueStorer[]    prmStorers = new BinaryValueStorer[   storers.length];
		final BinaryValueSetter[]    prmSetters = new BinaryValueSetter[   setters.length];
//		final ObjectValueCopier[]    prmCopiers = new ObjectValueCopier[   copiers.length];
//		final BinaryValueEqualator[] prmEqultrs = new BinaryValueEqualator[equltrs.length];
		final Field[]                prmFields  = new Field[        fieldsBinOrder.length];
		final long[]                 prmBinOffs  = new long[              allBinOfs.length];

		long primBinOffsets = 0;
		int r = 0, p = 0;
		for(int i = 0; i < allFieldsDeclOrder.length; i++)
		{
			final Class<?>             fldType = allFieldsDeclOrder[i].getType()                   ;
			final BinaryValueStorer    storer  = BinaryPersistence.getObjectValueStorer   (fldType);
			final BinaryValueSetter    setter  = BinaryPersistence.getObjectValueSetter   (fldType);
//			final ObjectValueCopier    copier  =       ObjectState.getObjectValueCopier   (fldType);
//			final BinaryValueEqualator equltr  = BinaryPersistence.getObjectValueEqualator(fldType);
			if(fldType.isPrimitive())
			{
				primBinOffsets += Memory.byteSizePrimitive(fldType);
				prmStorers[p] = storer;
				prmSetters[p] = setter;
//				prmCopiers[p] = copier;
//				prmEqultrs[p] = equltr;
				prmBinOffs[p] = primBinOffsets;
				prmFields[ p] = allFieldsDeclOrder[i];
				p++;
			}
			else
			{
				refStorers[r] = storer;
				refSetters[r] = setter;
//				refCopiers[r] = copier;
//				refEqultrs[r] = equltr;
				refFields[ r] = allFieldsDeclOrder[i];
				r++;
			}
		}

		for(int i = 0; i < r; i++)
		{
			allBinOfs[i] = i * BinaryPersistence.oidLength();
		}
		final long binPrimOffset = r * BinaryPersistence.oidLength();
		for(int i = 0; i < p; i++)
		{
			allBinOfs[r + i] = binPrimOffset + prmBinOffs[i];
		}

		System.arraycopy(refStorers, 0, storers       , 0, r);
		System.arraycopy(refSetters, 0, setters       , 0, r);
//		System.arraycopy(refCopiers, 0, copiers       , 0, r);
//		System.arraycopy(refEqultrs, 0, equltrs       , 0, r);
		System.arraycopy(refFields , 0, fieldsBinOrder, 0, r);
		System.arraycopy(prmStorers, 0, storers       , r, p);
		System.arraycopy(prmSetters, 0, setters       , r, p);
//		System.arraycopy(prmCopiers, 0, copiers       , r, p);
//		System.arraycopy(prmEqultrs, 0, equltrs       , r, p);
		System.arraycopy(prmFields , 0, fieldsBinOrder, r, p);

		return binPrimOffset + primBinOffsets; // the offset at the end is equal to the total binary length
	}
	
	protected static final void createTypeDescriptionMembers(
		final Field[]                                    persistentOrderFields,
		final PersistenceFieldLengthResolver             lengthResolver       ,
		final BulkList<PersistenceTypeDescriptionMember> members
	)
	{
		for(final Field field : persistentOrderFields)
		{
			members.add(PersistenceTypeDescriptionMemberField.New(
				field.getType().getName(),
				field.getName(),
				field.getDeclaringClass().getName(),
				!field.getType().isPrimitive(),
				lengthResolver.resolveMinimumLengthFromField(field),
				lengthResolver.resolveMaximumLengthFromField(field)
			));
		}
	}



	///////////////////////////////////////////////////////////////////////////
	// instance fields  //
	/////////////////////

	// instance persistence context //
	private final EqConstHashEnum<Field>                               allFields        ;
	private final EqConstHashEnum<Field>                               refFields        ;
	private final EqConstHashEnum<Field>                               prmFields        ;
	private final long[]                                               allMemOfs        ;
	private final long[]                                               refMemOfs        ;
//	private final long[]                                               allBinOfs        ;
	private final long                                                 refBinStartOffset;
	private final long                                                 refBinBoundOffset;
	private final long                                                 binaryLength     ;
	private final BinaryValueStorer[]                                  binStorers       ;
	private final BinaryValueSetter[]                                  memSetters       ;
	private final XImmutableSequence<PersistenceTypeDescriptionMember> members          ;
	private final boolean                                              hasReferences    ;



	///////////////////////////////////////////////////////////////////////////
	// constructors     //
	/////////////////////

	protected AbstractGenericBinaryHandler(
		final Class<T>                       type          ,
		final long                           tid           ,
		final XGettingEnum<Field>            allFields     ,
		final PersistenceFieldLengthResolver lengthResolver
	)
	{
		super(type, tid);

		// Unsafe JavaDoc says ensureClassInitialized is "often needed" for getting the field base, so better do it.
		Memory.ensureClassInitialized(type);

		this.allFields    =  filter(allFields, not(JadothReflect::isStatic)                                 );
		this.refFields    =  filter(allFields, not(JadothReflect::isStatic), not(JadothReflect::isPrimitive));
		this.prmFields    =  filter(allFields, not(JadothReflect::isStatic),     JadothReflect::isPrimitive );
		final Field[]
			allFieldsDeclOrder = this.allFields.toArray(Field.class),
			refFieldsDeclOrder = this.refFields.toArray(Field.class),
			allFieldsPersOrder = new Field[allFieldsDeclOrder.length]
//			refFieldsPersOrder = new Field[refFieldsDeclOrder.length]
		;
		
		this.hasReferences = !this.refFields.isEmpty();

		/* (15.12.2012)TODO: TypeHandler: Field strategy
		 * Must bring in the possibility for a ValueSkipSetter and ValueSkipStorer
		 * And for a ValueShallowStorer
		 * Probably via different Value handler lookup instances.
		 * However those would have to be located in the handler creator instance, not here
		 */
		this.binaryLength = calculateOffsets(
			allFieldsDeclOrder                                                   ,
			allFieldsPersOrder                                                   ,
			/*this.allBinOfs  = */new long[                allFieldsDeclOrder.length],
			this.binStorers = new BinaryValueStorer[   allFieldsDeclOrder.length],
			this.memSetters = new BinaryValueSetter[   allFieldsDeclOrder.length]
//			this.copiers    = new ObjectValueCopier[   allFieldsDeclOrder.length],
//			this.equalators = new BinaryValueEqualator[allFieldsDeclOrder.length]
		);

		// memory offsets must correspond to other arrays
		this.allMemOfs         = Memory.objectFieldOffsets(allFieldsPersOrder);
		
		// reference field offsets fit either way
		this.refMemOfs         = Memory.objectFieldOffsets(refFieldsDeclOrder);
		
		// references are always stored at beginning
		this.refBinStartOffset = BinaryPersistence.entityBinaryPosition(0);
		this.refBinBoundOffset = BinaryPersistence.entityBinaryPosition(
			BinaryPersistence.referenceBinaryLength(Jadoth.to_int(this.refFields.size()))
		);

		final BulkList<PersistenceTypeDescriptionMember> members = BulkList.New(Jadoth.to_int(allFields.size()));
		createTypeDescriptionMembers(allFieldsPersOrder, lengthResolver, members);

		/* (21.10.2014 TM)XXX: binary handler declaration order or persistent order?
		 * if pseudo field definitions are in persistent order, why does the generic handler use declaration order?
		 * Is it necessary (e.g. validation with actual class) or arbitrary?
		 * Maybe keep both orders?
		 *
		 * Would a change in declaration order that results in the same persistent order matter in the first place?
		 * Value handlers are derived dynamically. As long as it results in the same persistent order,
		 * everything is fine.
		 */
		this.members = members.immure();
	}

	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	public XGettingEnum<Field> getInstanceFields()
	{
		return this.allFields;
	}

	@Override
	public XGettingEnum<Field> getInstancePrimitiveFields()
	{
		return this.prmFields;
	}

	@Override
	public XGettingEnum<Field> getInstanceReferenceFields()
	{
		return this.refFields;
	}
	
	@Override
	public final boolean isPrimitiveType()
	{
		return false;
	}
	
	@Override
	public XGettingSequence<? extends PersistenceTypeDescriptionMember> members()
	{
		return this.members;
	}

	@Override
	public final boolean hasInstanceReferences()
	{
		return this.hasReferences;
	}
	
	@Override
	public final boolean hasPersistedReferences()
	{
		return this.hasReferences;
	}
	
	@Override
	public final boolean hasPersistedVariableLength()
	{
		return false;
	}

	@Override
	public final boolean hasVaryingPersistedLengthInstances()
	{
		return false;
	}

	@Override
	public final ObjectStateDescriptor<T> getStateDescriptor()
	{
		return this;
	}

	@Override
	public void store(final Binary bytes, final T instance, final long objectId, final SwizzleStoreLinker linker)
	{
		BinaryPersistence.storeFixedSize(
			bytes            ,
			linker        ,
			this.binaryLength,
			this.typeId()    ,
			objectId         ,
			instance         ,
			this.allMemOfs   ,
			this.binStorers
		);
	}

	@Override
	public abstract T create(final Binary bytes);

	@Override
	public void update(final Binary bytes, final T instance, final SwizzleBuildLinker builder)
	{
		/* must explicitely check type to avoid memory getting overwritten with bytes not fitting to the actual type
		 * this can be especially critical and important if a custom PersistenceRootResolver returns an instance
		 * that does not match the type defined by the typeId.
		 */
		if(!this.type().isInstance(instance))
		{
			/*
			 * I wished they knew how to write proper exceptions (pass class and instance) instead of cramming
			 * everything into contextless and expensive plain strings. Noobs.
			 * So, as usual, a new and proper exception type is needed.
			 * Also, while I'm on it, since not all types are classes (there are interfaces, too, which the JDK
			 * guys apparently don't know, which is especially hilarious), the correct term for such an exception type
			 * is "TypeCastException", not "ClassCastException".
			 */
			throw new TypeCastException(this.type(), instance);
		}

		BinaryPersistence.updateFixedSize(
			instance,
			this.memSetters,
			this.allMemOfs,
			bytes.buildItemAddress(),
			builder
		);
	}

	@Override
	public final void complete(final Binary medium, final T instance, final SwizzleBuildLinker builder)
	{
		// no-op for normal implementation (see non-reference-hashing collections for other examples)
	}

	@Override
	public void iterateInstanceReferences(final T instance, final SwizzleFunction iterator)
	{
		BinaryPersistence.iterateInstanceReferences(iterator, instance, this.refMemOfs);
	}

	@Override
	public void iteratePersistedReferences(final Binary bytes, final _longProcedure iterator)
	{
		BinaryPersistence.iterateBinaryReferences(
			bytes,
			BinaryPersistence.entityDataOffset(this.refBinStartOffset),
			BinaryPersistence.entityDataOffset(this.refBinBoundOffset),
			iterator
		);
	}

	void validate(final Field describedField, final long index)
	{
		if(!this.allFields.at(index).equals(describedField))
		{
			throw new PersistenceExceptionTypeConsistencyDefinitionValidationFieldMismatch(
				this.allFields.at(index),
				describedField
			);
		}
	}

	@Override
	public void validateFields(final XGettingSequence<Field> fieldDescriptions)
		throws SwizzleExceptionConsistency
	{
		fieldDescriptions.iterateIndexed(this::validate);
	}

	@Override
	public boolean isEqual(final T source, final T target, final ObjectStateHandlerLookup instanceStateHandlerLookup)
	{
		/* (09.06.2017 TM)NOTE:
		 * the whole concept of generic equality checks via binary handler implementations has never been used
		 * so far and is still experimental code. The only reason the code is not completely removed is that there
		 * is already a lot of implemented code and structures that could be used in the future.
		 */
		throw new UnsupportedOperationException();
	}

}